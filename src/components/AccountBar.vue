<script setup lang="ts">
import { computed } from "vue";
import { useAccountsStore } from "../stores/accounts";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import { formatBytes } from "../lib/format";

const emit = defineEmits<{ login: [] }>();

const store = useAccountsStore();
const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

async function switchTo(username: string) {
  try {
    await store.switchTo(username);
  } catch {
    // error surfaced via store.error
  }
}

async function remove(username: string) {
  try {
    await store.remove(username);
  } catch {
    // error surfaced via store.error
  }
}

const storagePct = computed(() => {
  const s = store.storage;
  if (!s || s.total === null || s.used === null || s.total <= 0) return 0;
  return Math.min(100, Math.round((s.used / s.total) * 100));
});

const storageSummary = computed(() => {
  const s = store.storage;
  if (!s) return "";
  if (s.total === null) return t("unlimited");
  if (s.used === null) return formatBytes(s.total);
  return `${formatBytes(s.used)} / ${formatBytes(s.total)}`;
});

const storageFreeLabel = computed(() => {
  const s = store.storage;
  if (!s) return "";
  if (s.total === null) return t("unlimited");
  if (s.used === null) return formatBytes(s.total);
  const free = s.total - s.used;
  if (free < 0) return `-${formatBytes(-free)}`;
  return `${formatBytes(free)} ${t("free")}`;
});
</script>

<template>
  <aside class="flex h-full w-72 shrink-0 flex-col border-r border-zinc-800 bg-zinc-900">
    <div class="flex items-center gap-2.5 border-b border-zinc-800 px-4 py-4">
      <img src="/flutlink-mark.svg" alt="FlutLink" class="h-8 w-8 shrink-0" />
      <div class="min-w-0">
        <h1 class="truncate text-base font-semibold tracking-tight text-white">
          {{ t("appName") }}
        </h1>
        <p class="truncate text-xs text-zinc-500">{{ t("tagline") }}</p>
      </div>
    </div>

    <div v-if="store.error" class="m-3 rounded-md border border-red-800 bg-red-950/50 px-3 py-2 text-xs text-red-300">
      {{ store.error }}
    </div>

    <div class="flex-1 overflow-y-auto p-3">
      <p class="px-1 pb-2 text-xs font-medium uppercase tracking-wide text-zinc-500">
        {{ t("accounts") }}
      </p>

      <button
        v-for="account in store.accounts"
        :key="account.instanceUrl + '/' + account.username"
        class="mb-1 flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm transition"
        :class="account.isActive
          ? 'bg-indigo-600 text-white'
          : 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700'"
        @click="switchTo(account.username)"
      >
        <div class="min-w-0 flex-1">
          <p class="truncate font-medium">
            {{ account.displayName || account.username }}
          </p>
          <p class="truncate text-xs opacity-70">{{ account.instanceUrl }}</p>
        </div>
        <span
          v-if="account.isAdmin"
          :title="t('admin')"
          class="rounded bg-white/20 px-1.5 py-0.5 text-[10px] font-semibold uppercase"
        >
          {{ t("admin") }}
        </span>
      </button>

      <button
        class="mt-1 w-full rounded-md border border-dashed border-zinc-600 px-3 py-2 text-sm text-zinc-400 hover:border-indigo-500 hover:text-indigo-300"
        @click="emit('login')"
      >
        + {{ t("addAccount") }}
      </button>
    </div>

    <div class="border-t border-zinc-800 p-3">
      <template v-if="store.active">
        <div v-if="store.storage" class="mb-3">
          <p class="mb-1 flex items-baseline justify-between text-xs">
            <span class="font-medium text-zinc-500">{{ t("storage") }}</span>
            <span class="text-zinc-300">{{ storageSummary }}</span>
          </p>
          <div class="h-1.5 w-full overflow-hidden rounded-full bg-zinc-800">
            <div
              class="h-full rounded-full transition-all"
              :class="storagePct >= 90 ? 'bg-red-500' : 'bg-indigo-500'"
              :style="{ width: storagePct + '%' }"
            ></div>
          </div>
          <p class="mt-1 text-[11px] text-zinc-500">{{ t("free") }}: {{ storageFreeLabel }}</p>
        </div>

        <p class="text-xs text-zinc-500">{{ t("signedInAs") }}</p>
        <p class="truncate text-sm text-white">
          {{ store.active.displayName || store.active.username }}
        </p>
        <button
          class="mt-2 text-xs text-zinc-400 underline-offset-2 hover:text-red-400 hover:underline"
          @click="remove(store.active.username)"
        >
          {{ t("removeAccount") }}
        </button>
      </template>
      <p v-else class="text-xs text-zinc-500">{{ t("noAccount") }}</p>
    </div>
  </aside>
</template>
