<script setup lang="ts">
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";

const emit = defineEmits<{ login: []; register: [] }>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);
</script>

<template>
  <div class="flex h-full flex-col items-center justify-center gap-6 bg-zinc-950 px-6 text-center">
    <div class="flex flex-col items-center gap-4">
      <img src="/flutlink-logo.svg" alt="FlutLink" class="h-12" />
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-white">{{ t("welcomeTitle") }}</h1>
        <p class="mx-auto mt-2 max-w-md text-sm leading-relaxed text-zinc-400">
          {{ t("welcomeSubtitle") }}
        </p>
      </div>
    </div>

    <div class="flex flex-col items-center gap-2 sm:flex-row">
      <button
        class="w-48 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-indigo-500"
        @click="emit('login')"
      >
        {{ t("signIn") }}
      </button>
      <button
        class="w-48 rounded-lg border border-zinc-700 px-4 py-2.5 text-sm font-medium text-zinc-200 transition hover:border-indigo-500 hover:text-indigo-300"
        @click="emit('register')"
      >
        {{ t("registerServer") }}
      </button>
    </div>

    <div class="mt-2 flex flex-col items-center gap-3">
      <div class="max-w-sm space-y-1 text-xs leading-relaxed text-zinc-600">
        <p>{{ t("initHint") }}</p>
        <p>{{ t("keychainSecured") }}</p>
      </div>
      <div class="flex items-center gap-2">
        <span class="text-xs text-zinc-500">{{ t("partOf") }}</span>
        <img src="/operationflut-logo.svg" alt="OperationFlut" class="h-4" />
      </div>
    </div>
  </div>
</template>
