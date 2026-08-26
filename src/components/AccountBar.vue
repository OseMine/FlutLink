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
  if (count > 0 && info.serverUrl) {
    return t("filteredAccountsHintServer")
      .replace("{count}", String(count))
      .replace("{server}", info.serverUrl);
  }
  if (count > 0) {
    return t("filteredAccountsHintNoServer").replace("{count}", String(count));
  }
  return null;
});

const tokenMissingHint = computed(() => {
  const info = store.filterInfo;
  if (!info || info.tokenMissing.length === 0) return null;
  return t("missingTokenAccountsHint").replace(
    "{count}",
    String(info.tokenMissing.length),
  );
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
  <aside class="flex h-full w-72 shrink-0 flex-col border-r border-line bg-panel">
    <div class="flex items-center gap-2.5 border-b border-line px-4 py-4">
      <img src="/flutlink-mark.svg" alt="FlutLink" class="h-8 w-8 shrink-0" />
      <div class="min-w-0">
        <h1 class="truncate text-[15px] font-semibold tracking-tight">
          {{ t("appName") }}
        </h1>
        <p class="truncate text-xs text-muted">{{ t("tagline") }}</p>
      </div>
    </div>

    <div v-if="store.error" class="mx-3 mt-3 rounded-md border border-error/40 bg-error/10 px-3 py-2 text-xs text-error">
      {{ store.error }}
    </div>

    <div v-if="filterHint" class="mx-3 mt-3 rounded-md border border-info/40 bg-info/10 px-3 py-2 text-xs text-info">
      {{ filterHint }}
    </div>

    <div v-if="tokenMissingHint" class="mx-3 mt-3 rounded-md border border-error/40 bg-error/10 px-3 py-2 text-xs text-error">
      {{ tokenMissingHint }}
    </div>

    <div class="flex-1 overflow-y-auto p-3">
      <p class="px-1 pb-2 text-[11px] font-medium uppercase tracking-wide text-muted">
        {{ t("accounts") }}
      </p>

      <button
        v-for="account in store.accounts"
        :key="account.instanceUrl + '/' + account.username"
        type="button"
        class="mb-0.5 flex w-full items-center gap-2 rounded-sm px-3 py-2 text-left text-sm transition"
        :class="account.isActive
          ? 'bg-card-hover text-fg'
          : 'text-muted hover:bg-card hover:text-fg'"
        @click="switchTo(account.username, account.instanceUrl)"
      >
        <span
          class="h-1.5 w-1.5 shrink-0 rounded-full"
          :class="account.isActive ? 'bg-primary' : 'bg-line-strong'"
        ></span>
        <span class="min-w-0 flex-1">
          <span class="block truncate font-medium">
            {{ account.displayName || account.username }}
          </span>
          <span class="block truncate text-xs opacity-70">{{ account.instanceUrl }}</span>
        </span>
        <span v-if="account.isAdmin" class="badge">
          <span class="badge-dot bg-primary"></span>
          {{ t("admin") }}
        </span>
      </button>

      <button
        type="button"
        class="mt-1 w-full rounded-sm border border-dashed border-line-strong px-3 py-2 text-sm text-muted transition hover:border-primary hover:text-primary"
        @click="emit('login')"
      >
        + {{ t("addAccount") }}
      </button>
    </div>

    <div class="border-t border-line p-3">
      <template v-if="store.active">
        <div v-if="store.storage" class="mb-3">
          <p class="mb-1 flex items-baseline justify-between text-xs">
            <span class="font-medium text-muted">{{ t("storage") }}</span>
            <span class="text-muted">{{ storageSummary }}</span>
          </p>
          <div class="progress-track !h-1">
            <div
              class="progress-fill"
              :class="storagePct >= 90 ? '!bg-error' : ''"
              :style="{ width: storagePct + '%' }"
            ></div>
          </div>
          <p class="mt-1 text-[11px] text-muted">{{ t("free") }}: {{ storageFreeLabel }}</p>
        </div>

        <p class="text-xs text-muted">{{ t("signedInAs") }}</p>
        <p class="truncate text-sm">
          {{ store.active.displayName || store.active.username }}
        </p>
        <button
          type="button"
          class="mt-2 text-xs text-muted underline-offset-2 transition hover:text-error hover:underline hover:cursor-pointer"
          @click="remove(store.active.username, store.active.instanceUrl)"
        >
          {{ t("removeAccount") }}
        </button>
      </template>
      <p v-else class="text-xs text-muted">{{ t("noAccount") }}</p>
    </div>
  </aside>
</template>
