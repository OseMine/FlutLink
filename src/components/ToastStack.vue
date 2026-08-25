<script setup lang="ts">
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import Icon from "./Icon.vue";

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);
</script>

<template>
  <div class="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2">
    <TransitionGroup name="toast">
      <div
        v-for="toast in ui.toasts"
        :key="toast.id"
        class="menu pointer-events-auto flex items-center gap-2.5 !rounded-md px-3 py-2 text-sm text-fg"
      >
        <!-- Neutral surface + colored status dot instead of color blocks -->
        <span
          class="h-2 w-2 shrink-0 rounded-full"
          :class="{
            'bg-success': toast.type === 'success',
            'bg-error': toast.type === 'error',
            'bg-info': toast.type === 'info',
          }"
        ></span>
        <span class="flex-1">{{ toast.message }}</span>
        <button
          type="button"
          class="-mr-1 grid h-6 w-6 shrink-0 place-items-center rounded-sm text-muted transition hover:bg-card-hover hover:text-fg"
          :aria-label="t('dismiss')"
          @click="ui.dismiss(toast.id)"
        >
          <Icon name="close" :size="14" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>
