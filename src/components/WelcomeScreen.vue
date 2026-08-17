<script setup lang="ts">
import "@material/web/button/filled-button.js";
import "@material/web/button/outlined-button.js";
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
      <md-filled-button class="w-48" @click="emit('login')">
        {{ t("signIn") }}
      </md-filled-button>
      <md-outlined-button class="w-48" @click="emit('register')">
        {{ t("registerServer") }}
      </md-outlined-button>
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
