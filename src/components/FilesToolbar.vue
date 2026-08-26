<script setup lang="ts">
import { watch } from "vue";
import { useAccountsStore } from "../stores/accounts";
import { useFilesStore } from "../stores/files";
import { useUiStore, type ViewMode } from "../stores/ui";
import { translate } from "../lib/i18n";
import { Icon } from "./Icon.vue";

const props = defineProps<{
  searchInput: string;
  setSearchInput: (value: string) => void;
  clearSearchInput: () => void;
  viewMode: ViewMode;
  setViewMode: (mode: ViewMode) => void;
  files: ReturnType<typeof useFilesStore>;
  t: (key: string) => string;
  openNewFolder: () => void;
  cancelNewFolder: () => void;
  uploading: boolean;
  toggleSplitView: () => void;
  adminViewAll: boolean;
  setAdminView: (value: boolean) => void;
  adminSearch: string;
  setAdminSearch: (value: string) => void;
  loadAdminUsers: () => void;
  adminUsers: string[];
  selectedUser: string;
  onUserSelect: () => void;
}>;

const emit = defineEmits<{
  openNewFolder: [];
  cancelNewFolder: [];
  rename: [entry: any];
  share: [entry: any];
}>;

const accounts = useAccountsStore();
const files = useFilesStore();
const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

watch(
  () => props.searchInput,
  (value) => {
    props.setSearchInput(value);
  }
);
watch(
  () => props.viewMode,
  (mode) => {
    props.setViewMode(mode);
  }
);
</script>

<template>
  <div class="flex items-center justify-between gap-3 border-b border-line px-6 py-3">
    <nav class="flex min-w-0 items-center gap-1 text-sm">
      <button
        type="button"
        class="icon-btn !h-7 !w-7"
        :disabled="files.crumbs.length <= 1"
        :title="t('back')"
        :aria-label="t('back')"
        @click="goBack"
      >
        <Icon name="back" :size="16" />
      </button>
      <template v-for="(crumb, i) in files.crumbs" :key="crumb.path">
        <button
          type="button"
          class="rounded-sm px-1.5 py-0.5 transition hover:bg-card-hover"
          :class="i === files.crumbs.length - 1 ? 'font-semibold' : 'text-muted'"
          @click="navigateTo(crumb.path)"
        >
          {{ crumb.path === "/" ? t("home") : crumb.label }}
        </button>
        <span v-if="i < files.crumbs.length - 1" class="text-muted/60">/</span>
      </nav>

      <div class="flex shrink-0 items-center gap-1.5">
        <!-- Search input -->
        <div class="flex h-8 w-52 items-center gap-1.5 rounded-sm border border-line-strong bg-card px-2 transition focus-within:border-primary">
          <Icon name="search" :size="14" class="shrink-0 text-muted" />
          <input
            v-model="searchInput"
            type="text"
            :placeholder="t('searchPlaceholder')"
            class="min-w-0 flex-1 bg-transparent text-[13px] outline-none placeholder:text-muted"
          />
          <button
            v-if="searchInput"
            type="button"
            class="grid h-5 w-5 shrink-0 place-items-center rounded-sm text-muted transition hover:text-fg"
            :title="t('clearSearch')"
            @click="clearSearchInput"
          >
            <Icon name="close" :size="13" />
          </button>
        </div>

        <!-- View toggle -->
        <div class="segment" role="group" :aria-label="t('viewList') + ' / ' + t('viewGrid')">
          <button
            type="button"
            :aria-pressed="viewMode === 'list'"
            :title="t('viewList')"
            @click="setViewMode('list')"
          >
            <Icon name="menu" :size="15" />
          </button>
          <button
            type="button"
            :aria-pressed="viewMode === 'grid'"
            :title="t('viewGrid')"
            @click="setViewMode('grid')"
          >
            <Icon name="grid" :size="15" />
          </button>
        </div>

        <button
          v-if="files.pairedPath"
          type="button"
          class="btn btn-outline"
          :class="{ '!border-primary/50 !bg-primary/10': files.splitView }"
          :title="t('splitViewHint')"
          @click="toggleSplitView"
        >
          <Icon name="columns" :size="14" />
          {{ t("splitView") }}
        </button>

        <button
          type="button"
          class="btn btn-outline"
          @click="openNewFolder"
          :disabled="uploading"
          :title="t('newFolder')"
        >
          <Icon name="add" :size="14" />
          {{ t("newFolder") }}
        </button>

        <!-- Primary upload action -->
        <button
          type="button"
          class="btn btn-primary"
          :disabled="uploading"
          :title="t('upload')"
        >
          <Icon name="upload" :size="14" />
          {{ t("upload") }}
        </button>
      </div>
    </nav>

    <!-- Admin: scope + impersonation picker -->
    <template v-if="accounts.active?.isAdmin">
      <div
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
      </template>
    </div>
  </div>
</template>