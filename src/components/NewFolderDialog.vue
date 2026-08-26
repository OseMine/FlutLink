<script setup lang="ts">
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";

const emit = defineEmits<{ create: []; cancel: [] }>();

const name = defineModel<string>({ required: true });

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
      @submit.prevent="emit('create')"
    >
      <h3 class="mb-3 text-base font-semibold">{{ t("newFolder") }}</h3>
      <input
        v-model="name"
        type="text"
        :placeholder="t('folderName')"
        autofocus
        class="input mb-4"
      />
      <div class="flex justify-end gap-2">
        <button type="button" class="btn btn-outline" @click="emit('cancel')">
          {{ t("cancel") }}
        </button>
        <button
          type="submit"
          class="btn btn-primary"
          :disabled="name.trim().length === 0"
        >
          {{ t("create") }}
        </button>
      </div>
    </form>
  </div>
</template>
