<script setup lang="ts">
import { ref } from "vue";
import AppLogo from "./AppLogo.vue";
import { useAccountsStore } from "../stores/accounts";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import { invokeError } from "../lib/ipc";

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: []; done: [] }>();

const accounts = useAccountsStore();
const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const defaultUrl =
  (import.meta.env.VITE_DEFAULT_NEXTCLOUD_URL as string | undefined) ?? "";
const form = ref({ instanceUrl: defaultUrl, username: "", token: "" });
const showPassword = ref(false);
const submitting = ref(false);
const formError = ref<string | null>(null);

async function submit() {
  if (submitting.value) return;
  submitting.value = true;
  formError.value = null;
  try {
    await accounts.add(form.value);
    ui.toast(t("accountAdded"), "success");
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
          <h2 class="text-lg font-semibold text-white">{{ t("signInTitle") }}</h2>
          <p class="text-xs text-zinc-500">{{ t("signInSubtitle") }}</p>
        </div>

        <form class="mt-5 space-y-3" @submit.prevent="submit">
          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-zinc-500">
              {{ t("serverUrl") }}
            </label>
            <input
              v-model="form.instanceUrl"
              required
              type="url"
              placeholder="https://flutcloud.ddns.net"
              class="w-full rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
            />
          </div>

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
      </div>
    </div>
  </Teleport>
</template>
