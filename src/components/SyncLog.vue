<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { api, type SyncLogEntry } from "../lib/ipc";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import Icon from "./Icon.vue";

const props = defineProps<{
  folderId?: string;
  limit?: number;
}>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const entries = ref<SyncLogEntry[]>([]);
const loading = ref(false);
const filterAction = ref("");
const filterResult = ref("");

const actionFilters = [
  { value: "", label: "All actions" },
  { value: "upload", label: "Upload" },
  { value: "download", label: "Download" },
  { value: "delete", label: "Delete" },
  { value: "mkdir", label: "Create dir" },
  { value: "conflict", label: "Conflict" },
  { value: "seed", label: "Seed" },
  { value: "error", label: "Error" },
];

const resultFilters = [
  { value: "", label: "All results" },
  { value: "ok", label: "OK" },
  { value: "error", label: "Error" },
  { value: "deferred", label: "Deferred" },
];

async function load() {
  loading.value = true;
  try {
    entries.value = await api.syncLogList(props.limit ?? 200);
  } catch {
    entries.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(() => void load());

watch(() => props.folderId, () => void load(), { immediate: false });
watch(() => props.limit, () => void load(), { immediate: false });

function filteredEntries() {
  return entries.value.filter((e) => {
    if (props.folderId && e.folderId !== props.folderId) return false;
    if (filterAction.value && e.action !== filterAction.value) return false;
    if (filterResult.value && e.result !== filterResult.value) return false;
    return true;
  });
}

function actionLabel(action: string): string {
  switch (action) {
    case "upload": return t("syncLogUpload");
    case "download": return t("syncLogDownload");
    case "delete": return t("syncLogDelete");
    case "mkdir": return t("syncLogMkdir");
    case "conflict": return t("syncLogConflict");
    case "seed": return t("syncLogSeed");
    case "error": return t("syncLogError");
    default: return action;
  }
}

function resultLabel(result: string): string {
  switch (result) {
    case "ok": return t("syncLogOk");
    case "error": return t("syncLogError");
    case "deferred": return t("syncLogDeferred");
    default: return result;
  }
}

function resultClass(result: string): string {
  if (result === "ok") return "text-success";
  if (result === "error") return "text-error";
  return "text-warning";
}

function formatTime(ts: number): string {
  const d = new Date(ts * 1000);
  return d.toLocaleString(ui.lang === "de" ? "de-DE" : ui.lang === "fr" ? "fr-FR" : ui.lang === "es" ? "es-ES" : "en-US", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

async function clearLog() {
  if (!window.confirm(t("syncLogClearConfirm"))) return;
  try {
    await api.syncLogClear();
    entries.value = [];
    ui.toast(t("syncLogCleared"), "success");
  } catch (e) {
    ui.toast(e.message, "error");
  }
}
</script>

<template>
  <div class="flex flex-col h-full">
    <div class="flex items-center justify-between px-4 py-2 border-b border-line">
      <h3 class="text-sm font-semibold">{{ t("syncLogTitle") }}</h3>
      <div class="flex items-center gap-2">
        <select
          v-model="filterAction"
          class="select select-sm w-36"
          @change="() => {}"
        >
          <option v-for="opt in actionFilters" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
        <select
          v-model="filterResult"
          class="select select-sm w-32"
        >
          <option v-for="opt in resultFilters" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
        <button type="button" class="btn btn-outline btn-sm" @click="clearLog">
          <Icon name="trash" :size="13" />
          {{ t("clear") }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="flex-1 flex items-center justify-center text-muted">
      {{ t("loading") }}…
    </div>

    <div v-else-if="!filteredEntries().length" class="flex-1 flex items-center justify-center text-muted text-sm">
      {{ t("syncLogEmpty") }}
    </div>

    <div v-else class="flex-1 overflow-y-auto">
      <table class="table table-xs w-full">
        <thead>
          <tr class="text-xs uppercase tracking-wide text-muted">
            <th class="px-3 py-1.5 text-left">{{ t("time") }}</th>
            <th class="px-3 py-1.5 text-left">{{ t("action") }}</th>
            <th class="px-3 py-1.5 text-left">{{ t("path") }}</th>
            <th class="px-3 py-1.5 text-left">{{ t("result") }}</th>
            <th class="px-3 py-1.5 text-left">{{ t("detail") }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="entry in filteredEntries()"
            :key="entry.timestamp + ':' + entry.path"
            class="border-t border-line/50 hover:bg-card-hover"
          >
            <td class="px-3 py-1.5 text-xs font-mono text-muted">{{ formatTime(entry.timestamp) }}</td>
            <td class="px-3 py-1.5 text-xs">
              <span class="badge normal-case" :class="entry.result === 'error' ? 'badge-error' : entry.result === 'ok' ? 'badge-success' : 'badge-warning'">
                {{ actionLabel(entry.action) }}
              </span>
            </td>
            <td class="px-3 py-1.5 text-xs truncate max-w-48" :title="entry.path">{{ entry.path }}</td>
            <td class="px-3 py-1.5 text-xs" :class="resultClass(entry.result)">{{ resultLabel(entry.result) }}</td>
            <td class="px-3 py-1.5 text-xs text-muted/80 truncate max-w-48" :title="entry.detail || ''">{{ entry.detail || "—" }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>