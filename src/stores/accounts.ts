import { defineStore } from "pinia";
import { ref } from "vue";
import { api, invokeError, type AccountMeta, type UserQuota } from "../lib/ipc";

export const useAccountsStore = defineStore("accounts", () => {
  const accounts = ref<AccountMeta[]>([]);
  const active = ref<AccountMeta | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const storage = ref<UserQuota | null>(null);

  function sync(account: AccountMeta) {
    accounts.value = accounts.value.map((a) =>
      a.username === account.username && a.instanceUrl === account.instanceUrl
        ? account
        : a
    );
    active.value = account.isActive ? account : active.value;
  }

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      accounts.value = await api.accountList();
      active.value = accounts.value.find((a) => a.isActive) ?? accounts.value[0] ?? null;
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
      if (!accounts.value.some((a) => a.username === account.username)) {
        accounts.value.push(account);
      }
      if (account.isActive) active.value = account;
      await loadStorage();
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
      if (!accounts.value.some((a) => a.username === account.username)) {
        accounts.value.push(account);
      }
      if (account.isActive) active.value = account;
      await loadStorage();
      return account;
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function switchTo(username: string) {
    error.value = null;
    try {
      const account = await api.accountSwitch(username);
      sync(account);
      await loadStorage();
      return account;
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  async function remove(username: string) {
    error.value = null;
    try {
      accounts.value = await api.accountRemove(username);
      active.value = accounts.value.find((a) => a.isActive) ?? null;
      await loadStorage();
    } catch (e) {
      error.value = invokeError(e).message;
      throw e;
    }
  }

  return { accounts, active, loading, error, storage, load, loadStorage, add, register, switchTo, remove };
});
