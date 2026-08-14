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
        <h2 class="text-lg font-semibold text-zinc-50">{{ t("syncFolders") }}</h2>
        <p class="text-sm text-zinc-500">{{ t("noSyncFoldersHint") }}</p>
      </div>
      <div class="flex gap-2">
        <button
          class="rounded-md border border-zinc-700 px-3 py-1.5 text-sm text-zinc-300 hover:bg-zinc-800"
          @click="syncNow"
        >
          {{ t("syncNow") }}
        </button>
        <button
          class="rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-500"
          @click="pickFolder"
        >
          + {{ t("addFolder") }}
        </button>
      </div>
    </div>

    <div v-if="sync.error" class="mb-4 rounded-md border border-red-800 bg-red-950/50 px-3 py-2 text-xs text-red-300">
      {{ sync.error }}
    </div>

    <p v-if="sync.loading" class="text-sm text-zinc-500">…</p>

    <div v-else-if="!sync.folders.length" class="rounded-lg border border-dashed border-zinc-700 p-8 text-center text-sm text-zinc-500">
      {{ t("noSyncFolders") }}
    </div>

    <div v-else class="space-y-3">
      <div
        v-for="folder in sync.folders"
        :key="folder.folderId"
        class="rounded-lg border border-zinc-800 bg-zinc-900 p-4"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <p class="truncate font-medium text-zinc-50">{{ folder.localPath }}</p>
            <p class="truncate text-xs text-zinc-500">
              {{ t("remoteFolder") }}: {{ folder.remotePath }}
            </p>
          </div>
          <span
            class="shrink-0 rounded px-2 py-0.5 text-[11px] font-semibold"
            :class="{
              'bg-emerald-500/15 text-emerald-300': folder.state === 'idle',
              'bg-indigo-500/15 text-indigo-300': folder.state === 'syncing',
              'bg-zinc-700 text-zinc-300': folder.state === 'paused',
              'bg-red-500/15 text-red-300': folder.state === 'error',
            }"
          >
            {{ stateLabel(folder.state) }}
          </span>
        </div>

        <p class="mt-2 text-xs text-zinc-500">
          {{ t("lastSynced") }}: {{ lastSyncedLabel(folder.lastSyncedAt) }}
        </p>

        <div v-if="folder.pendingUploads || folder.pendingDownloads || folder.pendingDeletes || folder.failures" class="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-zinc-400">
          <span v-if="folder.pendingUploads">{{ folder.pendingUploads }} {{ t("pendingUploads") }}</span>
          <span v-if="folder.pendingDownloads">{{ folder.pendingDownloads }} {{ t("pendingDownloads") }}</span>
          <span v-if="folder.pendingDeletes">{{ folder.pendingDeletes }} {{ t("pendingDeletes") }}</span>
          <span v-if="folder.failures" class="text-red-300">{{ folder.failures }} {{ t("failures") }}</span>
        </div>

        <p v-if="folder.lastError" class="mt-1 text-xs text-red-300">{{ folder.lastError }}</p>

        <div class="mt-3 flex gap-2">
          <button
            class="rounded-md border border-zinc-700 px-2.5 py-1 text-xs text-zinc-300 hover:bg-zinc-800"
            @click="togglePaused(folder)"
          >
            {{ folder.paused ? t("resume") : t("pause") }}
          </button>
          <button
            class="rounded-md border border-zinc-700 px-2.5 py-1 text-xs text-red-300 hover:bg-red-950/40"
            @click="remove(folder.folderId)"
          >
            {{ t("remove") }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
