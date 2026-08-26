<script setup lang="ts">
import { Icon } from "./Icon.vue";

const props = defineProps<{
  x: number;
  y: number;
  entry: any;
  isAdmin: boolean;
  t: (key: string) => string;
}>();

const emit = defineEmits<{
  open: [entry: any];
  toggleSelect: [path: string];
  contextmenu: [e: MouseEvent, entry: any];
  rename: [entry: any];
  createLink: [entry: any];
  pair: [entry: any];
  download: [entry: any];
  share: [entry: any];
  delete: [entry: any];
}>();

function emitOpen() {
  emit("open", props.entry);
}

function emitToggleSelect() {
  emit("toggleSelect", props.entry.path);
}

function emitContextmenu(e: MouseEvent) {
  emit("contextmenu", e, props.entry);
}

function emitRename() {
  emit("rename", props.entry);
}

function emitCreateLink() {
  emit("createLink", props.entry);
}

function emitPair() {
  emit("pair", props.entry);
}

function emitDownload() {
  emit("download", props.entry);
}

function emitShare() {
  emit("share", props.entry);
}

function emitDelete() {
  emit("delete", props.entry);
}
</script>

<template>
  <div
    ref="ctxMenu"
    class="absolute z-10 w-32 rounded-md bg-card shadow-lg divider-y origin-top-right right-0 top-full m-2"
    @click.self="emitContextmenu"
  >
    <template v-if="props.isAdmin">
      <button
        type="button"
        class="block px-3 py-1.5 text-sm text-left whitespace-nowrap cursor-pointer"
        @click="emitOpen"
      >
        {{ props.t("open") }}
      </button>
      <button
        type="button"
        class="block px-3 py-1.5 text-sm text-left whitespace-nowrap cursor-pointer"
        @click="emitRename"
      >
        {{ props.t("rename") }}
      </button>
      <button
        type="button"
        class="block px-3 py-1.5 text-sm text-left whitespace-nowrap cursor-pointer text-error"
        @click="emitDelete"
      >
        {{ props.t("delete") }}
      </button>
    </template>

    <template v-else>
      <button
        type="button"
        class="block px-3 py-1.5 text-sm text-left whitespace-nowrap cursor-pointer"
        @click="emitOpen"
      >
        {{ props.t("open") }}
      </button>
      <button
        type="button"
        class="block px-3 py-1.5 text-sm text-left whitespace-nowrap cursor-pointer"
        @click="emitCreateLink"
      >
        {{ props.t("link") }}
      </button>
      <button
        type="button"
        class="block px-3 py-1.5 text-sm text-left whitespace-nowrap cursor-pointer"
        @click="emitPair"
      >
        ↔
      </button>
      <button
        type="button"
        class="block px-3 py-1.5 text-sm text-left whitespace-nowrap cursor-pointer"
        @click="emitDownload"
      >
        <Icon name="download" :size="13" class="mr-1" />
        {{ props.t("download") }}
      </button>
      <button
        type="button"
        class="block px-3 py-1.5 text-sm text-left whitespace-nowrap cursor-pointer text-error"
        @click="emitDelete"
      >
        {{ props.t("delete") }}
      </button>
    </template>
  </div>
</template>