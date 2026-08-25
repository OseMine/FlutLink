<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import { api, invokeError, type UserDetails, type UserQuota } from "../lib/ipc";
import { useUiStore } from "../stores/ui";
import { useAccountsStore } from "../stores/accounts";
import { translate } from "../lib/i18n";
import { formatBytes } from "../lib/format";

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
  // U-R8-12: never load every user of a large instance at once — fetch only
  // the first PAGE-sized page (offset reset); "load more" appends further
  // pages sequentially via loadPage(true).
  if (!query) {
    if (requireQuery) error.value = t("searchUsersRequired");
    return;
  }
  await loadPage(false);
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

// L15-F3/#289: request counter — a stale adminGetUser response (slow click on
// A, fast click on B) must not overwrite the newer selection.
let selectSeq = 0;

async function selectUser(userId: string) {
  const seq = ++selectSeq;
  detailsLoading.value = true;
  error.value = null;
  editMsg.value = null;
  try {
    const details = await api.adminGetUser(userId);
    if (seq !== selectSeq) return; // out-of-order response, discard
    selected.value = details;
    edits.displayName = details.displayName ?? "";
    edits.email = details.email ?? "";
    edits.password = "";
    setQuotaFromTotal(details.quota?.total ?? null);
  } catch (e) {
    if (seq !== selectSeq) return;
    error.value = invokeError(e).message;
  } finally {
    if (seq === selectSeq) detailsLoading.value = false;
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
    // L19-F6: an emptied/invalid field yields NaN — `NaN <= 0` is false, so
    // the old check let "NaN" through to the OCS API.
    if (value === null || !Number.isFinite(value) || value <= 0) {
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
      <h2 class="text-lg font-semibold">{{ t("adminPanelTitle") }}</h2>
      <p class="text-sm text-muted">{{ t("adminPanelSubtitle") }}</p>
    </div>

    <div v-if="error" class="rounded-md border border-error/40 bg-error/10 px-3 py-2 text-sm text-error">
      {{ error }}
    </div>
    <div v-if="editMsg" class="rounded-md border border-success/40 bg-success/10 px-3 py-2 text-sm text-success">
      {{ editMsg }}
    </div>

    <div class="flex gap-2">
      <input
        v-model="search"
        type="text"
        :placeholder="t('searchUsers')"
        class="input flex-1"
        @keyup.enter="listUsers()"
      />
      <button type="button" class="btn btn-primary shrink-0" @click="listUsers()">
        {{ loading ? t("loading") : t("listUsers") }}
      </button>
      <button type="button" class="btn btn-outline shrink-0" @click="showCreate = !showCreate">
        + {{ t("createUser") }}
      </button>
    </div>

    <div v-if="showCreate" class="card p-4">
      <h3 class="mb-1 text-sm font-medium">{{ t("createUserTitle") }}</h3>
      <p class="mb-3 text-xs text-muted">{{ t("newUserHint") }}</p>
      <div class="grid gap-3 sm:grid-cols-3">
        <input
          v-model="newUser.userId"
          type="text"
          :placeholder="t('userId')"
          class="input"
        />
        <input
          v-model="newUser.password"
          type="password"
          :placeholder="t('password')"
          class="input"
        />
        <input
          v-model="newUser.displayName"
          type="text"
          :placeholder="t('displayName')"
          class="input"
        />
      </div>
      <button type="button" class="btn btn-primary mt-3" @click="createUser">
        {{ t("create") }}
      </button>
    </div>

    <div class="grid min-h-0 flex-1 grid-cols-2 gap-4">
      <!-- User list -->
      <div class="card min-h-0 overflow-y-auto !rounded-md">
        <ul v-if="users.length" class="divide-y divide-line">
          <li v-for="userId in users" :key="userId">
            <button
              type="button"
              class="w-full px-4 py-2 text-left text-sm transition hover:bg-card-hover"
              :class="selected?.id === userId ? 'bg-primary/10 text-primary' : ''"
              @click="selectUser(userId)"
            >
              {{ userId }}
            </button>
          </li>
        </ul>
        <button
          v-if="hasMore && users.length"
          type="button"
          class="btn btn-outline m-3 !w-[calc(100%-1.5rem)]"
          :disabled="loading"
          @click="loadMore"
        >
          {{ loading ? t("loading") : t("loadMore") }}
        </button>
        <p v-if="!users.length" class="p-4 text-sm text-muted">
          {{ loading ? t("loading") : t("noUsersYet") }}
        </p>
      </div>

      <!-- Details -->
      <div class="card min-h-0 overflow-y-auto p-4">
        <p v-if="detailsLoading" class="text-sm text-muted">{{ t("loadingDetails") }}</p>
        <template v-else-if="selected">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0">
              <h3 class="truncate text-base font-medium">{{ selected.displayName || selected.id }}</h3>
              <p class="text-sm text-muted">{{ selected.id }}</p>
              <p class="mt-1">
                <span class="badge normal-case">
                  <span class="badge-dot" :class="selected.enabled ? 'bg-success' : 'bg-error'"></span>
                  {{ selected.enabled ? t("enabled") : t("disabled") }}
                </span>
              </p>
            </div>
            <div class="flex shrink-0 flex-col items-end gap-1.5">
              <button type="button" class="btn btn-outline h-7 text-xs" @click="emit('browse', selected.id)">
                {{ t("browseFiles") }}
              </button>
              <button type="button" class="btn btn-outline h-7 text-xs" @click="toggleEnabled">
                {{ selected.enabled ? t("disableAccount") : t("enableAccount") }}
              </button>
              <button type="button" class="btn btn-danger h-7 text-xs" @click="removeUser">
                {{ t("deleteUser") }}
              </button>
            </div>
          </div>

          <div class="mt-4 space-y-3">
            <div>
              <label class="mb-1 block text-[11px] font-medium uppercase tracking-wide text-muted">
                {{ t("displayName") }}
              </label>
              <div class="flex gap-2">
                <input
                  v-model="edits.displayName"
                  type="text"
                  class="input flex-1"
                />
                <button type="button" class="btn btn-primary shrink-0" @click="saveField('displayname')">
                  {{ t("save") }}
                </button>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-[11px] font-medium uppercase tracking-wide text-muted">
                {{ t("email") }}
              </label>
              <div class="flex gap-2">
                <input
                  v-model="edits.email"
                  type="email"
                  class="input flex-1"
                />
                <button type="button" class="btn btn-primary shrink-0" @click="saveField('email')">
                  {{ t("save") }}
                </button>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-[11px] font-medium uppercase tracking-wide text-muted">
                {{ t("password") }}
              </label>
              <div class="flex gap-2">
                <input
                  v-model="edits.password"
                  :type="showPassword ? 'text' : 'password'"
                  :placeholder="t('passwordPlaceholder')"
                  class="input flex-1"
                />
                <button type="button" class="btn btn-outline h-[34px] shrink-0 px-2" @click="showPassword = !showPassword">
                  {{ showPassword ? t("hide") : t("show") }}
                </button>
                <button type="button" class="btn btn-primary shrink-0" @click="saveField('password')">
                  {{ t("save") }}
                </button>
              </div>
            </div>

            <div>
              <p class="mb-1.5 text-[11px] font-medium uppercase tracking-wide text-muted">
                {{ t("groups") }}
              </p>
              <div v-if="selected.groups.length" class="flex flex-wrap gap-1.5">
                <span
                  v-for="group in selected.groups"
                  :key="group"
                  class="badge normal-case"
                >
                  {{ group }}
                  <button
                    type="button"
                    class="leading-none transition hover:text-error"
                    :title="t('removeFromGroup')"
                    @click="removeFromGroup(group)"
                  >
                    ×
                  </button>
                </span>
              </div>
              <p v-else class="text-xs text-muted/80">{{ t("noGroups") }}</p>
              <div class="mt-2 flex gap-2">
                <input
                  v-model="groupInput"
                  type="text"
                  :placeholder="t('groupName')"
                  class="input flex-1"
                  @keyup.enter="addToGroup"
                />
                <button type="button" class="btn btn-primary shrink-0" @click="addToGroup">
                  {{ t("addToGroup") }}
                </button>
                <button type="button" class="btn btn-outline shrink-0" @click="createGroup">
                  {{ t("createGroup") }}
                </button>
              </div>
            </div>

            <div class="rounded-md bg-card-hover p-3 text-sm">
              <div class="flex justify-between">
                <span class="text-muted">{{ t("quota") }}</span>
                <span class="tabular-nums">{{ quotaTotal(selected.quota) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">{{ t("used") }}</span>
                <span class="tabular-nums">{{ quotaUsed(selected.quota) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">{{ t("free") }}</span>
                <span class="tabular-nums">{{ quotaFree(selected.quota) }}</span>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-[11px] font-medium uppercase tracking-wide text-muted">
                {{ t("setQuota") }}
              </label>
              <div class="flex gap-2">
                <select
                  v-model="edits.quotaPreset"
                  class="input w-32 shrink-0"
                >
                  <option value="1gb">1 GB</option>
                  <option value="5gb">5 GB</option>
                  <option value="10gb">10 GB</option>
                  <option value="unlimited">{{ t("unlimited") }}</option>
                  <option value="custom">{{ t("custom") }}</option>
                </select>
                <input
                  type="number"
                  :value="edits.quotaValue"
                  @input="edits.quotaValue = ($event.target as HTMLInputElement).valueAsNumber"
                  :disabled="edits.quotaUnit === 'unlimited'"
                  min="0"
                  step="0.1"
                  class="input flex-1"
                />
                <select
                  v-model="edits.quotaUnit"
                  :disabled="edits.quotaUnit === 'unlimited'"
                  class="input w-24 shrink-0"
                >
                  <option value="gb">{{ t("gb") }}</option>
                  <option value="mb">{{ t("mb") }}</option>
                  <option value="unlimited">{{ t("unlimited") }}</option>
                </select>
                <button type="button" class="btn btn-primary shrink-0" @click="setQuota">
                  {{ t("save") }}
                </button>
              </div>
            </div>
          </div>
        </template>
        <p v-else class="text-sm text-muted">{{ t("selectUser") }}</p>
      </div>
    </div>
  </div>
</template>
