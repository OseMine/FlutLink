<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { listen } from "@tauri-apps/api/event";
import AccountBar from "./components/AccountBar.vue";
import FileExplorer from "./components/FileExplorer.vue";
import AdminPanel from "./components/AdminPanel.vue";
import SyncPanel from "./components/SyncPanel.vue";
import WelcomeScreen from "./components/WelcomeScreen.vue";
import LoginModal from "./components/LoginModal.vue";
import SettingsModal from "./components/SettingsModal.vue";
import ToastStack from "./components/ToastStack.vue";
import Icon from "./components/Icon.vue";
import { useAccountsStore } from "./stores/accounts";
import { useFilesStore } from "./stores/files";
import { useSyncStore } from "./stores/sync";
import { useUiStore } from "./stores/ui";
import { translate } from "./lib/i18n";
import { api, invokeError, type ReleaseInfo, type UpdateProgress } from "./lib/ipc";
import "@material/web/button/filled-button.js";
import "@material/web/button/outlined-button.js";
import "@material/web/button/text-button.js";
import "@material/web/iconbutton/icon-button.js";
import "@material/web/divider/divider.js";

const accounts = useAccountsStore();
const files = useFilesStore();
const sync = useSyncStore();
const ui = useUiStore();

const tab = ref<"files" | "admin" | "sync">("files");
const showLogin = ref(false);
const loginMode = ref<"login" | "register">("login");
const showSettings = ref(false);
const accountMenu = ref(false);
// U-R8-8: resolve the initial theme synchronously (from the persisted theme +
// the OS preference) so `[data-theme]` is correct before first paint — no
// flash of the wrong theme for "System Default" users.
function initialTheme(): "operationflut" | "midnight" | "light" {
  if (ui.theme === "system") {
    return window.matchMedia("(prefers-color-scheme: dark)").matches
      ? "midnight"
      : "light";
  }
  return ui.theme;
}
const resolvedTheme = ref<"operationflut" | "midnight" | "light">(initialTheme());

// The theme lives on <html> so teleported overlays (modals, toasts) inherit
// the M3 tokens too. A customized accent hue overrides the theme's seed.
const accentStyle = computed(() => {
  if (ui.accentHue === null) return undefined;
  return { "--m3-accent-hue": String(ui.accentHue) } as Record<string, string>;
});

watch(
  resolvedTheme,
  (t) => {
    document.documentElement.dataset.theme = t;
  },
  { immediate: true }
);

// F11: non-blocking update banner (auto-checked at startup). Dismissing just
// hides the banner; the manual check in Settings stays available.
const updateBanner = ref<ReleaseInfo | null>(null);
const updateBannerBusy = ref(false);
const updateBannerProgress = ref(0);
const updateBannerStatus = ref("");

const t = (key: string) => translate(ui.lang, key);
const langLabel = computed(() => (ui.lang === "de" ? "Deutsch" : "English"));
const activeInitial = computed(() =>
  (accounts.active?.displayName || accounts.active?.username || "?").charAt(0).toUpperCase()
);

function resolveTheme() {
  if (ui.theme === "system") {
    resolvedTheme.value = window.matchMedia("(prefers-color-scheme: dark)").matches
      ? "midnight"
      : "light";
  } else {
    resolvedTheme.value = ui.theme;
  }
}

function toggleLang() {
  ui.setLang(ui.lang === "en" ? "de" : "en");
}

async function switchTo(account: { username: string; instanceUrl: string }) {
  try {
    await accounts.switchTo(account.username, account.instanceUrl);
    ui.toast(t("accountSwitched"), "success");
  } catch {
    // error surfaced via accounts.error
  }
}

async function removeActive() {
  const active = accounts.active;
  if (!active) return;
  // F7: never delete an account without explicit confirmation.
  const name = active.displayName || active.username;
  if (!window.confirm(t("deleteAccountConfirm").replace("{name}", name))) return;
  accountMenu.value = false;
  try {
    await accounts.remove(active.username, active.instanceUrl);
    ui.toast(t("accountRemoved"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  }
}

onMounted(() => {
  void accounts.load();
  void accounts.bind();
  void sync.bind();
  resolveTheme();
  window
    .matchMedia("(prefers-color-scheme: dark)")
    .addEventListener("change", resolveTheme);

  // F11: check for updates once at startup. Errors must not interrupt the
  // startup flow — the Settings tab still offers a manual check.
  void (async () => {
    try {
      const info = await api.checkUpdate();
      if (info) updateBanner.value = info;
    } catch {
      // silently ignored; manual check remains in Settings
    }
  })();

  // The server is fixed to FlutCloud, so the CLI --url flag only opens the login.
  void listen<string>("flutlink:cli-open", () => {
    loginMode.value = "login";
    showLogin.value = true;
  });
});

watch(() => ui.theme, resolveTheme);

function browseUserFiles(userId: string) {
  files.setTargetUser(userId);
  tab.value = "files";
}

function openLogin(mode: "login" | "register") {
  loginMode.value = mode;
  showLogin.value = true;
}

async function startUpdateDownload() {
  if (updateBannerBusy.value) return;
  updateBannerBusy.value = true;
  updateBannerProgress.value = 0;
  updateBannerStatus.value = "";
  let unlistenProgress: (() => void) | null = null;
  let unlistenStatus: (() => void) | null = null;
  try {
    unlistenProgress = await listen<UpdateProgress>("update://progress", (e) => {
      updateBannerProgress.value = e.payload.percent;
    });
    unlistenStatus = await listen<string>("update://status", (e) => {
      updateBannerStatus.value = e.payload;
    });
  } catch {
    // progress/status listeners are best-effort
  }
  try {
    await api.downloadAndInstallUpdate();
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    unlistenProgress?.();
    unlistenStatus?.();
    updateBannerBusy.value = false;
  }
}

watch(
  () => accounts.active,
  () => {
    if (accounts.active && !accounts.active.isAdmin) tab.value = "files";
  }
);
</script>

<template>
  <div class="flex h-full flex-col bg-surface text-on-surface" :style="accentStyle">
    <div
      v-if="updateBanner"
      class="flex items-center gap-3 border-b border-primary bg-primary-container/95 px-4 py-2 text-sm shadow-lg"
    >
      <span class="min-w-0 flex-1 truncate text-on-primary-container">
        {{ t("updateNewVersion").replace("{version}", updateBanner.version) }}
      </span>
      <template v-if="updateBannerBusy">
        <div class="h-1.5 w-40 shrink-0 overflow-hidden rounded-full bg-surface-container-high">
          <div
            class="h-full rounded-full bg-primary transition-all"
            :style="{ width: Math.min(updateBannerProgress, 100) + '%' }"
          ></div>
        </div>
        <span v-if="updateBannerStatus" class="max-w-xs truncate text-xs text-on-primary-container">
          {{ updateBannerStatus }}
        </span>
      </template>
      <md-filled-button
        v-else
        class="shrink-0"
        @click="startUpdateDownload"
      >
        {{ t("updateDownloadAndInstall") }}
      </md-filled-button>
      <md-text-button
        class="shrink-0"
        @click="updateBanner = null"
      >
        {{ t("dismiss") }}
      </md-text-button>
    </div>
    <div class="flex min-h-0 flex-1">
      <template v-if="accounts.active">
        <AccountBar @login="openLogin('login')" />

        <main class="flex min-w-0 flex-1 flex-col">
          <header class="flex items-center justify-between gap-3 border-b border-outline-variant px-6 py-3">
            <div class="flex items-center gap-2.5">
              <img src="/flutlink-logo.svg" alt="FlutLink" class="h-7" />
            </div>

            <nav class="flex items-center gap-1">
              <md-filled-button
                v-if="tab === 'files'"
                @click="tab = 'files'"
              >
                {{ t("files") }}
              </md-filled-button>
              <md-outlined-button
                v-else
                @click="tab = 'files'"
              >
                {{ t("files") }}
              </md-outlined-button>

              <md-filled-button
                v-if="tab === 'sync'"
                @click="tab = 'sync'"
              >
                {{ t("sync") }}
              </md-filled-button>
              <md-outlined-button
                v-else
                @click="tab = 'sync'"
              >
                {{ t("sync") }}
              </md-outlined-button>

              <md-filled-button
                v-if="tab === 'admin'"
                :disabled="!accounts.active?.isAdmin"
                :title="accounts.active?.isAdmin ? '' : t('adminLockedText')"
                @click="tab = 'admin'"
              >
                {{ t("admin") }}
                <span v-if="!accounts.active?.isAdmin" class="text-on-surface-variant">
                  <Icon name="lock" :size="14" />
                </span>
              </md-filled-button>
              <md-outlined-button
                v-else
                :disabled="!accounts.active?.isAdmin"
                :title="accounts.active?.isAdmin ? '' : t('adminLockedText')"
                @click="tab = 'admin'"
              >
                {{ t("admin") }}
                <span v-if="!accounts.active?.isAdmin" class="text-on-surface-variant">
                  <Icon name="lock" :size="14" />
                </span>
              </md-outlined-button>
            </nav>

            <div class="flex items-center gap-2">
              <md-outlined-button @click="toggleLang">
                {{ langLabel }}
              </md-outlined-button>
              <md-icon-button
                :title="t('settings')"
                @click="showSettings = true"
              >
                <Icon name="settings" :size="18" />
              </md-icon-button>

              <div class="relative">
                <button
                  class="flex h-9 w-9 items-center justify-center rounded-full bg-primary text-sm font-semibold text-on-primary transition hover:bg-primary-hover"
                  :title="t('signedInAs')"
                  @click="accountMenu = !accountMenu"
                >
                  {{ activeInitial }}
                </button>

                <div v-if="accountMenu" class="absolute right-0 top-full z-40 mt-2 w-72 overflow-hidden rounded-lg border border-outline bg-surface-container-high shadow-m3-3">
                  <div class="border-b border-outline-variant px-4 py-3">
                    <p class="text-xs text-on-surface-variant">{{ t("signedInAs") }}</p>
                    <p class="truncate text-sm font-medium text-on-surface">
                      {{ accounts.active?.displayName || accounts.active?.username }}
                    </p>
                    <p class="truncate text-xs text-on-surface-variant">{{ accounts.active?.instanceUrl }}</p>
                  </div>

                  <div class="p-2">
                    <p class="px-2 pb-1 pt-1 text-[10px] font-semibold uppercase tracking-wide text-outline">
                      {{ t("switchAccount") }}
                    </p>
                    <button
                      v-for="account in accounts.accounts"
                      :key="account.instanceUrl + '/' + account.username"
                      class="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm transition"
                      :class="account.isActive
                        ? 'bg-primary-container text-on-primary-container'
                        : 'text-on-surface-variant hover:bg-surface-container-highest'"
                      @click="switchTo(account); accountMenu = false"
                    >
                      <span class="min-w-0 flex-1 truncate">
                        {{ account.displayName || account.username }}
                      </span>
                      <span
                        v-if="account.isAdmin"
                        class="rounded bg-primary/30 px-1.5 py-0.5 text-[10px] font-semibold uppercase"
                      >
                        {{ t("admin") }}
                      </span>
                    </button>
                  </div>

                  <div class="border-t border-outline-variant p-2">
                    <button
                      class="w-full rounded-md px-2 py-1.5 text-left text-sm text-on-surface-variant transition hover:bg-surface-container-highest"
                      @click="showSettings = true; accountMenu = false"
                    >
                      {{ t("settings") }}
                    </button>
                    <button
                      class="w-full rounded-md px-2 py-1.5 text-left text-sm text-error hover:bg-error-container"
                      @click="removeActive"
                    >
                      {{ t("removeAccount") }}
                    </button>
                  </div>
                </div>

                <div v-if="accountMenu" class="fixed inset-0 z-30" @click="accountMenu = false"></div>
              </div>
            </div>
          </header>

          <div class="min-h-0 flex-1">
            <FileExplorer v-if="tab === 'files'" />
            <SyncPanel v-else-if="tab === 'sync'" />
            <AdminPanel v-else-if="tab === 'admin' && accounts.active?.isAdmin" @browse="browseUserFiles" />
            <div v-else class="m-auto w-full max-w-sm p-8 text-center text-on-surface-variant">
              <p class="text-lg">{{ t("adminLockedTitle") }}</p>
              <p class="text-sm">{{ t("adminLockedText") }}</p>
            </div>
          </div>
        </main>
      </template>

      <template v-else>
        <main class="flex min-w-0 flex-1 flex-col">
          <header class="flex items-center justify-between border-b border-outline-variant px-6 py-3">
            <div class="flex items-center gap-2.5">
              <img src="/flutlink-logo.svg" alt="FlutLink" class="h-7" />
            </div>
            <div class="flex items-center gap-2">
              <md-outlined-button @click="toggleLang">
                {{ langLabel }}
              </md-outlined-button>
              <md-icon-button
                :title="t('settings')"
                @click="showSettings = true"
              >
                <Icon name="settings" :size="18" />
              </md-icon-button>
            </div>
          </header>
          <WelcomeScreen class="min-h-0 flex-1" @login="openLogin('login')" @register="openLogin('register')" />
        </main>
      </template>
    </div>

    <LoginModal :open="showLogin" :initial-mode="loginMode" @close="showLogin = false" @done="showLogin = false" />
    <SettingsModal
      :open="showSettings"
      @close="showSettings = false"
      @login="showSettings = false; openLogin('login')"
    />
    <ToastStack />
  </div>
</template>
