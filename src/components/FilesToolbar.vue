<script setup lang="ts">
import Icon from "./Icon.vue";
import { useUiStore, type ViewMode } from "../stores/ui";
import { translate } from "../lib/i18n";

defineProps<{
  crumbs: { label: string; path: string }[];
  viewMode: ViewMode;
  uploading: boolean;
  pairedPath: string | null;
  splitActive: boolean;
}>();

const emit = defineEmits<{
  back: [];
  navigate: [path: string];
  "update:viewMode": [mode: ViewMode];
  "toggle-split": [];
  refresh: [];
  "new-folder": [];
  upload: [];
}>();

const search = defineModel<string>("search", { required: true });

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);
</script>

<template>
  <div class="flex items-center justify-between gap-3 border-b border-line px-6 py-3">
    <nav class="flex min-w-0 items-center gap-1 text-sm">
      <button
        type="button"
        class="icon-btn !h-7 !w-7"
        :disabled="crumbs.length <= 1"
        :title="t('back')"
        :aria-label="t('back')"
        @click="emit('back')"
      >
        <Icon name="back" :size="16" />
      </button>
      <template v-for="(crumb, i) in crumbs" :key="crumb.path">
        <button
          type="button"
          class="rounded-sm px-1.5 py-0.5 transition hover:bg-card-hover"
          :class="i === crumbs.length - 1 ? 'font-semibold' : 'text-muted'"
          @click="emit('navigate', crumb.path)"
        >
          {{ crumb.path === "/" ? t("home") : crumb.label }}
        </button>
        <span v-if="i < crumbs.length - 1" class="text-muted/60">/</span>
      </template>
    </nav>

    <div class="flex shrink-0 items-center gap-2">
      <!-- Featured search card: input styled as a container with inline actions -->
      <div class="flex h-8 w-52 items-center gap-1.5 rounded-sm border border-line-strong bg-card px-2 transition focus-within:border-primary">
        <Icon name="search" :size="14" class="shrink-0 text-muted" />
        <input
          id="flutlink-search"
          v-model="search"
          type="text"
          :placeholder="t('searchPlaceholder')"
          class="min-w-0 flex-1 bg-transparent text-[13px] outline-none placeholder:text-muted"
        />
        <button
          v-if="search"
          type="button"
          class="grid h-5 w-5 shrink-0 place-items-center rounded-sm text-muted transition hover:text-fg"
          :title="t('clearSearch')"
          @click="search = ''"
        >
          <Icon name="close" :size="13" />
        </button>
      </div>

      <!-- Micro-pills for the view toggle -->
      <div class="segment" role="group" :aria-label="t('viewList') + ' / ' + t('viewGrid')">
        <button
          type="button"
          :aria-pressed="viewMode === 'list'"
          :title="t('viewList')"
          @click="emit('update:viewMode', 'list')"
        >
          <Icon name="menu" :size="15" />
        </button>
        <button
          type="button"
          :aria-pressed="viewMode === 'grid'"
          :title="t('viewGrid')"
          @click="emit('update:viewMode', 'grid')"
        >
          <Icon name="grid" :size="15" />
        </button>
      </div>

      <button
        v-if="pairedPath"
        type="button"
        class="btn btn-outline"
        :class="{ '!border-primary/50 !bg-primary/10': splitActive }"
        :title="t('splitViewHint')"
        @click="emit('toggle-split')"
      >
        <Icon name="columns" :size="14" />
        {{ t("splitView") }}
      </button>
      <button type="button" class="btn btn-outline" @click="emit('refresh')">
        <Icon name="refresh" :size="14" />
        {{ t("refresh") }}
      </button>
      <button type="button" class="btn btn-outline" @click="emit('new-folder')">
        <Icon name="add" :size="14" />
        {{ t("newFolder") }}
      </button>
      <!-- The single filled primary action of this view -->
      <button type="button" class="btn btn-primary" :disabled="uploading" @click="emit('upload')">
        <Icon name="upload" :size="14" />
        {{ t("upload") }}
      </button>
    </div>
  </div>
</template>
