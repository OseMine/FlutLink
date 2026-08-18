<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import { api, invokeError, type UserDetails, type UserQuota } from "../lib/ipc";
import { useUiStore } from "../stores/ui";
import { useAccountsStore } from "../stores/accounts";
import { translate } from "../lib/i18n";
import { formatBytes } from "../lib/format";
import "@material/web/button/filled-button.js";
import "@material/web/button/outlined-button.js";
import "@material/web/textfield/outlined-text-field.js";
import "@material/web/divider/divider.js";
import "@material/web/switch/switch.js";
import "@material/web/select/outlined-select.js";
import "@material/web/select/select-option.js";

const emit = defineEmits<{ browse: [userId: string] }>();

const ui = useUiStore();
const accounts = useAccountsStore();
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

// Server-side pagination: the OCS API caps each request at 200 users, so on
// large instances the list is fetched page by page via "load more" instead of
// one blocking, unbounded request sequence (U-R8-12).
const PAGE = 200;
const offset = ref(0);
const hasMore = ref(false);

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

const groupInput = ref("");

async function addToGroup() {
  if (!selected.value) return;
  const group = groupInput.value.trim();
  if (!group) {
    error.value = t("groupNameEmpty");
    return;
  }
  error.value = null;
  editMsg.value = null;
  try {
    editMsg.value = await api.adminAddGroupMember(group, selected.value.id);
    groupInput.value = "";
    await selectUser(selected.value.id);
    ui.toast(t("groupMemberAdded"), "success");
  } catch (e) {
    error.value = invokeError(e).message;
  }
}

async function removeFromGroup(group: string) {
  if (!selected.value) return;
  error.value = null;
  editMsg.value = null;
  try {
    editMsg.value = await api.adminRemoveGroupMember(group, selected.value.id);
    await selectUser(selected.value.id);
    ui.toast(t("groupMemberRemoved"), "success");
  } catch (e) {
    error.value = invokeError(e).message;
  }
}

async function createGroup() {
  const group = groupInput.value.trim();
  if (!group) {
    error.value = t("groupNameEmpty");
    return;
  }
  error.value = null;
  editMsg.value = null;
  try {
    editMsg.value = await api.adminCreateGroup(group);
    groupInput.value = "";
    ui.toast(t("groupCreated"), "success");
  } catch (e) {
    error.value = invokeError(e).message;
  }
}

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

async function listUsers(requireQuery = true) {
  const query = search.value.trim();
  // U-R8-12: never load every user of a large instance at once — the OCS API
  // would paginate through all of them with one request per page.
  if (!query) {
    if (requireQuery) error.value = t("searchUsersRequired");
    return;
  }
  loading.value = true;
  error.value = null;
  editMsg.value = null;
  users.value = [];
  try {
    const result = await api.adminListUsers(query);
    users.value = result.users;
  } catch (e) {
    error.value = invokeError(e).message;
  } finally {
    loading.value = false;
  }
}

async function loadMore() {
  await loadPage(true);
}

async function loadPage(append: boolean) {
  loading.value = true;
  error.value = null;
  editMsg.value = null;
  if (!append) users.value = [];
  try {
    const { users: page, hasMore: more } = await api.adminListUsers(
      search.value.trim(),
      PAGE,
      append ? offset.value : 0
    );
    if (append) {
      // Guard against servers that ignore `offset` and repeat a page.
      const known = new Set(users.value);
      const fresh = page.filter((u) => !known.has(u));
      users.value = [...users.value, ...fresh];
      hasMore.value = more && fresh.length > 0;
    } else {
      users.value = page;
      hasMore.value = more;
    }
    offset.value = (append ? offset.value : 0) + PAGE;
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
  if (selected.value.id === accounts.active?.username) {
    error.value = t("cannotDisableSelf");
    return;
  }
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
  if (selected.value.id === accounts.active?.username) {
    error.value = t("cannotDeleteSelf");
    return;
  }
  if (!window.confirm(t("deleteUserConfirm").replace("{name}", name))) return;
  error.value = null;
  editMsg.value = null;
  try {
    editMsg.value = await api.adminDeleteUser(selected.value.id);
    selected.value = null;
    await listUsers(false);
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
    await listUsers(false);
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
      <md-outlined-text-field
        :label="t('searchUsers')"
        :value="search"
        @input="search = ($event.target as HTMLInputElement).value"
        @keyup.enter="listUsers()"
        class="flex-1"
      ></md-outlined-text-field>
      <md-filled-button @click="listUsers()">
        {{ loading ? t("loading") : t("listUsers") }}
      </md-filled-button>
      <md-outlined-button @click="showCreate = !showCreate">
        + {{ t("createUser") }}
      </md-outlined-button>
    </div>

    <div v-if="showCreate" class="rounded-lg border border-outline-variant bg-surface-container p-4">
      <h3 class="mb-1 text-sm font-medium text-on-surface">{{ t("createUserTitle") }}</h3>
      <p class="mb-3 text-xs text-on-surface-variant">{{ t("newUserHint") }}</p>
      <div class="grid gap-3 sm:grid-cols-3">
        <md-outlined-text-field
          :placeholder="t('userId')"
          :value="newUser.userId"
          @input="newUser.userId = ($event.target as HTMLInputElement).value"
        ></md-outlined-text-field>
        <md-outlined-text-field
          type="password"
          :placeholder="t('password')"
          :value="newUser.password"
          @input="newUser.password = ($event.target as HTMLInputElement).value"
        ></md-outlined-text-field>
        <md-outlined-text-field
          :placeholder="t('displayName')"
          :value="newUser.displayName"
          @input="newUser.displayName = ($event.target as HTMLInputElement).value"
        ></md-outlined-text-field>
      </div>
      <md-filled-button class="mt-3" @click="createUser">
        {{ t("create") }}
      </md-filled-button>
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
        <md-outlined-button
          v-if="hasMore && users.length"
          class="w-full"
          :disabled="loading"
          @click="loadMore"
        >
          {{ loading ? t("loading") : t("loadMore") }}
        </md-outlined-button>
        <p v-if="!users.length" class="p-4 text-sm text-on-surface-variant">
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
              <md-outlined-button
                @click="emit('browse', selected.id)"
              >
                {{ t("browseFiles") }}
              </md-outlined-button>
              <md-outlined-button
                @click="toggleEnabled"
              >
                {{ selected.enabled ? t("disableAccount") : t("enableAccount") }}
              </md-outlined-button>
              <md-outlined-button class="error-btn"
                @click="removeUser"
              >
                {{ t("deleteUser") }}
              </md-outlined-button>
            </div>
          </div>

          <div class="mt-4 space-y-3">
            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-on-surface-variant">
                {{ t("displayName") }}
              </label>
              <div class="flex gap-2">
                <md-outlined-text-field
                  :value="edits.displayName"
                  @input="edits.displayName = ($event.target as HTMLInputElement).value"
                  class="flex-1"
                ></md-outlined-text-field>
                <md-filled-button
                  @click="saveField('displayname')"
                >
                  {{ t("save") }}
                </md-filled-button>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-on-surface-variant">
                {{ t("email") }}
              </label>
              <div class="flex gap-2">
                <md-outlined-text-field
                  type="email"
                  :value="edits.email"
                  @input="edits.email = ($event.target as HTMLInputElement).value"
                  class="flex-1"
                ></md-outlined-text-field>
                <md-filled-button
                  @click="saveField('email')"
                >
                  {{ t("save") }}
                </md-filled-button>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-on-surface-variant">
                {{ t("password") }}
              </label>
              <div class="flex gap-2">
                <md-outlined-text-field
                  :type="showPassword ? 'text' : 'password'"
                  :placeholder="t('passwordPlaceholder')"
                  :value="edits.password"
                  @input="edits.password = ($event.target as HTMLInputElement).value"
                  class="flex-1"
                ></md-outlined-text-field>
                <md-outlined-button
                  @click="showPassword = !showPassword"
                >
                  {{ showPassword ? t("hide") : t("show") }}
                </md-outlined-button>
                <md-filled-button
                  @click="saveField('password')"
                >
                  {{ t("save") }}
                </md-filled-button>
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
                  class="flex items-center gap-1 rounded bg-surface-container-high px-2 py-0.5 text-xs text-on-surface-variant"
                >
                  {{ group }}
                  <button
                    class="leading-none text-on-surface-variant hover:text-error"
                    :title="t('removeFromGroup')"
                    @click="removeFromGroup(group)"
                  >
                    ×
                  </button>
                </span>
              </div>
              <p v-else class="text-xs text-outline">{{ t("noGroups") }}</p>
              <div class="mt-2 flex gap-2">
                <md-outlined-text-field
                  :placeholder="t('groupName')"
                  :value="groupInput"
                  @input="groupInput = ($event.target as HTMLInputElement).value"
                  @keyup.enter="addToGroup"
                  class="flex-1"
                ></md-outlined-text-field>
                <md-filled-button
                  @click="addToGroup"
                >
                  {{ t("addToGroup") }}
                </md-filled-button>
                <md-outlined-button
                  @click="createGroup"
                >
                  {{ t("createGroup") }}
                </md-outlined-button>
              </div>
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
                <md-outlined-select
                  :value="edits.quotaPreset"
                  @change="edits.quotaPreset = ($event.target as HTMLSelectElement).value as QuotaPresetId"
                >
                  <md-select-option value="1gb">1 GB</md-select-option>
                  <md-select-option value="5gb">5 GB</md-select-option>
                  <md-select-option value="10gb">10 GB</md-select-option>
                  <md-select-option value="unlimited">{{ t("unlimited") }}</md-select-option>
                  <md-select-option value="custom">{{ t("custom") }}</md-select-option>
                </md-outlined-select>
                <md-outlined-text-field
                  type="number"
                  :value="edits.quotaValue"
                  @input="edits.quotaValue = ($event.target as HTMLInputElement).valueAsNumber"
                  :disabled="edits.quotaUnit === 'unlimited'"
                  min="0"
                  step="0.1"
                  class="flex-1"
                ></md-outlined-text-field>
                <md-outlined-select
                  :value="edits.quotaUnit"
                  @change="edits.quotaUnit = ($event.target as HTMLSelectElement).value as 'gb' | 'mb' | 'unlimited'"
                  :disabled="edits.quotaUnit === 'unlimited'"
                >
                  <md-select-option value="gb">{{ t("gb") }}</md-select-option>
                  <md-select-option value="mb">{{ t("mb") }}</md-select-option>
                  <md-select-option value="unlimited">{{ t("unlimited") }}</md-select-option>
                </md-outlined-select>
                <md-filled-button
                  @click="setQuota"
                >
                  {{ t("save") }}
                </md-filled-button>
              </div>
            </div>
          </div>
        </template>
        <p v-else class="text-sm text-on-surface-variant">{{ t("selectUser") }}</p>
      </div>
    </div>
  </div>
</template>

