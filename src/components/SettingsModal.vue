<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import { listen } from "@tauri-apps/api/event";
import { getVersion } from "@tauri-apps/api/app";
import AppLogo from "./AppLogo.vue";
import Icon from "./Icon.vue";
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

// F9: read the real app version from the Tauri runtime instead of hardcoding
// it (the displayed value drifted from the packaged version).
const appVersion = ref("…");
void getVersion()
  .then((v) => {
    appVersion.value = v;
  })
  .catch(() => {
    appVersion.value = "";
  });

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

// Material You accent seed; null keeps the theme's default hue.
const accentValue = ref(ui.accentHue ?? 266);

function themeDefaultHue(): number {
  const resolved =
    ui.theme === "system"
      ? window.matchMedia("(prefers-color-scheme: dark)").matches
        ? "midnight"
        : "light"
      : ui.theme;
  return resolved === "midnight" ? 220 : 266;
}

function applyAccent() {
  ui.setAccentHue(Math.round(accentValue.value));
}

function resetAccent() {
  ui.setAccentHue(null);
  accentValue.value = themeDefaultHue();
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      tab.value = "accounts";
      accentValue.value = ui.accentHue ?? 266;
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
      <div class="flex max-h-[85vh] w-full max-w-md flex-col rounded-xl border border-outline bg-surface-container shadow-m3-3">
        <div class="flex items-center justify-between border-b border-outline-variant px-5 py-3">
          <h2 class="text-base font-semibold text-on-surface">{{ t("settingsTitle") }}</h2>
          <button
            class="flex h-8 w-8 items-center justify-center rounded-md text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface"
            :aria-label="t('close')"
            @click="emit('close')"
          >
            <Icon name="close" :size="18" />
          </button>
        </div>

        <div class="flex gap-1 border-b border-outline-variant px-4 pt-2">
          <button
            v-for="key in (['accounts', 'admin', 'about'] as const)"
            :key="key"
            class="rounded-t-md px-3 py-1.5 text-sm font-medium transition"
            :class="tab === key ? 'border-b-2 border-primary text-on-surface' : 'text-on-surface-variant hover:text-on-surface-variant'"
            @click="tab = key"
          >
            {{ t(key === 'accounts' ? 'tabAccounts' : key === 'admin' ? 'tabAdmin' : 'tabAbout') }}
          </button>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <!-- Accounts -->
          <div v-if="tab === 'accounts'" class="space-y-2">
            <p v-if="!accounts.accounts.length" class="text-sm text-on-surface-variant">
              {{ t("noAccount") }}
            </p>
            <div
              v-for="account in accounts.accounts"
              :key="account.instanceUrl + '/' + account.username"
              class="flex items-center gap-3 rounded-lg border border-outline-variant bg-surface-container-high/40 p-3"
            >
              <div class="min-w-0 flex-1">
                <p class="flex items-center gap-2 truncate text-sm font-medium text-on-surface">
                  {{ account.displayName || account.username }}
                  <span
                    v-if="account.isActive"
                    class="rounded bg-primary-container px-1.5 py-0.5 text-[10px] font-semibold uppercase text-on-primary-container"
                  >
                    {{ t("active") }}
                  </span>
                </p>
                <p class="truncate text-xs text-on-surface-variant">{{ account.instanceUrl }}</p>
              </div>
              <button
                v-if="!account.isActive"
                class="rounded-md border border-outline px-2.5 py-1 text-xs text-on-surface-variant hover:bg-surface-container-high"
                @click="switchTo(account.username, account.instanceUrl)"
              >
                {{ t("switchAccount") }}
              </button>
              <button
                class="rounded-md border border-outline px-2.5 py-1 text-xs text-error hover:bg-error-container"
                @click="remove(account.username, account.instanceUrl)"
              >
                {{ t("removeAccount") }}
              </button>
            </div>
            <button
              class="flex w-full items-center justify-center gap-1.5 rounded-lg border border-dashed border-outline px-3 py-2 text-sm text-on-surface-variant hover:border-primary hover:text-primary-emphasis"
              @click="emit('login')"
            >
              <Icon name="add" :size="16" />
              {{ t("addAccount") }}
            </button>
          </div>

          <!-- Admin -->
          <div v-if="tab === 'admin'">
            <p v-if="!accounts.active?.isAdmin" class="text-sm text-on-surface-variant">
              {{ t("adminTabNote") }}
            </p>
            <template v-else>
              <p class="mb-3 text-sm text-on-surface-variant">{{ t("adminTabNote") }}</p>
              <div v-if="adminError" class="mb-3 rounded-md border border-error bg-error-container px-3 py-2 text-xs text-on-error-container">
                {{ adminError }}
              </div>
              <p v-if="adminLoading" class="text-sm text-on-surface-variant">{{ t("users") }}…</p>
              <ul v-else-if="users.length" class="divide-y divide-outline-variant/60 rounded-lg border border-outline-variant">
                <li v-for="userId in users" :key="userId" class="px-3 py-2 text-sm text-on-surface">
                  {{ userId }}
                </li>
              </ul>
              <p v-else class="text-sm text-on-surface-variant">{{ t("noUsersFound") }}</p>
            </template>
          </div>

          <!-- About -->
          <div v-if="tab === 'about'" class="space-y-5">
            <div class="flex items-center gap-3">
              <AppLogo class="h-10 w-10" />
              <div>
                <p class="text-base font-semibold text-on-surface">{{ t("aboutApp") }}</p>
                <p class="text-xs text-on-surface-variant">
                  {{ t("version") }} {{ appVersion }} · {{ t("rustBackend") }} · Tauri v2
                </p>
              </div>
            </div>

            <div class="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-high/40 px-3 py-2.5">
              <span class="text-xs text-on-surface-variant">{{ t("partOf") }}</span>
              <img src="/operationflut-logo.svg" alt="OperationFlut" class="h-4" />
            </div>
            <p class="text-xs leading-relaxed text-outline">{{ t("aboutOperationflut") }}</p>

            <div class="space-y-1 text-xs leading-relaxed text-outline">
              <p>{{ t("trayHint") }}</p>
              <p>{{ t("cliHint") }}</p>
            </div>

            <div>
              <p class="mb-1.5 text-xs font-medium uppercase tracking-wide text-on-surface-variant">
                {{ t("language") }}
              </p>
              <div class="flex gap-1.5">
                <button
                  v-for="option in langOptions"
                  :key="option.value"
                  class="rounded-md border px-3 py-1.5 text-sm transition"
                  :class="ui.lang === option.value
                    ? 'border-primary bg-primary-container text-on-primary-container'
                    : 'border-outline text-on-surface-variant hover:bg-surface-container-high'"
                  @click="ui.setLang(option.value)"
                >
                  {{ option.label }}
                </button>
              </div>
            </div>

            <div>
              <p class="mb-1.5 text-xs font-medium uppercase tracking-wide text-on-surface-variant">
                {{ t("theme") }}
              </p>
              <div class="space-y-1.5">
                <button
                  v-for="option in themeOptions"
                  :key="option.value"
                  class="flex w-full items-center justify-between rounded-md border px-3 py-2 text-sm transition"
                  :class="ui.theme === option.value
                    ? 'border-primary bg-primary-container text-on-primary-container'
                    : 'border-outline text-on-surface-variant hover:bg-surface-container-high'"
                  @click="ui.setTheme(option.value)"
                >
                  {{ option.label }}
                  <Icon v-if="ui.theme === option.value" name="check" :size="16" class="text-on-primary-container" />
                </button>
              </div>
              <p class="mt-2 text-xs text-outline">{{ t("systemThemeNote") }}</p>
            </div>

            <div>
              <p class="mb-1.5 text-xs font-medium uppercase tracking-wide text-on-surface-variant">
                {{ t("accentColor") }}
              </p>
              <div class="flex items-center gap-3">
                <input
                  v-model.number="accentValue"
                  type="range"
                  min="0"
                  max="360"
                  step="1"
                  class="w-full accent-primary"
                  :aria-label="t('accentColor')"
                  @input="applyAccent"
                />
                <button
                  class="shrink-0 rounded-md border border-outline px-2.5 py-1.5 text-xs text-on-surface-variant hover:bg-surface-container-high"
                  @click="resetAccent"
                >
                  {{ t("accentReset") }}
                </button>
              </div>
              <p class="mt-2 text-xs text-outline">{{ t("accentColorHint") }}</p>
            </div>

            <div class="rounded-lg border border-outline-variant bg-surface-container-high/40 p-3">
              <p class="mb-2 text-xs font-medium uppercase tracking-wide text-on-surface-variant">
                {{ t("updates") }}
              </p>

              <template v-if="updateState === 'checking'">
                <p class="text-sm text-on-surface-variant">{{ t("checkingForUpdates") }}</p>
              </template>

              <template v-else-if="updateState === 'available' && updateInfo">
                <p class="mb-1 text-sm font-medium text-on-surface">
                  {{ t("updateAvailable") }}
                  <span class="text-primary-emphasis">v{{ updateInfo.version }}</span>
                </p>
                <p class="mb-2 truncate text-xs text-on-surface-variant">{{ updateInfo.name }}</p>
                <button
                  class="w-full rounded-md border border-primary bg-primary/20 px-3 py-2 text-sm text-primary-emphasis hover:bg-primary/30"
                  @click="downloadAndInstall"
                >
                  {{ t("updateDownloadAndInstall") }}
                </button>
              </template>

              <template
                v-else-if="updateState === 'downloading' || updateState === 'installing'"
              >
                <p class="mb-1 text-sm text-on-surface-variant">
                  {{
                    updateState === "downloading"
                      ? t("updateDownloading")
                      : t("updateInstalling")
                  }}
                </p>
                  <div
                    v-if="updateState === 'downloading'"
                    class="h-1.5 w-full overflow-hidden rounded-full bg-surface-container-high"
                  >
                    <div
                      class="h-full rounded-full bg-primary transition-all"
                      :style="{ width: Math.min(updateProgress, 100) + '%' }"
                    ></div>
                  </div>
                  <p v-if="updateStatusText" class="mt-1 truncate text-xs text-outline">
                    {{ updateStatusText }}
                  </p>
                </template>

                <template v-else-if="updateState === 'error'">
                  <p class="mb-2 text-xs text-error">
                    {{ updateError || t("updateCheckFailed") }}
                  </p>
                <button
                  class="w-full rounded-md border border-outline px-3 py-2 text-sm text-on-surface-variant hover:bg-surface-container-high"
                  @click="checkForUpdate"
                >
                  {{ t("checkForUpdates") }}
                </button>
              </template>

              <template v-else>
                <p class="mb-2 text-xs text-outline">{{ t("updateUpToDate") }}</p>
                <button
                  class="w-full rounded-md border border-outline px-3 py-2 text-sm text-on-surface-variant hover:bg-surface-container-high"
                  @click="checkForUpdate"
                >
                  {{ t("checkForUpdates") }}
                </button>
              </template>
            </div>

            <p class="text-xs leading-relaxed text-outline">{{ t("keychainSecured") }}</p>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
