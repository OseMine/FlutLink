import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { api, invokeError, type WebDavEntry } from "../lib/ipc";

export const useFilesStore = defineStore("files", () => {
  const currentPath = ref("/");
  const targetUser = ref<string | null>(null);
  const entries = ref<WebDavEntry[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

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

  async function deleteEntry(path: string) {
    try {
      await api.webdavDelete(path, targetUser.value ?? undefined);
      await refresh();
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
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
    crumbs,
    navigate,
    refresh,
    setTargetUser,
    reset,
    createShare,
    uploadFile,
    downloadFile,
    deleteEntry,
    createFolder,
    renameEntry,
  };
});
