<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { useAccountsStore } from "../stores/accounts";
import { useFilesStore } from "../stores/files";
import { useUiStore } from "../stores/ui";
import { api, type WebDavEntry } from "../lib/ipc";
import { translate } from "../lib/i18n";
import { formatBytes } from "../lib/format";

const accounts = useAccountsStore();
const files = useFilesStore();
const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

function formatMtime(mtime: string | null): string {
  if (!mtime) return "—";
  const date = new Date(mtime);
  return isNaN(date.getTime()) ? mtime : date.toLocaleString();
}

async function open(entry: WebDavEntry) {
  if (entry.isDir) await files.navigate(entry.path);
}

const shareState = reactive(new Map<string, { status: "loading" | "done" | "error"; value?: string }>());

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
    if (adminViewAll.value && !selectedUser.value && adminUsers.value.length) {
      const me = accounts.active.username;
      selectedUser.value =
        adminUsers.value.find((u) => u === me) ?? adminUsers.value[0];
      files.setTargetUser(selectedUser.value);
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

onMounted(async () => {
  if (accounts.active) {
    await files.refresh();
    void loadAdminUsers();
  }
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
  <div class="flex h-full flex-col">
    <div class="flex items-center justify-between gap-3 border-b border-zinc-800 px-6 py-3">
      <nav class="flex min-w-0 items-center gap-1 text-sm">
        <template v-for="(crumb, i) in files.crumbs" :key="crumb.path">
          <button
            class="rounded px-1.5 py-0.5 hover:bg-zinc-800 hover:text-white"
            :class="i === files.crumbs.length - 1 ? 'font-semibold text-white' : 'text-zinc-400'"
            @click="files.navigate(crumb.path)"
          >
            {{ crumb.label }}
          </button>
          <span v-if="i < files.crumbs.length - 1" class="text-zinc-600">/</span>
        </template>
      </nav>
      <button
        class="shrink-0 rounded-md border border-zinc-700 px-3 py-1 text-sm text-zinc-300 hover:bg-zinc-800"
        @click="files.refresh"
      >
        {{ t("refresh") }}
      </button>
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
            class="rounded-md border border-zinc-700 bg-zinc-800 px-2 py-1.5 text-xs text-white focus:border-indigo-500 focus:outline-none"
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

    <div v-if="files.loading && files.entries.length === 0" class="m-auto text-zinc-500">
      {{ t("connecting") }}
    </div>

    <div v-else-if="files.entries.length === 0" class="m-auto text-center text-zinc-500">
      <p class="text-lg">{{ t("folderEmptyTitle") }}</p>
    </div>

    <div v-else class="flex-1 overflow-y-auto px-4 py-2">
      <table class="w-full text-sm">
        <thead>
          <tr class="text-left text-xs uppercase tracking-wide text-zinc-500">
            <th class="px-3 py-2 font-medium">{{ t("name") }}</th>
            <th class="w-32 px-3 py-2 font-medium">{{ t("size") }}</th>
            <th class="w-44 px-3 py-2 font-medium">{{ t("modified") }}</th>
            <th class="w-24 px-3 py-2 font-medium">{{ t("kind") }}</th>
            <th class="w-24 px-3 py-2 font-medium"></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="entry in files.entries"
            :key="entry.path"
            class="border-t border-zinc-800/60 hover:bg-zinc-800/40"
          >
            <td class="px-3 py-2">
              <button class="flex items-center gap-2 text-left text-zinc-200 hover:text-white" @click="open(entry)">
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
                @click="createLink(entry)"
              >
                {{ t("link") }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
