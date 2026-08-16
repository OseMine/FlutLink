import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { listen } from "@tauri-apps/api/event";
import {
  api,
  invokeError,
  type BulkTarget,
  type TransferProgress,
  type WebDavEntry,
} from "../lib/ipc";

export const useFilesStore = defineStore("files", () => {
  const currentPath = ref("/");
  const targetUser = ref<string | null>(null);
  const entries = ref<WebDavEntry[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const transfer = ref<TransferProgress | null>(null);
  let progressBound = false;

  let refreshSeq = 0;

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
      if (seq === refreshSeq) entries.value = result;
    } catch (e) {
      if (seq === refreshSeq) error.value = invokeError(e).message;
    } finally {
      if (seq === refreshSeq) loading.value = false;
    }
  }

  function setTargetUser(username: string | null) {
    if (targetUser.value === username) return;
    targetUser.value = username;
    currentPath.value = "/";
    void refresh();
  }

  async function reset() {
    ++refreshSeq;
    targetUser.value = null;
    currentPath.value = "/";
    entries.value = [];
    await refresh();
  }

  async function createShare(path: string): Promise<string> {
    try {
      return await api.webdavCreateShare(path, targetUser.value ?? undefined);
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function uploadFile(localPath: string, remotePath: string) {
    try {
      await api.webdavUploadFile(remotePath, localPath, targetUser.value ?? undefined);
      await refresh();
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

  async function uploadLocalPaths(localPaths: string[]) {
    try {
      await api.webdavUploadLocalPaths(
        localPaths,
        currentPath.value,
        targetUser.value ?? undefined
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
    loading,
    error,
    transfer,
    crumbs,
    navigate,
    refresh,
    setTargetUser,
    reset,
    createShare,
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
