<script setup lang="ts">
import { onMounted, ref } from "vue";
import { open } from "@tauri-apps/plugin-dialog";
import { useSyncStore } from "../stores/sync";
import { useUiStore } from "../stores/ui";
import { translate, translateError } from "../lib/i18n";
import { invokeError } from "../lib/ipc";
import Icon from "./Icon.vue";
import SyncLog from "./SyncLog.vue";

const sync = useSyncStore();
const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);
const followSymlinks = ref(false);
const showSyncLog = ref(false);
const logFolderId = ref<string | undefined>(undefined);

function errorLabel(err: { code: string; detail?: string | null }): string {
  return translateError(ui.lang, err.code, err.detail);
}

function stateLabel(state: string): string {
  return t("state" + state.charAt(0).toUpperCase() + state.slice(1));
}

function lastSyncedLabel(ts: number | null): string {
  if (ts === null) return t("neverSynced");
  return new Date(ts * 1000).toLocaleString();
}

/// Status badge dot color per sync state (neutral surface + colored dot).
function stateDotClass(state: string): string {
  switch (state) {
    case "idle":
      return "bg-success";
    case "syncing":
      return "bg-primary";
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
    // F10: render the friendly, translated message for the well-known
    // "folder already synced" conflict; everything else is already
    // localized by the N14 code/detail translation.
    sync.error =
      err.code === "sync_folder_conflict"
        ? t("syncConflictMessage")
        : err.message;
  }
}

async function remove(folder: { folderId: string; localPath: string }) {
  // L19-F3: removing a sync folder also deletes its journal — never do that
  // without an explicit confirmation (same pattern as file/account deletion).
  if (!window.confirm(t("syncRemoveConfirm").replace("{path}", folder.localPath))) return;
  try {
    await sync.remove(folder.folderId);
    ui.toast(t("syncRemoved"), "success");
  } catch {
    // error surfaced via sync.error
  }
}

async function togglePaused(folder: { folderId: string; paused: boolean }) {
  try {
    await sync.setPaused(folder.folderId, !folder.paused);
  } catch {
    // error surfaced via sync.error
  }
}

async function syncNow() {
  try {
    await sync.trigger();
    ui.toast(t("syncTriggered"), "success");
  } catch (e) {
    // Consistent error handling (#301): localized message via invokeError,
    // like every other handler.
    ui.toast(invokeError(e).message, "error");
  }
}
</script>

<template>
  <div class="mx-auto w-full max-w-3xl p-6">
    <div class="mb-4 flex items-center justify-between gap-3">
      <div>
        <h2 class="text-lg font-semibold">{{ t("syncFolders") }}</h2>
        <p class="text-sm text-muted">{{ t("noSyncFoldersHint") }}</p>
      </div>
      <div class="flex shrink-0 items-center gap-2">
        <button type="button" class="btn btn-outline" @click="syncNow">
          {{ t("syncNow") }}
        </button>
        <label class="flex cursor-pointer select-none items-center gap-2 text-sm text-muted">
          <!-- #366: same quiet custom checkbox as everywhere else -->
          <input
            v-model="followSymlinks"
            type="checkbox"
            class="checkbox"
          />
          {{ t("followSymlinks") }}
        </label>
        <button type="button" class="btn btn-outline" @click="showSyncLog = true; logFolderId = undefined">
          <Icon name="history" :size="14" />
          {{ t("syncLogTitle") }}
        </button>
        <button type="button" class="btn btn-primary" @click="pickFolder">
          <Icon name="add" :size="14" />
          {{ t("addFolder") }}
        </button>
      </div>
    </div>

    <div v-if="sync.error" class="mb-4 rounded-md border border-error/40 bg-error/10 px-3 py-2 text-xs text-error">
      {{ sync.error }}
    </div>

    <p v-if="sync.loading" class="text-sm text-muted">…</p>

    <div v-else-if="!sync.folders.length" class="rounded-md border border-dashed border-line-strong p-8 text-center text-sm text-muted">
      {{ t("noSyncFolders") }}
    </div>

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
          <!-- Status: neutral surface + colored dot instead of a color block -->
          <span class="badge normal-case">
            <span class="badge-dot" :class="stateDotClass(folder.state)"></span>
            {{ stateLabel(folder.state) }}
          </span>
        </div>

        <p class="mt-2 text-xs text-muted">
          {{ t("lastSynced") }}: {{ lastSyncedLabel(folder.lastSyncedAt) }}
        </p>

        <div v-if="folder.pendingUploads || folder.pendingDownloads || folder.pendingDeletes || folder.failures" class="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs tabular-nums text-muted">
          <span v-if="folder.pendingUploads">{{ folder.pendingUploads }} {{ t("pendingUploads") }}</span>
          <span v-if="folder.pendingDownloads">{{ folder.pendingDownloads }} {{ t("pendingDownloads") }}</span>
          <span v-if="folder.pendingDeletes">{{ folder.pendingDeletes }} {{ t("pendingDeletes") }}</span>
          <span v-if="folder.failures" class="text-error">{{ folder.failures }} {{ t("failures") }}</span>
        </div>

        <p v-if="folder.lastError" class="mt-1 text-xs text-error">{{ errorLabel(folder.lastError) }}</p>

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
