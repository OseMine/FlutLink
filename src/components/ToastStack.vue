<script setup lang="ts">
import { useUiStore } from "../stores/ui";

const ui = useUiStore();
</script>

<template>
  <div class="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2">
    <transition-group name="toast">
      <div
        v-for="toast in ui.toasts"
        :key="toast.id"
        class="pointer-events-auto flex items-start gap-2 rounded-lg border px-3 py-2 text-sm shadow-xl"
        :class="
          toast.type === 'success'
            ? 'border-emerald-800 bg-emerald-950/90 text-emerald-200'
            : toast.type === 'error'
              ? 'border-red-800 bg-red-950/90 text-red-200'
              : 'border-zinc-700 bg-zinc-900/95 text-zinc-200'
        "
      >
        <span class="flex-1 leading-snug">{{ toast.message }}</span>
        <button
          class="mt-0.5 text-zinc-500 transition hover:text-zinc-200"
          aria-label="Dismiss"
          @click="ui.dismiss(toast.id)"
        >
          ✕
        </button>
      </div>
    </transition-group>
  </div>
</template>
