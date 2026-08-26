<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { open as openDialog, save } from "@tauri-apps/plugin-dialog";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import { useAccountsStore } from "../stores/accounts";
import { useFilesStore } from "../stores/files";
import { useUiStore, type ViewMode } from "../stores/ui";
import { api, invokeError, type AppErrorLike, type BulkTarget, type CreateShareOptions, type Share, type WebDavEntry } from "../lib/ipc";
import { sortEntries, type EntrySortKey } from "../lib/sort";
import { translate } from "../lib/i18n";
import { registerEscapeCloser } from "../lib/escape";
import Icon from "./Icon.vue";
import EntryList from "./EntryList.vue";
import FilesToolbar from "./FilesToolbar.vue";
import ImpersonationBar from "./ImpersonationBar.vue";
import ContextMenu from "./ContextMenu.vue";
import NewFolderDialog from "./NewFolderDialog.vue";
import RenameDialog from "./RenameDialog.vue";
import ShareDialog, { type ShareFormValues } from "./ShareDialog.vue";

const accounts = useAccountsStore();
const files = useFilesStore();
const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

// #368: layout preferences live in the ui store (localStorage-persisted), so
// they survive app restarts and tab switches (App.vue destroys this component
// via v-if on every tab change).
const viewMode = computed({
  get: () => ui.filesView.viewMode,
  set: (mode: ViewMode) => ui.setFilesView({ viewMode: mode }),
});
const sortKey = computed(() => ui.filesView.sortKey);
const sortAsc = computed(() => ui.filesView.sortAsc);

function toggleSort(key: EntrySortKey) {
  if (sortKey.value === key) ui.setFilesView({ sortAsc: !sortAsc.value });
  else ui.setFilesView({ sortKey: key, sortAsc: true });
}

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
const transferProgress = computed(() => files.transfer?.percent ?? null);

const sortedEntries = computed(() =>
  sortEntries(files.displayEntries, sortKey.value, sortAsc.value)
);

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
  // L12-N4: errors are surfaced once, via the store's error banner.
  await files.searchFiles(searchInput.value);
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

/// #366: the select-all control is a checkbox now — indeterminate while a
/// proper subset is selected.
const someSelected = computed(
  () =>
    selected.value.size > 0 &&
    selected.value.size < files.displayEntries.length
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
    ui.toast(t("fileDownloaded"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    // L15-F4/#291: clear the banner in finally so it also disappears on errors.
    files.clearTransfer();
    busyPath.value = null;
  }
}

async function bulkDelete() {
  if (busyPath.value) return;
  // L17-F1: ask BEFORE claiming the busy slot — an early return on "cancel"
  // used to leave `busyPath` set forever and silently block every action.
  const count = selected.value.size;
  if (!window.confirm(t("deleteSelectedConfirm").replace("{count}", String(count)))) return;
  busyPath.value = "bulk-delete";
  try {
    await files.bulkDelete([...selected.value]);
    clearSelection();
    ui.toast(t("fileDeleted"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    files.clearTransfer();
    busyPath.value = null;
  }
}

async function dropUpload(paths: string[]) {
  if (busyPath.value || paths.length === 0) return;
  busyPath.value = "drop";
  try {
    await files.uploadLocalPaths(paths);
    ui.toast(t("fileUploaded"), "success");
  } catch (e) {
    if ((e as AppErrorLike)?.code === "target_exists") {
      // Q9: never silently overwrite an existing remote file. Ask first and
      // only retry the whole batch with overwrite once the user agrees.
      if (window.confirm(t("uploadOverwriteAllConfirm"))) {
        try {
          await files.uploadLocalPaths(paths, true);
          ui.toast(t("fileUploaded"), "success");
        } catch (e2) {
          ui.toast(invokeError(e2).message, "error");
        }
      }
      return;
    }
    ui.toast(invokeError(e).message, "error");
  } finally {
    files.clearTransfer();
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

type CtxAction = "open" | "download" | "rename" | "share" | "delete";

/// Dispatch a context-menu action, then close the menu — same ordering as the
/// former inline handlers (`action(); ctxMenu = null`).
function onCtxAction(action: CtxAction, entry: WebDavEntry) {
  if (action === "open") void open(entry);
  else if (action === "download") {
    if (entry.isDir) void downloadZip(entry);
    else void download(entry);
  } else if (action === "rename") startRename(entry);
  else if (action === "share") void openShareDialog(entry);
  else void removeEntry(entry);
  closeCtx();
}

async function open(entry: WebDavEntry) {
  if (entry.isDir) {
    await navigateTo(entry.path);
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

/// L15-F2/#290: every navigation clears an active search first, otherwise
/// `displayEntries` keeps showing stale results for a folder that no longer
/// matches the breadcrumbs.
async function navigateTo(path: string) {
  if (isSearching.value) clearSearchInput();
  await files.navigate(path);
}

/// Jump to the counterpart of a single entry (`/resources/…` ↔ `/parts/…`).
function goToPaired(entry: WebDavEntry) {
  if (entry.pairedPath) void navigateTo(entry.pairedPath);
}

/// Jump to an arbitrary path (used by the pairing bar / split-view swap).
function goToPath(path: string | null) {
  if (path) void navigateTo(path);
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
    ui.toast(t("fileDownloaded"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    files.clearTransfer();
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
    ui.toast(t("fileDownloaded"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    files.clearTransfer();
    busyPath.value = null;
  }
}

/// U-R8-6: keep `thumbs`/`shareState` bounded across folder navigation. Only
/// entries inside the current folder (or, in split view, its paired folder —
/// L19-F1) stay relevant; the `currentPath` watcher runs before `files.entries`
/// are refreshed, so pruning goes by path prefix.
function isInCurrentFolder(path: string): boolean {
  const prefix = files.currentPath === "/" ? "/" : files.currentPath + "/";
  return path.startsWith(prefix);
}

function isInPairedFolder(path: string): boolean {
  const pair = files.pairedPath;
  if (!pair) return false;
  const prefix = pair === "/" ? "/" : pair + "/";
  return path.startsWith(prefix);
}

function pruneCaches() {
  for (const path of thumbs.keys()) {
    if (!isInCurrentFolder(path) && !isInPairedFolder(path)) thumbs.delete(path);
  }
  for (const path of shareState.keys()) {
    if (!isInCurrentFolder(path) && !isInPairedFolder(path)) shareState.delete(path);
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
  if (dataUrl && (isInCurrentFolder(entry.path) || isInPairedFolder(entry.path))) {
    thumbs.set(entry.path, dataUrl);
  }
}

function goBack() {
  const crumbs = files.crumbs;
  if (crumbs.length <= 1) return;
  const parent = crumbs[crumbs.length - 2].path;
  void navigateTo(parent);
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
        await files.uploadFile(local, remote, false, false);
      } catch (e) {
        if ((e as AppErrorLike)?.code === "target_exists") {
          // Q9: never silently overwrite an existing remote file. Ask first,
          // retry this single file with overwrite if the user agrees.
          if (
            window.confirm(t("uploadOverwriteConfirm").replace("{name}", name))
          ) {
            try {
              await files.uploadFile(local, remote, true, false);
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
    // L15-F6/#291: one refresh per batch instead of a full PROPFIND (+ shares/
    // thumbs via the entries watcher) after every single upload.
    await files.refresh();
    if (failed === 0) ui.toast(t("fileUploaded"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    files.clearTransfer();
    uploading.value = false;
  }
}

/// L15-F5/#288: same entry-name rules for new-folder and rename dialogs.
function isValidEntryName(name: string): boolean {
  return (
    !!name &&
    name !== "." &&
    name !== ".." &&
    !name.includes("/") &&
    !name.includes("\\")
  );
}

// L15-F1/#288: clicks inside a <form> can also fire submit — handlers run
// through this synchronous guard so they can never double-fire.
let dialogBusy = false;

// L19-F5: both dialogs share `nameInput` — always start (and leave) it empty
// so an aborted dialog never pre-fills the next one with stale content.
function openNewFolder() {
  nameInput.value = "";
  showNewFolder.value = true;
}

function cancelNewFolder() {
  showNewFolder.value = false;
  nameInput.value = "";
}

function cancelRename() {
  renameTarget.value = null;
  nameInput.value = "";
}

async function createFolder() {
  if (dialogBusy) return;
  dialogBusy = true;
  try {
    const name = nameInput.value.trim();
    if (!isValidEntryName(name)) {
      ui.toast(t("folderNameInvalid"), "error");
      return;
    }
    await files.createFolder(name);
    ui.toast(t("folderCreated"), "success");
    showNewFolder.value = false;
    nameInput.value = "";
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    dialogBusy = false;
  }
}

function startRename(entry: WebDavEntry) {
  renameTarget.value = entry;
  nameInput.value = entry.name;
}

async function doRename() {
  if (dialogBusy) return;
  dialogBusy = true;
  try {
    const target = renameTarget.value;
    const name = nameInput.value.trim();
    if (!target || !name || name === target.name) {
      renameTarget.value = null;
      return;
    }
    // L15-F5/#288: reject invalid names instead of building broken paths.
    // L19-F4: the dialog stays open on validation/server errors (only the
    // toast shows) so the typed input is not lost — close on success only.
    if (!isValidEntryName(name)) {
      ui.toast(t("folderNameInvalid"), "error");
      return;
    }
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
    renameTarget.value = null;
    nameInput.value = "";
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    dialogBusy = false;
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
const shareDialogComp = ref<InstanceType<typeof ShareDialog> | null>(null);

function shareTarget(share: Share): string {
  if (share.shareType === 3) return share.url ?? "";
  return share.shareWithDisplayname || share.shareWith || "";
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

// L19-N1: Escape closes the context menu and every dialog — one overlay per
// press, most recent first. The closers are (re)registered whenever the set
// of open overlays changes.
let escapeUnregisters: (() => void)[] = [];
function clearEscapeClosers() {
  for (const off of escapeUnregisters) off();
  escapeUnregisters = [];
}
const overlayClosers = computed<(() => void)[]>(() => {
  const closers: (() => void)[] = [];
  if (showNewFolder.value) closers.push(cancelNewFolder);
  if (renameTarget.value) closers.push(cancelRename);
  if (shareDialog.value) closers.push(closeShareDialog);
  if (ctxMenu.value) closers.push(closeCtx);
  return closers;
});
watch(overlayClosers, (closers) => {
  clearEscapeClosers();
  // Reverse registration order so the topmost overlay receives the first
  // Escape press.
  escapeUnregisters = [...closers].reverse().map((c) => registerEscapeCloser(c));
});
onUnmounted(clearEscapeClosers);

async function createShare(form: ShareFormValues) {
  const entry = shareDialog.value?.entry;
  if (!entry || submitting.value) return;
  const options: CreateShareOptions = {};
  if (form.type === "link") {
    options.shareType = 3;
    if (form.password.trim()) options.password = form.password.trim();
    if (form.expireDate) options.expireDate = form.expireDate;
    if (form.publicUpload) options.publicUpload = true;
  } else {
    options.shareType = form.type === "user" ? 0 : 1;
    const recipient = form.shareWith.trim();
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
    shareDialogComp.value?.resetForm();
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

watch(
  () => files.currentPath,
  () => {
    clearSelection();
    pruneCaches();
  }
);

// L19-F1: prune + thumbnail loading covers both split-view panes — the
// paired pane must keep its thumbs when `files.entries` changes and vice
// versa, so the relevant-path set is the union of both entry lists.
watch(
  () => [files.displayEntries, files.pairedEntries] as const,
  ([display, paired]) => {
    const paths = new Set<string>();
    for (const entry of display) paths.add(entry.path);
    for (const entry of paired) paths.add(entry.path);
    for (const key of [...thumbs.keys()]) {
      if (!paths.has(key)) thumbs.delete(key);
    }
    for (const key of [...shareState.keys()]) {
      if (!paths.has(key)) shareState.delete(key);
    }
    void loadAllShares();
    for (const entry of display) void loadThumb(entry);
    for (const entry of paired) void loadThumb(entry);
  }
);

// L15-F7/#290: keyboard navigation walks `sortedEntries` (i.e.
// `displayEntries`), so clamp against that list — after a delete during an
// active search the old `files.entries` length is the wrong bound.
watch(
  () => sortedEntries.value.length,
  (len) => {
    if (kbdIndex.value >= len) kbdIndex.value = len > 0 ? len - 1 : -1;
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
  // L15-F9/#290: a pending 300 ms search debounce must not fire after the
  // component is gone (tab switch destroys it via v-if).
  if (searchTimer) {
    clearTimeout(searchTimer);
    searchTimer = null;
  }
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
    searchInput.value = "";
    await files.reset();
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
      class="pointer-events-none absolute inset-0 z-40 flex items-center justify-center border-2 border-dashed border-primary bg-canvas/80"
    >
      <p class="card px-4 py-2 text-sm">{{ t("dropToUpload") }}</p>
    </div>

    <FilesToolbar
      v-model:search="searchInput"
      v-model:view-mode="viewMode"
      :crumbs="files.crumbs"
      :uploading="uploading"
      :paired-path="files.pairedPath"
      :split-active="files.splitView"
      @back="goBack"
      @navigate="navigateTo"
      @toggle-split="files.toggleSplitView"
      @refresh="files.refresh()"
      @new-folder="openNewFolder"
      @upload="uploadFiles"
    />

    <ImpersonationBar />

    <div
      v-if="files.pairedPath"
      class="flex items-center gap-2 border-b border-line bg-panel px-6 py-1.5 text-xs text-muted"
    >
      <span class="badge normal-case">
        {{ paneLabel(paneKind(files.currentPath)) }}: {{ files.currentPath }}
      </span>
      <button
        type="button"
        class="action-badge shrink-0"
        :title="t('openPaired')"
        @click="goToPath(files.pairedPath)"
      >
        ↔
      </button>
      <span class="badge normal-case">
        {{ paneLabel(paneKind(files.pairedPath)) }}: {{ files.pairedPath }}
      </span>
    </div>

    <div
      v-if="files.offline"
      class="flex items-center gap-2 border-b border-info/40 bg-info/10 px-6 py-1.5 text-xs text-info"
    >
      <Icon name="cloud_off" :size="14" class="shrink-0" />
      <span class="shrink-0 font-semibold">{{ t("offline") }}</span>
      <span class="truncate opacity-80">{{ t("offlineHint") }}</span>
    </div>

    <div v-if="files.error" class="mx-4 mt-4 rounded-md border border-error/40 bg-error/10 px-3 py-2 text-sm text-error">
      {{ files.error }}
    </div>

    <div
      v-if="isSearching"
      class="flex items-center gap-2 border-b border-line px-6 py-1.5 text-xs text-muted"
    >
      <Icon name="search" :size="13" class="opacity-70" />
      <span>{{ t("searchResults") }}</span>
      <span v-if="!files.searching" class="font-semibold text-primary">{{ files.displayEntries.length }}</span>
      <span v-else>{{ t("searching") }}</span>
      <button type="button" class="ml-auto underline-offset-2 hover:underline" @click="clearSearchInput">
        {{ t("clearSearch") }}
      </button>
    </div>

    <!-- Select-all / bulk actions -->
    <div
      v-if="selected.size > 0 || files.displayEntries.length > 0"
      class="flex items-center gap-3 border-b border-line px-6 py-1.5 text-xs text-muted"
    >
      <label class="flex cursor-pointer select-none items-center gap-1.5">
        <input
          type="checkbox"
          class="checkbox"
          :checked="allSelected"
          :indeterminate.prop="someSelected"
          @change="toggleSelectAll()"
        />
        <span>{{ t("selectAll") }}</span>
      </label>
      <template v-if="selected.size > 0">
        <span>{{ selected.size }} {{ t("selected") }}</span>
        <button type="button" class="btn btn-outline h-7" @click="bulkDownload">
          <Icon name="download" :size="13" />
          {{ t("download") }}
        </button>
        <button type="button" class="btn btn-danger h-7" @click="bulkDelete">
          <Icon name="delete" :size="13" />
          {{ t("delete") }}
        </button>
        <button type="button" class="underline-offset-2 hover:underline" @click="clearSelection">
          {{ t("clear") }}
        </button>
      </template>
      <span v-if="busyPath !== null" class="ml-auto">{{ t("working") }}</span>
    </div>

    <!-- Transfer progress -->
    <div
      v-if="files.transfer"
      class="flex items-center gap-3 border-b border-line px-6 py-2 text-xs text-muted"
    >
      <span class="max-w-48 truncate">
        {{ t(files.transfer.direction === "upload" ? "uploading" : files.transfer.direction === "download" ? "downloading" : "deleting") }}
        {{ files.transfer.path }}
      </span>
      <span v-if="files.transfer.totalFiles > 1" class="shrink-0 tabular-nums">
        {{ files.transfer.index + 1 }} / {{ files.transfer.totalFiles }}
      </span>
      <div class="progress-track flex-1">
        <div class="progress-fill" :style="{ width: (transferProgress ?? 0) + '%' }"></div>
      </div>
      <span class="w-10 shrink-0 text-right tabular-nums">{{ files.transfer.percent.toFixed(0) }}%</span>
    </div>

    <div
      v-if="isSearching && files.searching && files.displayEntries.length === 0"
      class="m-auto text-muted"
    >
      {{ t("searching") }}
    </div>

    <div
      v-else-if="isSearching && files.displayEntries.length === 0"
      class="m-auto text-center text-muted"
    >
      <p class="text-lg">{{ t("noSearchResults").replace("{query}", searchInput.trim()) }}</p>
    </div>

    <div v-else-if="files.loading && files.displayEntries.length === 0" class="m-auto text-muted">
      {{ t("connecting") }}
    </div>

    <div v-else-if="files.displayEntries.length === 0" class="m-auto text-center text-muted">
      <p class="text-lg">{{ t("folderEmptyTitle") }}</p>
    </div>

    <!-- Split view: virtual resources ↔ real parts, side by side -->
    <div v-else-if="files.splitView" class="flex min-h-0 flex-1 overflow-hidden">
      <div class="flex min-w-0 flex-1 flex-col overflow-hidden border-r border-line">
        <div class="flex shrink-0 items-center justify-between gap-2 border-b border-line bg-panel px-4 py-1.5 text-xs text-muted">
          <span class="truncate font-semibold">{{ files.currentPath }}</span>
          <span class="flex shrink-0 items-center gap-2">
            <span class="badge normal-case">
              {{ paneLabel(paneKind(files.currentPath)) }}
            </span>
            <button
              type="button"
              class="action-badge shrink-0"
              :title="t('openPaired')"
              @click="goToPath(files.pairedPath)"
            >
              ↔
            </button>
          </span>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto">
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
      </div>
      <div class="flex min-w-0 flex-1 flex-col overflow-hidden">
        <div class="flex shrink-0 items-center gap-2 border-b border-line bg-panel px-4 py-1.5 text-xs text-muted">
          <span class="truncate font-semibold">{{ files.pairedPath }}</span>
          <span class="badge normal-case">
            {{ paneLabel(paneKind(files.pairedPath)) }}
          </span>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto">
          <div v-if="files.pairedLoading && files.pairedEntries.length === 0" class="p-4 text-muted">
            {{ t("connecting") }}
          </div>
          <div v-else-if="files.pairedError" class="p-4 text-error">{{ files.pairedError }}</div>
          <div v-else-if="files.pairedEntries.length === 0" class="p-4 text-center text-muted">
            <p class="text-lg">{{ t("folderEmptyTitle") }}</p>
          </div>
          <!-- L19-F1: same props/events as the left pane — grid hover buttons
               (download/share/delete) and badges/thumbs work in both panes. -->
          <EntryList
            v-else
            :entries="files.pairedEntries"
            :view-mode="viewMode"
            :selected="emptySelection"
            :share-state="shareState"
            :selectable="false"
            :searching="isSearching"
            :thumbs="thumbs"
            :shares-by-path="sharesByPath"
            :sort-key="sortKey"
            :sort-asc="sortAsc"
            @open="open"
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
      </div>
    </div>

    <!-- List / grid views -->
    <div v-else class="min-h-0 flex-1 overflow-y-auto">
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

    <ContextMenu
      v-if="ctxMenu"
      :x="ctxMenu.x"
      :y="ctxMenu.y"
      :entry="ctxMenu.entry"
      @action="onCtxAction"
    />

    <NewFolderDialog
      v-if="showNewFolder"
      v-model="nameInput"
      @create="createFolder"
      @cancel="cancelNewFolder"
    />

    <RenameDialog
      v-if="renameTarget"
      v-model="nameInput"
      @save="doRename"
      @cancel="cancelRename"
    />

    <ShareDialog
      v-if="shareDialog"
      ref="shareDialogComp"
      :entry="shareDialog.entry"
      :shares="shareDialog.shares"
      :loading="shareDialog.loading"
      :submitting="submitting"
      @close="closeShareDialog"
      @create="createShare"
      @revoke="revokeShare"
      @copy="copyShareUrl"
    />
  </div>
</template>
