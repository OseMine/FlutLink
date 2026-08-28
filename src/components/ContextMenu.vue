<script setup lang="ts">
import type { WebDavEntry } from "../lib/ipc";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";

defineProps<{ x: number; y: number; entry: WebDavEntry }>();

const emit = defineEmits<{
  action: [
    action:
      | "open"
      | "download"
      | "rename"
      | "share"
      | "bookmark"
      | "copyTo"
      | "moveTo"
      | "delete",
    entry: WebDavEntry,
  ];
}>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);
</script>

<template>
  <div
    class="menu fixed z-50 w-44 py-1"
    :style="{ left: x + 'px', top: y + 'px' }"
    @click.stop
  >
    <button
      type="button"
      class="mx-1 block w-[calc(100%-0.5rem)] rounded-sm px-2 py-1.5 text-left text-sm transition hover:bg-card-hover"
      @click="emit('action', 'open', entry)"
    >
      {{ t("open") }}
    </button>
    <button
      type="button"
      class="mx-1 block w-[calc(100%-0.5rem)] rounded-sm px-2 py-1.5 text-left text-sm transition hover:bg-card-hover"
      @click="emit('action', 'download', entry)"
    >
      {{ entry.isDir ? t("downloadZip") : t("download") }}
    </button>
    <button
      type="button"
      class="mx-1 block w-[calc(100%-0.5rem)] rounded-sm px-2 py-1.5 text-left text-sm transition hover:bg-card-hover"
      @click="emit('action', 'rename', entry)"
    >
      {{ t("rename") }}
    </button>
    <button
      type="button"
      class="mx-1 block w-[calc(100%-0.5rem)] rounded-sm px-2 py-1.5 text-left text-sm transition hover:bg-card-hover"
      @click="emit('action', 'share', entry)"
    >
      {{ t("share") }}
    </button>
    <button
      v-if="entry.isDir"
      type="button"
      class="mx-1 block w-[calc(100%-0.5rem)] rounded-sm px-2 py-1.5 text-left text-sm transition hover:bg-card-hover"
      @click="emit('action', 'bookmark', entry)"
    >
      {{ t("addBookmark") }}
    </button>
    <button
      type="button"
      class="mx-1 block w-[calc(100%-0.5rem)] rounded-sm px-2 py-1.5 text-left text-sm transition hover:bg-card-hover"
      @click="emit('action', 'copyTo', entry)"
    >
      {{ t("copyTo") }}
    </button>
    <button
      type="button"
      class="mx-1 block w-[calc(100%-0.5rem)] rounded-sm px-2 py-1.5 text-left text-sm transition hover:bg-card-hover"
      @click="emit('action', 'moveTo', entry)"
    >
      {{ t("moveTo") }}
    </button>
    <div class="mx-2 my-1 border-t border-line"></div>
    <button
      type="button"
      class="mx-1 block w-[calc(100%-0.5rem)] rounded-sm px-2 py-1.5 text-left text-sm text-error transition hover:bg-error/10"
      @click="emit('action', 'delete', entry)"
    >
      {{ t("delete") }}
    </button>
  </div>
</template>
