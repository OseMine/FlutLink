<script setup lang="ts">
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import Icon from "./Icon.vue";

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);
</script>

<template>
  <div class="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2">
    <transition-group name="toast">
      <div
        v-for="toast in ui.toasts"
        :key="toast.id"
        class="pointer-events-auto flex items-start gap-2 rounded-lg border px-3 py-2 text-sm shadow-m3-2"
        :class="
          toast.type === 'success'
            ? 'border-success bg-success-container/95 text-on-success-container'
            : toast.type === 'error'
              ? 'border-error bg-error-container/95 text-on-error-container'
              : 'border-outline-variant bg-surface-container/95 text-on-surface'
        "
      >
        <span class="flex-1 leading-snug">{{ toast.message }}</span>
        <button
          class="mt-0.5 text-on-surface-variant transition hover:text-on-surface"
          :aria-label="t('close')"
          @click="ui.dismiss(toast.id)"
        >
          <Icon name="close" :size="16" />
        </button>
      </div>
    </transition-group>
  </div>
</template>
