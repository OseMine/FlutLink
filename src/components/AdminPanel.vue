<script setup lang="ts">
import { reactive, ref } from "vue";
import { api, invokeError, type UserDetails, type UserQuota } from "../lib/ipc";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import { formatBytes } from "../lib/format";

const emit = defineEmits<{ browse: [userId: string] }>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const MB = 1024 * 1024;
const GB = 1024 * 1024 * 1024;

const search = ref("");
const users = ref<string[]>([]);
const selected = ref<UserDetails | null>(null);
const loading = ref(false);
const detailsLoading = ref(false);
const error = ref<string | null>(null);
const editMsg = ref<string | null>(null);
const showPassword = ref(false);
const showCreate = ref(false);

const edits = reactive({
  displayName: "",
  email: "",
  password: "",
  quotaValue: null as number | null,
  quotaUnit: "gb" as "gb" | "mb" | "unlimited",
});

const newUser = reactive({
  userId: "",
  password: "",
  displayName: "",
});

function setQuotaFromTotal(total: number | null) {
  if (total === null || total < 0) {
    edits.quotaValue = null;
    edits.quotaUnit = "unlimited";
    return;
  }
  if (total >= GB) {
    edits.quotaUnit = "gb";
    edits.quotaValue = Math.round((total / GB) * 10) / 10;
  } else {
    edits.quotaUnit = "mb";
    edits.quotaValue = Math.round((total / MB) * 10) / 10;
  }
}

async function listUsers() {
  loading.value = true;
  error.value = null;
  editMsg.value = null;
  selected.value = null;
  try {
    users.value = await api.adminListUsers(search.value.trim());
  } catch (e) {
    error.value = invokeError(e).message;
  } finally {
    loading.value = false;
  }
}

async function selectUser(userId: string) {
  detailsLoading.value = true;
  error.value = null;
  editMsg.value = null;
  try {
    selected.value = await api.adminGetUser(userId);
    edits.displayName = selected.value.displayName ?? "";
    edits.email = selected.value.email ?? "";
    edits.password = "";
    setQuotaFromTotal(selected.value.quota?.total ?? null);
  } catch (e) {
    error.value = invokeError(e).message;
  } finally {
    detailsLoading.value = false;
  }
}

async function saveField(key: "displayname" | "email" | "password") {
  if (!selected.value) return;
  const value = edits[key === "displayname" ? "displayName" : key];
  error.value = null;
  editMsg.value = null;
  if (!value) {
    if (key === "password") {
      edits.password = "";
      editMsg.value = t("passwordUnchanged");
      return;
    }
    error.value = key === "displayname" ? t("displayNameEmpty") : t("emailEmpty");
    return;
  }
  try {
    editMsg.value = await api.adminEditUser(selected.value.id, key, value);
    if (key === "displayname" || key === "email") {
      selected.value = await api.adminGetUser(selected.value.id);
      edits.displayName = selected.value.displayName ?? "";
      edits.email = selected.value.email ?? "";
    } else {
      edits.password = "";
    }
  } catch (e) {
    error.value = invokeError(e).message;
  }
}

async function setQuota() {
  if (!selected.value) return;
  error.value = null;
  editMsg.value = null;
  let quota: string;
  if (edits.quotaUnit === "unlimited") {
    quota = "-3";
  } else {
    const value = edits.quotaValue;
    if (value === null || value <= 0) {
      error.value = t("quotaInvalid");
      return;
    }
    const factor = edits.quotaUnit === "gb" ? GB : MB;
    quota = String(Math.round(value * factor));
  }
  try {
    editMsg.value = await api.adminSetUserQuota(selected.value.id, quota);
    await selectUser(selected.value.id);
  } catch (e) {
    error.value = invokeError(e).message;
  }
}

async function toggleEnabled() {
  if (!selected.value) return;
  error.value = null;
  editMsg.value = null;
  try {
    // The OCS provisioning API expects "0"/"1" for the `enabled` key.
    await api.adminEditUser(
      selected.value.id,
      "enabled",
      selected.value.enabled ? "0" : "1"
    );
    await selectUser(selected.value.id);
    ui.toast(selected.value.enabled ? t("userEnabled") : t("userDisabled"), "success");
  } catch (e) {
    error.value = invokeError(e).message;
  }
}

async function removeUser() {
  if (!selected.value) return;
  const name = selected.value.displayName || selected.value.id;
  if (!window.confirm(t("deleteUserConfirm").replace("{name}", name))) return;
  error.value = null;
  editMsg.value = null;
  try {
    editMsg.value = await api.adminDeleteUser(selected.value.id);
    selected.value = null;
    await listUsers();
    ui.toast(t("userDeleted"), "success");
  } catch (e) {
    error.value = invokeError(e).message;
  }
}

async function createUser() {
  if (!newUser.userId.trim() || !newUser.password) {
    error.value = t("userFieldsRequired");
    return;
  }
  error.value = null;
  editMsg.value = null;
  try {
    editMsg.value = await api.adminCreateUser(
      newUser.userId.trim(),
      newUser.password,
      newUser.displayName.trim() || undefined
    );
    showCreate.value = false;
    newUser.userId = "";
    newUser.password = "";
    newUser.displayName = "";
    await listUsers();
    ui.toast(t("userCreated"), "success");
  } catch (e) {
    error.value = invokeError(e).message;
  }
}

function quotaTotal(q: UserQuota | null): string {
  return q?.total === null ? t("unlimited") : formatBytes(q?.total ?? null);
}

function quotaUsed(q: UserQuota | null): string {
  return formatBytes(q?.used ?? null);
}

function quotaFree(q: UserQuota | null): string {
  if (!q || q.total === null) return t("unlimited");
  if (q.used === null) return formatBytes(q.total);
  const free = q.total - q.used;
  if (free < 0) return `-${formatBytes(-free)}`;
  return formatBytes(free);
}
</script>

<template>
  <div class="flex h-full flex-col gap-4 overflow-hidden p-6">
    <div>
      <h2 class="text-lg font-semibold text-zinc-50">{{ t("adminPanelTitle") }}</h2>
      <p class="text-sm text-zinc-500">{{ t("adminPanelSubtitle") }}</p>
    </div>

    <div v-if="error" class="rounded-md border border-red-800 bg-red-950/50 px-3 py-2 text-sm text-red-300">
      {{ error }}
    </div>
    <div v-if="editMsg" class="rounded-md border border-emerald-800 bg-emerald-950/50 px-3 py-2 text-sm text-emerald-300">
      {{ editMsg }}
    </div>

    <div class="flex gap-2">
      <input
        v-model="search"
        class="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-50 placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
        :placeholder="t('searchUsers')"
        @keyup.enter="listUsers"
      />
      <button
        class="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500"
        @click="listUsers"
      >
        {{ loading ? t("loading") : t("listUsers") }}
      </button>
      <button
        class="rounded-md border border-zinc-700 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-800"
        @click="showCreate = !showCreate"
      >
        + {{ t("createUser") }}
      </button>
    </div>

    <div v-if="showCreate" class="rounded-lg border border-zinc-800 bg-zinc-900 p-4">
      <h3 class="mb-1 text-sm font-medium text-zinc-50">{{ t("createUserTitle") }}</h3>
      <p class="mb-3 text-xs text-zinc-500">{{ t("newUserHint") }}</p>
      <div class="grid gap-3 sm:grid-cols-3">
        <input
          v-model="newUser.userId"
          :placeholder="t('userId')"
          class="rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-50 placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
        />
        <input
          v-model="newUser.password"
          type="password"
          :placeholder="t('password')"
          class="rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-50 placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
        />
        <input
          v-model="newUser.displayName"
          :placeholder="t('displayName')"
          class="rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-50 placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
        />
      </div>
      <button
        class="mt-3 rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500"
        @click="createUser"
      >
        {{ t("create") }}
      </button>
    </div>

    <div class="grid min-h-0 flex-1 grid-cols-2 gap-4">
      <div class="overflow-y-auto rounded-lg border border-zinc-800">
        <ul v-if="users.length" class="divide-y divide-zinc-800/60">
          <li v-for="userId in users" :key="userId">
            <button
              class="w-full px-4 py-2 text-left text-sm text-zinc-200 hover:bg-zinc-800/60"
              :class="selected?.id === userId ? 'bg-indigo-950/60 text-indigo-200' : ''"
              @click="selectUser(userId)"
            >
              {{ userId }}
            </button>
          </li>
        </ul>
        <p v-else class="p-4 text-sm text-zinc-500">
          {{ loading ? t("loading") : t("noUsersYet") }}
        </p>
      </div>

      <div class="overflow-y-auto rounded-lg border border-zinc-800 p-4">
        <p v-if="detailsLoading" class="text-sm text-zinc-500">{{ t("loadingDetails") }}</p>
        <template v-else-if="selected">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0">
              <h3 class="truncate text-base font-medium text-zinc-50">{{ selected.displayName || selected.id }}</h3>
              <p class="text-sm text-zinc-500">{{ selected.id }}</p>
              <p class="mt-1 text-xs">
                <span
                  class="rounded px-1.5 py-0.5 font-semibold"
                  :class="selected.enabled ? 'bg-emerald-500/15 text-emerald-300' : 'bg-red-500/15 text-red-300'"
                >
                  {{ selected.enabled ? t("enabled") : t("disabled") }}
                </span>
              </p>
            </div>
            <div class="flex shrink-0 flex-col items-end gap-1.5">
              <button
                class="rounded-md border border-zinc-700 px-2.5 py-1 text-xs text-zinc-300 hover:bg-zinc-800"
                @click="emit('browse', selected.id)"
              >
                {{ t("browseFiles") }}
              </button>
              <button
                class="rounded-md border border-zinc-700 px-2.5 py-1 text-xs text-zinc-300 hover:bg-zinc-800"
                @click="toggleEnabled"
              >
                {{ selected.enabled ? t("disableAccount") : t("enableAccount") }}
              </button>
              <button
                class="rounded-md border border-red-800 px-2.5 py-1 text-xs text-red-300 hover:bg-red-950/40"
                @click="removeUser"
              >
                {{ t("deleteUser") }}
              </button>
            </div>
          </div>

          <div class="mt-4 space-y-3">
            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-zinc-500">
                {{ t("displayName") }}
              </label>
              <div class="flex gap-2">
                <input
                  v-model="edits.displayName"
                  class="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-50 focus:border-indigo-500 focus:outline-none"
                />
                <button
                  class="rounded-md bg-zinc-700 px-3 py-2 text-sm text-zinc-50 hover:bg-zinc-600"
                  @click="saveField('displayname')"
                >
                  {{ t("save") }}
                </button>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-zinc-500">
                {{ t("email") }}
              </label>
              <div class="flex gap-2">
                <input
                  v-model="edits.email"
                  type="email"
                  class="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-50 focus:border-indigo-500 focus:outline-none"
                />
                <button
                  class="rounded-md bg-zinc-700 px-3 py-2 text-sm text-zinc-50 hover:bg-zinc-600"
                  @click="saveField('email')"
                >
                  {{ t("save") }}
                </button>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-zinc-500">
                {{ t("password") }}
              </label>
              <div class="flex gap-2">
                <input
                  v-model="edits.password"
                  :type="showPassword ? 'text' : 'password'"
                  :placeholder="t('passwordPlaceholder')"
                  class="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-50 placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
                />
                <button
                  class="rounded-md bg-zinc-800 px-3 py-2 text-sm text-zinc-300 hover:bg-zinc-700"
                  @click="showPassword = !showPassword"
                >
                  {{ showPassword ? t("hide") : t("show") }}
                </button>
                <button
                  class="rounded-md bg-zinc-700 px-3 py-2 text-sm text-zinc-50 hover:bg-zinc-600"
                  @click="saveField('password')"
                >
                  {{ t("save") }}
                </button>
              </div>
            </div>

            <div>
              <p class="mb-1.5 text-xs font-medium uppercase tracking-wide text-zinc-500">
                {{ t("groups") }}
              </p>
              <div v-if="selected.groups.length" class="flex flex-wrap gap-1.5">
                <span
                  v-for="group in selected.groups"
                  :key="group"
                  class="rounded bg-zinc-800 px-2 py-0.5 text-xs text-zinc-300"
                >
                  {{ group }}
                </span>
              </div>
              <p v-else class="text-xs text-zinc-600">{{ t("noGroups") }}</p>
            </div>

            <div class="rounded-md bg-zinc-800/60 p-3 text-sm text-zinc-300">
              <div class="flex justify-between">
                <span class="text-zinc-500">{{ t("quota") }}</span>
                <span>{{ quotaTotal(selected.quota) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-zinc-500">{{ t("used") }}</span>
                <span>{{ quotaUsed(selected.quota) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-zinc-500">{{ t("free") }}</span>
                <span>{{ quotaFree(selected.quota) }}</span>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-zinc-500">
                {{ t("setQuota") }}
              </label>
              <div class="flex gap-2">
                <input
                  v-model.number="edits.quotaValue"
                  type="number"
                  :disabled="edits.quotaUnit === 'unlimited'"
                  min="0"
                  step="0.1"
                  class="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-50 focus:border-indigo-500 focus:outline-none disabled:opacity-40"
                />
                <select
                  v-model="edits.quotaUnit"
                  class="rounded-md border border-zinc-700 bg-zinc-800 px-2 py-2 text-sm text-zinc-50 focus:border-indigo-500 focus:outline-none"
                >
                  <option value="gb">{{ t("gb") }}</option>
                  <option value="mb">{{ t("mb") }}</option>
                  <option value="unlimited">{{ t("unlimited") }}</option>
                </select>
                <button
                  class="rounded-md bg-zinc-700 px-3 py-2 text-sm text-zinc-50 hover:bg-zinc-600"
                  @click="setQuota"
                >
                  {{ t("save") }}
                </button>
              </div>
            </div>
          </div>
        </template>
        <p v-else class="text-sm text-zinc-500">{{ t("selectUser") }}</p>
      </div>
    </div>
  </div>
</template>
