<script setup lang="ts">
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";

const emit = defineEmits<{ login: []; register: []; guest: [] }>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);
</script>

<template>
  <div class="flex h-full flex-col items-center justify-center gap-6 bg-canvas px-6 text-center">
    <div class="flex flex-col items-center gap-4">
      <img src="/flutlink-logo.svg" alt="FlutLink" class="h-12" />
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">{{ t("welcomeTitle") }}</h1>
        <p class="mx-auto mt-2 max-w-md text-sm leading-relaxed text-muted">
          {{ t("welcomeSubtitle") }}
        </p>
      </div>
    </div>

    <div class="flex flex-col items-center gap-2 sm:flex-row">
      <!-- One filled primary action per view; everything else stays quiet -->
      <button type="button" class="btn btn-primary h-9 w-48" @click="emit('login')">
        {{ t("signIn") }}
      </button>
      <button type="button" class="btn btn-outline h-9 w-48" @click="emit('register')">
        {{ t("registerServer") }}
      </button>
    </div>

    <button type="button" class="btn btn-ghost -mt-3" @click="emit('guest')">
      {{ t("browseAsGuest") }}
    </button>

    <div class="mt-2 flex flex-col items-center gap-3">
      <div class="max-w-sm space-y-1 text-xs leading-relaxed text-muted/80">
        <p>{{ t("initHint") }}</p>
        <p>{{ t("keychainSecured") }}</p>
      </div>
      <div class="flex items-center gap-2">
        <span class="text-xs text-muted">{{ t("partOf") }}</span>
        <img src="/operationflut-logo.svg" alt="OperationFlut" class="h-4" />
      </div>
    </div>
  </div>
</template>
