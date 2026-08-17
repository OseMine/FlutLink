<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { open as openDialog, save } from "@tauri-apps/plugin-dialog";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import { useAccountsStore } from "../stores/accounts";
import { useFilesStore } from "../stores/files";
import { useUiStore } from "../stores/ui";
import { api, invokeError, type AppErrorLike, type BulkTarget, type CreateShareOptions, type Share, type WebDavEntry } from "../lib/ipc";
import { translate } from "../lib/i18n";
import Icon from "./Icon.vue";
import EntryList from "./EntryList.vue";

const accounts = useAccountsStore();
const files = useFilesStore();
const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const viewMode = ref<"list" | "grid">("list");
const selected = ref<Set<string>>(new Set());
const ctxMenu = ref<{ x: number; y: number; entry: WebDavEntry } | null>(null);
const busyPath = ref<string | null>(null);
const uploading = ref(false);
const showNewFolder = ref(false);
const renameTarget = ref<WebDavEntry | null>(null);
const nameInput = ref("");
const draggingOver = ref(false);
const kbdIndex = ref(-1);
const thumbs = reactive(new Map<string, string>());
const thumbLoading = new Set<string>();
const searchInput = ref("");
let searchTimer: ReturnType<typeof setTimeout> | null = null;
const emptySelection = new Set<string>();
let unlistenDragDrop: (() => void) | null = null;

const isSearching = computed(() => files.searchQuery.length > 0);

const sortKey = ref<"name" | "size" | "mtime">("name");
const sortAsc = ref(true);

const sortedEntries = computed(() => {
  const dirs = files.displayEntries.filter((e) => e.isDir);
  const others = files.displayEntries.filter((e) => !e.isDir);
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

function toggleSort(key: "name" | "size" | "mtime") {
  if (sortKey.value === key) sortAsc.value = !sortAsc.value;
  else {
    sortKey.value = key;
    sortAsc.value = true;
  }
}

watch(searchInput, (value) => {
  if (searchTimer) clearTimeout(searchTimer);
  const q = value.trim();
  if (!q) {
    files.clearSearch();
    return;
  }
  searchTimer = setTimeout(() => {
    void runSearch();
  }, 300);
});

async function runSearch() {
  try {
    await files.searchFiles(searchInput.value);
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  }
}

function clearSearchInput() {
  searchInput.value = "";
  files.clearSearch();
}

function toggleSelect(path: string) {
  const next = new Set(selected.value);
  if (next.has(path)) next.delete(path);
  else next.add(path);
  selected.value = next;
}

const allSelected = computed(
  () =>
    files.displayEntries.length > 0 &&
    files.displayEntries.every((e) => selected.value.has(e.path))
);

function toggleSelectAll() {
  if (allSelected.value) clearSelection();
  else selected.value = new Set(files.displayEntries.map((e) => e.path));
}

function clearSelection() {
  selected.value = new Set();
}

const selectedTargets = computed<BulkTarget[]>(() =>
  files.displayEntries
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
    if ((e as AppErrorLike)?.code === "target_exists") {
      // Q9: never silently overwrite an existing remote file. Ask first and
      // only retry the whole batch with overwrite once the user agrees.
      if (window.confirm(t("uploadOverwriteAllConfirm"))) {
        try {
          await files.uploadLocalPaths(paths, true);
          files.clearTransfer();
          ui.toast(t("fileUploaded"), "success");
        } catch (e2) {
          ui.toast(invokeError(e2).message, "error");
        }
      }
      return;
    }
    ui.toast(invokeError(e).message, "error");
  } finally {
    busyPath.value = null;
  }
}

function openCtx(e: MouseEvent, entry: WebDavEntry) {
  e.preventDefault();
  if (!selected.value.has(entry.path)) {
    selected.value = new Set([entry.path]);
  }
  ctxMenu.value = { x: e.clientX, y: e.clientY, entry };
}

function closeCtx() {
  ctxMenu.value = null;
}

async function open(entry: WebDavEntry) {
  if (entry.isDir) {
    if (isSearching.value) clearSearchInput();
    await files.navigate(entry.path);
    return;
  }
  if (busyPath.value) return;
  busyPath.value = entry.path;
  try {
    await api.openRemoteFile(entry.path, files.targetUser ?? undefined);
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    busyPath.value = null;
  }
}

/// Jump to the counterpart of a single entry (`/resources/…` ↔ `/parts/…`).
function goToPaired(entry: WebDavEntry) {
  if (entry.pairedPath) void files.navigate(entry.pairedPath);
}

/// Jump to an arbitrary path (used by the pairing bar / split-view swap).
function goToPath(path: string | null) {
  if (path) void files.navigate(path);
}

/// "virtual" (read-only `resources`) or "real" (write-enabled `parts`) pane
/// label for a path inside the FlutCloud virtual namespaces, else `null`.
function paneKind(path: string | null): "virtual" | "real" | null {
  if (!path) return null;
  for (const seg of path.split("/")) {
    if (seg.toLowerCase() === "resources") return "virtual";
    if (seg.toLowerCase() === "parts") return "real";
  }
  return null;
}

function paneLabel(kind: "virtual" | "real" | null): string {
  return kind === "virtual" ? t("virtualPane") : t("realPane");
}

async function download(entry: WebDavEntry) {
  if (busyPath.value) return;
  busyPath.value = entry.path;
  try {
    const dest = await save({ defaultPath: entry.name });
    if (typeof dest !== "string") return;
    await files.downloadFile(entry.path, dest);
    files.clearTransfer();
    ui.toast(t("fileDownloaded"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    busyPath.value = null;
  }
}

/// Download a folder as a ZIP archive (Nextcloud `Accept: application/zip`
/// WebDAV extension).
async function downloadZip(entry: WebDavEntry) {
  if (busyPath.value) return;
  busyPath.value = entry.path;
  try {
    const dest = await save({ defaultPath: entry.name + ".zip" });
    if (typeof dest !== "string") return;
    await files.downloadZip(entry.path, dest);
    files.clearTransfer();
    ui.toast(t("fileDownloaded"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    busyPath.value = null;
  }
}

/// U-R8-6: keep `thumbs`/`shareState` bounded across folder navigation. Only
/// entries inside the current folder stay relevant; the `currentPath` watcher
/// runs before `files.entries` are refreshed, so pruning goes by path prefix.
function isInCurrentFolder(path: string): boolean {
  const prefix = files.currentPath === "/" ? "/" : files.currentPath + "/";
  return path.startsWith(prefix);
}

function pruneCaches() {
  for (const path of thumbs.keys()) {
    if (!isInCurrentFolder(path)) thumbs.delete(path);
  }
  for (const path of shareState.keys()) {
    if (!isInCurrentFolder(path)) shareState.delete(path);
  }
}

/// Best-effort thumbnail fetch for image entries (Nextcloud
/// `/core/preview.png`). Failures fall back to the generic file icon.
async function loadThumb(entry: WebDavEntry) {
  if (entry.isDir || !entry.contentType?.startsWith("image/")) return;
  if (thumbs.has(entry.path) || thumbLoading.has(entry.path)) return;
  thumbLoading.add(entry.path);
  const dataUrl = await files.getThumbnail(entry.path);
  thumbLoading.delete(entry.path);
  if (dataUrl && isInCurrentFolder(entry.path)) thumbs.set(entry.path, dataUrl);
}

function goBack() {
  const crumbs = files.crumbs;
  if (crumbs.length <= 1) return;
  const parent = crumbs[crumbs.length - 2].path;
  void files.navigate(parent);
}

/// Keyboard navigation over the entry list: arrows move the focus, Enter opens
/// the focused entry, Delete/Backspace removes it (or the selection).
function onKeydown(e: KeyboardEvent) {
  const entries = sortedEntries.value;
  if (!entries.length) return;
  const target = e.target as HTMLElement | null;
  const typing = !!target && ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName);
  if (typing) return;
  switch (e.key) {
    case "ArrowDown":
    case "ArrowRight":
      e.preventDefault();
      kbdIndex.value = (kbdIndex.value + 1) % entries.length;
      break;
    case "ArrowUp":
    case "ArrowLeft":
      e.preventDefault();
      kbdIndex.value = kbdIndex.value <= 0 ? entries.length - 1 : kbdIndex.value - 1;
      break;
    case "Enter":
      if (e.target !== e.currentTarget || kbdIndex.value < 0) return;
      e.preventDefault();
      void open(entries[kbdIndex.value]);
      break;
    case "Delete":
    case "Backspace":
      if (e.target !== e.currentTarget) return;
      e.preventDefault();
      if (kbdIndex.value >= 0) {
        void removeEntry(entries[kbdIndex.value]);
      } else if (selected.value.size > 0) {
        void bulkDelete();
      }
      break;
  }
}

async function uploadFiles() {
  if (uploading.value) return;
  uploading.value = true;
  try {
    const picked = await openDialog({ multiple: true });
    if (!picked) return;
    const list = typeof picked === "string" ? [picked] : picked;
    // F2: a failed file must not abort the remaining uploads. Collect the
    // per-file errors and report the total afterwards.
    let failed = 0;
    for (const local of list) {
      const name = local.split(/[\\/]/).pop() ?? "file";
      const remote =
        (files.currentPath === "/" ? "" : files.currentPath) + "/" + name;
      try {
        await files.uploadFile(local, remote);
      } catch (e) {
        if ((e as AppErrorLike)?.code === "target_exists") {
          // Q9: never silently overwrite an existing remote file. Ask first,
          // retry this single file with overwrite if the user agrees.
          if (
            window.confirm(t("uploadOverwriteConfirm").replace("{name}", name))
          ) {
            try {
              await files.uploadFile(local, remote, true);
              continue;
            } catch (e2) {
              failed += 1;
              ui.toast(`${name}: ${invokeError(e2).message}`, "error");
              continue;
            }
          }
          failed += 1;
          ui.toast(`${name}: ${t("uploadSkipped")}`, "error");
          continue;
        }
        failed += 1;
        ui.toast(`${name}: ${invokeError(e).message}`, "error");
      }
    }
    files.clearTransfer();
    if (failed === 0) ui.toast(t("fileUploaded"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    uploading.value = false;
  }
}

async function createFolder() {
  const name = nameInput.value.trim();
  if (!name || name === "." || name === ".." || name.includes("/") || name.includes("\\")) {
    ui.toast(t("folderNameInvalid"), "error");
    return;
  }
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

const sharesByPath = ref<Map<string, Share[]>>(new Map());
const shareDialog = ref<{ entry: WebDavEntry; shares: Share[]; loading: boolean } | null>(null);
const submitting = ref(false);
const shareForm = reactive({
  type: "link" as "link" | "user" | "group",
  shareWith: "",
  password: "",
  expireDate: "",
  publicUpload: false,
});

const shareTypes = computed<{ value: "link" | "user" | "group"; label: string }[]>(() => [
  { value: "link", label: t("shareTypeLink") },
  { value: "user", label: t("shareTypeUser") },
  { value: "group", label: t("shareTypeGroup") },
]);

function shareLabel(share: Share): string {
  if (share.shareType === 3) return t("shareTypeLink");
  if (share.shareType === 1) return t("shareTypeGroup");
  return t("shareTypeUser");
}

function shareTarget(share: Share): string {
  if (share.shareType === 3) return share.url ?? "";
  return share.shareWithDisplayname || share.shareWith || "";
}

function resetShareForm() {
  shareForm.type = "link";
  shareForm.shareWith = "";
  shareForm.password = "";
  shareForm.expireDate = "";
  shareForm.publicUpload = false;
}

const shareState = reactive(
  new Map<string, { status: "loading" | "done" | "error"; value?: string }>()
);

async function createLink(entry: WebDavEntry) {
  shareState.set(entry.path, { status: "loading" });
  try {
    const share = await files.createShare(entry.path, { shareType: 3 });
    const url = share.url ?? "";
    shareState.set(entry.path, { status: "done", value: url });
    try {
      await navigator.clipboard.writeText(url);
      ui.toast(t("linkCopied"), "success");
    } catch {
      // F1: a clipboard failure must not destroy the freshly created link.
      // The URL stays visible in the entry state and can be copied again.
      ui.toast(t("linkCopyFailed"), "error");
    }
  } catch {
    shareState.set(entry.path, { status: "error" });
  }
}

async function copyLink(path: string) {
  const state = shareState.get(path);
  if (!state?.value) return;
  try {
    await navigator.clipboard.writeText(state.value);
    ui.toast(t("linkCopied"), "success");
  } catch {
    ui.toast(t("linkCopyFailed"), "error");
  }
}

async function loadAllShares() {
  try {
    const shares = await files.listShares();
    const map = new Map<string, Share[]>();
    for (const share of shares) {
      const key = share.path ?? "";
      const list = map.get(key);
      if (list) list.push(share);
      else map.set(key, [share]);
    }
    sharesByPath.value = map;
  } catch {
    // badge indicators are best-effort; the share dialog shows real errors
  }
}

async function refreshShares(entry: WebDavEntry) {
  try {
    const shares = await files.listShares(entry.path);
    if (shareDialog.value?.entry.path === entry.path) {
      shareDialog.value.shares = shares;
    }
    const map = new Map(sharesByPath.value);
    map.set(entry.path, shares);
    sharesByPath.value = map;
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  }
}

async function openShareDialog(entry: WebDavEntry) {
  shareDialog.value = { entry, shares: [], loading: true };
  resetShareForm();
  try {
    const shares = await files.listShares(entry.path);
    if (shareDialog.value?.entry.path === entry.path) {
      shareDialog.value.shares = shares;
    }
    const map = new Map(sharesByPath.value);
    map.set(entry.path, shares);
    sharesByPath.value = map;
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    if (shareDialog.value?.entry.path === entry.path) {
      shareDialog.value.loading = false;
    }
  }
}

function closeShareDialog() {
  shareDialog.value = null;
}

async function createShare() {
  const entry = shareDialog.value?.entry;
  if (!entry || submitting.value) return;
  const options: CreateShareOptions = {};
  if (shareForm.type === "link") {
    options.shareType = 3;
    if (shareForm.password.trim()) options.password = shareForm.password.trim();
    if (shareForm.expireDate) options.expireDate = shareForm.expireDate;
    if (shareForm.publicUpload) options.publicUpload = true;
  } else {
    options.shareType = shareForm.type === "user" ? 0 : 1;
    const recipient = shareForm.shareWith.trim();
    if (!recipient) {
      ui.toast(t("shareRecipientRequired"), "error");
      return;
    }
    options.shareWith = recipient;
  }
  submitting.value = true;
  try {
    await files.createShare(entry.path, options);
    ui.toast(t("shareCreated"), "success");
    resetShareForm();
    await refreshShares(entry);
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    submitting.value = false;
  }
}

async function revokeShare(share: Share) {
  if (!shareDialog.value) return;
  if (!window.confirm(t("shareRevokeConfirm").replace("{recipient}", shareTarget(share) || share.id.toString()))) return;
  try {
    await files.deleteShare(share.id);
    ui.toast(t("shareDeleted"), "success");
    await refreshShares(shareDialog.value.entry);
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  }
}

async function copyShareUrl(url: string) {
  try {
    await navigator.clipboard.writeText(url);
    ui.toast(t("linkCopied"), "success");
  } catch {
    ui.toast(t("linkCopyFailed"), "error");
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
  () => {
    clearSelection();
    pruneCaches();
  }
);

watch(
  () => files.entries,
  () => {
    const paths = new Set(files.entries.map((e) => e.path));
    for (const key of [...thumbs.keys()]) {
      if (!paths.has(key)) thumbs.delete(key);
    }
    for (const key of [...shareState.keys()]) {
      if (!paths.has(key)) shareState.delete(key);
    }
    void loadAllShares();
    for (const entry of files.entries) void loadThumb(entry);
    if (kbdIndex.value >= files.entries.length) kbdIndex.value = -1;
  }
);

watch(
  () => files.targetUser,
  () => {
    thumbs.clear();
    thumbLoading.clear();
    shareState.clear();
  }
);

onMounted(async () => {
  if (accounts.active) {
    await files.refresh();
    void loadAdminUsers();
    void loadAllShares();
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
    sharesByPath.value = new Map();
    shareDialog.value = null;
    shareState.clear();
    thumbs.clear();
    thumbLoading.clear();
    kbdIndex.value = -1;
    adminViewAll.value = true;
    selectedUser.value = "";
    searchInput.value = "";
    await files.reset();
    void loadAdminUsers();
  }
);
</script>

<template>
  <div
    class="relative flex h-full flex-col outline-none"
    tabindex="0"
    @keydown="onKeydown"
    @blur="kbdIndex = -1"
    @click="closeCtx"
    @dragover.prevent
    @drop.prevent
  >
    <div
      v-if="draggingOver"
      class="pointer-events-none absolute inset-0 z-40 flex items-center justify-center border-2 border-dashed border-primary bg-primary-container/40"
    >
      <p class="rounded-lg bg-surface-container-high px-4 py-2 text-sm text-on-primary-container">{{ t("dropToUpload") }}</p>
    </div>
    <div class="flex items-center justify-between gap-3 border-b border-outline-variant px-6 py-3">
      <nav class="flex min-w-0 items-center gap-1 text-sm">
        <button
          class="rounded p-1 text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface disabled:opacity-40"
          :disabled="files.crumbs.length <= 1"
          :title="t('back')"
          @click="goBack"
        >
          <Icon name="back" :size="16" />
        </button>
        <template v-for="(crumb, i) in files.crumbs" :key="crumb.path">
          <button
            class="rounded px-1.5 py-0.5 hover:bg-surface-container-high hover:text-on-surface"
            :class="i === files.crumbs.length - 1 ? 'font-semibold text-on-surface' : 'text-on-surface-variant'"
            @click="files.navigate(crumb.path)"
          >
            {{ crumb.path === "/" ? t("home") : crumb.label }}
          </button>
          <span v-if="i < files.crumbs.length - 1" class="text-outline">/</span>
        </template>
      </nav>

      <div class="flex shrink-0 items-center gap-2">
        <div class="relative">
          <Icon
            name="search"
            :size="15"
            class="pointer-events-none absolute left-2 top-1/2 -translate-y-1/2 text-on-surface-variant"
          />
          <input
            v-model="searchInput"
            :placeholder="t('searchPlaceholder')"
            class="w-44 rounded-md border border-outline bg-surface-container-high py-1 pl-7 pr-7 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
          />
          <button
            v-if="searchInput"
            class="absolute right-1.5 top-1/2 -translate-y-1/2 text-on-surface-variant hover:text-on-surface"
            :title="t('clearSearch')"
            @click="clearSearchInput"
          >
            <Icon name="close" :size="14" />
          </button>
        </div>
        <div class="flex overflow-hidden rounded-md border border-outline">
          <button
            class="flex items-center px-2.5 py-1 transition"
            :class="viewMode === 'list' ? 'bg-surface-container-high text-on-surface' : 'text-on-surface-variant'"
            :title="t('viewList')"
            @click="viewMode = 'list'"
          >
            <Icon name="menu" :size="16" />
          </button>
          <button
            class="flex items-center px-2.5 py-1 transition"
            :class="viewMode === 'grid' ? 'bg-surface-container-high text-on-surface' : 'text-on-surface-variant'"
            :title="t('viewGrid')"
            @click="viewMode = 'grid'"
          >
            <Icon name="grid" :size="16" />
          </button>
        </div>
        <button
          v-if="files.pairedPath"
          class="flex items-center gap-1.5 rounded-md border border-outline px-3 py-1 text-sm text-on-surface-variant hover:bg-surface-container-high"
          :class="files.splitView ? 'bg-primary text-on-primary hover:bg-primary-hover' : ''"
          :title="t('splitViewHint')"
          @click="files.toggleSplitView"
        >
          <Icon name="columns" :size="15" />
          {{ t("splitView") }}
        </button>
        <button
          class="flex items-center gap-1.5 rounded-md border border-outline px-3 py-1 text-sm text-on-surface-variant hover:bg-surface-container-high"
          @click="files.refresh"
        >
          <Icon name="refresh" :size="15" />
          {{ t("refresh") }}
        </button>
        <button
          class="flex items-center gap-1.5 rounded-md border border-outline px-3 py-1 text-sm text-on-surface-variant hover:bg-surface-container-high"
          @click="showNewFolder = true"
        >
          <Icon name="add" :size="15" />
          {{ t("newFolder") }}
        </button>
        <button
          class="flex items-center gap-1.5 rounded-md bg-primary px-3 py-1 text-sm font-medium text-on-primary hover:bg-primary-hover disabled:opacity-50"
          :disabled="uploading"
          @click="uploadFiles"
        >
          <Icon name="upload" :size="15" />
          {{ t("upload") }}
        </button>
      </div>
    </div>

    <div
      v-if="accounts.active?.isAdmin"
      class="flex flex-wrap items-center gap-3 border-b border-outline-variant bg-surface-container/40 px-6 py-2"
    >
      <div class="flex overflow-hidden rounded-md border border-outline">
        <button
          class="px-3 py-1.5 text-xs font-medium transition"
          :class="adminViewAll ? 'bg-primary text-on-primary' : 'text-on-surface-variant hover:bg-surface-container-high'"
          @click="setAdminView(true)"
        >
          {{ t("allUsersFolders") }}
        </button>
        <button
          class="px-3 py-1.5 text-xs font-medium transition"
          :class="!adminViewAll ? 'bg-primary text-on-primary' : 'text-on-surface-variant hover:bg-surface-container-high'"
          @click="setAdminView(false)"
        >
          {{ t("myFilesOnly") }}
        </button>
      </div>

      <template v-if="adminViewAll">
        <div class="flex items-center gap-2">
          <span class="text-xs text-on-surface-variant">{{ t("filterUser") }}</span>
          <select
            v-model="selectedUser"
            class="rounded-md border border-outline bg-surface-container-high px-2 py-1.5 text-xs text-on-surface focus:border-primary"
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
          class="text-xs text-primary-emphasis underline-offset-2 hover:underline"
          @click="loadAdminUsers"
        >
          {{ t("refresh") }}
        </button>
      </template>
    </div>

    <div
      v-if="files.targetUser && files.targetUser !== accounts.active?.username"
      class="flex items-center gap-2 border-b border-info bg-info-container/60 px-6 py-1.5 text-xs text-on-info-container"
    >
      <span class="shrink-0 opacity-80">{{ t("impersonationNotice") }}</span>
      <span class="truncate font-semibold">{{ files.targetUser }}</span>
    </div>

    <div
      v-if="files.pairedPath"
      class="flex items-center gap-2 border-b border-outline-variant bg-surface-container/40 px-6 py-1.5 text-xs text-on-surface-variant"
    >
      <span class="truncate rounded bg-info-container px-2 py-0.5 text-on-info-container">
        {{ paneLabel(paneKind(files.currentPath)) }}: {{ files.currentPath }}
      </span>
      <button
        class="flex shrink-0 items-center rounded-md border border-outline px-2 py-0.5 text-on-surface-variant hover:bg-surface-container-high"
        :title="t('openPaired')"
        @click="goToPath(files.pairedPath)"
      >
        ↔
      </button>
      <span class="truncate rounded bg-success-container px-2 py-0.5 text-on-success-container">
        {{ paneLabel(paneKind(files.pairedPath)) }}: {{ files.pairedPath }}
      </span>
    </div>

    <div
      v-if="files.offline"
      class="flex items-center gap-2 border-b border-info bg-info-container/60 px-6 py-1.5 text-xs text-on-info-container"
    >
      <Icon name="cloud_off" :size="14" class="shrink-0" />
      <span class="shrink-0 font-semibold">{{ t("offline") }}</span>
      <span class="truncate opacity-80">{{ t("offlineHint") }}</span>
    </div>

    <div v-if="files.error" class="m-4 rounded-md border border-error bg-error-container px-3 py-2 text-sm text-on-error-container">
      {{ files.error }}
    </div>

    <div
      v-if="isSearching"
      class="flex items-center gap-2 border-b border-outline-variant bg-primary-container/40 px-6 py-1.5 text-xs text-on-primary-container"
    >
      <Icon name="search" :size="14" class="opacity-80" />
      <span>{{ t("searchResults") }}</span>
      <span v-if="!files.searching" class="font-semibold">{{ files.displayEntries.length }}</span>
      <span v-else>{{ t("searching") }}</span>
      <button class="ml-auto underline-offset-2 hover:underline" @click="clearSearchInput">
        {{ t("clearSearch") }}
      </button>
    </div>

    <div v-if="selected.size > 0 || files.displayEntries.length > 0" class="flex items-center gap-3 border-b border-outline-variant bg-primary-container/40 px-6 py-1.5 text-xs text-on-primary-container">
      <label class="flex cursor-pointer items-center gap-1.5 select-none">
        <input
          type="checkbox"
          class="accent-primary"
          :checked="allSelected"
          @change="toggleSelectAll"
        />
        <span>{{ t("selectAll") }}</span>
      </label>
      <template v-if="selected.size > 0">
        <span>{{ selected.size }} {{ t("selected") }}</span>
        <button
          class="rounded-md border border-outline px-2 py-0.5 text-on-surface-variant hover:bg-surface-container-high"
          :disabled="busyPath !== null"
          @click="bulkDownload"
        >
          {{ t("download") }}
        </button>
        <button
          class="rounded-md border border-error px-2 py-0.5 text-error hover:bg-error-container/40"
          :disabled="busyPath !== null"
          @click="bulkDelete"
        >
          {{ t("delete") }}
        </button>
        <button class="underline-offset-2 hover:underline" @click="clearSelection">
          {{ t("clear") }}
        </button>
      </template>
      <span v-if="busyPath !== null" class="ml-auto text-on-surface-variant">{{ t("working") }}</span>
    </div>

    <div
      v-if="files.transfer"
      class="flex items-center gap-3 border-b border-outline-variant bg-surface-container/60 px-6 py-2 text-xs text-on-surface-variant"
    >
      <span class="max-w-48 truncate">
        {{ t(files.transfer.direction === "upload" ? "uploading" : files.transfer.direction === "download" ? "downloading" : "deleting") }}
        {{ files.transfer.path }}
      </span>
      <span v-if="files.transfer.totalFiles > 1" class="shrink-0">
        {{ files.transfer.index + 1 }} / {{ files.transfer.totalFiles }}
      </span>
      <div class="h-1.5 min-w-24 flex-1 overflow-hidden rounded-full bg-surface-container-high">
        <div
          class="h-full bg-primary transition-[width]"
          :style="{ width: files.transfer.percent + '%' }"
        ></div>
      </div>
      <span class="w-10 shrink-0 text-right">{{ files.transfer.percent.toFixed(0) }}%</span>
    </div>

    <div
      v-if="isSearching && files.searching && files.displayEntries.length === 0"
      class="m-auto text-on-surface-variant"
    >
      {{ t("searching") }}
    </div>

    <div
      v-else-if="isSearching && files.displayEntries.length === 0"
      class="m-auto text-center text-on-surface-variant"
    >
      <p class="text-lg">{{ t("noSearchResults").replace("{query}", searchInput.trim()) }}</p>
    </div>

    <div v-else-if="files.loading && files.displayEntries.length === 0" class="m-auto text-on-surface-variant">
      {{ t("connecting") }}
    </div>

    <div v-else-if="files.displayEntries.length === 0" class="m-auto text-center text-on-surface-variant">
      <p class="text-lg">{{ t("folderEmptyTitle") }}</p>
    </div>

    <!-- Split view: virtual resources ↔ real parts, side by side -->
    <div v-else-if="files.splitView" class="flex flex-1 overflow-hidden">
      <div class="flex min-w-0 flex-1 flex-col overflow-hidden border-r border-outline-variant">
        <div class="flex shrink-0 items-center justify-between gap-2 border-b border-outline-variant bg-surface-container/40 px-4 py-1.5 text-xs text-on-surface-variant">
          <span class="truncate font-semibold text-on-surface">{{ files.currentPath }}</span>
          <span class="flex shrink-0 items-center gap-2">
            <span class="rounded bg-info-container px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-on-info-container">
              {{ paneLabel(paneKind(files.currentPath)) }}
            </span>
            <button
              class="flex shrink-0 items-center rounded-md border border-outline px-2 py-0.5 hover:bg-surface-container-high"
              :title="t('openPaired')"
              @click="goToPath(files.pairedPath)"
            >
              ↔
            </button>
          </span>
        </div>
        <div class="flex-1 overflow-y-auto">
          <EntryList
            :entries="files.entries"
            :view-mode="viewMode"
            :selected="selected"
            :share-state="shareState"
            :sort-key="sortKey"
            :sort-asc="sortAsc"
            :kbd-index="kbdIndex"
            @open="open"
            @toggle-select="toggleSelect"
            @contextmenu="openCtx"
            @rename="startRename"
            @create-link="createLink"
            @copy-link="copyLink"
            @pair="goToPaired"
            @toggle-sort="toggleSort"
          />
        </div>
      </div>
      <div class="flex min-w-0 flex-1 flex-col overflow-hidden">
        <div class="flex shrink-0 items-center gap-2 border-b border-outline-variant bg-surface-container/40 px-4 py-1.5 text-xs text-on-surface-variant">
          <span class="truncate font-semibold text-on-surface">{{ files.pairedPath }}</span>
          <span class="shrink-0 rounded bg-success-container px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-on-success-container">
            {{ paneLabel(paneKind(files.pairedPath)) }}
          </span>
        </div>
        <div class="flex-1 overflow-y-auto">
          <div v-if="files.pairedLoading && files.pairedEntries.length === 0" class="p-4 text-on-surface-variant">
            {{ t("connecting") }}
          </div>
          <div v-else-if="files.pairedError" class="p-4 text-error">{{ files.pairedError }}</div>
          <div v-else-if="files.pairedEntries.length === 0" class="p-4 text-center text-on-surface-variant">
            <p class="text-lg">{{ t("folderEmptyTitle") }}</p>
          </div>
          <EntryList
            v-else
            :entries="files.pairedEntries"
            :view-mode="viewMode"
            :selected="emptySelection"
            :share-state="shareState"
            :selectable="false"
            :sort-key="sortKey"
            :sort-asc="sortAsc"
            @open="open"
            @contextmenu="openCtx"
            @rename="startRename"
            @create-link="createLink"
            @copy-link="copyLink"
            @pair="goToPaired"
            @toggle-sort="toggleSort"
          />
        </div>
      </div>
    </div>

    <!-- List view -->
    <div v-else-if="viewMode === 'list'" class="flex-1 overflow-y-auto">
      <EntryList
        :entries="files.displayEntries"
        :view-mode="viewMode"
        :selected="selected"
        :share-state="shareState"
        :searching="isSearching"
        :thumbs="thumbs"
        :shares-by-path="sharesByPath"
        :sort-key="sortKey"
        :sort-asc="sortAsc"
        :kbd-index="kbdIndex"
        @open="open"
        @toggle-select="toggleSelect"
        @contextmenu="openCtx"
        @rename="startRename"
        @create-link="createLink"
        @copy-link="copyLink"
        @pair="goToPaired"
        @download="download"
        @delete="removeEntry"
        @share="openShareDialog"
        @toggle-sort="toggleSort"
      />
    </div>

    <!-- Grid view -->
    <div v-else class="flex-1 overflow-y-auto">
      <EntryList
        :entries="files.displayEntries"
        :view-mode="viewMode"
        :selected="selected"
        :share-state="shareState"
        :searching="isSearching"
        :thumbs="thumbs"
        :shares-by-path="sharesByPath"
        :sort-key="sortKey"
        :sort-asc="sortAsc"
        :kbd-index="kbdIndex"
        @open="open"
        @toggle-select="toggleSelect"
        @contextmenu="openCtx"
        @rename="startRename"
        @create-link="createLink"
        @copy-link="copyLink"
        @pair="goToPaired"
        @download="download"
        @delete="removeEntry"
        @share="openShareDialog"
        @toggle-sort="toggleSort"
      />
    </div>

    <!-- Context menu -->
    <div
      v-if="ctxMenu"
      class="fixed z-50 w-44 overflow-hidden rounded-lg border border-outline bg-surface-container py-1 shadow-m3-3"
      :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
      @click.stop
    >
      <button
        class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm text-on-surface hover:bg-surface-container-high"
        @click="open(ctxMenu.entry); ctxMenu = null"
      >
        {{ t("open") }}
      </button>
      <button
        class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm text-on-surface hover:bg-surface-container-high"
        @click="ctxMenu.entry.isDir ? downloadZip(ctxMenu.entry) : download(ctxMenu.entry); ctxMenu = null"
      >
        {{ ctxMenu.entry.isDir ? t("downloadZip") : t("download") }}
      </button>
      <button
        class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm text-on-surface hover:bg-surface-container-high"
        @click="startRename(ctxMenu.entry); ctxMenu = null"
      >
        {{ t("rename") }}
      </button>
      <button
        class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm text-on-surface hover:bg-surface-container-high"
        @click="openShareDialog(ctxMenu.entry); ctxMenu = null"
      >
        {{ t("share") }}
      </button>
      <div class="my-1 border-t border-outline-variant"></div>
      <button
        class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm text-error hover:bg-error-container"
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
        class="w-full max-w-xs rounded-xl border border-outline bg-surface-container p-5 shadow-m3-3"
        @submit.prevent="createFolder"
      >
        <h3 class="mb-3 text-base font-semibold text-on-surface">{{ t("newFolder") }}</h3>
        <input
          v-model="nameInput"
          :placeholder="t('folderName')"
          autofocus
          class="mb-4 w-full rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
        />
        <div class="flex gap-2">
          <button
            type="button"
            class="flex-1 rounded-md bg-surface-container-high px-4 py-2 text-sm text-on-surface-variant hover:bg-surface-container-highest"
            @click="showNewFolder = false"
          >
            {{ t("cancel") }}
          </button>
          <button
            type="submit"
            class="flex-1 rounded-md bg-primary px-4 py-2 text-sm font-medium text-on-primary hover:bg-primary-hover"
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
        class="w-full max-w-xs rounded-xl border border-outline bg-surface-container p-5 shadow-m3-3"
        @submit.prevent="doRename"
      >
        <h3 class="mb-3 text-base font-semibold text-on-surface">{{ t("rename") }}</h3>
        <input
          v-model="nameInput"
          autofocus
          class="mb-4 w-full rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
        />
        <div class="flex gap-2">
          <button
            type="button"
            class="flex-1 rounded-md bg-surface-container-high px-4 py-2 text-sm text-on-surface-variant hover:bg-surface-container-highest"
            @click="renameTarget = null"
          >
            {{ t("cancel") }}
          </button>
          <button
            type="submit"
            class="flex-1 rounded-md bg-primary px-4 py-2 text-sm font-medium text-on-primary hover:bg-primary-hover"
          >
            {{ t("save") }}
          </button>
        </div>
      </form>
    </div>

    <!-- Share dialog -->
    <div
      v-if="shareDialog"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      @click.self="closeShareDialog"
    >
      <div
        class="flex max-h-[85vh] w-full max-w-md flex-col rounded-xl border border-outline bg-surface-container shadow-m3-3"
      >
        <div class="flex items-center justify-between border-b border-outline-variant px-5 py-3">
          <h3 class="min-w-0 truncate text-base font-semibold text-on-surface">
            {{ t("share") }} — {{ shareDialog.entry.name }}
          </h3>
          <button class="shrink-0 text-on-surface-variant hover:text-on-surface" @click="closeShareDialog">
            <Icon name="close" :size="18" />
          </button>
        </div>

        <div class="flex-1 overflow-y-auto px-5 py-4">
          <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
            {{ t("existingShares") }}
          </p>
          <div v-if="shareDialog.loading" class="mb-4 text-sm text-on-surface-variant">{{ t("loading") }}</div>
          <div v-else-if="!shareDialog.shares.length" class="mb-4 text-sm text-on-surface-variant">
            {{ t("noShares") }}
          </div>
          <ul v-else class="mb-4 space-y-2">
            <li
              v-for="share in shareDialog.shares"
              :key="share.id"
              class="flex items-center gap-2 rounded-md border border-outline-variant bg-surface-container-high px-3 py-2"
            >
              <span class="min-w-0 flex-1">
                <span class="block truncate text-sm text-on-surface">{{ shareLabel(share) }}</span>
                <span class="block truncate text-xs text-on-surface-variant">{{ shareTarget(share) }}</span>
                <span
                  v-if="share.hasPassword || share.expiration"
                  class="block truncate text-[10px] text-on-surface-variant"
                >
                  {{ share.hasPassword ? t("sharePasswordSet") : "" }}{{ share.hasPassword && share.expiration ? " · " : "" }}{{ share.expiration ? t("shareExpires").replace("{date}", share.expiration) : "" }}
                </span>
              </span>
              <button
                v-if="share.shareType === 3 && share.url"
                class="shrink-0 rounded border border-outline px-1.5 py-1 text-[10px] text-on-surface-variant hover:bg-surface-container-highest"
                :title="t('copyLinkTitle')"
                @click="copyShareUrl(share.url)"
              >
                ⧉
              </button>
              <button
                class="shrink-0 rounded border border-error px-1.5 py-1 text-[10px] text-error hover:bg-error-container/40"
                @click="revokeShare(share)"
              >
                {{ t("revoke") }}
              </button>
            </li>
          </ul>

          <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
            {{ t("newShare") }}
          </p>
          <div class="space-y-3">
            <div class="flex gap-2">
              <button
                v-for="type in shareTypes"
                :key="type.value"
                type="button"
                class="flex-1 rounded-md border px-3 py-1.5 text-sm transition"
                :class="shareForm.type === type.value ? 'border-primary bg-primary text-on-primary' : 'border-outline text-on-surface-variant hover:bg-surface-container-high'"
                @click="shareForm.type = type.value"
              >
                {{ type.label }}
              </button>
            </div>
            <input
              v-if="shareForm.type !== 'link'"
              v-model="shareForm.shareWith"
              :placeholder="t('shareRecipient')"
              class="w-full rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
            />
            <template v-else>
              <input
                v-model="shareForm.password"
                :placeholder="t('sharePasswordPlaceholder')"
                class="w-full rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
              />
              <input
                v-model="shareForm.expireDate"
                type="date"
                class="w-full rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface focus:border-primary"
              />
              <label class="flex cursor-pointer items-center gap-2 text-sm text-on-surface-variant select-none">
                <input v-model="shareForm.publicUpload" type="checkbox" class="accent-primary" />
                {{ t("publicUpload") }}
              </label>
            </template>
            <button
              class="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-on-primary hover:bg-primary-hover disabled:opacity-50"
              :disabled="submitting"
              @click="createShare"
            >
              {{ t("createShare") }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
