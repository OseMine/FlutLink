import { defineStore } from "pinia";
import { ref } from "vue";
import { listen } from "@tauri-apps/api/event";
import {
  api,
  invokeError,
  type AccountFilterInfo,
  type AccountMeta,
  type UserQuota,
} from "../lib/ipc";

export const useAccountsStore = defineStore("accounts", () => {
  const accounts = ref<AccountMeta[]>([]);
  const active = ref<AccountMeta | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const storage = ref<UserQuota | null>(null);
  const filterInfo = ref<AccountFilterInfo | null>(null);
  let bound = false;

  async function bind() {
    if (bound) return;
    bound = true;
    await listen("accounts-changed", () => {
      void load();
    });
  }

  // L15-F10/#289: sequence guard — load() is triggered from mount,
  // accounts-changed and after add/register/switchTo; two parallel loads can
  // finish out of order, so an older snapshot must not overwrite a newer one
  // (same pattern as files.refreshSeq).
  let loadSeq = 0;

  async function load() {
    const seq = ++loadSeq;
    loading.value = true;
    error.value = null;
    try {
      const list = await api.accountList();
      if (seq !== loadSeq) return; // superseded by a newer load()
      accounts.value = list;
      active.value = list.find((a) => a.isActive) ?? list[0] ?? null;
      const info = await api.accountFilterInfo();
      if (seq !== loadSeq) return;
      filterInfo.value = info;
    } catch (e) {
      if (seq !== loadSeq) return;
      error.value = invokeError(e).message;
    } finally {
      if (seq === loadSeq) loading.value = false;
    }
    await loadStorage();
  }

  async function loadStorage() {
    if (!active.value) {
      storage.value = null;
      return;
    }
    try {
      // Guard against stale quota snapshots after rapid account switches.
      const owner = `${active.value.instanceUrl}/${active.value.username}`;
      const result = await api.accountStorage();
      const current = active.value;
      if (!current || `${current.instanceUrl}/${current.username}` !== owner) return;
      storage.value = result.quota;
    } catch {
      storage.value = null;
    }
  }

  async function add(input: { instanceUrl: string; username: string; token: string }) {
    error.value = null;
    try {
      const account = await api.accountAdd(input.instanceUrl, input.username, input.token);
      // Reload from the backend so the store matches the server state exactly
      // (e.g. whether this account ended up being the active one).
      await load();
      return account;
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function register(input: {
    instanceUrl: string;
    username: string;
    password: string;
    displayName?: string;
    adminUsername: string;
    adminPassword: string;
  }) {
    error.value = null;
    try {
      const account = await api.registerUser(input);
      await load();
      return account;
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function switchTo(username: string, instanceUrl: string) {
    error.value = null;
    try {
      const account = await api.accountSwitch(username, instanceUrl);
      // Reload the whole list so the is_active flags of every account match the
      // backend state exactly (a partial `sync()` leaves stale flags behind).
      await load();
      return account;
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function remove(username: string, instanceUrl: string) {
    error.value = null;
    try {
      accounts.value = await api.accountRemove(username, instanceUrl);
      active.value = accounts.value.find((a) => a.isActive) ?? null;
      await loadStorage();
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  return { accounts, active, loading, error, storage, filterInfo, bind, load, loadStorage, add, register, switchTo, remove };
});
