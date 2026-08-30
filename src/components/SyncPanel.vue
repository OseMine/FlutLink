<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { open } from "@tauri-apps/plugin-dialog";
import { useSyncStore } from "../stores/sync";
import { useUiStore } from "../stores/ui";
import { translate, translateError } from "../lib/i18n";
import { invokeError } from "../lib/ipc";
import Icon from "./Icon.vue";
import SyncLog from "./SyncLog.vue";

interface FolderItem {
  folderId: string;
  localPath: string;
  paused?: boolean;
}

const sync = useSyncStore();
const ui = useUiStore();

const followSymlinks = ref(false);
const showSyncLog = ref(false);
const logFolderId = ref<string | undefined>(undefined);
const isProcessing = ref(false);

const t = (key: string) => translate(ui.lang, key);
const hasFolders = computed(() => sync.folders.length > 0);

function errorLabel(err: { code: string; detail?: string | null }): string {
  return translateError(ui.lang, err.code, err.detail);
}

function stateLabel(state: string): string {
  const stateKeys: Record<string, string> = {
    idle: "stateIdle",
    syncing: "stateSyncing",
    paused: "statePaused",
    error: "stateError",
  };
  return t(stateKeys[state] || "stateUnknown");
}

function lastSyncedLabel(ts: number | null): string {
  if (ts === null) return t("neverSynced");
  return new Date(ts * 1000).toLocaleString(ui.lang);
}

function stateDotClass(state: string): string {
  switch (state) {
    case "idle":
      return "bg-success";
    case "syncing":
      return "bg-primary animate-pulse";
    case "error":
      return "bg-error";
    default:
      return "bg-muted";
  }
}

onMounted(() => {
  void sync.load();
  void sync.bind();
});

async function pickFolder() {
  if (isProcessing.value) return;
  isProcessing.value = true;
  try {
    const selected = await open({
      directory: true,
      multiple: false,
      title: t("chooseFolder"),
    });
    if (typeof selected === "string") {
      await sync.add(selected, followSymlinks.value);
      ui.toast(t("syncAdded"), "success");
    }
  } catch (e) {
    const err = invokeError(e);
    sync.error =
      err.code === "sync_folder_conflict"
        ? t("syncConflictMessage")
        : err.message;
  } finally {
    isProcessing.value = false;
  }
}

async function remove(folder: FolderItem) {
  if (!window.confirm(t("syncRemoveConfirm").replace("{path}", folder.localPath))) return;
  try {
    await sync.remove(folder.folderId);
    ui.toast(t("syncRemoved"), "success");
  } catch {
    // Error state managed by sync store
  }
}

async function togglePaused(folder: FolderItem) {
  try {
    await sync.setPaused(folder.folderId, !folder.paused);
  } catch {
    // Error state managed by sync store
  }
}

async function syncNow() {
  if (!hasFolders.value || isProcessing.value) return;
  isProcessing.value = true;
  try {
    await sync.trigger();
    ui.toast(t("syncTriggered"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    isProcessing.value = false;
  }
}
</script>

<template>
  <div class="mx-auto w-full max-w-3xl p-6">
    <!-- Header Layout Fixed for Small Widths -->
    <div class="mb-6 flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
      <div class="max-w-md shrink">
        <h2 class="text-xl font-semibold tracking-tight">{{ t("syncFolders") }}</h2>
        <p class="mt-1 text-xs leading-relaxed text-muted">{{ t("noSyncFoldersHint") }}</p>
      </div>

      <!-- Controls Row -->
      <div class="flex flex-wrap items-center gap-2.5">
        <button
          type="button"
          class="btn btn-outline"
          :disabled="!hasFolders || isProcessing"
          @click="syncNow"
        >
          {{ t("syncNow") }}
        </button>

        <label class="flex cursor-pointer select-none items-center gap-2 text-xs text-muted hover:text-fg">
          <input
            v-model="followSymlinks"
            type="checkbox"
            class="checkbox h-4 w-4 rounded border-line-strong bg-card accent-primary focus:ring-1"
          />
          {{ t("followSymlinks") }}
        </label>

        <button
          type="button"
          class="btn btn-outline"
          @click="showSyncLog = true; logFolderId = undefined"
        >
          <Icon name="history" :size="14" />
          {{ t("syncLogTitle") }}
        </button>

        <button
          type="button"
          class="btn btn-primary"
          :disabled="isProcessing"
          @click="pickFolder"
        >
          <Icon name="add" :size="14" />
          {{ t("addFolder") }}
        </button>
      </div>
    </div>

    <!-- Error Banner -->
    <div
      v-if="sync.error"
      class="mb-4 rounded-md border border-error/40 bg-error/10 px-3 py-2 text-xs text-error"
    >
      {{ sync.error }}
    </div>

    <!-- Loading State -->
    <div v-if="sync.loading" class="py-8 text-center text-sm text-muted">
      …
    </div>

    <!-- Empty State with Embedded Action -->
    <div
      v-else-if="!hasFolders"
      class="flex flex-col items-center justify-center rounded-lg border border-dashed border-line-strong p-10 text-center"
    >
      <p class="text-sm text-muted">{{ t("noSyncFolders") }}</p>
      <button
        type="button"
        class="btn btn-primary mt-4"
        :disabled="isProcessing"
        @click="pickFolder"
      >
        <Icon name="add" :size="14" />
        {{ t("addFolder") }}
      </button>
    </div>

    <!-- Folder Cards List -->
    <div v-else class="space-y-3">
      <div
        v-for="folder in sync.folders"
        :key="folder.folderId"
        class="card p-4 transition hover:border-line-strong"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <p class="truncate font-medium">{{ folder.localPath }}</p>
            <p class="truncate text-xs text-muted">
              {{ t("remoteFolder") }}: {{ folder.remotePath }}
            </p>
            <p v-if="folder.followSymlinks" class="mt-0.5 text-xs text-muted">
              ⤷ {{ t("followSymlinksEnabled") }}
            </p>
          </div>

          <span class="badge normal-case">
            <span class="badge-dot" :class="stateDotClass(folder.state)"></span>
            {{ stateLabel(folder.state) }}
          </span>
        </div>

        <p class="mt-2 text-xs text-muted">
          {{ t("lastSynced") }}: {{ lastSyncedLabel(folder.lastSyncedAt) }}
        </p>

        <div
          v-if="folder.pendingUploads || folder.pendingDownloads || folder.pendingDeletes || folder.failures"
          class="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs tabular-nums text-muted"
        >
          <span v-if="folder.pendingUploads">{{ folder.pendingUploads }} {{ t("pendingUploads") }}</span>
          <span v-if="folder.pendingDownloads">{{ folder.pendingDownloads }} {{ t("pendingDownloads") }}</span>
          <span v-if="folder.pendingDeletes">{{ folder.pendingDeletes }} {{ t("pendingDeletes") }}</span>
          <span v-if="folder.failures" class="text-error">{{ folder.failures }} {{ t("failures") }}</span>
        </div>

        <p v-if="folder.lastError" class="mt-1 text-xs text-error">
          {{ errorLabel(folder.lastError) }}
        </p>

        <div class="mt-3 flex gap-2">
          <button type="button" class="btn btn-outline h-7" @click="togglePaused(folder)">
            {{ folder.paused ? t("resume") : t("pause") }}
          </button>
          <button type="button" class="btn btn-danger h-7" @click="remove(folder)">
            {{ t("remove") }}
          </button>
        </div>
      </div>
    </div>
  </div>

  <SyncLog
    v-if="showSyncLog"
    :folder-id="logFolderId"
    :limit="200"
    @close="showSyncLog = false"
  />
</template>