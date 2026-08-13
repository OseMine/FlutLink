<script setup lang="ts">
import { computed, ref, watch } from "vue";
import AppLogo from "./AppLogo.vue";
import { useAccountsStore } from "../stores/accounts";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import { invokeError } from "../lib/ipc";

const props = defineProps<{
  open: boolean;
  initialUrl?: string;
  initialMode?: "login" | "register";
}>();
const emit = defineEmits<{ close: []; done: [] }>();

const accounts = useAccountsStore();
const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const defaultUrl =
  (import.meta.env.VITE_DEFAULT_NEXTCLOUD_URL as string | undefined) ?? "";

// The server URL is set automatically and never edited manually.
const serverUrl = computed(
  () => props.initialUrl?.trim() || defaultUrl || "https://flutcloud.ddns.net"
);

const mode = ref<"login" | "register">("login");
watch(
  () => props.initialMode,
  (m) => {
    if (m) mode.value = m;
  }
);

const form = ref({
  username: "",
  token: "",
  displayName: "",
  adminUsername: "",
  adminPassword: "",
});
const showPassword = ref(false);
const showAdminPassword = ref(false);
const submitting = ref(false);
const formError = ref<string | null>(null);

async function submit() {
  if (submitting.value) return;
  submitting.value = true;
  formError.value = null;
  try {
    await accounts.add({
      instanceUrl: serverUrl.value,
      username: form.value.username,
      token: form.value.token,
    });
    ui.toast(t("accountAdded"), "success");
    emit("done");
  } catch (e) {
    formError.value = invokeError(e).message;
  } finally {
    submitting.value = false;
  }
}

async function submitRegister() {
  if (submitting.value) return;
  if (!form.value.username.trim() || !form.value.token) {
    formError.value = t("requiredFields");
    return;
  }
  submitting.value = true;
  formError.value = null;
  try {
    await accounts.register({
      instanceUrl: serverUrl.value,
      username: form.value.username.trim(),
      password: form.value.token,
      displayName: form.value.displayName.trim() || undefined,
      adminUsername: form.value.adminUsername.trim(),
      adminPassword: form.value.adminPassword,
    });
    ui.toast(t("accountRegistered"), "success");
    emit("done");
  } catch (e) {
    formError.value = invokeError(e).message;
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="props.open"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      @click.self="emit('close')"
    >
      <div class="w-full max-w-sm rounded-xl border border-zinc-700 bg-zinc-900 p-6 shadow-2xl">
        <div class="flex flex-col items-center gap-2 text-center">
          <AppLogo class="h-10 w-10" />
          <h2 class="text-lg font-semibold text-white">
            {{ mode === "login" ? t("signInTitle") : t("registerTitle") }}
          </h2>
          <p class="text-xs text-zinc-500">
            {{ mode === "login" ? t("signInSubtitle") : t("registerSubtitle") }}
          </p>
        </div>

        <div class="mt-4 grid grid-cols-2 rounded-md bg-zinc-800 p-1">
          <button
            class="rounded px-3 py-1.5 text-sm font-medium transition"
            :class="mode === 'login' ? 'bg-indigo-600 text-white' : 'text-zinc-400 hover:text-white'"
            @click="mode = 'login'"
          >
            {{ t("signInTab") }}
          </button>
          <button
            class="rounded px-3 py-1.5 text-sm font-medium transition"
            :class="mode === 'register' ? 'bg-indigo-600 text-white' : 'text-zinc-400 hover:text-white'"
            @click="mode = 'register'"
          >
            {{ t("registerTab") }}
          </button>
        </div>

        <div class="mt-3 rounded-md bg-zinc-800/60 px-3 py-2 text-xs text-zinc-400">
          {{ t("serverAutoNote") }}: <span class="text-zinc-200">{{ serverUrl }}</span>
        </div>

        <form
          v-if="mode === 'login'"
          class="mt-4 space-y-3"
          @submit.prevent="submit"
        >
          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-zinc-500">
              {{ t("username") }}
            </label>
            <input
              v-model="form.username"
              required
              autocomplete="off"
              class="w-full rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
            />
          </div>

          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-zinc-500">
              {{ t("password") }}
            </label>
            <div class="flex gap-2">
              <input
                v-model="form.token"
                required
                :type="showPassword ? 'text' : 'password'"
                :placeholder="t('tokenPlaceholder')"
                autocomplete="off"
                class="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
              />
              <button
                type="button"
                class="rounded-md bg-zinc-800 px-3 py-2 text-sm text-zinc-300 hover:bg-zinc-700"
                @click="showPassword = !showPassword"
              >
                {{ showPassword ? "Hide" : "Show" }}
              </button>
            </div>
          </div>

          <p class="text-xs leading-relaxed text-zinc-600">{{ t("authNote") }}</p>

          <div v-if="formError" class="rounded-md border border-red-800 bg-red-950/50 px-3 py-2 text-xs text-red-300">
            {{ formError }}
          </div>

          <div class="flex gap-2 pt-1">
            <button
              type="button"
              class="rounded-md bg-zinc-800 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-700"
              @click="emit('close')"
            >
              {{ t("cancel") }}
            </button>
            <button
              type="submit"
              :disabled="submitting"
              class="flex-1 rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-500 disabled:opacity-50"
            >
              {{ submitting ? t("connecting") : t("connect") }}
            </button>
          </div>
        </form>

        <form
          v-else
          class="mt-4 space-y-3"
          @submit.prevent="submitRegister"
        >
          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-zinc-500">
              {{ t("username") }}
            </label>
            <input
              v-model="form.username"
              required
              autocomplete="off"
              class="w-full rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
            />
          </div>

          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-zinc-500">
              {{ t("password") }}
            </label>
            <div class="flex gap-2">
              <input
                v-model="form.token"
                required
                :type="showPassword ? 'text' : 'password'"
                autocomplete="off"
                class="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
              />
              <button
                type="button"
                class="rounded-md bg-zinc-800 px-3 py-2 text-sm text-zinc-300 hover:bg-zinc-700"
                @click="showPassword = !showPassword"
              >
                {{ showPassword ? "Hide" : "Show" }}
              </button>
            </div>
          </div>

          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-zinc-500">
              {{ t("displayNameOptional") }}
            </label>
            <input
              v-model="form.displayName"
              autocomplete="off"
              class="w-full rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
            />
          </div>

          <div class="border-t border-zinc-800 pt-3">
            <p class="mb-2 text-xs font-medium uppercase tracking-wide text-zinc-500">
              {{ t("adminSection") }}
            </p>
            <div class="grid grid-cols-2 gap-2">
              <input
                v-model="form.adminUsername"
                required
                :placeholder="t('adminUsername')"
                autocomplete="off"
                class="w-full rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
              />
              <div class="flex gap-2">
                <input
                  v-model="form.adminPassword"
                  required
                  :type="showAdminPassword ? 'text' : 'password'"
                  :placeholder="t('adminPassword')"
                  autocomplete="off"
                  class="flex-1 min-w-0 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
                />
                <button
                  type="button"
                  class="shrink-0 rounded-md bg-zinc-800 px-2.5 py-2 text-sm text-zinc-300 hover:bg-zinc-700"
                  @click="showAdminPassword = !showAdminPassword"
                >
                  {{ showAdminPassword ? "Hide" : "Show" }}
                </button>
              </div>
            </div>
            <p class="mt-2 text-xs leading-relaxed text-zinc-600">{{ t("adminNote") }}</p>
          </div>

          <div v-if="formError" class="rounded-md border border-red-800 bg-red-950/50 px-3 py-2 text-xs text-red-300">
            {{ formError }}
          </div>

          <div class="flex gap-2 pt-1">
            <button
              type="button"
              class="rounded-md bg-zinc-800 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-700"
              @click="emit('close')"
            >
              {{ t("cancel") }}
            </button>
            <button
              type="submit"
              :disabled="submitting"
              class="flex-1 rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-500 disabled:opacity-50"
            >
              {{ submitting ? t("registering") : t("register") }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>
