<script setup lang="ts">
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";

const emit = defineEmits<{ login: []; register: [] }>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);
</script>

<template>
  <div class="flex h-full flex-col items-center justify-center gap-6 bg-surface px-6 text-center">
    <div class="flex flex-col items-center gap-4">
      <img src="/flutlink-logo.svg" alt="FlutLink" class="h-12" />
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-on-surface">{{ t("welcomeTitle") }}</h1>
        <p class="mx-auto mt-2 max-w-md text-sm leading-relaxed text-on-surface-variant">
          {{ t("welcomeSubtitle") }}
        </p>
      </div>
    </div>

    <div class="flex flex-col items-center gap-2 sm:flex-row">
      <button
        class="w-48 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-on-primary transition hover:bg-primary-hover"
        @click="emit('login')"
      >
        {{ t("signIn") }}
      </button>
      <button
        class="w-48 rounded-lg border border-outline px-4 py-2.5 text-sm font-medium text-on-surface transition hover:border-primary hover:text-primary-emphasis"
        @click="emit('register')"
      >
        {{ t("registerServer") }}
      </button>
    </div>

    <div class="mt-2 flex flex-col items-center gap-3">
      <div class="max-w-sm space-y-1 text-xs leading-relaxed text-outline">
        <p>{{ t("initHint") }}</p>
        <p>{{ t("keychainSecured") }}</p>
      </div>
      <div class="flex items-center gap-2">
        <span class="text-xs text-on-surface-variant">{{ t("partOf") }}</span>
        <img src="/operationflut-logo.svg" alt="OperationFlut" class="h-4" />
      </div>
    </div>
  </div>
</template>
