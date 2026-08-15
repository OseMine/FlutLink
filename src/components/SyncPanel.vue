<script setup lang="ts">
import { onMounted } from "vue";
import { open } from "@tauri-apps/plugin-dialog";
import { useSyncStore } from "../stores/sync";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";

const sync = useSyncStore();
const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

function stateLabel(state: string): string {
  return t("state" + state.charAt(0).toUpperCase() + state.slice(1));
}

function lastSyncedLabel(ts: number | null): string {
  if (ts === null) return t("neverSynced");
  return new Date(ts * 1000).toLocaleString();
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
      await sync.add(selected);
      ui.toast(t("syncAdded"), "success");
    }
  } catch (e) {
    sync.error = e instanceof Error ? e.message : String(e);
  }
}

async function remove(folderId: string) {
  try {
    await sync.remove(folderId);
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
  await sync.trigger();
  ui.toast(t("syncTriggered"), "success");
}
</script>

<template>
  <div class="mx-auto w-full max-w-3xl p-6">
    <div class="mb-4 flex items-center justify-between">
      <div>
        <h2 class="text-lg font-semibold text-on-surface">{{ t("syncFolders") }}</h2>
        <p class="text-sm text-on-surface-variant">{{ t("noSyncFoldersHint") }}</p>
      </div>
      <div class="flex gap-2">
        <button
          class="rounded-md border border-outline px-3 py-1.5 text-sm text-on-surface-variant hover:bg-surface-container-high"
          @click="syncNow"
        >
          {{ t("syncNow") }}
        </button>
        <button
          class="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-on-primary hover:bg-primary-hover"
          @click="pickFolder"
        >
          + {{ t("addFolder") }}
        </button>
      </div>
    </div>

    <div v-if="sync.error" class="mb-4 rounded-md border border-error bg-error-container px-3 py-2 text-xs text-on-error-container">
      {{ sync.error }}
    </div>

    <p v-if="sync.loading" class="text-sm text-on-surface-variant">…</p>

    <div v-else-if="!sync.folders.length" class="rounded-lg border border-dashed border-outline p-8 text-center text-sm text-on-surface-variant">
      {{ t("noSyncFolders") }}
    </div>

    <div v-else class="space-y-3">
      <div
        v-for="folder in sync.folders"
        :key="folder.folderId"
        class="rounded-lg border border-outline-variant bg-surface-container p-4"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <p class="truncate font-medium text-on-surface">{{ folder.localPath }}</p>
            <p class="truncate text-xs text-on-surface-variant">
              {{ t("remoteFolder") }}: {{ folder.remotePath }}
            </p>
          </div>
          <span
            class="shrink-0 rounded px-2 py-0.5 text-[11px] font-semibold"
            :class="{
              'bg-success/15 text-success': folder.state === 'idle',
              'bg-primary/15 text-primary-emphasis': folder.state === 'syncing',
              'bg-surface-container-highest text-on-surface-variant': folder.state === 'paused',
              'bg-error/15 text-error': folder.state === 'error',
            }"
          >
            {{ stateLabel(folder.state) }}
          </span>
        </div>

        <p class="mt-2 text-xs text-on-surface-variant">
          {{ t("lastSynced") }}: {{ lastSyncedLabel(folder.lastSyncedAt) }}
        </p>

        <div v-if="folder.pendingUploads || folder.pendingDownloads || folder.pendingDeletes || folder.failures" class="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-on-surface-variant">
          <span v-if="folder.pendingUploads">{{ folder.pendingUploads }} {{ t("pendingUploads") }}</span>
          <span v-if="folder.pendingDownloads">{{ folder.pendingDownloads }} {{ t("pendingDownloads") }}</span>
          <span v-if="folder.pendingDeletes">{{ folder.pendingDeletes }} {{ t("pendingDeletes") }}</span>
          <span v-if="folder.failures" class="text-error">{{ folder.failures }} {{ t("failures") }}</span>
        </div>

        <p v-if="folder.lastError" class="mt-1 text-xs text-error">{{ folder.lastError }}</p>

        <div class="mt-3 flex gap-2">
          <button
            class="rounded-md border border-outline px-2.5 py-1 text-xs text-on-surface-variant hover:bg-surface-container-high"
            @click="togglePaused(folder)"
          >
            {{ folder.paused ? t("resume") : t("pause") }}
          </button>
          <button
            class="rounded-md border border-outline px-2.5 py-1 text-xs text-error hover:bg-error-container"
            @click="remove(folder.folderId)"
          >
            {{ t("remove") }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
