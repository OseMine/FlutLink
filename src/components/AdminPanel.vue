<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { api, invokeError, type ActivityEntry, type UserDetails } from "../lib/ipc";
import { useUiStore } from "../stores/ui";
import { useAccountsStore } from "../stores/accounts";
import { translate } from "../lib/i18n";
import Icon from "./Icon.vue";
import AdminUserList from "./AdminUserList.vue";
import AdminUserDetails from "./AdminUserDetails.vue";
import QuotaEditor from "./QuotaEditor.vue";

const emit = defineEmits<{ browse: [userId: string] }>();

const ui = useUiStore();
const accounts = useAccountsStore();
const t = (key: string) => translate(ui.lang, key);

const search = ref("");
const users = ref<string[]>([]);
const selected = ref<UserDetails | null>(null);
const loading = ref(false);
const detailsLoading = ref(false);
const error = ref<string | null>(null);
const editMsg = ref<string | null>(null);
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
});

const newUser = reactive({
  userId: "",
  password: "",
  displayName: "",
});

const groupInput = ref("");

// Bumped whenever QuotaEditor must re-sync its inputs from `selected.quota`
// (i.e. exactly where the pre-split code called setQuotaFromTotal).
const quotaRevision = ref(0);

async function addToGroup(group: string) {
  if (!selected.value) return;
  group = group.trim();
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

// #425: bulk group membership — multi-select + add/remove against one group.
const bulkSelected = ref<string[]>([]);
const bulkGroup = ref("");
const bulkGroups = ref<string[]>([]);
const bulkBusy = ref(false);
const showBulkBar = ref(false);

function toggleSelect(userId: string) {
  const i = bulkSelected.value.indexOf(userId);
  if (i >= 0) bulkSelected.value.splice(i, 1);
  else bulkSelected.value.push(userId);
}

function selectAllInList() {
  const known = new Set(bulkSelected.value);
  for (const u of users.value) if (!known.has(u)) bulkSelected.value.push(u);
}

function clearSelection() {
  bulkSelected.value = [];
}

async function loadGroups() {
  try {
    bulkGroups.value = await api.adminListGroups("");
  } catch {
    bulkGroups.value = [];
  }
}

async function toggleBulkBar() {
  showBulkBar.value = !showBulkBar.value;
  if (showBulkBar.value) {
    bulkGroup.value = bulkGroups.value[0] ?? "";
    if (!bulkGroups.value.length) await loadGroups();
  }
}

async function runBulk(add: boolean) {
  if (!bulkSelected.value.length) {
    error.value = t("bulkNoSelection");
    return;
  }
  if (!bulkGroup.value.trim()) {
    error.value = t("bulkNoGroup");
    return;
  }
  error.value = null;
  editMsg.value = null;
  bulkBusy.value = true;
  try {
    const result = add
      ? await api.adminBulkAddGroupMembers(bulkGroup.value, [...bulkSelected.value])
      : await api.adminBulkRemoveGroupMembers(bulkGroup.value, [...bulkSelected.value]);
    ui.toast(
      add ? t("bulkAddDone").replace("{n}", String(result.succeeded)) : t("bulkRemoveDone").replace("{n}", String(result.succeeded)),
      "success"
    );
    if (result.failed.length) {
      error.value = result.failed.map((f) => `${f.userId}: ${f.reason}`).join("\n");
    }
    // Keep the selection so a partially-failed operation is easy to retry.
  } catch (e) {
    error.value = invokeError(e).message;
  } finally {
    bulkBusy.value = false;
  }
}

// #420: server-side activity feed (admin dashboard).
const activity = ref<ActivityEntry[]>([]);
const activityLoading = ref(false);
const activityError = ref<string | null>(null);

async function loadActivity() {
  activityLoading.value = true;
  activityError.value = null;
  try {
    activity.value = await api.adminActivityLog(50);
  } catch (e) {
    activityError.value = invokeError(e).message;
  } finally {
    activityLoading.value = false;
  }
}

function fmtActivityTime(datetime: string | null): string {
  if (!datetime) return "";
  const d = new Date(datetime);
  if (Number.isNaN(d.getTime())) return datetime;
  return d.toLocaleString();
}

async function createGroup(group: string) {
  group = group.trim();
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

async function listUsers(requireQuery = true) {
  const query = search.value.trim();
  if (!query) {
    if (requireQuery) error.value = t("searchUsersRequired");
    return;
  }
  await loadPage(false);
}

// U-R8-12: debounced auto-search — typing triggers a 300 ms delayed fetch.
let searchTimer: ReturnType<typeof setTimeout> | null = null;

function debouncedSearch() {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    searchTimer = null;
    void loadPage(false);
  }, 300);
}

onMounted(() => {
  void loadPage(false);
  void loadGroups();
  void loadActivity();
});

onUnmounted(() => {
  if (searchTimer) {
    clearTimeout(searchTimer);
    searchTimer = null;
  }
});

watch(search, () => {
  debouncedSearch();
});

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
    quotaRevision.value += 1;
    edits.displayName = details.displayName ?? "";
    edits.email = details.email ?? "";
    edits.password = "";
  } catch (e) {
    if (seq !== selectSeq) return;
    error.value = invokeError(e).message;
  } finally {
    if (seq === selectSeq) detailsLoading.value = false;
  }
}

async function saveField(key: "displayname" | "email" | "password") {
  if (!selected.value) return;
  // L22-F3: share selectUser's sequence counter — if another user was picked
  // while the PUT/refetch was in flight, its late response must not overwrite
  // the new selection or wipe its unsaved edits.
  const seq = selectSeq;
  const userId = selected.value.id;
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
    editMsg.value = await api.adminEditUser(userId, key, value);
    if (seq !== selectSeq) return; // selection changed meanwhile, discard
    if (key === "displayname" || key === "email") {
      const details = await api.adminGetUser(userId);
      if (seq !== selectSeq) return; // out-of-order response, discard
      selected.value = details;
      edits.displayName = details.displayName ?? "";
      edits.email = details.email ?? "";
    } else {
      edits.password = "";
    }
  } catch (e) {
    if (seq !== selectSeq) return;
    error.value = invokeError(e).message;
  }
}

// L22-F3-style batch save: commit only the fields the user actually changed,
// sequentially, reusing saveField's per-field guards and refetch logic.
async function saveEdits() {
  if (!selected.value) return;
  const u = selected.value;
  if (edits.displayName.trim() && edits.displayName !== (u.displayName ?? "")) {
    await saveField("displayname");
    if (!selected.value || selected.value.id !== u.id) return;
  }
  if (edits.email.trim() && edits.email !== (u.email ?? "")) {
    await saveField("email");
    if (!selected.value || selected.value.id !== u.id) return;
  }
  if (edits.password) await saveField("password");
}

async function setQuota(quota: string | null) {
  if (!selected.value) return;
  error.value = null;
  editMsg.value = null;
  if (quota === null) {
    error.value = t("quotaInvalid");
    return;
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
</script>

<template>
  <div class="flex h-full flex-col gap-4 overflow-y-auto p-6">
    <div class="flex items-center gap-3">
      <div class="min-w-0 flex-1">
        <h2 class="text-lg font-semibold">{{ t("adminPanelTitle") }}</h2>
        <p class="text-xs text-muted">{{ t("adminPanelSubtitle") }}</p>
      </div>
      <div class="relative w-64 shrink-0">
        <Icon name="search" :size="16" class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
        <input
          v-model="search"
          type="text"
          :placeholder="t('searchUsers')"
          class="input w-full pl-9"
        />
      </div>
      <button type="button" class="btn btn-primary shrink-0" @click="showCreate = !showCreate">
        + {{ t("createUser") }}
      </button>
    </div>

    <div v-if="error" class="rounded-md border border-error/40 bg-error/10 px-3 py-2 text-sm text-error">
      {{ error }}
    </div>
    <div v-if="editMsg" class="rounded-md border border-success/40 bg-success/10 px-3 py-2 text-sm text-success">
      {{ editMsg }}
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

    <div class="flex min-h-0 flex-1 overflow-hidden gap-4">
      <!-- User list (30 %) -->
      <div class="flex h-full w-[30%] shrink-0 flex-col">
        <div v-if="showBulkBar" class="card mb-2 !rounded-md p-2">
          <p class="mb-1.5 px-1 text-xs font-medium text-muted">
            {{ bulkSelected.length ? t("bulkHint").replace("{n}", String(bulkSelected.length)) : t("bulkNoSelection") }}
          </p>
          <div class="flex items-center gap-1.5">
            <select
              v-model="bulkGroup"
              class="input min-w-0 flex-1 !rounded-md px-2 py-1 text-sm"
              :disabled="bulkBusy"
            >
              <option value="" disabled>{{ t("bulkSelectGroup") }}</option>
              <option v-for="g in bulkGroups" :key="g" :value="g">{{ g }}</option>
            </select>
            <button
              type="button"
              class="btn btn-outline shrink-0 !px-2 !py-1 text-xs"
              :disabled="bulkBusy || !bulkSelected.length"
              @click="runBulk(true)"
            >
              {{ t("bulkAdd") }}
            </button>
            <button
              type="button"
              class="btn btn-outline shrink-0 !px-2 !py-1 text-xs"
              :disabled="bulkBusy || !bulkSelected.length"
              @click="runBulk(false)"
            >
              {{ t("bulkRemove") }}
            </button>
          </div>
        </div>
        <AdminUserList
          :users="users"
          :selected-id="selected?.id ?? null"
          :selected-ids="bulkSelected"
          :loading="loading"
          :has-more="hasMore"
          @select="selectUser"
          @toggle-select="toggleSelect"
          @select-all="selectAllInList"
          @clear-selection="clearSelection"
          @load-more="loadMore"
        />
        <button type="button" class="btn btn-ghost mt-2 shrink-0 !rounded-md text-xs" @click="toggleBulkBar">
          {{ showBulkBar ? t("hideBulk") : t("bulkBar") }}
        </button>
      </div>

      <!-- Details (70 %) -->
      <div class="card min-h-0 min-w-0 flex-1 p-4">
        <p v-if="detailsLoading" class="text-sm text-muted">{{ t("loadingDetails") }}</p>
        <template v-else-if="selected">
          <AdminUserDetails
            v-model:display-name="edits.displayName"
            v-model:email="edits.email"
            v-model:password="edits.password"
            v-model:group-input="groupInput"
            :user="selected"
            @browse="(id) => emit('browse', id)"
            @save="saveEdits"
            @toggle-enabled="toggleEnabled"
            @remove="removeUser"
            @add-to-group="addToGroup"
            @remove-from-group="removeFromGroup"
            @create-group="createGroup"
          >
            <QuotaEditor :quota="selected.quota" :revision="quotaRevision" @save="setQuota" />
          </AdminUserDetails>
        </template>
        <div v-else class="flex h-full flex-col items-center justify-center text-center">
          <Icon name="person" :size="48" class="mb-3 text-muted/30" />
          <p class="text-sm text-muted">{{ t("selectUser") }}</p>
        </div>
      </div>
    </div>

    <!-- #420: recent server activity -->
    <div class="card shrink-0 !rounded-md p-3">
      <div class="mb-2 flex items-center justify-between">
        <h3 class="text-sm font-medium">{{ t("activity") }}</h3>
        <button type="button" class="btn btn-ghost !px-2 !py-1 text-xs" :disabled="activityLoading" @click="loadActivity">
          <Icon name="sync" :size="14" :class="{ 'animate-spin': activityLoading }" class="inline" />
          {{ t("refresh") }}
        </button>
      </div>
      <div v-if="activityError" class="rounded-md border border-error/40 bg-error/10 px-3 py-2 text-xs text-error">
        {{ activityError }}
      </div>
      <div v-else-if="!activity.length" class="py-2 text-center text-xs text-muted">
        {{ t("activityEmpty") }}
      </div>
      <ul v-else class="max-h-40 space-y-1 overflow-y-auto pr-1">
        <li v-for="a in activity" :key="a.activityId" class="flex gap-2 text-xs">
          <span class="shrink-0 font-medium text-muted">{{ fmtActivityTime(a.datetime) }}</span>
          <span class="shrink-0 font-medium">{{ a.user }}</span>
          <span class="min-w-0 flex-1 truncate">{{ a.subject || a.message }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>
