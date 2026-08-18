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

async function switchTo(username: string, instanceUrl: string) {
  try {
    await store.switchTo(username, instanceUrl);
  } catch {
    // error surfaced via store.error
  }
}

async function remove(username: string, instanceUrl: string) {
  const name = store.accounts.find(
    (a) => a.username === username && a.instanceUrl === instanceUrl,
  )?.displayName ?? username;
  // F7: never delete an account without explicit confirmation.
  if (!window.confirm(t("deleteAccountConfirm").replace("{name}", name))) return;
  try {
    await store.remove(username, instanceUrl);
  } catch {
    // error surfaced via store.error
  }
}

const filterHint = computed(() => {
  const info = store.filterInfo;
  if (!info) return null;
  const count = info.droppedCount;
  if (info.serverUrl) {
    return t("filteredAccountsHintServer")
      .replace("{count}", String(count))
      .replace("{server}", info.serverUrl);
  }
  return t("filteredAccountsHintNoServer").replace("{count}", String(count));
});

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
  return formatBytes(free);
});
</script>

<template>
  <aside class="flex h-full w-72 shrink-0 flex-col border-r border-outline-variant bg-surface-container">
    <div class="flex items-center gap-2.5 border-b border-outline-variant px-4 py-4">
      <img src="/flutlink-mark.svg" alt="FlutLink" class="h-8 w-8 shrink-0" />
      <div class="min-w-0">
        <h1 class="truncate text-base font-semibold tracking-tight text-on-surface">
          {{ t("appName") }}
        </h1>
        <p class="truncate text-xs text-on-surface-variant">{{ t("tagline") }}</p>
      </div>
    </div>

    <div v-if="store.error" class="m-3 rounded-md border border-error bg-error-container px-3 py-2 text-xs text-on-error-container">
      {{ store.error }}
    </div>

    <div v-if="filterHint" class="m-3 rounded-md border border-info bg-info-container/60 px-3 py-2 text-xs text-on-info-container">
      {{ filterHint }}
    </div>

    <div class="flex-1 overflow-y-auto p-3">
      <p class="px-1 pb-2 text-xs font-medium uppercase tracking-wide text-on-surface-variant">
        {{ t("accounts") }}
      </p>

      <button
        v-for="account in store.accounts"
        :key="account.instanceUrl + '/' + account.username"
        class="mb-1 flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm transition"
        :class="account.isActive
          ? 'bg-primary text-on-primary'
          : 'bg-surface-container-high text-on-surface-variant hover:bg-surface-container-highest'"
        @click="switchTo(account.username, account.instanceUrl)"
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
          class="rounded bg-primary-container/50 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-on-primary-container"
        >
          {{ t("admin") }}
        </span>
      </button>

      <button
        class="mt-1 w-full rounded-md border border-dashed border-outline px-3 py-2 text-sm text-on-surface-variant hover:border-primary hover:text-primary-emphasis"
        @click="emit('login')"
      >
        + {{ t("addAccount") }}
      </button>
    </div>

    <div class="border-t border-outline-variant p-3">
      <template v-if="store.active">
        <div v-if="store.storage" class="mb-3">
          <p class="mb-1 flex items-baseline justify-between text-xs">
            <span class="font-medium text-on-surface-variant">{{ t("storage") }}</span>
            <span class="text-on-surface-variant">{{ storageSummary }}</span>
          </p>
          <div class="h-1.5 w-full overflow-hidden rounded-full bg-surface-container-high">
            <div
              class="h-full rounded-full transition-all"
              :class="storagePct >= 90 ? 'bg-error' : 'bg-primary'"
              :style="{ width: storagePct + '%' }"
            ></div>
          </div>
          <p class="mt-1 text-[11px] text-on-surface-variant">{{ t("free") }}: {{ storageFreeLabel }}</p>
        </div>

        <p class="text-xs text-on-surface-variant">{{ t("signedInAs") }}</p>
        <p class="truncate text-sm text-on-surface">
          {{ store.active.displayName || store.active.username }}
        </p>
        <button
          class="mt-2 text-xs text-on-surface-variant underline-offset-2 hover:text-error hover:underline"
          @click="remove(store.active.username, store.active.instanceUrl)"
        >
          {{ t("removeAccount") }}
        </button>
      </template>
      <p v-else class="text-xs text-on-surface-variant">{{ t("noAccount") }}</p>
    </div>
  </aside>
</template>
