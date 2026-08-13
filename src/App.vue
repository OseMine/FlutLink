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
import { useAccountsStore } from "./stores/accounts";
import { useFilesStore } from "./stores/files";
import { useSyncStore } from "./stores/sync";
import { useUiStore } from "./stores/ui";
import { translate } from "./lib/i18n";

const accounts = useAccountsStore();
const files = useFilesStore();
const sync = useSyncStore();
const ui = useUiStore();

const tab = ref<"files" | "admin" | "sync">("files");
const showLogin = ref(false);
const loginMode = ref<"login" | "register">("login");
const showSettings = ref(false);
const accountMenu = ref(false);
const resolvedTheme = ref<"operationflut" | "midnight">("operationflut");

const t = (key: string) => translate(ui.lang, key);
const langLabel = computed(() => (ui.lang === "de" ? "Deutsch" : "English"));
const activeInitial = computed(() =>
  (accounts.active?.displayName || accounts.active?.username || "?").charAt(0).toUpperCase()
);

function resolveTheme() {
  resolvedTheme.value = ui.theme === "system" ? "operationflut" : ui.theme;
}

function toggleLang() {
  ui.setLang(ui.lang === "en" ? "de" : "en");
}

onMounted(() => {
  void accounts.load();
  void sync.bind();
  resolveTheme();
  window
    .matchMedia("(prefers-color-scheme: light)")
    .addEventListener("change", resolveTheme);

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

watch(
  () => accounts.active,
  () => {
    if (accounts.active && !accounts.active.isAdmin) tab.value = "files";
  }
);
</script>

<template>
  <div class="flex h-full bg-zinc-950 text-zinc-100" :data-theme="resolvedTheme">
    <template v-if="accounts.active">
      <AccountBar @login="showLogin = true" />

      <main class="flex min-w-0 flex-1 flex-col">
        <header class="flex items-center justify-between gap-3 border-b border-zinc-800 px-6 py-3">
          <div class="flex items-center gap-2.5">
            <img src="/flutlink-logo.svg" alt="FlutLink" class="h-7" />
          </div>

          <nav class="flex items-center gap-1">
            <button
              class="rounded-md px-4 py-2 text-sm font-medium transition"
              :class="tab === 'files' ? 'bg-zinc-800 text-white' : 'text-zinc-500 hover:text-zinc-300'"
              @click="tab = 'files'"
            >
              {{ t("files") }}
            </button>
            <button
              class="rounded-md px-4 py-2 text-sm font-medium transition"
              :class="tab === 'sync' ? 'bg-zinc-800 text-white' : 'text-zinc-500 hover:text-zinc-300'"
              @click="tab = 'sync'"
            >
              {{ t("sync") }}
            </button>
            <button
              class="rounded-md px-4 py-2 text-sm font-medium transition"
              :class="tab === 'admin' ? 'bg-zinc-800 text-white' : 'text-zinc-500 hover:text-zinc-300'"
              :disabled="!accounts.active?.isAdmin"
              :title="accounts.active?.isAdmin ? '' : t('adminLockedText')"
              @click="tab = 'admin'"
            >
              {{ t("admin") }}
              <span v-if="!accounts.active?.isAdmin" class="text-zinc-600"> 🔒</span>
            </button>
          </nav>

          <div class="flex items-center gap-2">
            <button
              class="rounded-md border border-zinc-700 px-3 py-1.5 text-xs font-medium text-zinc-300 hover:bg-zinc-800"
              @click="toggleLang"
            >
              {{ langLabel }}
            </button>
            <button
              class="rounded-md border border-zinc-700 px-3 py-1.5 text-sm text-zinc-300 hover:bg-zinc-800"
              :title="t('settings')"
              @click="showSettings = true"
            >
              ⚙
            </button>

            <div class="relative">
              <button
                class="flex h-9 w-9 items-center justify-center rounded-full bg-indigo-600 text-sm font-semibold text-white transition hover:bg-indigo-500"
                :title="t('signedInAs')"
                @click="accountMenu = !accountMenu"
              >
                {{ activeInitial }}
              </button>

              <div v-if="accountMenu" class="absolute right-0 top-full z-40 mt-2 w-72 overflow-hidden rounded-lg border border-zinc-700 bg-zinc-900 shadow-2xl">
                <div class="border-b border-zinc-800 px-4 py-3">
                  <p class="text-xs text-zinc-500">{{ t("signedInAs") }}</p>
                  <p class="truncate text-sm font-medium text-white">
                    {{ accounts.active?.displayName || accounts.active?.username }}
                  </p>
                  <p class="truncate text-xs text-zinc-500">{{ accounts.active?.instanceUrl }}</p>
                </div>

                <div class="p-2">
                  <p class="px-2 pb-1 pt-1 text-[10px] font-semibold uppercase tracking-wide text-zinc-600">
                    {{ t("switchAccount") }}
                  </p>
                  <button
                    v-for="account in accounts.accounts"
                    :key="account.instanceUrl + '/' + account.username"
                    class="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm transition"
                    :class="account.isActive
                      ? 'bg-indigo-600/20 text-white'
                      : 'text-zinc-300 hover:bg-zinc-800'"
                    @click="accounts.switchTo(account.username)"
                  >
                    <span class="min-w-0 flex-1 truncate">
                      {{ account.displayName || account.username }}
                    </span>
                    <span
                      v-if="account.isAdmin"
                      class="rounded bg-indigo-600/30 px-1.5 py-0.5 text-[10px] font-semibold uppercase"
                    >
                      {{ t("admin") }}
                    </span>
                  </button>
                </div>

                <div class="border-t border-zinc-800 p-2">
                  <button
                    class="w-full rounded-md px-2 py-1.5 text-left text-sm text-zinc-300 hover:bg-zinc-800"
                    @click="showSettings = true; accountMenu = false"
                  >
                    {{ t("settings") }}
                  </button>
                  <button
                    class="w-full rounded-md px-2 py-1.5 text-left text-sm text-red-300 hover:bg-red-950/40"
                    @click="accounts.remove(accounts.active!.username); accountMenu = false"
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
          <div v-else class="m-auto w-full max-w-sm p-8 text-center text-zinc-500">
            <p class="text-lg">{{ t("adminLockedTitle") }}</p>
            <p class="text-sm">{{ t("adminLockedText") }}</p>
          </div>
        </div>
      </main>
    </template>

    <template v-else>
      <main class="flex min-w-0 flex-1 flex-col">
        <header class="flex items-center justify-between border-b border-zinc-800 px-6 py-3">
          <div class="flex items-center gap-2.5">
            <img src="/flutlink-logo.svg" alt="FlutLink" class="h-7" />
          </div>
          <div class="flex items-center gap-2">
            <button
              class="rounded-md border border-zinc-700 px-3 py-1.5 text-xs font-medium text-zinc-300 hover:bg-zinc-800"
              @click="toggleLang"
            >
              {{ langLabel }}
            </button>
            <button
              class="rounded-md border border-zinc-700 px-3 py-1.5 text-sm text-zinc-300 hover:bg-zinc-800"
              :title="t('settings')"
              @click="showSettings = true"
            >
              ⚙
            </button>
          </div>
        </header>
        <WelcomeScreen class="min-h-0 flex-1" @login="openLogin('login')" @register="openLogin('register')" />
      </main>
    </template>

    <LoginModal :open="showLogin" :initial-mode="loginMode" @close="showLogin = false" @done="showLogin = false" />
    <SettingsModal
      :open="showSettings"
      @close="showSettings = false"
      @login="showSettings = false; showLogin = true"
    />
    <ToastStack />
  </div>
</template>
