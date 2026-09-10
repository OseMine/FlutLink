<script setup lang="ts">
import { onMounted, ref } from "vue";
import { save } from "@tauri-apps/plugin-dialog";
import { api, invokeError, type FileVersion } from "../lib/ipc";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import { formatBytes } from "../lib/format";
import Icon from "./Icon.vue";

const props = defineProps<{
  /** Decoded DAV path of the file, e.g. "/Photos/IMG_1.jpg". */
  path: string;
  targetUser?: string | null;
}>();

const emit = defineEmits<{ close: [] }>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const versions = ref<FileVersion[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const working = ref(false);

onMounted(() => {
  load();
});

async function load() {
  loading.value = true;
  error.value = null;
  try {
    versions.value = await api.fileVersionsList(props.path, props.targetUser ?? undefined);
  } catch (e) {
    error.value = invokeError(e).message;
  } finally {
    loading.value = false;
  }
}

function fmtMtime(mtime: string | null): string {
  if (!mtime) return t("versionsUnknown");
  const d = new Date(mtime);
  if (Number.isNaN(d.getTime())) return mtime;
  return d.toLocaleString();
}

async function download(v: FileVersion) {
  try {
    const target = await save({
      defaultPath: v.displayName ?? "version",
    });
    if (!target) return;
    working.value = true;
    error.value = null;
    await api.fileVersionsDownload(props.path, v.versionId, target, props.targetUser ?? undefined);
    ui.toast(t("versionDownloaded"), "success");
  } catch (e) {
    error.value = invokeError(e).message;
  } finally {
    working.value = false;
  }
}

async function restore(v: FileVersion) {
  if (!window.confirm(t("versionRestoreConfirm").replace("{name}", props.path))) return;
  working.value = true;
  error.value = null;
  try {
    await api.fileVersionsRestore(props.path, v.versionId, props.targetUser ?? undefined);
    ui.toast(t("versionRestored"), "success");
    emit("close");
  } catch (e) {
    error.value = invokeError(e).message;
  } finally {
    working.value = false;
  }
}
</script>

<template>
  <div
    class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"
    @click.self="emit('close')"
  >
    <div class="card flex max-h-[80vh] w-full max-w-lg flex-col gap-3 p-5 shadow-xl">
      <div class="flex items-center gap-2">
        <h3 class="min-w-0 flex-1 truncate text-base font-semibold">{{ t("versions") }}</h3>
        <button type="button" class="btn btn-ghost shrink-0 !px-2" aria-label="close" @click="emit('close')">
          <Icon name="close" :size="16" />
        </button>
      </div>
      <p class="truncate text-xs text-muted">{{ path }}</p>

      <div v-if="loading" class="flex items-center gap-2 text-sm text-muted">
        <Icon name="sync" :size="16" class="animate-spin" />
        {{ t("loading") }}
      </div>
      <div v-else-if="error" class="rounded-md border border-error/40 bg-error/10 px-3 py-2 text-sm text-error">
        {{ error }}
      </div>
      <div v-else-if="!versions.length" class="py-6 text-center text-sm text-muted">
        {{ t("noVersions") }}
      </div>
      <ul v-else class="min-h-0 flex-1 space-y-1 overflow-y-auto">
        <li
          v-for="v in versions"
          :key="v.versionId"
          class="flex items-center gap-3 rounded-md border border-line px-3 py-2"
        >
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm">{{ v.displayName ?? v.versionId }}</p>
            <p class="text-xs text-muted">
              {{ fmtMtime(v.mtime) }}
              <template v-if="v.size !== null"> · {{ formatBytes(v.size) }}</template>
            </p>
          </div>
          <button type="button" class="btn btn-outline shrink-0" :disabled="working" @click="download(v)">
            {{ t("download") }}
          </button>
          <button
            type="button"
            class="btn btn-primary shrink-0"
            :disabled="working"
            @click="restore(v)"
          >
            {{ t("restore") }}
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>