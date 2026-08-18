<script setup lang="ts">
import { ref, watch } from "vue";
import AppLogo from "./AppLogo.vue";
import { useAccountsStore } from "../stores/accounts";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import { api, invokeError } from "../lib/ipc";
import "@material/web/button/filled-button.js";
import "@material/web/button/outlined-button.js";
import "@material/web/button/text-button.js";
import "@material/web/textfield/outlined-text-field.js";
import "@material/web/tabs/tabs.js";
import "@material/web/tabs/primary-tab.js";
import "@material/web/divider/divider.js";

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

// U-R8-11: never leave the previous credentials (including the app token) in
// the form — on shared devices reopening the dialog must start empty.
function resetForm() {
  form.value = {
    username: "",
    token: "",
    displayName: "",
    adminUsername: "",
    adminPassword: "",
  };
  showPassword.value = false;
  showAdminPassword.value = false;
  formError.value = null;
}

function close() {
  resetForm();
  emit("close");
}

function done() {
  resetForm();
  emit("done");
}

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
    done();
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
    done();
  } catch (e) {
    formError.value = invokeError(e).message;
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
    <div
      v-if="props.open"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      @click.self="close"
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

        <md-tabs :active-tab-index="mode === 'login' ? 0 : 1">
          <md-primary-tab @click="mode = 'login'">{{ t("signInTab") }}</md-primary-tab>
          <md-primary-tab @click="mode = 'register'">{{ t("registerTab") }}</md-primary-tab>
        </md-tabs>

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
          <md-outlined-text-field
            :label="t('username')"
            :value="form.username"
            required
            autocomplete="off"
            @input="form.username = ($event.target as HTMLInputElement).value"
          ></md-outlined-text-field>

          <md-outlined-text-field
            type="password"
            :label="t('password')"
            :value="form.token"
            required
            autocomplete="off"
            @input="form.token = ($event.target as HTMLInputElement).value"
          ></md-outlined-text-field>

          <md-text-button @click="showPassword = !showPassword">
            {{ showPassword ? t("hide") : t("show") }}
          </md-text-button>

          <p class="text-xs leading-relaxed text-outline">{{ t("authNote") }}</p>

          <div v-if="formError" class="rounded-md border border-error bg-error-container px-3 py-2 text-xs text-on-error-container">
            {{ formError }}
          </div>

          <div class="flex gap-2 pt-1">
            <md-outlined-button class="mt-4 w-full" @click="close">
              {{ t("cancel") }}
            </md-outlined-button>
            <md-filled-button :disabled="submitting" class="mt-4 w-full" @click="submit">
              {{ submitting ? t("connecting") : t("connect") }}
            </md-filled-button>
          </div>
        </form>

        <form
          v-else
          class="mt-4 space-y-3"
          @submit.prevent="submitRegister"
        >
          <md-outlined-text-field
            :label="t('username')"
            :value="form.username"
            required
            autocomplete="off"
            @input="form.username = ($event.target as HTMLInputElement).value"
          ></md-outlined-text-field>

          <md-outlined-text-field
            type="password"
            :label="t('password')"
            :value="form.token"
            required
            autocomplete="off"
            @input="form.token = ($event.target as HTMLInputElement).value"
          ></md-outlined-text-field>

          <md-text-button @click="showPassword = !showPassword">
            {{ showPassword ? t("hide") : t("show") }}
          </md-text-button>

          <md-outlined-text-field
            :label="t('displayNameOptional')"
            :value="form.displayName"
            autocomplete="off"
            @input="form.displayName = ($event.target as HTMLInputElement).value"
          ></md-outlined-text-field>

          <md-divider class="my-2"></md-divider>

          <p class="mb-2 text-xs font-medium uppercase tracking-wide text-on-surface-variant">
            {{ t("adminSection") }}
          </p>
          <div class="grid grid-cols-2 gap-2">
            <md-outlined-text-field
              :label="t('adminUsername')"
              :value="form.adminUsername"
              required
              autocomplete="off"
              @input="form.adminUsername = ($event.target as HTMLInputElement).value"
            ></md-outlined-text-field>
            <div>
              <md-outlined-text-field
                type="password"
                :label="t('adminPassword')"
                :value="form.adminPassword"
                required
                autocomplete="off"
                @input="form.adminPassword = ($event.target as HTMLInputElement).value"
              ></md-outlined-text-field>
              <md-text-button @click="showAdminPassword = !showAdminPassword">
                {{ showAdminPassword ? t("hide") : t("show") }}
              </md-text-button>
            </div>
          </div>
          <p class="mt-2 text-xs leading-relaxed text-outline">{{ t("adminNote") }}</p>

          <div v-if="formError" class="rounded-md border border-error bg-error-container px-3 py-2 text-xs text-on-error-container">
            {{ formError }}
          </div>

          <div class="flex gap-2 pt-1">
            <md-outlined-button class="mt-4 w-full" @click="close">
              {{ t("cancel") }}
            </md-outlined-button>
            <md-filled-button :disabled="submitting" class="mt-4 w-full" @click="submitRegister">
              {{ submitting ? t("registering") : t("register") }}
            </md-filled-button>
          </div>
        </form>
      </div>
    </div>
    </Transition>
  </Teleport>
</template>
