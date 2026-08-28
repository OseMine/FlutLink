<script setup lang="ts">
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";

// #411: choose the destination folder for a WebDAV COPY/MOVE. The source file
// name is preserved; only an invalid folder path is blocked, conflicts are
// surfaced by the backend (Overwrite: F → TargetExists).
const props = defineProps<{
  entryName: string;
  mode: "copy" | "move";
}>();

const emit = defineEmits<{
  cancel: [];
  save: [destFolder: string];
}>();

const dest = defineModel<string>({ required: true });

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-scrim/60 p-4"
    @click.self="emit('cancel')"
  >
    <form
      class="modal-surface w-full max-w-xs p-5"
      @submit.prevent="emit('save', dest.trim())"
    >
      <h3 class="mb-3 text-base font-semibold">
        {{ mode === "copy" ? t("copyTo") : t("moveTo") }}
      </h3>
      <p class="mb-3 truncate text-sm text-muted">
        {{ t("destinationFolder") }} · {{ entryName }}
      </p>
      <input
        v-model="dest"
        type="text"
        autofocus
        class="input mb-4"
        :placeholder="t('destinationFolderPlaceholder')"
      />
      <div class="flex justify-end gap-2">
        <button type="button" class="btn btn-outline" @click="emit('cancel')">
          {{ t("cancel") }}
        </button>
        <button type="submit" class="btn btn-primary">
          {{ mode === "copy" ? t("copy") : t("move") }}
        </button>
      </div>
    </form>
  </div>
</template>