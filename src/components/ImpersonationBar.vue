<script setup lang="ts">
import { ref, watch } from "vue";
import { api } from "../lib/ipc";
import { useAccountsStore } from "../stores/accounts";
import { useFilesStore } from "../stores/files";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";

const accounts = useAccountsStore();
const files = useFilesStore();
const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const adminUsers = ref<string[]>([]);
const adminViewAll = ref(true);
const selectedUser = ref<string>("");
const adminSearch = ref("");
// U-R8-12/L12-N3: cap the impersonation user lookup at one OCS page instead
// of paginating through every user of the instance.
const ADMIN_PAGE = 200;

async function loadAdminUsers() {
  if (!accounts.active?.isAdmin) return;
  const query = adminSearch.value.trim();
  // The impersonation dropdown must not fetch all users on mount or on
  // account switches — it loads lazily, only for an explicit search term,
  // limited to the first page.
  if (!query) {
    adminUsers.value = [];
    // Neutral hint instead of an error toast (#301): the required search term
    // is expected behaviour, not a failure.
    ui.toast(t("searchUsersHint"), "info");
    return;
  }
  try {
    const res = await api.adminListUsers(query, ADMIN_PAGE);
    adminUsers.value = res.users;
    if (files.targetUser && res.users.includes(files.targetUser)) {
      selectedUser.value = files.targetUser;
    }
  } catch {
    // user list unavailable; impersonation still selectable via retry button
  }
}

function setAdminView(all: boolean) {
  adminViewAll.value = all;
  if (all) {
    if (selectedUser.value) files.setTargetUser(selectedUser.value);
  } else {
    selectedUser.value = "";
    files.setTargetUser(null);
  }
}

function onUserSelect() {
  if (selectedUser.value) files.setTargetUser(selectedUser.value);
}

// Reset the picker state on account switch (moved out of FileExplorer's
// combined reset watcher — same trigger, same effect).
watch(
  () => accounts.active?.username,
  () => {
    adminViewAll.value = true;
    selectedUser.value = "";
    adminUsers.value = [];
    adminSearch.value = "";
  }
);
</script>

<template>
  <!-- Admin: scope + impersonation picker -->
  <div
    v-if="accounts.active?.isAdmin"
    class="flex flex-wrap items-center gap-3 border-b border-line bg-panel px-6 py-2"
  >
    <div class="segment">
      <button
        type="button"
        class="!w-auto px-3 text-xs font-medium"
        :class="{ '!bg-card !text-fg': adminViewAll }"
        :aria-pressed="adminViewAll"
        @click="setAdminView(true)"
      >
        {{ t("allUsersFolders") }}
      </button>
      <button
        type="button"
        class="!w-auto px-3 text-xs font-medium"
        :class="{ '!bg-card !text-fg': !adminViewAll }"
        :aria-pressed="!adminViewAll"
        @click="setAdminView(false)"
      >
        {{ t("myFilesOnly") }}
      </button>
    </div>

    <template v-if="adminViewAll">
      <input
        v-model="adminSearch"
        type="text"
        :placeholder="t('searchUsers')"
        class="input !h-7 w-44 text-xs"
        @keyup.enter="loadAdminUsers"
      />
      <div class="flex items-center gap-2">
        <span class="text-xs text-muted">{{ t("filterUser") }}</span>
        <select
          :value="selectedUser"
          class="input !h-7 w-36 text-xs"
          @change="selectedUser = ($event.target as HTMLSelectElement).value; onUserSelect()"
        >
          <option value="" disabled>{{ t("users") }}…</option>
          <option v-for="userId in adminUsers" :key="userId" :value="userId">
            {{ userId }}
          </option>
        </select>
      </div>
      <button
        v-if="!adminUsers.length"
        type="button"
        class="text-xs text-primary underline-offset-2 hover:underline"
        @click="loadAdminUsers"
      >
        {{ t("refresh") }}
      </button>
    </template>
  </div>

  <div
    v-if="files.targetUser && files.targetUser !== accounts.active?.username"
    class="flex items-center gap-2 border-b border-info/40 bg-info/10 px-6 py-1.5 text-xs text-info"
  >
    <span class="shrink-0 opacity-80">{{ t("impersonationNotice") }}</span>
    <span class="truncate font-semibold">{{ files.targetUser }}</span>
  </div>
</template>
