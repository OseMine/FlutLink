<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import { listen } from "@tauri-apps/api/event";
import { getVersion } from "@tauri-apps/api/app";
import { openUrl } from "@tauri-apps/plugin-opener";
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
import { translate, updateStatusText as localizedUpdateStatus, type Lang } from "../lib/i18n";
import { registerEscapeCloser } from "../lib/escape";

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

// Notarization: the project is built and managed by @marcante_musik.
const maintainerUrl = "https://instagram.com/marcante_musik";
const openMaintainerProfile = () => {
  void openUrl(maintainerUrl).catch(() => {});
};

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
  { value: "midnight", label: t("themeMidnight") },
  { value: "light", label: t("themeLight") },
  { value: "system", label: t("themeSystem") },
]);

watch(
  () => props.open,
  (open) => {
    if (open) {
      tab.value = "accounts";
    }
  }
);

const adminSearch = ref("");
// L16-F1/#274: cap the admin tab lookup at one OCS page (same as
// FileExplorer.loadAdminUsers) instead of fetching every user page.
const ADMIN_PAGE = 200;

async function loadUsers() {
  if (!accounts.active?.isAdmin) return;
  const query = adminSearch.value.trim();
  if (!query) {
    adminError.value = t("searchUsersRequired");
    return;
  }
  adminLoading.value = true;
  adminError.value = null;
  try {
    const res = await api.adminListUsers(query, ADMIN_PAGE);
    users.value = res.users;
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
  // F7: never delete an account without explicit confirmation (same dialog
  // as App.vue removeActive / AccountBar remove).
  if (!window.confirm(t("deleteAccountConfirm").replace("{name}", username))) return;
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
      updateAssetName.value = e.payload.assetName ?? null;
      // N16-1/#295: the backend emits "installing" once the download finished
      // and the installer was launched - mirror it into the state machine so
      // the template branches are live, the progress bar disappears and the
      // user sees an "installation started" feedback.
      if (e.payload.code === "installing") {
        updateState.value = "installing";
      }
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
  return localizedUpdateStatus(ui.lang, updateStatusKey.value, updateAssetName.value);
});

// L19-N1: Escape closes the modal while it is open.
let removeEscapeCloser: (() => void) | null = null;
watch(
  () => props.open,
  (open) => {
    if (open && !removeEscapeCloser) {
      removeEscapeCloser = registerEscapeCloser(() => emit("close"));
    } else if (!open && removeEscapeCloser) {
      removeEscapeCloser();
      removeEscapeCloser = null;
    }
  }
);
onUnmounted(() => removeEscapeCloser?.());
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
    <div
      v-if="props.open"
      class="fixed inset-0 z-50 flex items-center justify-center bg-scrim/60 p-4 backdrop-blur-sm"
      @click.self="emit('close')"
    >
      <div class="modal-surface flex max-h-[85vh] w-full max-w-md flex-col">
        <div class="flex items-center justify-between border-b border-line px-5 py-3">
          <h2 class="text-base font-semibold">{{ t("settingsTitle") }}</h2>
          <button
            type="button"
            class="icon-btn !h-7 !w-7"
            :aria-label="t('close')"
            @click="emit('close')"
          >
            <Icon name="close" :size="16" />
          </button>
        </div>

        <!-- Native tab list: marked tab and shown panel can never desync -->
        <div role="tablist" class="flex items-stretch border-b border-line px-3">
          <button
            type="button"
            role="tab"
            class="tab"
            :aria-selected="tab === 'accounts'"
            @click="tab = 'accounts'"
          >
            {{ t("tabAccounts") }}
          </button>
          <button
            type="button"
            role="tab"
            class="tab"
            :aria-selected="tab === 'admin'"
            @click="tab = 'admin'"
          >
            {{ t("tabAdmin") }}
          </button>
          <button
            type="button"
            role="tab"
            class="tab"
            :aria-selected="tab === 'about'"
            @click="tab = 'about'"
          >
            {{ t("tabAbout") }}
          </button>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <!-- Accounts -->
          <div v-if="tab === 'accounts'" class="space-y-2">
            <p v-if="!accounts.accounts.length" class="text-sm text-muted">
              {{ t("noAccount") }}
            </p>
            <div
              v-for="account in accounts.accounts"
              :key="account.instanceUrl + '/' + account.username"
              class="card flex items-center gap-3 p-3"
            >
              <div class="min-w-0 flex-1">
                <p class="flex items-center gap-2 truncate text-sm font-medium">
                  {{ account.displayName || account.username }}
                  <span
                    v-if="account.isActive"
                    class="badge !border-primary/40 !bg-primary/10 !text-primary"
                  >
                    <span class="badge-dot bg-primary"></span>
                    {{ t("active") }}
                  </span>
                </p>
                <p class="truncate text-xs text-muted">{{ account.instanceUrl }}</p>
              </div>
              <button type="button" class="btn btn-outline shrink-0" @click="switchTo(account.username, account.instanceUrl)">
                {{ t("switchAccount") }}
              </button>
              <button type="button" class="btn btn-danger shrink-0" @click="remove(account.username, account.instanceUrl)">
                {{ t("removeAccount") }}
              </button>
            </div>
            <button type="button" class="btn btn-outline w-full" @click="emit('login')">
              <Icon name="add" :size="15" />
              {{ t("addAccount") }}
            </button>
          </div>

          <!-- Admin -->
          <div v-if="tab === 'admin'">
            <p v-if="!accounts.active?.isAdmin" class="text-sm text-muted">
              {{ t("adminTabNote") }}
            </p>
            <template v-else>
              <p class="mb-3 text-sm text-muted">{{ t("adminTabNote") }}</p>
              <div class="mb-3 flex gap-2">
                <input
                  v-model="adminSearch"
                  type="text"
                  :placeholder="t('searchUsers')"
                  class="input flex-1"
                  @keyup.enter="loadUsers()"
                />
                <button type="button" class="btn btn-primary shrink-0" @click="loadUsers()">
                  {{ adminLoading ? t("loading") : t("listUsers") }}
                </button>
              </div>
              <div v-if="adminError" class="mb-3 rounded-md border border-error/40 bg-error/10 px-3 py-2 text-xs text-error">
                {{ adminError }}
              </div>
              <p v-if="adminLoading" class="text-sm text-muted">{{ t("users") }}…</p>
              <ul v-else-if="users.length" class="divide-y divide-line rounded-md border border-line">
                <li v-for="userId in users" :key="userId" class="px-3 py-2 text-sm">
                  {{ userId }}
                </li>
              </ul>
              <p v-else class="text-sm text-muted">{{ t("noUsersFound") }}</p>
            </template>
          </div>

          <!-- About -->
          <div v-if="tab === 'about'" class="space-y-5">
            <div class="flex items-center gap-3">
              <AppLogo class="h-10 w-10" />
              <div>
                <p class="text-base font-semibold">{{ t("aboutApp") }}</p>
                <p class="text-xs text-muted">
                  {{ t("version") }} {{ appVersion }} · {{ t("rustBackend") }} · Tauri v2
                </p>
              </div>
            </div>

            <div class="card flex items-center gap-2 px-3 py-2.5">
              <span class="text-xs text-muted">{{ t("partOf") }}</span>
              <img src="/operationflut-logo.svg" alt="OperationFlut" class="h-4" />
            </div>
            <button
              type="button"
              class="card flex w-full items-center gap-2 px-3 py-2.5 text-left transition hover:border-line-strong hover:bg-card-hover hover:cursor-pointer"
              @click="openMaintainerProfile"
            >
              <Icon name="person" :size="14" class="text-muted" />
              <span class="text-xs text-muted">{{ t("builtManagedBy") }}</span>
              <span class="text-xs font-medium text-primary">@marcante_musik</span>
              <Icon name="open" :size="12" class="ml-auto text-muted" />
            </button>
            <p class="text-xs leading-relaxed text-muted/80">{{ t("aboutOperationflut") }}</p>

            <div class="space-y-1 text-xs leading-relaxed text-muted/80">
              <p>{{ t("trayHint") }}</p>
              <p>{{ t("cliHint") }}</p>
            </div>

            <div>
              <p class="mb-1.5 text-[11px] font-medium uppercase tracking-wide text-muted">
                {{ t("language") }}
              </p>
              <div class="flex gap-1.5">
                <button
                  v-for="option in langOptions"
                  :key="option.value"
                  type="button"
                  class="pill h-8"
                  :class="ui.lang === option.value ? 'pill-active' : ''"
                  @click="ui.setLang(option.value)"
                >
                  {{ option.label }}
                </button>
              </div>
            </div>

            <div>
              <p class="mb-1.5 text-[11px] font-medium uppercase tracking-wide text-muted">
                {{ t("theme") }}
              </p>
              <div class="space-y-1.5">
                <button
                  v-for="option in themeOptions"
                  :key="option.value"
                  type="button"
                  class="flex w-full items-center justify-between rounded-md border px-3 py-2 text-sm transition"
                  :class="ui.theme === option.value
                    ? 'border-primary/50 bg-primary/10'
                    : 'border-line hover:bg-card-hover'"
                  @click="ui.setTheme(option.value)"
                >
                  {{ option.label }}
                  <Icon v-if="ui.theme === option.value" name="check" :size="16" class="text-primary" />
                </button>
              </div>
              <p class="mt-2 text-xs text-muted/80">{{ t("systemThemeNote") }}</p>
            </div>

            <div class="card p-3">
              <p class="mb-2 text-[11px] font-medium uppercase tracking-wide text-muted">
                {{ t("updates") }}
              </p>

              <template v-if="updateState === 'checking'">
                <p class="text-sm text-muted">{{ t("checkingForUpdates") }}</p>
              </template>

              <template v-else-if="updateState === 'available' && updateInfo">
                <p class="mb-1 text-sm font-medium">
                  {{ t("updateAvailable") }}
                  <span class="text-primary">v{{ updateInfo.version }}</span>
                </p>
                <p class="mb-2 truncate text-xs text-muted">{{ updateInfo.name }}</p>
                <button type="button" class="btn btn-primary" @click="downloadAndInstall">
                  {{ t("updateDownloadAndInstall") }}
                </button>
              </template>

              <template v-else-if="updateState === 'downloading' || updateState === 'installing'">
                <p class="mb-1 text-sm text-muted">
                  {{
                    updateState === "downloading"
                      ? t("updateDownloading")
                      : t("updateInstalling")
                  }}
                </p>
                <div v-if="updateState === 'downloading'" class="progress-track">
                  <div class="progress-fill" :style="{ width: Math.min(updateProgress, 100) + '%' }"></div>
                </div>
                <p v-if="updateStatusText" class="mt-1 truncate text-xs text-muted/80">
                  {{ updateStatusText }}
                </p>
              </template>

              <template v-else-if="updateState === 'error'">
                <p class="mb-2 text-xs text-error">
                  {{ updateError || t("updateCheckFailed") }}
                </p>
                <button type="button" class="btn btn-outline" @click="checkForUpdate">
                  {{ t("checkForUpdates") }}
                </button>
              </template>

              <template v-else>
                <p class="mb-2 text-xs text-muted/80">{{ t("updateUpToDate") }}</p>
                <button type="button" class="btn btn-outline" @click="checkForUpdate">
                  {{ t("checkForUpdates") }}
                </button>
              </template>
            </div>

            <p class="text-xs leading-relaxed text-muted/80">{{ t("keychainSecured") }}</p>
          </div>
        </div>
      </div>
    </div>
    </Transition>
  </Teleport>
</template>
