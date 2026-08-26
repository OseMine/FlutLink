<script lang="ts">
export type ShareFormValues = {
  type: "link" | "user" | "group";
  shareWith: string;
  password: string;
  expireDate: string;
  publicUpload: boolean;
};
</script>

<script setup lang="ts">
import { computed, reactive } from "vue";
import type { Share, WebDavEntry } from "../lib/ipc";
import Icon from "./Icon.vue";
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

defineExpose({ resetForm });

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

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);
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
              type="button"
              class="btn btn-danger h-7 shrink-0 px-2 text-xs"
              @click="emit('revoke', share)"
            >
              {{ t("revoke") }}
            </button>
          </li>
        </ul>

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
