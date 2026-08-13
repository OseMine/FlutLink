import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { api, invokeError, type WebDavEntry } from "../lib/ipc";

export const useFilesStore = defineStore("files", () => {
  const currentPath = ref("/");
  const targetUser = ref<string | null>(null);
  const entries = ref<WebDavEntry[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  const crumbs = computed(() => {
    const parts = currentPath.value.split("/").filter(Boolean);
    const result = [{ label: "Home", path: "/" }];
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
    loading.value = true;
    error.value = null;
    try {
      entries.value = await api.webdavList(
        currentPath.value,
        targetUser.value ?? undefined
      );
    } catch (e) {
      error.value = invokeError(e).message;
    } finally {
      loading.value = false;
    }
  }

  function setTargetUser(username: string | null) {
    if (targetUser.value === username) return;
    targetUser.value = username;
    currentPath.value = "/";
    void refresh();
  }

  async function reset() {
    targetUser.value = null;
    currentPath.value = "/";
    await refresh();
  }

  async function createShare(path: string): Promise<string> {
    try {
      return await api.webdavCreateShare(path);
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
  };
});
