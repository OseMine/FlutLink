<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import { listen } from "@tauri-apps/api/event";
import { openUrl } from "@tauri-apps/plugin-opener";
import Icon from "./Icon.vue";
import { useUiStore } from "../stores/ui";
import {
  api,
  invokeError,
  type ReleaseInfo,
  type UpdateProgress,
  type UpdateStatus,
} from "../lib/ipc";
import { translate, updateStatusText as localizedUpdateStatus } from "../lib/i18n";
import { registerEscapeCloser } from "../lib/escape";

const props = defineProps<{
  open: boolean;
  info: ReleaseInfo | null;
}>();
const emit = defineEmits<{ close: [] }>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const busy = ref(false);
const progress = ref(0);
const statusText = ref("");
let unlistenProgress: (() => void) | null = null;
let unlistenStatus: (() => void) | null = null;

onUnmounted(() => {
  unlistenProgress?.();
  unlistenStatus?.();
});

watch(
  () => props.open,
  (open) => {
    if (!open) {
      busy.value = false;
      progress.value = 0;
      statusText.value = "";
      unlistenProgress?.();
      unlistenStatus?.();
    }
  }
);

async function downloadAndInstall() {
  if (busy.value || !props.info) return;
  busy.value = true;
  progress.value = 0;
  statusText.value = "";
  try {
    unlistenProgress = await listen<UpdateProgress>("update://progress", (e) => {
      progress.value = e.payload.percent;
    });
    unlistenStatus = await listen<UpdateStatus>("update://status", (e) => {
      statusText.value =
        localizedUpdateStatus(ui.lang, e.payload.code, e.payload.assetName) ||
        `${e.payload.code}${e.payload.assetName ? " — " + e.payload.assetName : ""}`;
    });
  } catch {
    // best-effort
  }
  try {
    await api.downloadAndInstallUpdate();
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    unlistenProgress?.();
    unlistenStatus?.();
    busy.value = false;
  }
}

function openReleasePage() {
  if (props.info?.releaseUrl) {
    void openUrl(props.info.releaseUrl).catch(() => {});
  }
}

const notesHtml = computed(() => {
  if (!props.info?.notes) return null;
  // GitHub releases return markdown; render as simple line breaks for now
  return props.info.notes
    .replace(/\r\n/g, "\n")
    .split("\n")
    .map((line) => {
      if (line.startsWith("### ")) return `<p class="font-semibold mt-2 mb-1">${line.slice(4)}</p>`;
      if (line.startsWith("## ")) return `<p class="font-bold mt-3 mb-1 text-sm">${line.slice(3)}</p>`;
      if (line.startsWith("# ")) return `<p class="font-bold text-base mt-3 mb-1">${line.slice(2)}</p>`;
      if (line.startsWith("- ")) return `<li class="ml-4 list-disc">${line.slice(2)}</li>`;
      if (line.match(/^\d+\.\s/)) return `<li class="ml-4 list-decimal">${line.replace(/^\d+\.\s/, "")}</li>`;
      if (line.trim() === "") return "<br />";
      return `<p>${line}</p>`;
    })
    .join("");
});

// L19-N1: Escape closes the modal while it is open.
let removeEscapeCloser: (() => void) | null = null;
watch(
  () => props.open,
  (open) => {
    if (open && !removeEscapeCloser) {
      removeEscapeCloser = registerEscapeCloser(() => emit("close"));
    } else if (!open && removeEscapeCloser) {
      removeEscapeCloser();
      removeEscapeCloser = null;
    }
  }
);
onUnmounted(() => removeEscapeCloser?.());
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="props.open && props.info"
        class="fixed inset-0 z-50 flex items-center justify-center bg-scrim/60 p-4 backdrop-blur-sm"
        @click.self="emit('close')"
      >
        <div class="modal-surface flex max-h-[80vh] w-full max-w-md flex-col">
          <div class="flex items-center justify-between border-b border-line px-5 py-3">
            <h2 class="text-base font-semibold">{{ t("updateAvailable") }}</h2>
            <button
              type="button"
              class="icon-btn !h-7 !w-7"
              :aria-label="t('close')"
              @click="emit('close')"
            >
              <Icon name="close" :size="16" />
            </button>
          </div>

          <div class="min-h-0 flex-1 overflow-y-auto p-5">
            <div class="mb-4 flex items-center gap-3">
              <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary/15">
                <Icon name="cloud" :size="20" class="text-primary" />
              </div>
              <div class="min-w-0">
                <p class="text-sm font-medium">
                  {{ t("updateNewVersion").replace("{version}", props.info.version) }}
                </p>
                <p class="truncate text-xs text-muted">{{ props.info.name }}</p>
              </div>
            </div>

            <div
              v-if="notesHtml"
              class="mb-4 rounded-md border border-line bg-card/50 p-3 text-xs leading-relaxed text-fg/80"
            >
              <p class="mb-2 text-[11px] font-medium uppercase tracking-wide text-muted">
                {{ t("updateReleaseNotes") }}
              </p>
              <div class="max-h-48 overflow-y-auto" v-html="notesHtml"></div>
            </div>

            <button
              v-if="props.info.releaseUrl"
              type="button"
              class="mb-4 flex items-center gap-1.5 text-xs text-primary transition hover:text-primary-hover"
              @click="openReleasePage"
            >
              <Icon name="open" :size="12" />
              {{ t("updateViewOnGitHub") }}
            </button>
          </div>

          <div class="flex items-center gap-2 border-t border-line px-5 py-3">
            <template v-if="busy">
              <div class="min-w-0 flex-1">
                <div class="progress-track">
                  <div class="progress-fill" :style="{ width: Math.min(progress, 100) + '%' }"></div>
                </div>
                <p v-if="statusText" class="mt-1 truncate text-xs text-muted/80">
                  {{ statusText }}
                </p>
              </div>
              <span class="shrink-0 text-xs font-medium text-muted">
                {{ Math.round(Math.min(progress, 100)) }}%
              </span>
            </template>
            <template v-else>
              <button type="button" class="btn btn-outline" @click="emit('close')">
                {{ t("dismiss") }}
              </button>
              <button type="button" class="btn btn-primary" @click="downloadAndInstall">
                {{ t("updateDownloadAndInstall") }}
              </button>
            </template>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
