<script setup lang="ts">
import { onUnmounted, ref, watch } from "vue";
import AppLogo from "./AppLogo.vue";
import { useAccountsStore } from "../stores/accounts";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import { api, invokeError } from "../lib/ipc";
import { registerEscapeCloser } from "../lib/escape";

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

// L19-N1: Escape closes the modal while it is open (via the same close path
// as the cancel button, so the form is reset too).
let removeEscapeCloser: (() => void) | null = null;
watch(
  () => props.open,
  (open) => {
    if (open && !removeEscapeCloser) {
      removeEscapeCloser = registerEscapeCloser(close);
    } else if (!open && removeEscapeCloser) {
      removeEscapeCloser();
      removeEscapeCloser = null;
    }
  }
);
onUnmounted(() => removeEscapeCloser?.());

async function submit() {
  if (submitting.value) return;
  if (!serverUrl.value) {
    formError.value = serverUrlError.value ?? t("serverNotConfigured");
    return;
  }
  // L17-N3: the login tab validates required fields client-side, exactly like
  // the register tab — empty fields show the localized hint instead of an OCS
  // server error.
  if (!form.value.username.trim() || !form.value.token) {
    formError.value = t("requiredFields");
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
      class="fixed inset-0 z-50 flex items-center justify-center bg-scrim/60 p-4 backdrop-blur-sm"
      @click.self="close"
    >
      <div class="modal-surface w-full max-w-sm p-6">
        <div class="flex flex-col items-center gap-2 text-center">
          <AppLogo class="h-10 w-10" />
          <h2 class="text-lg font-semibold">
            {{ mode === "login" ? t("signInTitle") : t("registerTitle") }}
          </h2>
          <p class="text-xs text-muted">
            {{ mode === "login" ? t("signInSubtitle") : t("registerSubtitle") }}
          </p>
        </div>

        <!-- #367: native tab list — marked tab and shown form can no longer
             drift apart through keyboard navigation -->
        <div role="tablist" class="mt-4 flex items-stretch border-b border-line">
          <button
            type="button"
            role="tab"
            class="tab"
            :aria-selected="mode === 'login'"
            @click="mode = 'login'"
          >
            {{ t("signInTab") }}
          </button>
          <button
            type="button"
            role="tab"
            class="tab"
            :aria-selected="mode === 'register'"
            @click="mode = 'register'"
          >
            {{ t("registerTab") }}
          </button>
        </div>

        <div class="card mt-3 px-3 py-2 text-xs text-muted">
          {{ t("serverAutoNote") }}:
          <span class="text-fg">{{ serverUrl || "…" }}</span>
        </div>
        <p v-if="serverUrlError" class="mt-1 text-xs text-error">{{ serverUrlError }}</p>

        <form
          v-if="mode === 'login'"
          class="mt-4 space-y-3"
          @submit.prevent="submit"
        >
          <label class="block">
            <span class="mb-1 block text-xs font-medium text-muted">{{ t("username") }}</span>
            <input
              v-model="form.username"
              type="text"
              autocomplete="off"
              class="input"
            />
          </label>

          <label class="block">
            <span class="mb-1 block text-xs font-medium text-muted">{{ t("password") }}</span>
            <!-- #362: dynamic type binding so the reveal button really works -->
            <div class="relative">
              <input
                v-model="form.token"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="off"
                class="input pr-16"
              />
              <button
                type="button"
                class="btn btn-ghost absolute right-1 top-1/2 h-7 -translate-y-1/2 px-2 text-xs"
                @click="showPassword = !showPassword"
              >
                {{ showPassword ? t("hide") : t("show") }}
              </button>
            </div>
          </label>

          <p class="text-xs leading-relaxed text-muted/80">{{ t("authNote") }}</p>

          <div v-if="formError" class="rounded-md border border-error/40 bg-error/10 px-3 py-2 text-xs text-error">
            {{ formError }}
          </div>

          <div class="flex gap-2 pt-1">
            <!-- #364: explicit type="button" — must never act as a submitter -->
            <button type="button" class="btn btn-outline flex-1" @click="close">
              {{ t("cancel") }}
            </button>
            <button type="submit" :disabled="submitting" class="btn btn-primary flex-1">
              {{ submitting ? t("connecting") : t("connect") }}
            </button>
          </div>
        </form>

        <form
          v-else
          class="mt-4 space-y-3"
          @submit.prevent="submitRegister"
        >
          <label class="block">
            <span class="mb-1 block text-xs font-medium text-muted">{{ t("username") }}</span>
            <input v-model="form.username" type="text" autocomplete="off" class="input" />
          </label>

          <label class="block">
            <span class="mb-1 block text-xs font-medium text-muted">{{ t("password") }}</span>
            <div class="relative">
              <input
                v-model="form.token"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="off"
                class="input pr-16"
              />
              <button
                type="button"
                class="btn btn-ghost absolute right-1 top-1/2 h-7 -translate-y-1/2 px-2 text-xs"
                @click="showPassword = !showPassword"
              >
                {{ showPassword ? t("hide") : t("show") }}
              </button>
            </div>
          </label>

          <label class="block">
            <span class="mb-1 block text-xs font-medium text-muted">{{ t("displayNameOptional") }}</span>
            <input v-model="form.displayName" type="text" autocomplete="off" class="input" />
          </label>

          <div class="border-t border-line pt-3">
            <p class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-muted">
              {{ t("adminSection") }}
            </p>
            <div class="grid grid-cols-2 gap-2">
              <label class="block">
                <span class="mb-1 block text-xs font-medium text-muted">{{ t("adminUsername") }}</span>
                <input v-model="form.adminUsername" type="text" autocomplete="off" class="input" />
              </label>
              <label class="block">
                <span class="mb-1 block text-xs font-medium text-muted">{{ t("adminPassword") }}</span>
                <div class="relative">
                  <input
                    v-model="form.adminPassword"
                    :type="showAdminPassword ? 'text' : 'password'"
                    autocomplete="off"
                    class="input pr-16"
                  />
                  <button
                    type="button"
                    class="btn btn-ghost absolute right-1 top-1/2 h-7 -translate-y-1/2 px-2 text-xs"
                    @click="showAdminPassword = !showAdminPassword"
                  >
                    {{ showAdminPassword ? t("hide") : t("show") }}
                  </button>
                </div>
              </label>
            </div>
          </div>
          <p class="text-xs leading-relaxed text-muted/80">{{ t("adminNote") }}</p>

          <div v-if="formError" class="rounded-md border border-error/40 bg-error/10 px-3 py-2 text-xs text-error">
            {{ formError }}
          </div>

          <div class="flex gap-2 pt-1">
            <button type="button" class="btn btn-outline flex-1" @click="close">
              {{ t("cancel") }}
            </button>
            <button type="submit" :disabled="submitting" class="btn btn-primary flex-1">
              {{ submitting ? t("registering") : t("register") }}
            </button>
          </div>
        </form>
      </div>
    </div>
    </Transition>
  </Teleport>
</template>
