<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, onUnmounted, ref, watch } from "vue";
import { listen } from "@tauri-apps/api/event";
import AccountBar from "./components/AccountBar.vue";
import FileExplorer from "./components/FileExplorer.vue";
import WelcomeScreen from "./components/WelcomeScreen.vue";
import ToastStack from "./components/ToastStack.vue";
import Icon from "./components/Icon.vue";
// L12-N6: panels and modals that are not visible on first paint are
// code-split via dynamic imports so the initial chunk stays small.
const AdminPanel = defineAsyncComponent(() => import("./components/AdminPanel.vue"));
const SyncPanel = defineAsyncComponent(() => import("./components/SyncPanel.vue"));
const LoginModal = defineAsyncComponent(() => import("./components/LoginModal.vue"));
const SettingsModal = defineAsyncComponent(() => import("./components/SettingsModal.vue"));
const GuestBrowser = defineAsyncComponent(() => import("./components/GuestBrowser.vue"));
import { useAccountsStore } from "./stores/accounts";
import { useFilesStore } from "./stores/files";
import { useSyncStore } from "./stores/sync";
import { useUiStore } from "./stores/ui";
import { translate, updateStatusText } from "./lib/i18n";
import { installEscapeHandler, registerEscapeCloser } from "./lib/escape";
import { api, invokeError, type ReleaseInfo, type UpdateProgress, type UpdateStatus } from "./lib/ipc";

type Tab = "files" | "sync" | "admin" | "guest";

const accounts = useAccountsStore();
const files = useFilesStore();
const sync = useSyncStore();
const ui = useUiStore();

const tab = ref<Tab>("files");
const showLogin = ref(false);
const loginMode = ref<"login" | "register">("login");
const showSettings = ref(false);
const accountMenu = ref(false);
// U-R8-8: resolve the initial theme synchronously (from the persisted theme +
// the OS preference) so `[data-theme]` is correct before first paint — no
// flash of the wrong theme for "System Default" users.
function initialTheme(): "midnight" | "light" {
  if (ui.theme === "system") {
    return window.matchMedia("(prefers-color-scheme: dark)").matches
      ? "midnight"
      : "light";
  }
  return ui.theme;
}
const resolvedTheme = ref<"midnight" | "light">(initialTheme());

// The theme lives on <html> so teleported overlays (modals, toasts) inherit
// the tokens too.

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

// #367: one declarative tab list instead of paired filled/outlined buttons.
// Admin stays visible but locked without admin rights; the Guests tab is
// only offered to admins (#372).
interface NavItem {
  id: Tab;
  label: string;
  locked?: boolean;
  title?: string;
}
const navItems = computed<NavItem[]>(() => [
  { id: "files", label: t("files") },
  { id: "sync", label: t("sync") },
  {
    id: "admin",
    label: t("admin"),
    locked: !accounts.active?.isAdmin,
    title: accounts.active?.isAdmin ? undefined : t("adminLockedText"),
  },
  ...(accounts.active?.isAdmin ? [{ id: "guest" as const, label: t("guestTabTitle") }] : []),
]);

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
  // L19-N1: one global listener closes the topmost open menu/modal on Escape.
  installEscapeHandler();
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
    unlistenStatus = await listen<UpdateStatus>("update://status", (e) => {
      // L19-F7: localized status texts via the same code→key mapping as the
      // SettingsModal; unknown codes fall back to the raw backend code.
      const text = updateStatusText(ui.lang, e.payload.code, e.payload.assetName);
      updateBannerStatus.value =
        text ||
        `${e.payload.code}${e.payload.assetName ? " — " + e.payload.assetName : ""}`;
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
    if (!accounts.active) return;
    // Non-admins never keep privileged tabs selected across account switches.
    if (!accounts.active.isAdmin && (tab.value === "admin" || tab.value === "guest")) {
      tab.value = "files";
    }
    // Signing in ends standalone guest browsing — guest administration for
    // admins happens through the dedicated tab instead (#372).
    if (ui.guestMode) ui.setGuestMode(false);
  }
);

// L19-N1/#365: Escape closes the account menu (topmost overlay wins).
let removeMenuEscape: (() => void) | null = null;
watch(accountMenu, (open) => {
  if (open && !removeMenuEscape) {
    removeMenuEscape = registerEscapeCloser(() => {
      accountMenu.value = false;
    });
  } else if (!open && removeMenuEscape) {
    removeMenuEscape();
    removeMenuEscape = null;
  }
});
onUnmounted(() => removeMenuEscape?.());

function startGuestMode() {
  ui.setGuestMode(true);
}

function startGuestModeOff() {
  ui.setGuestMode(false);
}
</script>

<template>
  <div class="flex h-full flex-col bg-canvas text-fg">
    <div
      v-if="updateBanner"
      class="flex items-center gap-3 border-b border-line-strong bg-card px-4 py-2 text-sm"
    >
      <span class="min-w-0 flex-1 truncate">
        {{ t("updateNewVersion").replace("{version}", updateBanner.version) }}
      </span>
      <template v-if="updateBannerBusy">
        <div class="progress-track w-40 shrink-0">
          <div class="progress-fill" :style="{ width: Math.min(updateBannerProgress, 100) + '%' }"></div>
        </div>
        <span v-if="updateBannerStatus" class="max-w-xs truncate text-xs text-muted">
          {{ updateBannerStatus }}
        </span>
      </template>
      <button
        v-else
        type="button"
        class="btn btn-primary shrink-0"
        @click="startUpdateDownload"
      >
        {{ t("updateDownloadAndInstall") }}
      </button>
      <button type="button" class="btn btn-ghost shrink-0" @click="updateBanner = null">
        {{ t("dismiss") }}
      </button>
    </div>
    <div class="flex min-h-0 flex-1">
      <template v-if="accounts.active">
        <AccountBar @login="openLogin('login')" />

        <main class="flex min-w-0 flex-1 flex-col">
          <header class="flex h-14 shrink-0 items-center justify-between gap-3 border-b border-line px-6">
            <div class="flex items-center gap-2.5">
              <img src="/flutlink-logo.svg" alt="FlutLink" class="h-7" />
            </div>

            <!-- #367: real tab list with an underline indicator -->
            <nav role="tablist" class="-mb-px flex h-full items-stretch gap-1">
              <button
                v-for="item in navItems"
                :key="item.id"
                type="button"
                role="tab"
                class="tab"
                :aria-selected="tab === item.id"
                :disabled="item.locked"
                :title="item.title ?? ''"
                @click="tab = item.id"
              >
                {{ item.label }}
                <Icon v-if="item.locked" name="lock" :size="13" />
              </button>
            </nav>

            <div class="flex items-center gap-1.5">
              <button type="button" class="btn btn-ghost" @click="toggleLang">
                {{ langLabel }}
              </button>
              <button
                type="button"
                class="icon-btn"
                :title="t('settings')"
                :aria-label="t('settings')"
                @click="showSettings = true"
              >
                <Icon name="settings" :size="18" />
              </button>

              <div class="relative">
                <button
                  type="button"
                  class="flex h-9 w-9 items-center justify-center rounded-full bg-primary text-[13px] font-semibold text-on-primary transition hover:bg-primary-hover"
                  :title="t('signedInAs')"
                  @click="accountMenu = !accountMenu"
                >
                  {{ activeInitial }}
                </button>

                <div v-if="accountMenu" class="menu absolute right-0 top-full z-40 mt-2 w-72 overflow-hidden py-1">
                  <div class="border-b border-line px-4 py-3">
                    <p class="text-xs text-muted">{{ t("signedInAs") }}</p>
                    <p class="truncate text-sm font-medium">
                      {{ accounts.active?.displayName || accounts.active?.username }}
                    </p>
                    <p class="truncate text-xs text-muted">{{ accounts.active?.instanceUrl }}</p>
                  </div>

                  <div class="p-1.5">
                    <p class="px-2 pb-1 pt-1 text-[10px] font-semibold uppercase tracking-wide text-muted">
                      {{ t("switchAccount") }}
                    </p>
                    <button
                      v-for="account in accounts.accounts"
                      :key="account.instanceUrl + '/' + account.username"
                      type="button"
                      class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm transition hover:bg-card-hover"
                      :class="account.isActive ? 'bg-primary/10' : ''"
                      @click="switchTo(account); accountMenu = false"
                    >
                      <span
                        class="h-1.5 w-1.5 shrink-0 rounded-full"
                        :class="account.isActive ? 'bg-primary' : 'bg-transparent'"
                      ></span>
                      <span class="min-w-0 flex-1 truncate">
                        {{ account.displayName || account.username }}
                      </span>
                      <span v-if="account.isAdmin" class="badge">
                        <span class="badge-dot bg-primary"></span>
                        {{ t("admin") }}
                      </span>
                    </button>
                  </div>

                  <div class="border-t border-line p-1.5">
                    <button
                      type="button"
                      class="w-full rounded-sm px-2 py-1.5 text-left text-sm text-muted transition hover:bg-card-hover hover:text-fg"
                      @click="showSettings = true; accountMenu = false"
                    >
                      {{ t("settings") }}
                    </button>
                    <button
                      type="button"
                      class="w-full rounded-sm px-2 py-1.5 text-left text-sm text-error transition hover:bg-error/10"
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
            <!-- #372: guest administration reachable while signed in -->
            <GuestBrowser
              v-else-if="tab === 'guest' && accounts.active?.isAdmin"
              embedded
              class="min-h-0 flex-1"
              @exit="tab = 'files'"
            />
            <div v-else class="m-auto w-full max-w-sm p-8 text-center text-muted">
              <p class="text-lg">{{ t("adminLockedTitle") }}</p>
              <p class="text-sm">{{ t("adminLockedText") }}</p>
            </div>
          </div>
        </main>
      </template>

      <template v-else>
        <main class="flex min-w-0 flex-1 flex-col">
          <header class="flex h-14 shrink-0 items-center justify-between border-b border-line px-6">
            <div class="flex items-center gap-2.5">
              <img src="/flutlink-logo.svg" alt="FlutLink" class="h-7" />
            </div>
            <div class="flex items-center gap-1.5">
              <button type="button" class="btn btn-ghost" @click="toggleLang">
                {{ langLabel }}
              </button>
              <button
                type="button"
                class="icon-btn"
                :title="t('settings')"
                :aria-label="t('settings')"
                @click="showSettings = true"
              >
                <Icon name="settings" :size="18" />
              </button>
            </div>
          </header>
          <GuestBrowser
            v-if="ui.guestMode"
            class="min-h-0 flex-1"
            @exit="startGuestModeOff"
          />
          <WelcomeScreen
            v-else
            class="min-h-0 flex-1"
            @login="openLogin('login')"
            @register="openLogin('register')"
            @guest="startGuestMode"
          />
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
