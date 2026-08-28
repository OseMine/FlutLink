<script lang="ts">
export type ShareFormValues = {
  type: "link" | "user" | "group";
  shareWith: string;
  password: string;
  expireDate: string;
  publicUpload: boolean;
};

// #406: password/expiry/permission edits for an existing share. `password`/
// `expireDate` at undefined keep the current server value; "" clears them.
export type ShareUpdateValues = {
  password?: string;
  expireDate?: string;
  publicUpload?: boolean;
};
</script>

<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import type { Share, WebDavEntry } from "../lib/ipc";
import Icon from "./Icon.vue";
import QrCode from "./QrCode.vue";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";

defineProps<{
  entry: WebDavEntry;
  shares: Share[];
  loading: boolean;
  submitting: boolean;
}>();

const emit = defineEmits<{
  close: [];
  create: [form: ShareFormValues];
  revoke: [share: Share];
  copy: [url: string];
  edit: [share: Share, values: ShareUpdateValues];
}>();

const form = reactive<ShareFormValues>({
  type: "link",
  shareWith: "",
  password: "",
  expireDate: "",
  publicUpload: false,
});

function resetForm() {
  form.type = "link";
  form.shareWith = "";
  form.password = "";
  form.expireDate = "";
  form.publicUpload = false;
}

defineExpose({ resetForm, cancelEdit, revealQr });

const shareTypes = computed<{ value: "link" | "user" | "group"; label: string }[]>(() => [
  { value: "link", label: t("shareTypeLink") },
  { value: "user", label: t("shareTypeUser") },
  { value: "group", label: t("shareTypeGroup") },
]);

function shareLabel(share: Share): string {
  if (share.shareType === 3) return t("shareTypeLink");
  if (share.shareType === 1) return t("shareTypeGroup");
  return t("shareTypeUser");
}

function shareTarget(share: Share): string {
  if (share.shareType === 3) return share.url ?? "";
  return share.shareWithDisplayname || share.shareWith || "";
}

// #406: inline edit form for an existing share (link shares: password, expiry,
// upload permission). Fields start empty/undetermined and are only sent when
// the user actually changed them.
const editing = ref<Share | null>(null);
const editPassword = ref("");
const editExpiry = ref("");
const editPublicUpload = ref(false);
const clearPassword = ref(false);

function startEdit(share: Share) {
  editing.value = share;
  editPassword.value = "";
  editExpiry.value = share.expiration ?? "";
  editPublicUpload.value = (share.permissions ?? 1) >= 15;
  clearPassword.value = false;
}

function cancelEdit() {
  editing.value = null;
}

function submitEdit() {
  const share = editing.value;
  if (!share) return;
  const values: ShareUpdateValues = {
    password: clearPassword.value ? "" : editPassword.value.trim() || undefined,
    expireDate: editExpiry.value || undefined,
    publicUpload: editPublicUpload.value,
  };
  emit("edit", share, values);
}

// #409/#423: QR preview for a share link, generated locally. `revealQr` is
// called by the parent right after a link share was created.
const qrUrl = ref<string | null>(null);

function toggleQr(url: string) {
  qrUrl.value = qrUrl.value === url ? null : url;
}

function revealQr(url: string) {
  qrUrl.value = url;
}

function closeQr() {
  qrUrl.value = null;
}

// #424: visual strength meter for the share password. Scores 0..4 by counting
// satisfied criteria; empty passwords score 0 and hide the bar.
const passwordStrength = computed<{ score: number; label: string }>(() => {
  const pw = form.password;
  if (!pw) return { score: 0, label: "" };
  let score = 0;
  if (pw.length >= 8) score++;
  if (pw.length >= 12) score++;
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) score++;
  if (/\d/.test(pw)) score++;
  if (/[^A-Za-z0-9]/.test(pw)) score++;
  const labels = [
    "",
    t("passwordWeak"),
    t("passwordFair"),
    t("passwordGood"),
    t("passwordStrong"),
    t("passwordStrong"),
  ];
  return { score, label: labels[Math.min(score, 5)] };
});

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const passwordBarClass = computed(() => {
  const s = passwordStrength.value.score;
  if (s === 0) return "bg-line";
  if (s <= 2) return "bg-danger";
  if (s === 3) return "bg-warning";
  return "bg-primary";
});

const passwordTextClass = computed(() => {
  const s = passwordStrength.value.score;
  if (s <= 2) return "text-danger";
  if (s === 3) return "text-warning";
  return "text-muted";
});
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-scrim/60 p-4"
    @click.self="emit('close')"
  >
    <div class="modal-surface flex max-h-[85vh] w-full max-w-md flex-col">
      <div class="flex items-center justify-between border-b border-line px-5 py-3">
        <h3 class="min-w-0 truncate text-base font-semibold">
          {{ t("share") }} — {{ entry.name }}
        </h3>
        <button
          type="button"
          class="icon-btn !h-7 !w-7 shrink-0"
          :aria-label="t('close')"
          @click="emit('close')"
        >
          <Icon name="close" :size="16" />
        </button>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto px-5 py-4">
        <p class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-muted">
          {{ t("existingShares") }}
        </p>
        <div v-if="loading" class="mb-4 text-sm text-muted">{{ t("loading") }}</div>
        <div v-else-if="!shares.length" class="mb-4 text-sm text-muted">
          {{ t("noShares") }}
        </div>
        <ul v-else class="mb-4 space-y-2">
          <li
            v-for="share in shares"
            :key="share.id"
            class="card flex items-center gap-2 p-3"
          >
            <span class="min-w-0 flex-1">
              <span class="block truncate text-sm">{{ shareLabel(share) }}</span>
              <span class="block truncate text-xs text-muted">{{ shareTarget(share) }}</span>
              <span
                v-if="share.hasPassword || share.expiration"
                class="block truncate text-[11px] text-muted/80"
              >
                {{ share.hasPassword ? t("sharePasswordSet") : "" }}{{ share.hasPassword && share.expiration ? " · " : "" }}{{ share.expiration ? t("shareExpires").replace("{date}", share.expiration) : "" }}
              </span>
            </span>
            <button
              v-if="share.shareType === 3 && share.url"
              type="button"
              class="action-badge shrink-0"
              :title="t('copyLinkTitle')"
              @click="emit('copy', share.url ?? '')"
            >
              ⧉
            </button>
            <button
              v-if="share.shareType === 3 && share.url"
              type="button"
              class="action-badge shrink-0"
              :title="t('qrCode')"
              @click="toggleQr(share.url ?? '')"
            >
              <Icon
                name="qr"
                :size="14"
                :class="qrUrl === (share.url ?? '') ? 'text-primary' : ''"
              />
            </button>
            <button
              v-if="share.shareType === 3"
              type="button"
              class="action-badge shrink-0"
              :title="t('editShare')"
              @click="startEdit(share)"
            >
              <Icon name="edit" :size="14" />
            </button>
            <button
              type="button"
              class="btn btn-danger h-7 shrink-0 px-2 text-xs"
              @click="emit('revoke', share)"
            >
              {{ t("revoke") }}
            </button>
          </li>
        </ul>

        <div v-if="qrUrl" class="card mb-4 space-y-2 p-4">
          <div class="flex items-center justify-between gap-2">
            <p class="text-[11px] font-semibold uppercase tracking-wide text-muted">
              {{ t("qrCode") }}
            </p>
            <button
              type="button"
              class="action-badge shrink-0"
              :title="t('close')"
              @click="closeQr"
            >
              {{ t("close") }}
            </button>
          </div>
          <QrCode :value="qrUrl" />
          <p class="break-all text-center text-[11px] text-muted">{{ qrUrl }}</p>
        </div>

        <div v-if="editing" class="card mb-4 space-y-3 p-3">
          <p class="text-[11px] font-semibold uppercase tracking-wide text-muted">
            {{ t("editShare") }} — {{ editing.path ?? shareTarget(editing) }}
          </p>
          <input
            v-model="editPassword"
            type="password"
            :placeholder="editing.hasPassword ? t('newPasswordKeepPlaceholder') : t('sharePasswordPlaceholder')"
            class="input"
          />
          <label
            v-if="editing.hasPassword"
            class="flex cursor-pointer select-none items-center gap-2 text-sm text-muted"
          >
            <input type="checkbox" class="checkbox" v-model="clearPassword" />
            {{ t("clearPassword") }}
          </label>
          <input v-model="editExpiry" type="date" class="input" />
          <label class="flex cursor-pointer select-none items-center gap-2 text-sm text-muted">
            <input type="checkbox" class="checkbox" v-model="editPublicUpload" />
            {{ t("publicUpload") }}
          </label>
          <div class="flex justify-end gap-2">
            <button type="button" class="btn btn-outline" @click="cancelEdit">
              {{ t("cancel") }}
            </button>
            <button
              type="button"
              class="btn btn-primary"
              :disabled="submitting"
              @click="submitEdit"
            >
              {{ t("save") }}
            </button>
          </div>
        </div>

        <p class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-muted">
          {{ t("newShare") }}
        </p>
        <div class="space-y-3">
          <!-- Share type micro-pills -->
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="type in shareTypes"
              :key="type.value"
              type="button"
              class="pill"
              :class="form.type === type.value ? 'pill-active' : ''"
              @click="form.type = type.value"
            >
              {{ type.label }}
            </button>
          </div>
          <input
            v-if="form.type !== 'link'"
            v-model="form.shareWith"
            type="text"
            :placeholder="t('shareRecipient')"
            class="input"
          />
          <template v-else>
            <input
              v-model="form.password"
              type="password"
              :placeholder="t('sharePasswordPlaceholder')"
              class="input"
            />
            <div v-if="form.password" class="space-y-1">
              <div class="flex gap-1">
                <div
                  v-for="segment in 5"
                  :key="segment"
                  class="h-1 flex-1 rounded-full"
                  :class="passwordStrength.score >= segment ? passwordBarClass : 'bg-line'"
                />
              </div>
              <p class="text-[11px]" :class="passwordTextClass">
                {{ passwordStrength.label }}
              </p>
            </div>
            <input
              v-model="form.expireDate"
              type="date"
              class="input"
            />
            <label class="flex cursor-pointer select-none items-center gap-2 text-sm text-muted">
              <input
                type="checkbox"
                class="checkbox"
                :checked="form.publicUpload"
                @change="form.publicUpload = !form.publicUpload"
              />
              {{ t("publicUpload") }}
            </label>
          </template>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="submitting"
            @click="emit('create', { ...form })"
          >
            {{ t("createShare") }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
