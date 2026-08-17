<script setup lang="ts">
import { useUiStore } from "../stores/ui";
import "@material/web/iconbutton/icon-button.js";
import Icon from "./Icon.vue";

const ui = useUiStore();
</script>

<template>
  <div class="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2">
    <TransitionGroup name="toast">
      <div
        v-for="toast in ui.toasts"
        :key="toast.id"
        class="pointer-events-auto flex items-start gap-2 rounded-lg border px-3 py-2 text-sm shadow-m3-2"
        :class="{
          'border-success bg-success-container/95 text-on-success-container': toast.type === 'success',
          'border-error bg-error-container/95 text-on-error-container': toast.type === 'error',
          'border-outline-variant bg-surface-container/95 text-on-surface': toast.type === 'info',
        }"
      >
        <span class="flex-1">{{ toast.message }}</span>
        <md-icon-button class="toast-dismiss" @click="ui.dismiss(toast.id)">
          <Icon name="close" :size="16" />
        </md-icon-button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style>
.toast-dismiss {
  --md-icon-button-icon-color: currentColor;
}
</style>
