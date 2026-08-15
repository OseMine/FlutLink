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

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      accounts.value = await api.accountList();
      active.value = accounts.value.find((a) => a.isActive) ?? accounts.value[0] ?? null;
      filterInfo.value = await api.accountFilterInfo();
    } catch (e) {
      error.value = invokeError(e).message;
    } finally {
      loading.value = false;
    }
    await loadStorage();
  }

  async function loadStorage() {
    if (!active.value) {
      storage.value = null;
      return;
    }
    try {
      storage.value = await api.accountStorage();
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
