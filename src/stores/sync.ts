import { defineStore } from "pinia";
import { ref } from "vue";
import { listen } from "@tauri-apps/api/event";
import { api, invokeError, type SyncFolderStatus } from "../lib/ipc";

let bound = false;

export const useSyncStore = defineStore("sync", () => {
  const folders = ref<SyncFolderStatus[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      folders.value = await api.syncList();
    } catch (e) {
      error.value = invokeError(e).message;
    } finally {
      loading.value = false;
    }
  }

  async function bind() {
    if (bound) return;
    bound = true;
    await listen<SyncFolderStatus[]>("sync-status", (event) => {
      folders.value = event.payload;
    });
    // New folders added outside this view (e.g. via the CLI `--path` flag or
    // a second window) must appear immediately, without an app reload.
    await listen("sync-folders-changed", () => {
      void load();
    });
  }

  async function add(localPath: string) {
    error.value = null;
    try {
      const status = await api.syncAdd(localPath);
      folders.value = await api.syncList();
      return status;
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function remove(folderId: string) {
    error.value = null;
    try {
      await api.syncRemove(folderId);
      await load();
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function setPaused(folderId: string, paused: boolean) {
    error.value = null;
    try {
      await api.syncSetPaused(folderId, paused);
      await load();
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function trigger() {
    // F5: do not swallow failures here — the caller decides how to report
    // them. A silent success toast is worse than an honest error.
    await api.syncTrigger();
  }

  return { folders, loading, error, load, bind, add, remove, setPaused, trigger };
});
