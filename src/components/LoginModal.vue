<script setup lang="ts">
import { ref, watch } from "vue";
import AppLogo from "./AppLogo.vue";
import { useAccountsStore } from "../stores/accounts";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import { api, invokeError } from "../lib/ipc";

const props = defineProps<{
  open: boolean;
  initialMode?: "login" | "register";
}>();
const emit = defineEmits<{ close: []; done: [] }>();

const accounts = useAccountsStore();
const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

// FlutLink is a dedicated FlutCloud client — the server is fixed and never
// edited manually. The URL is read from the backend (which loads it from the
// local `.env`), so it never appears in the client code.
const serverUrl = ref("");
const serverUrlError = ref<string | null>(null);

watch(
  () => props.open,
  async (open) => {
    if (!open || serverUrl.value) return;
    try {
      serverUrl.value = await api.getFlutcloudUrl();
    } catch (e) {
      serverUrlError.value = invokeError(e).message;
    }
  },
  { immediate: true }
);

const mode = ref<"login" | "register">("login");
watch(
  () => props.initialMode,
  (m) => {
    if (m) mode.value = m;
  }
);
// F8: also reset the mode every time the dialog opens. `initialMode` alone is
// not enough — reopening with the same value would leave the last tab active
// (e.g. "Register" after a previous registration).
watch(
  () => props.open,
  (open) => {
    if (open) mode.value = props.initialMode ?? "login";
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
  if (!serverUrl.value) {
    formError.value = serverUrlError.value ?? t("serverNotConfigured");
    return;
  }
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
  if (!serverUrl.value) {
    formError.value = serverUrlError.value ?? t("serverNotConfigured");
    return;
  }
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
      <div class="w-full max-w-sm rounded-xl border border-outline bg-surface-container p-6 shadow-m3-3">
        <div class="flex flex-col items-center gap-2 text-center">
          <AppLogo class="h-10 w-10" />
          <h2 class="text-lg font-semibold text-on-surface">
            {{ mode === "login" ? t("signInTitle") : t("registerTitle") }}
          </h2>
          <p class="text-xs text-on-surface-variant">
            {{ mode === "login" ? t("signInSubtitle") : t("registerSubtitle") }}
          </p>
        </div>

        <div class="mt-4 grid grid-cols-2 rounded-md bg-surface-container-high p-1">
          <button
            class="rounded px-3 py-1.5 text-sm font-medium transition"
            :class="mode === 'login' ? 'bg-primary text-on-primary' : 'text-on-surface-variant hover:text-on-surface'"
            @click="mode = 'login'"
          >
            {{ t("signInTab") }}
          </button>
          <button
            class="rounded px-3 py-1.5 text-sm font-medium transition"
            :class="mode === 'register' ? 'bg-primary text-on-primary' : 'text-on-surface-variant hover:text-on-surface'"
            @click="mode = 'register'"
          >
            {{ t("registerTab") }}
          </button>
        </div>

        <div class="mt-3 rounded-md bg-surface-container-high/60 px-3 py-2 text-xs text-on-surface-variant">
          {{ t("serverAutoNote") }}:
          <span class="text-on-surface">{{ serverUrl || "…" }}</span>
        </div>
        <p v-if="serverUrlError" class="mt-1 text-xs text-error">{{ serverUrlError }}</p>

        <form
          v-if="mode === 'login'"
          class="mt-4 space-y-3"
          @submit.prevent="submit"
        >
          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-on-surface-variant">
              {{ t("username") }}
            </label>
            <input
              v-model="form.username"
              required
              autocomplete="off"
              class="w-full rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
            />
          </div>

          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-on-surface-variant">
              {{ t("password") }}
            </label>
            <div class="flex gap-2">
              <input
                v-model="form.token"
                required
                :type="showPassword ? 'text' : 'password'"
                :placeholder="t('tokenPlaceholder')"
                autocomplete="off"
                class="flex-1 rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
              />
              <button
                type="button"
                class="rounded-md bg-surface-container-high px-3 py-2 text-sm text-on-surface-variant hover:bg-surface-container-highest"
                @click="showPassword = !showPassword"
              >
                {{ showPassword ? t("hide") : t("show") }}
              </button>
            </div>
          </div>

          <p class="text-xs leading-relaxed text-outline">{{ t("authNote") }}</p>

          <div v-if="formError" class="rounded-md border border-error bg-error-container px-3 py-2 text-xs text-on-error-container">
            {{ formError }}
          </div>

          <div class="flex gap-2 pt-1">
            <button
              type="button"
              class="rounded-md bg-surface-container-high px-4 py-2 text-sm text-on-surface-variant hover:bg-surface-container-highest"
              @click="emit('close')"
            >
              {{ t("cancel") }}
            </button>
            <button
              type="submit"
              :disabled="submitting"
              class="flex-1 rounded-md bg-primary px-4 py-2 text-sm font-medium text-on-primary transition hover:bg-primary-hover disabled:opacity-50"
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
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-on-surface-variant">
              {{ t("username") }}
            </label>
            <input
              v-model="form.username"
              required
              autocomplete="off"
              class="w-full rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
            />
          </div>

          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-on-surface-variant">
              {{ t("password") }}
            </label>
            <div class="flex gap-2">
              <input
                v-model="form.token"
                required
                :type="showPassword ? 'text' : 'password'"
                autocomplete="off"
                class="flex-1 rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
              />
              <button
                type="button"
                class="rounded-md bg-surface-container-high px-3 py-2 text-sm text-on-surface-variant hover:bg-surface-container-highest"
                @click="showPassword = !showPassword"
              >
                {{ showPassword ? t("hide") : t("show") }}
              </button>
            </div>
          </div>

          <div>
            <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-on-surface-variant">
              {{ t("displayNameOptional") }}
            </label>
            <input
              v-model="form.displayName"
              autocomplete="off"
              class="w-full rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
            />
          </div>

          <div class="border-t border-outline-variant pt-3">
            <p class="mb-2 text-xs font-medium uppercase tracking-wide text-on-surface-variant">
              {{ t("adminSection") }}
            </p>
            <div class="grid grid-cols-2 gap-2">
              <input
                v-model="form.adminUsername"
                required
                :placeholder="t('adminUsername')"
                autocomplete="off"
                class="w-full rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
              />
              <div class="flex gap-2">
                <input
                  v-model="form.adminPassword"
                  required
                  :type="showAdminPassword ? 'text' : 'password'"
                  :placeholder="t('adminPassword')"
                  autocomplete="off"
                  class="flex-1 min-w-0 rounded-md border border-outline bg-surface-container-high px-3 py-2 text-sm text-on-surface placeholder:text-on-surface-variant focus:border-primary"
                />
                <button
                  type="button"
                  class="shrink-0 rounded-md bg-surface-container-high px-2.5 py-2 text-sm text-on-surface-variant hover:bg-surface-container-highest"
                  @click="showAdminPassword = !showAdminPassword"
                >
                  {{ showAdminPassword ? t("hide") : t("show") }}
                </button>
              </div>
            </div>
            <p class="mt-2 text-xs leading-relaxed text-outline">{{ t("adminNote") }}</p>
          </div>

          <div v-if="formError" class="rounded-md border border-error bg-error-container px-3 py-2 text-xs text-on-error-container">
            {{ formError }}
          </div>

          <div class="flex gap-2 pt-1">
            <button
              type="button"
              class="rounded-md bg-surface-container-high px-4 py-2 text-sm text-on-surface-variant hover:bg-surface-container-highest"
              @click="emit('close')"
            >
              {{ t("cancel") }}
            </button>
            <button
              type="submit"
              :disabled="submitting"
              class="flex-1 rounded-md bg-primary px-4 py-2 text-sm font-medium text-on-primary transition hover:bg-primary-hover disabled:opacity-50"
            >
              {{ submitting ? t("registering") : t("register") }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>
