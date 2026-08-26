<script setup lang="ts">
import { defineEmits, defineProps, ref } from "vue";
import { useFilesStore } from "../stores/files";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import { Icon } from "./Icon.vue";

const props = defineProps<{
  entry: any;
  sharesByPath: any;
  shareState: any;
  toggleSelect: any;
  emitShare: any;
  closeShareDialog: any;
  resetShareForm: any;
}>;

const emit = defineEmits<{
  close: [];
  toggleSelect: [path: string];
  createLink: [entry: any];
  copyLink: [path: string];
  share: [entry: any];
}>();

const files = useFilesStore();
const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const shareDialogOpen = ref(false);

function openDialog(entry: any) {
  shareDialogOpen.value = true;
  // Reset form state
  emit("resetShareForm");
}

function closeDialog() {
  shareDialogOpen.value = false;
}

function performShare(entry: any) {
  emit("share", entry);
  closeDialog();
}

function copyLink(path: string) {
  emit("copyLink", path);
  closeDialog();
}
</script>

<template>
  <div v-if="shareDialogOpen" class="fixed inset-0 z-50 flex items-center justify-center">
    <div
      class="relative bg-black/50 backdrop-blur-sm min-h-screen flex items-center justify-center"
    >
      <div
        class="relative bg-white rounded-lg p-6 w-full max-w-md shadow-lg transform scale-100 transition-all"
      >
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-medium">{{ t("share") }}</h3>
          <button
            type="button"
            class="rounded-full p-1 hover:bg-gray-100"
            @click="closeDialog"
          >
            <Icon name="close" :size="18" />
          </button>
        </div>

        <div class="space-y-3">
          <p class="text-sm text-muted">{{ t("shareLinkFor") }}: {{ entry.name }}</p>

          <template v-for="(share, i) in shareState" :key="i">
            <div
              v-if="share.status === 'done'"
              class="flex items-center gap-2 text-success"
            >
              <Icon name="check" :size="12" />
              <span>{{ t("alreadyShared") }}</span>
            </span>
          </template>

          <template v-if="share.status === 'loading'">
            <div class="flex items-center gap-2 text-muted">
              <span>…</span>
            </div>
          </template>

          <template v-if="share.status === 'error'">
            <div class="flex items-center gap-2 text-error">
              <Icon name="close" :size="12" />
              <span>{{ t("shareError") }}</span>
            </div>
          </template>
        </div>

        <div class="mt-4 pt-4 border-t border-line">
          <button
            type="button"
            class="w-full justify-end text-success"
            @click="closeDialog"
          >
            {{ t("close") }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>