<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { open as openDialog, save } from "@tauri-apps/plugin-dialog";
import { openPath } from "@tauri-apps/plugin-opener";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import { join, tempDir } from "@tauri-apps/api/path";
import { useAccountsStore } from "../stores/accounts";
import { useFilesStore } from "../stores/files";
import { useUiStore } from "../stores/ui";
import { api, invokeError, type BulkTarget, type WebDavEntry } from "../lib/ipc";
import { translate } from "../lib/i18n";
import { formatBytes } from "../lib/format";

const accounts = useAccountsStore();
const files = useFilesStore();
const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const viewMode = ref<"list" | "grid">("list");
const sortKey = ref<"name" | "size" | "mtime">("name");
const sortAsc = ref(true);
const selected = ref<Set<string>>(new Set());
const ctxMenu = ref<{ x: number; y: number; entry: WebDavEntry } | null>(null);
const busyPath = ref<string | null>(null);
const showNewFolder = ref(false);
const renameTarget = ref<WebDavEntry | null>(null);
const nameInput = ref("");
const draggingOver = ref(false);
let unlistenDragDrop: (() => void) | null = null;

function formatMtime(mtime: string | null): string {
  if (!mtime) return "—";
  const date = new Date(mtime);
  return isNaN(date.getTime()) ? mtime : date.toLocaleString();
}

const sortedEntries = computed(() => {
  const dirs = files.entries.filter((e) => e.isDir);
  const others = files.entries.filter((e) => !e.isDir);
  const cmp = (a: WebDavEntry, b: WebDavEntry): number => {
    if (sortKey.value === "size") {
      const av = a.size ?? 0;
      const bv = b.size ?? 0;
      return sortAsc.value ? av - bv : bv - av;
    }
    if (sortKey.value === "mtime") {
      const av = a.mtime ? new Date(a.mtime).getTime() : 0;
      const bv = b.mtime ? new Date(b.mtime).getTime() : 0;
      return sortAsc.value ? av - bv : bv - av;
    }
    const av = a.name.toLowerCase();
    const bv = b.name.toLowerCase();
    return sortAsc.value ? av.localeCompare(bv) : bv.localeCompare(av);
  };
  dirs.sort(cmp);
  others.sort(cmp);
  return [...dirs, ...others];
});

function isSelected(path: string): boolean {
  return selected.value.has(path);
}

function toggleSelect(path: string) {
  const next = new Set(selected.value);
  if (next.has(path)) next.delete(path);
  else next.add(path);
  selected.value = next;
}

const allSelected = computed(
  () =>
    files.entries.length > 0 &&
    files.entries.every((e) => selected.value.has(e.path))
);

function toggleSelectAll() {
  if (allSelected.value) clearSelection();
  else selected.value = new Set(files.entries.map((e) => e.path));
}

function clearSelection() {
  selected.value = new Set();
}

const selectedTargets = computed<BulkTarget[]>(() =>
  files.entries
    .filter((e) => selected.value.has(e.path))
    .map((e) => ({ path: e.path, isDir: e.isDir }))
);

async function bulkDownload() {
  if (busyPath.value) return;
  busyPath.value = "bulk-download";
  try {
    const dest = await openDialog({ directory: true });
    if (typeof dest !== "string") return;
    await files.bulkDownload(selectedTargets.value, dest);
    files.clearTransfer();
    ui.toast(t("fileDownloaded"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    busyPath.value = null;
  }
}

async function bulkDelete() {
  if (busyPath.value) return;
  busyPath.value = "bulk-delete";
  const count = selected.value.size;
  if (!window.confirm(t("deleteSelectedConfirm").replace("{count}", String(count)))) return;
  try {
    await files.bulkDelete([...selected.value]);
    files.clearTransfer();
    clearSelection();
    ui.toast(t("fileDeleted"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    busyPath.value = null;
  }
}

async function dropUpload(paths: string[]) {
  if (busyPath.value || paths.length === 0) return;
  busyPath.value = "drop";
  try {
    await files.uploadLocalPaths(paths);
    files.clearTransfer();
    ui.toast(t("fileUploaded"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    busyPath.value = null;
  }
}

function toggleSort(key: "name" | "size" | "mtime") {
  if (sortKey.value === key) sortAsc.value = !sortAsc.value;
  else {
    sortKey.value = key;
    sortAsc.value = true;
  }
}

function openCtx(e: MouseEvent, entry: WebDavEntry) {
  e.preventDefault();
  ctxMenu.value = { x: e.clientX, y: e.clientY, entry };
}

function closeCtx() {
  ctxMenu.value = null;
}

async function open(entry: WebDavEntry) {
  if (entry.isDir) {
    await files.navigate(entry.path);
    return;
  }
  if (busyPath.value) return;
  busyPath.value = entry.path;
  try {
    const dir = await tempDir();
    const dest = await join(dir, entry.name);
    await files.downloadFile(entry.path, dest);
    await openPath(dest);
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    busyPath.value = null;
  }
}

async function download(entry: WebDavEntry) {
  if (busyPath.value) return;
  busyPath.value = entry.path;
  try {
    const dest = await save({ defaultPath: entry.name });
    if (typeof dest !== "string") return;
    await files.downloadFile(entry.path, dest);
    ui.toast(t("fileDownloaded"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    busyPath.value = null;
  }
}

async function uploadFiles() {
  if (busyPath.value) return;
  busyPath.value = "";
  try {
    const picked = await openDialog({ multiple: true });
    if (!picked) return;
    const list = typeof picked === "string" ? [picked] : picked;
    for (const local of list) {
      const name = local.split(/[\\/]/).pop() ?? "file";
      const remote =
        (files.currentPath === "/" ? "" : files.currentPath) + "/" + name;
      await files.uploadFile(local, remote);
    }
    ui.toast(t("fileUploaded"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    busyPath.value = null;
  }
}

async function createFolder() {
  const name = nameInput.value.trim();
  if (!name) return;
  try {
    await files.createFolder(name);
    ui.toast(t("folderCreated"), "success");
    showNewFolder.value = false;
    nameInput.value = "";
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  }
}

function startRename(entry: WebDavEntry) {
  renameTarget.value = entry;
  nameInput.value = entry.name;
}

async function doRename() {
  const target = renameTarget.value;
  const name = nameInput.value.trim();
  if (!target || !name || name === target.name) {
    renameTarget.value = null;
    return;
  }
  try {
    await files.renameEntry(target.path, name);
    const parent = target.path.slice(0, target.path.lastIndexOf("/"));
    const newPath = parent + "/" + name;
    if (selected.value.has(target.path)) {
      const next = new Set(selected.value);
      next.delete(target.path);
      next.add(newPath);
      selected.value = next;
    }
    ui.toast(t("fileRenamed"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    renameTarget.value = null;
  }
}

async function removeEntry(entry: WebDavEntry) {
  if (!window.confirm(t("deleteConfirm").replace("{name}", entry.name))) return;
  try {
    await files.deleteEntry(entry.path);
    selected.value.delete(entry.path);
    ui.toast(t("fileDeleted"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  }
}

const shareState = reactive(
  new Map<string, { status: "loading" | "done" | "error"; value?: string }>()
);

function shareStatus(path: string) {
  return shareState.get(path);
}

async function createLink(entry: WebDavEntry) {
  shareState.set(entry.path, { status: "loading" });
  try {
    const url = await files.createShare(entry.path);
    shareState.set(entry.path, { status: "done", value: url });
    await navigator.clipboard.writeText(url);
    ui.toast(t("linkCopied"), "success");
  } catch {
    shareState.set(entry.path, { status: "error" });
  }
}

const adminUsers = ref<string[]>([]);
const adminViewAll = ref(true);
const selectedUser = ref<string>("");

async function loadAdminUsers() {
  if (!accounts.active?.isAdmin) return;
  try {
    adminUsers.value = await api.adminListUsers("");
    if (adminViewAll.value) {
      if (files.targetUser) {
        selectedUser.value = files.targetUser;
      } else if (!selectedUser.value && adminUsers.value.length) {
        const me = accounts.active.username;
        selectedUser.value =
          adminUsers.value.find((u) => u === me) ?? adminUsers.value[0];
        files.setTargetUser(selectedUser.value);
      }
    }
  } catch {
    // user list unavailable; impersonation still selectable via retry button
  }
}

function setAdminView(all: boolean) {
  adminViewAll.value = all;
  if (all) {
    if (!selectedUser.value && adminUsers.value.length) {
      const me = accounts.active?.username ?? "";
      selectedUser.value = adminUsers.value.find((u) => u === me) ?? adminUsers.value[0];
    }
    if (selectedUser.value) files.setTargetUser(selectedUser.value);
  } else {
    selectedUser.value = "";
    files.setTargetUser(null);
  }
}

function onUserSelect() {
  if (selectedUser.value) files.setTargetUser(selectedUser.value);
}

watch(
  () => files.currentPath,
  () => clearSelection()
);

onMounted(async () => {
  if (accounts.active) {
    await files.refresh();
    void loadAdminUsers();
  }
  void files.bindProgress();
  unlistenDragDrop = await getCurrentWebview().onDragDropEvent((event) => {
    const payload = event.payload;
    if (payload.type === "enter") {
      draggingOver.value = payload.paths.length > 0;
    } else if (payload.type === "over") {
      draggingOver.value = true;
    } else if (payload.type === "leave") {
      draggingOver.value = false;
    } else if (payload.type === "drop") {
      draggingOver.value = false;
      void dropUpload(payload.paths);
    }
  });
});

onUnmounted(() => {
  unlistenDragDrop?.();
});

watch(
  () => accounts.active?.username,
  async () => {
    shareState.clear();
    adminViewAll.value = true;
    selectedUser.value = "";
    await files.reset();
    void loadAdminUsers();
  }
);
</script>

<template>
  <div
    class="relative flex h-full flex-col"
    @click="closeCtx"
    @dragover.prevent
    @drop.prevent
  >
    <div
      v-if="draggingOver"
      class="pointer-events-none absolute inset-0 z-40 flex items-center justify-center border-2 border-dashed border-indigo-500 bg-indigo-950/40"
    >
      <p class="rounded-lg bg-zinc-900 px-4 py-2 text-sm text-indigo-200">{{ t("dropToUpload") }}</p>
    </div>
    <div class="flex items-center justify-between gap-3 border-b border-zinc-800 px-6 py-3">
      <nav class="flex min-w-0 items-center gap-1 text-sm">
        <template v-for="(crumb, i) in files.crumbs" :key="crumb.path">
          <button
            class="rounded px-1.5 py-0.5 hover:bg-zinc-800 hover:text-zinc-50"
            :class="i === files.crumbs.length - 1 ? 'font-semibold text-zinc-50' : 'text-zinc-400'"
            @click="files.navigate(crumb.path)"
          >
            {{ crumb.path === "/" ? t("home") : crumb.label }}
          </button>
          <span v-if="i < files.crumbs.length - 1" class="text-zinc-600">/</span>
        </template>
      </nav>

      <div class="flex shrink-0 items-center gap-2">
        <div class="flex overflow-hidden rounded-md border border-zinc-700">
          <button
            class="px-2.5 py-1 text-xs transition"
            :class="viewMode === 'list' ? 'bg-zinc-800 text-zinc-50' : 'text-zinc-400 hover:text-zinc-50'"
            :title="t('viewList')"
            @click="viewMode = 'list'"
          >
            ☰
          </button>
          <button
            class="px-2.5 py-1 text-xs transition"
            :class="viewMode === 'grid' ? 'bg-zinc-800 text-zinc-50' : 'text-zinc-400 hover:text-zinc-50'"
            :title="t('viewGrid')"
            @click="viewMode = 'grid'"
          >
            ▦
          </button>
        </div>
        <button
          class="rounded-md border border-zinc-700 px-3 py-1 text-sm text-zinc-300 hover:bg-zinc-800"
          @click="files.refresh"
        >
          {{ t("refresh") }}
        </button>
        <button
          class="rounded-md border border-zinc-700 px-3 py-1 text-sm text-zinc-300 hover:bg-zinc-800"
          @click="showNewFolder = true"
        >
          + {{ t("newFolder") }}
        </button>
        <button
          class="rounded-md bg-indigo-600 px-3 py-1 text-sm font-medium text-white hover:bg-indigo-500"
          :disabled="busyPath !== null"
          @click="uploadFiles"
        >
          {{ t("upload") }}
        </button>
      </div>
    </div>

    <div
      v-if="accounts.active?.isAdmin"
      class="flex flex-wrap items-center gap-3 border-b border-zinc-800 bg-zinc-900/40 px-6 py-2"
    >
      <div class="flex overflow-hidden rounded-md border border-zinc-700">
        <button
          class="px-3 py-1.5 text-xs font-medium transition"
          :class="adminViewAll ? 'bg-indigo-600 text-white' : 'text-zinc-300 hover:bg-zinc-800'"
          @click="setAdminView(true)"
        >
          {{ t("allUsersFolders") }}
        </button>
        <button
          class="px-3 py-1.5 text-xs font-medium transition"
          :class="!adminViewAll ? 'bg-indigo-600 text-white' : 'text-zinc-300 hover:bg-zinc-800'"
          @click="setAdminView(false)"
        >
          {{ t("myFilesOnly") }}
        </button>
      </div>

      <template v-if="adminViewAll">
        <div class="flex items-center gap-2">
          <span class="text-xs text-zinc-500">{{ t("filterUser") }}</span>
          <select
            v-model="selectedUser"
            class="rounded-md border border-zinc-700 bg-zinc-800 px-2 py-1.5 text-xs text-zinc-50 focus:border-indigo-500 focus:outline-none"
            @change="onUserSelect"
          >
            <option v-if="!selectedUser" value="" disabled>{{ t("users") }}…</option>
            <option v-for="userId in adminUsers" :key="userId" :value="userId">
              {{ userId }}
            </option>
          </select>
        </div>
        <button
          v-if="!adminUsers.length"
          class="text-xs text-indigo-300 underline-offset-2 hover:underline"
          @click="loadAdminUsers"
        >
          {{ t("refresh") }}
        </button>
      </template>
    </div>

    <div
      v-if="files.targetUser"
      class="flex items-center gap-2 border-b border-sky-900 bg-sky-950/40 px-6 py-1.5 text-xs text-sky-300"
    >
      <span class="shrink-0 opacity-80">{{ t("impersonationNotice") }}</span>
      <span class="truncate font-semibold">{{ files.targetUser }}</span>
    </div>

    <div v-if="files.error" class="m-4 rounded-md border border-red-800 bg-red-950/50 px-3 py-2 text-sm text-red-300">
      {{ files.error }}
    </div>

    <div v-if="selected.size > 0 || files.entries.length > 0" class="flex items-center gap-3 border-b border-zinc-800 bg-indigo-950/30 px-6 py-1.5 text-xs text-indigo-200">
      <label class="flex cursor-pointer items-center gap-1.5 select-none">
        <input
          type="checkbox"
          class="accent-indigo-500"
          :checked="allSelected"
          @change="toggleSelectAll"
        />
        <span>{{ t("selectAll") }}</span>
      </label>
      <template v-if="selected.size > 0">
        <span>{{ selected.size }} {{ t("selected") }}</span>
        <button
          class="rounded-md border border-indigo-700 px-2 py-0.5 text-indigo-200 hover:bg-indigo-900/40"
          :disabled="busyPath !== null"
          @click="bulkDownload"
        >
          {{ t("download") }}
        </button>
        <button
          class="rounded-md border border-red-800 px-2 py-0.5 text-red-300 hover:bg-red-950/40"
          :disabled="busyPath !== null"
          @click="bulkDelete"
        >
          {{ t("delete") }}
        </button>
        <button class="underline-offset-2 hover:underline" @click="clearSelection">
          {{ t("clear") }}
        </button>
      </template>
      <span v-if="busyPath !== null" class="ml-auto text-zinc-400">{{ t("working") }}</span>
    </div>

    <div
      v-if="files.transfer"
      class="flex items-center gap-3 border-b border-zinc-800 bg-zinc-900/60 px-6 py-2 text-xs text-zinc-400"
    >
      <span class="max-w-48 truncate">
        {{ t(files.transfer.direction === "upload" ? "uploading" : files.transfer.direction === "download" ? "downloading" : "deleting") }}
        {{ files.transfer.path }}
      </span>
      <span v-if="files.transfer.totalFiles > 1" class="shrink-0">
        {{ files.transfer.index + 1 }} / {{ files.transfer.totalFiles }}
      </span>
      <div class="h-1.5 min-w-24 flex-1 overflow-hidden rounded-full bg-zinc-800">
        <div
          class="h-full bg-indigo-500 transition-[width]"
          :style="{ width: files.transfer.percent + '%' }"
        ></div>
      </div>
      <span class="w-10 shrink-0 text-right">{{ files.transfer.percent.toFixed(0) }}%</span>
    </div>

    <div v-if="files.loading && files.entries.length === 0" class="m-auto text-zinc-500">
      {{ t("connecting") }}
    </div>

    <div v-else-if="files.entries.length === 0" class="m-auto text-center text-zinc-500">
      <p class="text-lg">{{ t("folderEmptyTitle") }}</p>
    </div>

    <!-- List view -->
    <div v-else-if="viewMode === 'list'" class="flex-1 overflow-y-auto px-4 py-2">
      <table class="w-full text-sm">
        <thead>
          <tr class="text-left text-xs uppercase tracking-wide text-zinc-500">
            <th class="w-8 px-3 py-2"></th>
            <th class="px-3 py-2 font-medium">
              <button class="uppercase tracking-wide hover:text-zinc-50" @click="toggleSort('name')">
                {{ t("name") }} {{ sortKey === "name" ? (sortAsc ? "▲" : "▼") : "" }}
              </button>
            </th>
            <th class="w-28 px-3 py-2 font-medium">
              <button class="uppercase tracking-wide hover:text-zinc-50" @click="toggleSort('size')">
                {{ t("size") }} {{ sortKey === "size" ? (sortAsc ? "▲" : "▼") : "" }}
              </button>
            </th>
            <th class="w-44 px-3 py-2 font-medium">
              <button class="uppercase tracking-wide hover:text-zinc-50" @click="toggleSort('mtime')">
                {{ t("modified") }} {{ sortKey === "mtime" ? (sortAsc ? "▲" : "▼") : "" }}
              </button>
            </th>
            <th class="w-24 px-3 py-2 font-medium">{{ t("kind") }}</th>
            <th class="w-32 px-3 py-2 font-medium"></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="entry in sortedEntries"
            :key="entry.path"
            class="border-t border-zinc-800/60 hover:bg-zinc-800/40"
            :class="isSelected(entry.path) ? 'bg-indigo-950/40' : ''"
            @contextmenu="openCtx($event, entry)"
          >
            <td class="px-3 py-2">
              <input
                type="checkbox"
                class="accent-indigo-500"
                :checked="isSelected(entry.path)"
                @change="toggleSelect(entry.path)"
              />
            </td>
            <td class="px-3 py-2">
              <button class="flex items-center gap-2 text-left text-zinc-200 hover:text-zinc-50" @click="open(entry)">
                <span class="w-5 text-center">
                  <template v-if="entry.isDir">📁</template>
                  <template v-else>📄</template>
                </span>
                <span class="truncate">{{ entry.name }}</span>
              </button>
            </td>
            <td class="px-3 py-2 text-zinc-400">{{ entry.isDir ? "—" : formatBytes(entry.size) }}</td>
            <td class="px-3 py-2 text-zinc-400">{{ formatMtime(entry.mtime) }}</td>
            <td class="px-3 py-2">
              <span
                v-if="entry.isResource"
                class="rounded bg-sky-900/60 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-sky-300"
              >
                {{ t("resource") }}
              </span>
              <span
                v-else-if="entry.isPart"
                class="rounded bg-emerald-900/60 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-emerald-300"
              >
                {{ t("part") }}
              </span>
              <span v-else class="text-xs text-zinc-600">{{ t("sync") }}</span>
            </td>
            <td class="px-3 py-2 text-right">
              <span v-if="shareStatus(entry.path)?.status === 'loading'" class="text-xs text-zinc-500">…</span>
              <span
                v-else-if="shareStatus(entry.path)?.status === 'done'"
                class="text-xs text-emerald-400"
                :title="t('linkCopied')"
              >
                ✓
              </span>
              <span v-else-if="shareStatus(entry.path)?.status === 'error'" class="text-xs text-red-400">✗</span>
              <button
                v-else
                class="rounded-md border border-zinc-700 px-2 py-0.5 text-xs text-zinc-300 hover:bg-zinc-800"
                @click.stop="createLink(entry)"
              >
                {{ t("link") }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Grid view -->
    <div v-else class="flex-1 overflow-y-auto p-4">
      <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
        <div
          v-for="entry in sortedEntries"
          :key="entry.path"
          class="flex cursor-default flex-col items-center gap-1 rounded-lg border p-3 text-center transition"
          :class="isSelected(entry.path) ? 'border-indigo-600 bg-indigo-950/40' : 'border-zinc-800 bg-zinc-900 hover:bg-zinc-800/60'"
          @contextmenu="openCtx($event, entry)"
          @dblclick="open(entry)"
        >
          <input
            type="checkbox"
            class="accent-indigo-500"
            :checked="isSelected(entry.path)"
            @change="toggleSelect(entry.path)"
          />
          <span class="text-3xl">{{ entry.isDir ? "📁" : "📄" }}</span>
          <p class="w-full truncate text-xs text-zinc-200" :title="entry.name">{{ entry.name }}</p>
          <p class="w-full truncate text-[10px] text-zinc-500">
            {{ entry.isDir ? "—" : formatBytes(entry.size) }}
          </p>
          <div class="flex gap-1">
            <button
              class="rounded border border-zinc-700 px-1.5 py-0.5 text-[10px] text-zinc-300 hover:bg-zinc-800"
              @click.stop="open(entry)"
            >
              {{ t("open") }}
            </button>
            <button
              class="rounded border border-zinc-700 px-1.5 py-0.5 text-[10px] text-zinc-300 hover:bg-zinc-800"
              @click.stop="startRename(entry)"
            >
              {{ t("rename") }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Context menu -->
    <div
      v-if="ctxMenu"
      class="fixed z-50 w-44 overflow-hidden rounded-lg border border-zinc-700 bg-zinc-900 py-1 shadow-2xl"
      :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
      @click.stop
    >
      <button
        class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm text-zinc-200 hover:bg-zinc-800"
        @click="open(ctxMenu.entry); ctxMenu = null"
      >
        {{ t("open") }}
      </button>
      <button
        v-if="!ctxMenu.entry.isDir"
        class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm text-zinc-200 hover:bg-zinc-800"
        @click="download(ctxMenu.entry); ctxMenu = null"
      >
        {{ t("download") }}
      </button>
      <button
        class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm text-zinc-200 hover:bg-zinc-800"
        @click="startRename(ctxMenu.entry); ctxMenu = null"
      >
        {{ t("rename") }}
      </button>
      <button
        class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm text-zinc-200 hover:bg-zinc-800"
        @click="createLink(ctxMenu.entry); ctxMenu = null"
      >
        {{ t("link") }}
      </button>
      <div class="my-1 border-t border-zinc-800"></div>
      <button
        class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm text-red-300 hover:bg-red-950/40"
        @click="removeEntry(ctxMenu.entry); ctxMenu = null"
      >
        {{ t("delete") }}
      </button>
    </div>

    <!-- New folder dialog -->
    <div
      v-if="showNewFolder"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      @click.self="showNewFolder = false"
    >
      <form
        class="w-full max-w-xs rounded-xl border border-zinc-700 bg-zinc-900 p-5 shadow-2xl"
        @submit.prevent="createFolder"
      >
        <h3 class="mb-3 text-base font-semibold text-zinc-50">{{ t("newFolder") }}</h3>
        <input
          v-model="nameInput"
          :placeholder="t('folderName')"
          autofocus
          class="mb-4 w-full rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-50 placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
        />
        <div class="flex gap-2">
          <button
            type="button"
            class="flex-1 rounded-md bg-zinc-800 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-700"
            @click="showNewFolder = false"
          >
            {{ t("cancel") }}
          </button>
          <button
            type="submit"
            class="flex-1 rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500"
          >
            {{ t("create") }}
          </button>
        </div>
      </form>
    </div>

    <!-- Rename dialog -->
    <div
      v-if="renameTarget"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      @click.self="renameTarget = null"
    >
      <form
        class="w-full max-w-xs rounded-xl border border-zinc-700 bg-zinc-900 p-5 shadow-2xl"
        @submit.prevent="doRename"
      >
        <h3 class="mb-3 text-base font-semibold text-zinc-50">{{ t("rename") }}</h3>
        <input
          v-model="nameInput"
          autofocus
          class="mb-4 w-full rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-50 placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
        />
        <div class="flex gap-2">
          <button
            type="button"
            class="flex-1 rounded-md bg-zinc-800 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-700"
            @click="renameTarget = null"
          >
            {{ t("cancel") }}
          </button>
          <button
            type="submit"
            class="flex-1 rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500"
          >
            {{ t("save") }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
