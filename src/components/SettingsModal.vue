<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import { listen } from "@tauri-apps/api/event";
import AppLogo from "./AppLogo.vue";
import { useAccountsStore } from "../stores/accounts";
import { useUiStore, type Theme } from "../stores/ui";
import {
  api,
  invokeError,
  type ReleaseInfo,
  type UpdateProgress,
  type UpdateStatus,
} from "../lib/ipc";
import { translate, type Lang } from "../lib/i18n";

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: []; login: [] }>();

const accounts = useAccountsStore();
const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const tab = ref<"accounts" | "admin" | "about">("accounts");
const users = ref<string[]>([]);
const adminLoading = ref(false);
const adminError = ref<string | null>(null);

type UpdateState =
  | "idle"
  | "checking"
  | "available"
  | "downloading"
  | "installing"
  | "error";

const updateState = ref<UpdateState>("idle");
const updateInfo = ref<ReleaseInfo | null>(null);
const updateError = ref<string | null>(null);
const updateProgress = ref(0);
const updateStatusKey = ref<string | null>(null);
const updateAssetName = ref<string | null>(null);
let unlistenProgress: (() => void) | null = null;
let unlistenStatus: (() => void) | null = null;

onUnmounted(() => {
  unlistenProgress?.();
  unlistenStatus?.();
});

const langOptions: { value: Lang; label: string }[] = [
  { value: "en", label: "English" },
  { value: "de", label: "Deutsch" },
];

const themeOptions = computed<{ value: Theme; label: string }[]>(() => [
  { value: "operationflut", label: t("themeOperationflut") },
  { value: "midnight", label: t("themeMidnight") },
  { value: "system", label: t("themeSystem") },
]);

watch(
  () => props.open,
  (open) => {
    if (open) {
      tab.value = "accounts";
      if (accounts.active?.isAdmin) void loadUsers();
    }
  }
);

async function loadUsers() {
  if (!accounts.active?.isAdmin) return;
  adminLoading.value = true;
  adminError.value = null;
  try {
    users.value = await api.adminListUsers("");
  } catch (e) {
    adminError.value = invokeError(e).message;
  } finally {
    adminLoading.value = false;
  }
}

async function switchTo(username: string, instanceUrl: string) {
  try {
    await accounts.switchTo(username, instanceUrl);
    ui.toast(t("accountSwitched"), "success");
  } catch {
    // error surfaced via accounts.error
  }
}

async function remove(username: string, instanceUrl: string) {
  try {
    await accounts.remove(username, instanceUrl);
    ui.toast(t("accountRemoved"), "success");
  } catch {
    // error surfaced via accounts.error
  }
}

async function checkForUpdate() {
  updateState.value = "checking";
  updateError.value = null;
  try {
    const info = await api.checkUpdate();
    updateInfo.value = info;
    updateState.value = info ? "available" : "idle";
  } catch (e) {
    updateError.value = invokeError(e).message;
    updateState.value = "error";
  }
}

async function downloadAndInstall() {
  updateState.value = "downloading";
  updateProgress.value = 0;
  updateStatusKey.value = null;
  updateAssetName.value = null;
  updateError.value = null;
  unlistenProgress?.();
  unlistenStatus?.();
  try {
    unlistenProgress = await listen<UpdateProgress>("update://progress", (e) => {
      updateProgress.value = e.payload.percent;
    });
    unlistenStatus = await listen<UpdateStatus>("update://status", (e) => {
      updateStatusKey.value = e.payload.code;
      updateAssetName.value = e.payload.asset_name ?? null;
    });
  } catch {
    // progress/status listeners are best-effort
  }
  try {
    await api.downloadAndInstallUpdate();
  } catch (e) {
    updateError.value = invokeError(e).message;
    updateState.value = "error";
  }
}

const updateStatusText = computed(() => {
  if (!updateStatusKey.value) return "";
  switch (updateStatusKey.value) {
    case "checking":
      return t("checkingForUpdates");
    case "downloading":
      return updateAssetName.value
        ? t("updateDownloadingName").replace("{name}", updateAssetName.value)
        : t("updateDownloading");
    case "installing":
      return t("updateInstalling");
    default:
      return "";
  }
});
</script>

<template>
  <Teleport to="body">
    <div
      v-if="props.open"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      @click.self="emit('close')"
    >
      <div class="flex max-h-[85vh] w-full max-w-md flex-col rounded-xl border border-zinc-700 bg-zinc-900 shadow-2xl">
        <div class="flex items-center justify-between border-b border-zinc-800 px-5 py-3">
          <h2 class="text-base font-semibold text-zinc-50">{{ t("settingsTitle") }}</h2>
          <button
            class="rounded-md px-2 py-1 text-sm text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200"
            :aria-label="t('close')"
            @click="emit('close')"
          >
            ✕
          </button>
        </div>

        <div class="flex gap-1 border-b border-zinc-800 px-4 pt-2">
          <button
            v-for="key in (['accounts', 'admin', 'about'] as const)"
            :key="key"
            class="rounded-t-md px-3 py-1.5 text-sm font-medium transition"
            :class="tab === key ? 'border-b-2 border-indigo-500 text-zinc-50' : 'text-zinc-500 hover:text-zinc-300'"
            @click="tab = key"
          >
            {{ t(key === 'accounts' ? 'tabAccounts' : key === 'admin' ? 'tabAdmin' : 'tabAbout') }}
          </button>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <!-- Accounts -->
          <div v-if="tab === 'accounts'" class="space-y-2">
            <p v-if="!accounts.accounts.length" class="text-sm text-zinc-500">
              {{ t("noAccount") }}
            </p>
            <div
              v-for="account in accounts.accounts"
              :key="account.instanceUrl + '/' + account.username"
              class="flex items-center gap-3 rounded-lg border border-zinc-800 bg-zinc-800/40 p-3"
            >
              <div class="min-w-0 flex-1">
                <p class="flex items-center gap-2 truncate text-sm font-medium text-zinc-50">
                  {{ account.displayName || account.username }}
                  <span
                    v-if="account.isActive"
                    class="rounded bg-indigo-600/20 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-indigo-300"
                  >
                    {{ t("active") }}
                  </span>
                </p>
                <p class="truncate text-xs text-zinc-500">{{ account.instanceUrl }}</p>
              </div>
              <button
                v-if="!account.isActive"
                class="rounded-md border border-zinc-700 px-2.5 py-1 text-xs text-zinc-300 hover:bg-zinc-800"
                @click="switchTo(account.username, account.instanceUrl)"
              >
                {{ t("switchAccount") }}
              </button>
              <button
                class="rounded-md border border-zinc-700 px-2.5 py-1 text-xs text-red-300 hover:bg-red-950/40"
                @click="remove(account.username, account.instanceUrl)"
              >
                {{ t("removeAccount") }}
              </button>
            </div>
            <button
              class="w-full rounded-lg border border-dashed border-zinc-600 px-3 py-2 text-sm text-zinc-400 hover:border-indigo-500 hover:text-indigo-300"
              @click="emit('login')"
            >
              + {{ t("addAccount") }}
            </button>
          </div>

          <!-- Admin -->
          <div v-if="tab === 'admin'">
            <p v-if="!accounts.active?.isAdmin" class="text-sm text-zinc-500">
              {{ t("adminTabNote") }}
            </p>
            <template v-else>
              <p class="mb-3 text-sm text-zinc-500">{{ t("adminTabNote") }}</p>
              <div v-if="adminError" class="mb-3 rounded-md border border-red-800 bg-red-950/50 px-3 py-2 text-xs text-red-300">
                {{ adminError }}
              </div>
              <p v-if="adminLoading" class="text-sm text-zinc-500">{{ t("users") }}…</p>
              <ul v-else-if="users.length" class="divide-y divide-zinc-800/60 rounded-lg border border-zinc-800">
                <li v-for="userId in users" :key="userId" class="px-3 py-2 text-sm text-zinc-200">
                  {{ userId }}
                </li>
              </ul>
              <p v-else class="text-sm text-zinc-500">{{ t("noUsersFound") }}</p>
            </template>
          </div>

          <!-- About -->
          <div v-if="tab === 'about'" class="space-y-5">
            <div class="flex items-center gap-3">
              <AppLogo class="h-10 w-10" />
              <div>
                <p class="text-base font-semibold text-zinc-50">{{ t("aboutApp") }}</p>
                <p class="text-xs text-zinc-500">
                  {{ t("version") }} 0.1.0 · {{ t("rustBackend") }} · Tauri v2
                </p>
              </div>
            </div>

            <div class="flex items-center gap-2 rounded-lg border border-zinc-800 bg-zinc-800/40 px-3 py-2.5">
              <span class="text-xs text-zinc-400">{{ t("partOf") }}</span>
              <img src="/operationflut-logo.svg" alt="OperationFlut" class="h-4" />
            </div>
            <p class="text-xs leading-relaxed text-zinc-600">{{ t("aboutOperationflut") }}</p>

            <div class="space-y-1 text-xs leading-relaxed text-zinc-600">
              <p>{{ t("trayHint") }}</p>
              <p>{{ t("cliHint") }}</p>
            </div>

            <div>
              <p class="mb-1.5 text-xs font-medium uppercase tracking-wide text-zinc-500">
                {{ t("language") }}
              </p>
              <div class="flex gap-1.5">
                <button
                  v-for="option in langOptions"
                  :key="option.value"
                  class="rounded-md border px-3 py-1.5 text-sm transition"
                  :class="ui.lang === option.value
                    ? 'border-indigo-500 bg-indigo-600/20 text-zinc-50'
                    : 'border-zinc-700 text-zinc-300 hover:bg-zinc-800'"
                  @click="ui.setLang(option.value)"
                >
                  {{ option.label }}
                </button>
              </div>
            </div>

            <div>
              <p class="mb-1.5 text-xs font-medium uppercase tracking-wide text-zinc-500">
                {{ t("theme") }}
              </p>
              <div class="space-y-1.5">
                <button
                  v-for="option in themeOptions"
                  :key="option.value"
                  class="flex w-full items-center justify-between rounded-md border px-3 py-2 text-sm transition"
                  :class="ui.theme === option.value
                    ? 'border-indigo-500 bg-indigo-600/20 text-zinc-50'
                    : 'border-zinc-700 text-zinc-300 hover:bg-zinc-800'"
                  @click="ui.setTheme(option.value)"
                >
                  {{ option.label }}
                  <span v-if="ui.theme === option.value" class="text-indigo-300">✓</span>
                </button>
              </div>
              <p class="mt-2 text-xs text-zinc-600">{{ t("systemThemeNote") }}</p>
            </div>

            <div class="rounded-lg border border-zinc-800 bg-zinc-800/40 p-3">
              <p class="mb-2 text-xs font-medium uppercase tracking-wide text-zinc-500">
                {{ t("updates") }}
              </p>

              <template v-if="updateState === 'checking'">
                <p class="text-sm text-zinc-400">{{ t("checkingForUpdates") }}</p>
              </template>

              <template v-else-if="updateState === 'available' && updateInfo">
                <p class="mb-1 text-sm font-medium text-zinc-50">
                  {{ t("updateAvailable") }}
                  <span class="text-indigo-300">v{{ updateInfo.version }}</span>
                </p>
                <p class="mb-2 truncate text-xs text-zinc-500">{{ updateInfo.name }}</p>
                <button
                  class="w-full rounded-md border border-indigo-600 bg-indigo-600/20 px-3 py-2 text-sm text-indigo-200 hover:bg-indigo-600/30"
                  @click="downloadAndInstall"
                >
                  {{ t("updateDownloadAndInstall") }}
                </button>
              </template>

              <template
                v-else-if="updateState === 'downloading' || updateState === 'installing'"
              >
                <p class="mb-1 text-sm text-zinc-400">
                  {{
                    updateState === "downloading"
                      ? t("updateDownloading")
                      : t("updateInstalling")
                  }}
                </p>
                <div
                  v-if="updateState === 'downloading'"
                  class="h-1.5 w-full overflow-hidden rounded-full bg-zinc-800"
                >
                  <div
                    class="h-full rounded-full bg-indigo-500 transition-all"
                    :style="{ width: Math.min(updateProgress, 100) + '%' }"
                  ></div>
                </div>
                <p v-if="updateStatusText" class="mt-1 truncate text-xs text-zinc-600">
                  {{ updateStatusText }}
                </p>
              </template>

              <template v-else-if="updateState === 'error'">
                <p class="mb-2 text-xs text-red-300">
                  {{ updateError || t("updateCheckFailed") }}
                </p>
                <button
                  class="w-full rounded-md border border-zinc-700 px-3 py-2 text-sm text-zinc-300 hover:bg-zinc-800"
                  @click="checkForUpdate"
                >
                  {{ t("checkForUpdates") }}
                </button>
              </template>

              <template v-else>
                <p class="mb-2 text-xs text-zinc-600">{{ t("updateUpToDate") }}</p>
                <button
                  class="w-full rounded-md border border-zinc-700 px-3 py-2 text-sm text-zinc-300 hover:bg-zinc-800"
                  @click="checkForUpdate"
                >
                  {{ t("checkForUpdates") }}
                </button>
              </template>
            </div>

            <p class="text-xs leading-relaxed text-zinc-600">{{ t("keychainSecured") }}</p>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
