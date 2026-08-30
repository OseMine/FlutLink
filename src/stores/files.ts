import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { listen } from "@tauri-apps/api/event";
import {
  api,
  invokeError,
  type BulkTarget,
  type CreateShareOptions,
  type Share,
  type TransferProgress,
  type WebDavEntry,
} from "../lib/ipc";
import { onRetrySuccess } from "../lib/ipc";

/// Pair a path under the FlutCloud virtual namespaces: `/resources/…`
/// (read-only virtual links) ↔ `/parts/…` (write-enabled). Only the
/// top-level namespace segment (depth 1, `segments[1]`) is swapped — a real
/// user folder named `resources`/`parts` deeper in the tree (e.g.
/// `/Photos/resources/x`) must NOT be paired (L15-F8/#292).
export function pairOf(path: string): string | null {
  if (!path || path === "/") return null;
  const segments = path.split("/");
  // Depth 1 only (`segments[1]`): "/resources/…" ↔ "/parts/…". A real user
  // folder named resources/parts deeper in the tree (e.g.
  // "/Photos/resources/x") must NOT be paired (L15-F8/#292).
  const seg = (segments[1] ?? "").toLowerCase();
  if (seg === "resources") {
    segments[1] = "parts";
    return segments.join("/");
  }
  if (seg === "parts") {
    segments[1] = "resources";
    return segments.join("/");
  }
  return null;
}

export const useFilesStore = defineStore("files", () => {
  const currentPath = ref("/");
  const targetUser = ref<string | null>(null);
  const entries = ref<WebDavEntry[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const offline = ref(false);
  const transfer = ref<TransferProgress | null>(null);
  const searchQuery = ref("");
  const searchResults = ref<WebDavEntry[]>([]);
  const searching = ref(false);
  const splitView = ref(false);
  const pairedEntries = ref<WebDavEntry[]>([]);
  const pairedLoading = ref(false);
  const pairedError = ref<string | null>(null);
  let progressBound = false;

  const displayEntries = computed(() =>
    searchQuery.value ? searchResults.value : entries.value
  );

  let refreshSeq = 0;
  let searchSeq = 0;
  let pairedSeq = 0;

  const crumbs = computed(() => {
    const parts = currentPath.value.split("/").filter(Boolean);
    const result = [{ label: "home", path: "/" }];
    let acc = "";
    for (const part of parts) {
      acc += "/" + part;
      result.push({ label: part, path: acc });
    }
    return result;
  });

  /// The counterpart folder of `currentPath` (e.g. `/resources/link` →
  /// `/parts/link`), or `null` outside the `resources`/`parts` namespaces.
  const pairedPath = computed(() => pairOf(currentPath.value));

  async function navigate(path: string) {
    currentPath.value = path;
    await refresh();
  }

  async function refresh() {
    const seq = ++refreshSeq;
    loading.value = true;
    error.value = null;
    try {
      const result = await api.webdavList(
        currentPath.value,
        targetUser.value ?? undefined
      );
      if (seq === refreshSeq) {
        entries.value = result.entries;
        offline.value = result.stale;
      }
    } catch (e) {
      if (seq === refreshSeq) error.value = invokeError(e).message;
    } finally {
      if (seq === refreshSeq) loading.value = false;
    }
    if (splitView.value) {
      if (pairedPath.value) void refreshPaired();
      else splitView.value = false;
    }
  }

  // L24-F5: after a buffered `webdav_list`/`webdav_search` retry succeeds,
  // re-run the active listing so the view leaves its error state.
  onRetrySuccess((cmd) => {
    if (cmd === "webdav_list") void refresh();
    else if (cmd === "webdav_search") void searchFiles(searchQuery.value);
    else if (cmd === "guest_list_entries") {
      // GuestBrowser doesn't have a dedicated store — handled in its component.
    }
  });

  async function refreshPaired() {
    const pair = pairedPath.value;
    if (!pair) return;
    const seq = ++pairedSeq;
    pairedLoading.value = true;
    pairedError.value = null;
    try {
      const result = await api.webdavList(
        pair,
        targetUser.value ?? undefined
      );
      if (seq === pairedSeq) pairedEntries.value = result.entries;
    } catch (e) {
      if (seq === pairedSeq) pairedError.value = invokeError(e).message;
    } finally {
      if (seq === pairedSeq) pairedLoading.value = false;
    }
  }

  async function toggleSplitView() {
    splitView.value = !splitView.value;
    if (splitView.value) {
      if (pairedPath.value) await refreshPaired();
    } else {
      pairedEntries.value = [];
    }
  }

  function setTargetUser(username: string | null) {
    if (targetUser.value === username) return;
    targetUser.value = username;
    clearSearch();
    currentPath.value = "/";
    void refresh();
  }

  async function reset() {
    ++refreshSeq;
    ++pairedSeq;
    targetUser.value = null;
    currentPath.value = "/";
    entries.value = [];
    clearSearch();
    splitView.value = false;
    pairedEntries.value = [];
    await refresh();
  }

  async function searchFiles(query: string) {
    const q = query.trim();
    if (!q) {
      clearSearch();
      return;
    }
    const seq = ++searchSeq;
    searchQuery.value = q;
    searching.value = true;
    error.value = null;
    try {
      const results = await api.webdavSearch(
        q,
        targetUser.value ?? undefined
      );
      if (seq === searchSeq) searchResults.value = results;
    } catch (e) {
      // L12-N4: report a failed search exactly once — via the store's error
      // banner; do not rethrow (the caller would otherwise toast it again).
      if (seq === searchSeq) error.value = invokeError(e).message;
    } finally {
      if (seq === searchSeq) searching.value = false;
    }
  }

  function clearSearch() {
    ++searchSeq;
    searchQuery.value = "";
    searchResults.value = [];
    searching.value = false;
  }

  async function createShare(
    path: string,
    options: CreateShareOptions
  ): Promise<Share> {
    try {
      return await api.webdavCreateShare(
        path,
        options,
        targetUser.value ?? undefined
      );
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function listShares(path?: string): Promise<Share[]> {
    try {
      return await api.webdavListShares(
        path,
        targetUser.value ?? undefined
      );
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function deleteShare(shareId: number) {
    try {
      await api.webdavDeleteShare(shareId, targetUser.value ?? undefined);
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  /// Upload one local file. `refreshAfter=false` skips the per-file PROPFIND
  /// so batch uploads (L15-F6/#291) can refresh exactly once after the batch.
  async function uploadFile(
    localPath: string,
    remotePath: string,
    overwrite = false,
    refreshAfter = true
  ) {
    try {
      await api.webdavUploadFile(
        remotePath,
        localPath,
        targetUser.value ?? undefined,
        overwrite
      );
      if (refreshAfter) await refresh();
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function downloadFile(remotePath: string, localPath: string) {
    try {
      await api.webdavDownloadFile(remotePath, localPath, targetUser.value ?? undefined);
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function downloadZip(remotePath: string, localPath: string) {
    try {
      await api.webdavDownloadZip(remotePath, localPath, targetUser.value ?? undefined);
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function getThumbnail(path: string): Promise<string | null> {
    try {
      return await api.webdavThumbnail(path, 256, targetUser.value ?? undefined);
    } catch {
      return null;
    }
  }

  async function deleteEntry(path: string) {
    try {
      await api.webdavDelete(path, targetUser.value ?? undefined);
      await refresh();
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function bulkDelete(paths: string[]) {
    try {
      await api.webdavBulkDelete(paths, targetUser.value ?? undefined);
      await refresh();
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function bulkDownload(targets: BulkTarget[], destDir: string) {
    try {
      await api.webdavBulkDownload(targets, destDir, targetUser.value ?? undefined);
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function uploadLocalPaths(localPaths: string[], overwrite = false) {
    try {
      await api.webdavUploadLocalPaths(
        localPaths,
        currentPath.value,
        targetUser.value ?? undefined,
        overwrite
      );
      await refresh();
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function bindProgress() {
    if (progressBound) return;
    progressBound = true;
    await listen<TransferProgress>("file://progress", (e) => {
      transfer.value = e.payload;
    });
  }

  function clearTransfer() {
    transfer.value = null;
  }

  async function createFolder(name: string) {
    try {
      const path = (currentPath.value === "/" ? "" : currentPath.value) + "/" + name;
      await api.webdavMkdir(path, targetUser.value ?? undefined);
      await refresh();
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function renameEntry(path: string, newName: string) {
    try {
      await api.webdavRename(path, newName, targetUser.value ?? undefined);
      await refresh();
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  return {
    currentPath,
    targetUser,
    entries,
    displayEntries,
    loading,
    error,
    offline,
    transfer,
    searchQuery,
    searchResults,
    searching,
    crumbs,
    splitView,
    pairedPath,
    pairedEntries,
    pairedLoading,
    pairedError,
    navigate,
    refresh,
    toggleSplitView,
    setTargetUser,
    reset,
    searchFiles,
    clearSearch,
    createShare,
    listShares,
    deleteShare,
    uploadFile,
    downloadFile,
    downloadZip,
    getThumbnail,
    deleteEntry,
    bulkDelete,
    bulkDownload,
    uploadLocalPaths,
    bindProgress,
    clearTransfer,
    createFolder,
    renameEntry,
  };
});
