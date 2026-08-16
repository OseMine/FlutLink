<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import { api, invokeError, type UserDetails, type UserQuota } from "../lib/ipc";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import { formatBytes } from "../lib/format";

const emit = defineEmits<{ browse: [userId: string] }>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const MB = 1024 * 1024;
const GB = 1024 * 1024 * 1024;

type QuotaPresetId = "1gb" | "5gb" | "10gb" | "unlimited" | "custom";
const QUOTA_PRESETS: { id: Exclude<QuotaPresetId, "unlimited" | "custom">; value: number; unit: "gb" }[] = [
  { id: "1gb", value: 1, unit: "gb" },
  { id: "5gb", value: 5, unit: "gb" },
  { id: "10gb", value: 10, unit: "gb" },
];

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
  quotaPreset: "custom" as QuotaPresetId,
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
    edits.quotaPreset = "unlimited";
    return;
  }
  const gbValue = Math.round((total / GB) * 10) / 10;
  const preset = QUOTA_PRESETS.find(
    (p) => p.value === gbValue && p.unit === "gb"
  );
  if (preset) {
    edits.quotaValue = gbValue;
    edits.quotaUnit = "gb";
    edits.quotaPreset = preset.id;
    return;
  }
  if (total >= GB) {
    edits.quotaUnit = "gb";
    edits.quotaValue = gbValue;
  } else {
    edits.quotaUnit = "mb";
    edits.quotaValue = Math.round((total / MB) * 10) / 10;
  }
  edits.quotaPreset = "custom";
}

watch(
  () => edits.quotaPreset,
  (preset) => {
    if (preset === "unlimited") {
      edits.quotaValue = null;
      edits.quotaUnit = "unlimited";
      return;
    }
    if (preset === "custom") return;
    const found = QUOTA_PRESETS.find((p) => p.id === preset);
    if (found) {
      edits.quotaValue = found.value;
      edits.quotaUnit = found.unit;
    }
  }
);

watch(
  () => [edits.quotaValue, edits.quotaUnit] as const,
  ([value, unit]) => {
    if (edits.quotaPreset === "unlimited") return;
    if (unit === "unlimited") {
      edits.quotaPreset = "unlimited";
      return;
    }
    if (edits.quotaPreset === "custom") return;
    const matches = QUOTA_PRESETS.some(
      (p) => p.value === value && p.unit === unit
    );
    if (!matches) edits.quotaPreset = "custom";
  }
);

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
      <h2 class="text-lg font-semibold text-on-surface">{{ t("adminPanelTitle") }}</h2>
      <p class="text-sm text-on-surface-variant">{{ t("adminPanelSubtitle") }}</p>
    </div>

    <div v-if="error" class="rounded-md border border-error bg-error-container px-3 py-2 text-sm text-on-error-container">
      {{ error }}
    </div>
    <div v-if="editMsg" class="rounded-md border border-success bg-success-container px-3 py-2 text-sm text-on-success-container">
      {{ editMsg }}
    </div>

    <div class="flex gap-2">
      <input
        v-model="search"
        class="flex-1 rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
        :placeholder="t('searchUsers')"
        @keyup.enter="listUsers"
      />
      <button
        class="rounded-md bg-primary px-4 py-2 text-sm font-medium text-on-primary hover:bg-primary-hover"
        @click="listUsers"
      >
        {{ loading ? t("loading") : t("listUsers") }}
      </button>
      <button
        class="rounded-md border border-outline px-4 py-2 text-sm text-on-surface-variant hover:bg-surface-container-high"
        @click="showCreate = !showCreate"
      >
        + {{ t("createUser") }}
      </button>
    </div>

    <div v-if="showCreate" class="rounded-lg border border-outline-variant bg-surface-container p-4">
      <h3 class="mb-1 text-sm font-medium text-on-surface">{{ t("createUserTitle") }}</h3>
      <p class="mb-3 text-xs text-on-surface-variant">{{ t("newUserHint") }}</p>
      <div class="grid gap-3 sm:grid-cols-3">
        <input
          v-model="newUser.userId"
          :placeholder="t('userId')"
          class="rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
        />
        <input
          v-model="newUser.password"
          type="password"
          :placeholder="t('password')"
          class="rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
        />
        <input
          v-model="newUser.displayName"
          :placeholder="t('displayName')"
          class="rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
        />
      </div>
      <button
        class="mt-3 rounded-md bg-primary px-4 py-2 text-sm font-medium text-on-primary hover:bg-primary-hover"
        @click="createUser"
      >
        {{ t("create") }}
      </button>
    </div>

    <div class="grid min-h-0 flex-1 grid-cols-2 gap-4">
      <div class="overflow-y-auto rounded-lg border border-outline-variant">
        <ul v-if="users.length" class="divide-y divide-outline-variant/60">
          <li v-for="userId in users" :key="userId">
            <button
              class="w-full px-4 py-2 text-left text-sm text-on-surface hover:bg-surface-container-high/60"
              :class="selected?.id === userId ? 'bg-primary-container/60 text-on-primary-container' : ''"
              @click="selectUser(userId)"
            >
              {{ userId }}
            </button>
          </li>
        </ul>
        <p v-else class="p-4 text-sm text-on-surface-variant">
          {{ loading ? t("loading") : t("noUsersYet") }}
        </p>
      </div>

      <div class="overflow-y-auto rounded-lg border border-outline-variant p-4">
        <p v-if="detailsLoading" class="text-sm text-on-surface-variant">{{ t("loadingDetails") }}</p>
        <template v-else-if="selected">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0">
              <h3 class="truncate text-base font-medium text-on-surface">{{ selected.displayName || selected.id }}</h3>
              <p class="text-sm text-on-surface-variant">{{ selected.id }}</p>
              <p class="mt-1 text-xs">
                <span
                  class="rounded px-1.5 py-0.5 font-semibold"
                  :class="selected.enabled ? 'bg-success/15 text-success' : 'bg-error/15 text-error'"
                >
                  {{ selected.enabled ? t("enabled") : t("disabled") }}
                </span>
              </p>
            </div>
            <div class="flex shrink-0 flex-col items-end gap-1.5">
              <button
                class="rounded-md border border-outline px-2.5 py-1 text-xs text-on-surface-variant hover:bg-surface-container-high"
                @click="emit('browse', selected.id)"
              >
                {{ t("browseFiles") }}
              </button>
              <button
                class="rounded-md border border-outline px-2.5 py-1 text-xs text-on-surface-variant hover:bg-surface-container-high"
                @click="toggleEnabled"
              >
                {{ selected.enabled ? t("disableAccount") : t("enableAccount") }}
              </button>
              <button
                class="rounded-md border border-error px-2.5 py-1 text-xs text-error hover:bg-error-container"
                @click="removeUser"
              >
                {{ t("deleteUser") }}
              </button>
            </div>
          </div>

          <div class="mt-4 space-y-3">
            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-on-surface-variant">
                {{ t("displayName") }}
              </label>
              <div class="flex gap-2">
                <input
                  v-model="edits.displayName"
                  class="flex-1 rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface focus:border-primary"
                />
                <button
                  class="rounded-md bg-primary px-3 py-2 text-sm text-on-primary hover:bg-primary-hover"
                  @click="saveField('displayname')"
                >
                  {{ t("save") }}
                </button>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-on-surface-variant">
                {{ t("email") }}
              </label>
              <div class="flex gap-2">
                <input
                  v-model="edits.email"
                  type="email"
                  class="flex-1 rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface focus:border-primary"
                />
                <button
                  class="rounded-md bg-primary px-3 py-2 text-sm text-on-primary hover:bg-primary-hover"
                  @click="saveField('email')"
                >
                  {{ t("save") }}
                </button>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-on-surface-variant">
                {{ t("password") }}
              </label>
              <div class="flex gap-2">
                <input
                  v-model="edits.password"
                  :type="showPassword ? 'text' : 'password'"
                  :placeholder="t('passwordPlaceholder')"
                  class="flex-1 rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
                />
                <button
                  class="rounded-md bg-surface-container-high px-3 py-2 text-sm text-on-surface-variant hover:bg-surface-container-highest"
                  @click="showPassword = !showPassword"
                >
                  {{ showPassword ? t("hide") : t("show") }}
                </button>
                <button
                  class="rounded-md bg-primary px-3 py-2 text-sm text-on-primary hover:bg-primary-hover"
                  @click="saveField('password')"
                >
                  {{ t("save") }}
                </button>
              </div>
            </div>

            <div>
              <p class="mb-1.5 text-xs font-medium uppercase tracking-wide text-on-surface-variant">
                {{ t("groups") }}
              </p>
              <div v-if="selected.groups.length" class="flex flex-wrap gap-1.5">
                <span
                  v-for="group in selected.groups"
                  :key="group"
                  class="rounded bg-surface-container-high px-2 py-0.5 text-xs text-on-surface-variant"
                >
                  {{ group }}
                </span>
              </div>
              <p v-else class="text-xs text-outline">{{ t("noGroups") }}</p>
            </div>

            <div class="rounded-md bg-surface-container-high/60 p-3 text-sm text-on-surface-variant">
              <div class="flex justify-between">
                <span class="text-on-surface-variant">{{ t("quota") }}</span>
                <span>{{ quotaTotal(selected.quota) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-on-surface-variant">{{ t("used") }}</span>
                <span>{{ quotaUsed(selected.quota) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-on-surface-variant">{{ t("free") }}</span>
                <span>{{ quotaFree(selected.quota) }}</span>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-on-surface-variant">
                {{ t("setQuota") }}
              </label>
              <div class="flex gap-2">
                <select
                  v-model="edits.quotaPreset"
                  class="rounded-md border border-outline bg-surface-container-high px-2 py-2 text-sm text-on-surface focus:border-primary"
                >
                  <option value="1gb">1 GB</option>
                  <option value="5gb">5 GB</option>
                  <option value="10gb">10 GB</option>
                  <option value="unlimited">{{ t("unlimited") }}</option>
                  <option value="custom">{{ t("custom") }}</option>
                </select>
                <input
                  v-model.number="edits.quotaValue"
                  type="number"
                  :disabled="edits.quotaUnit === 'unlimited'"
                  min="0"
                  step="0.1"
                  class="flex-1 rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface focus:border-primary disabled:opacity-40"
                />
                <select
                  v-model="edits.quotaUnit"
                  :disabled="edits.quotaUnit === 'unlimited'"
                  class="rounded-md border border-outline bg-surface-container-high px-2 py-2 text-sm text-on-surface focus:border-primary disabled:opacity-40"
                >
                  <option value="gb">{{ t("gb") }}</option>
                  <option value="mb">{{ t("mb") }}</option>
                  <option value="unlimited">{{ t("unlimited") }}</option>
                </select>
                <button
                  class="rounded-md bg-primary px-3 py-2 text-sm text-on-primary hover:bg-primary-hover"
                  @click="setQuota"
                >
                  {{ t("save") }}
                </button>
              </div>
            </div>
          </div>
        </template>
        <p v-else class="text-sm text-on-surface-variant">{{ t("selectUser") }}</p>
      </div>
    </div>
  </div>
</template>
