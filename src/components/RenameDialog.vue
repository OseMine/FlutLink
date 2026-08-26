<script setup lang="ts">
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";

const emit = defineEmits<{ save: []; cancel: [] }>();

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
      @submit.prevent="emit('save')"
    >
      <h3 class="mb-3 text-base font-semibold">{{ t("rename") }}</h3>
      <input
        v-model="name"
        type="text"
        autofocus
        class="input mb-4"
      />
      <div class="flex justify-end gap-2">
        <button type="button" class="btn btn-outline" @click="emit('cancel')">
          {{ t("cancel") }}
        </button>
        <button type="submit" class="btn btn-primary">{{ t("save") }}</button>
      </div>
    </form>
  </div>
</template>
