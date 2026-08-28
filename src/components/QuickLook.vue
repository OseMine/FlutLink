<script setup lang="ts">
import type { WebDavEntry } from "../lib/ipc";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import Icon from "./Icon.vue";

// #405: Quick Look overlay (Space). Shows an image preview for image entries
// (thumbnail data URL from the backend) and a generic file glyph otherwise.
const props = defineProps<{
  entry: WebDavEntry;
  imageUrl: string | null;
  canPrev: boolean;
  canNext: boolean;
}>();

const emit = defineEmits<{
  close: [];
  prev: [];
  next: [];
  download: [];
  open: [];
}>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const isImage = props.entry.isDir ? false : !!props.entry.contentType?.startsWith("image/");
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-scrim/70 p-6"
    @click.self="emit('close')"
  >
    <div class="menu max-h-full w-full max-w-2xl overflow-hidden">
      <div class="flex items-center justify-between gap-2 border-b border-line px-4 py-2">
        <p class="min-w-0 truncate text-sm font-semibold">{{ entry.name }}</p>
        <div class="flex shrink-0 items-center gap-1">
          <button
            type="button"
            class="icon-btn !h-7 !w-7"
            :disabled="!canPrev"
            :title="t('quickLookPrev')"
            @click="emit('prev')"
          >
            <Icon name="back" :size="15" />
          </button>
          <button
            type="button"
            class="icon-btn !h-7 !w-7"
            :disabled="!canNext"
            :title="t('quickLookNext')"
            @click="emit('next')"
          >
            <Icon name="back" :size="15" class="rotate-180" />
          </button>
          <button
            type="button"
            class="icon-btn !h-7 !w-7"
            :title="t('close')"
            @click="emit('close')"
          >
            <Icon name="close" :size="15" />
          </button>
        </div>
      </div>
      <div class="flex max-h-[65vh] min-h-64 items-center justify-center overflow-auto bg-line/10">
        <img
          v-if="isImage && imageUrl"
          :src="imageUrl"
          :alt="entry.name"
          class="max-h-[60vh] max-w-full object-contain p-4"
        />
        <div v-else class="flex flex-col items-center gap-2 p-8 text-muted">
          <Icon name="file" :size="72" />
        </div>
      </div>
      <div class="flex items-center justify-between gap-2 border-t border-line px-4 py-2 text-xs text-muted">
        <span class="truncate">{{ entry.path }}</span>
        <div class="flex shrink-0 items-center gap-2">
          <button type="button" class="btn btn-outline h-8" @click="emit('open')">
            <Icon name="open" :size="13" />
            {{ t("open") }}
          </button>
          <button
            v-if="!entry.isDir"
            type="button"
            class="btn btn-primary h-8"
            @click="emit('download')"
          >
            <Icon name="download" :size="13" />
            {{ t("download") }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>