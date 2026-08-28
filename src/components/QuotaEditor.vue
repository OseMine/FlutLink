<script setup lang="ts">
import { computed, reactive, watch } from "vue";
import type { UserQuota } from "../lib/ipc";
import { formatBytes } from "../lib/format";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";

const props = defineProps<{
  quota: UserQuota | null;
  /** Bumped by the parent whenever the quota inputs must re-sync from
   *  `quota` (user re-selected). Deliberately NOT bumped by the details
   *  refetch after displayname/email saves — unsaved edits survive those,
   *  matching the pre-split behaviour. */
  revision: number;
}>();

const emit = defineEmits<{ save: [quota: string | null] }>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const MB = 1024 * 1024;
const GB = 1024 * 1024 * 1024;

const edits = reactive({
  quotaValue: null as number | null,
  quotaUnit: "gb" as "gb" | "mb" | "unlimited",
});

function setQuotaFromTotal(total: number | null) {
  if (total === null || total < 0) {
    edits.quotaValue = null;
    edits.quotaUnit = "unlimited";
    return;
  }
  if (total >= GB) {
    edits.quotaUnit = "gb";
    edits.quotaValue = Math.round((total / GB) * 10) / 10;
  } else {
    edits.quotaUnit = "mb";
    edits.quotaValue = Math.round((total / MB) * 10) / 10;
  }
}

// Initial mount + every parent-side `revision` bump (user selection).
watch(
  () => props.revision,
  () => setQuotaFromTotal(props.quota?.total ?? null),
  { immediate: true }
);

// When switching to unlimited, clear the value.
watch(
  () => edits.quotaUnit,
  (unit) => {
    if (unit === "unlimited") edits.quotaValue = null;
  }
);

const isUnlimited = computed(() => edits.quotaUnit === "unlimited");

function save() {
  let quota: string;
  if (edits.quotaUnit === "unlimited") {
    quota = "-3";
  } else {
    const value = edits.quotaValue;
    // L19-F6: an emptied/invalid field yields NaN — `NaN <= 0` is false, so
    // the old check let "NaN" through to the OCS API.
    if (value === null || !Number.isFinite(value) || value <= 0) {
      emit("save", null);
      return;
    }
    const factor = edits.quotaUnit === "gb" ? GB : MB;
    quota = String(Math.round(value * factor));
  }
  emit("save", quota);
}

function quotaTotal(q: UserQuota | null): string {
  return q?.total === null ? t("unlimited") : formatBytes(q?.total ?? null);
}

function quotaUsed(q: UserQuota | null): string {
  return formatBytes(q?.used ?? null);
}

function quotaFree(q: UserQuota | null): string {
  if (!q || q.total === null) return t("unlimited");
  if (q.used === null) return formatBytes(q.total);
  const free = q.total - q.used;
  if (free < 0) return `-${formatBytes(-free)}`;
  return formatBytes(free);
}

const usagePercent = computed(() => {
  const q = props.quota;
  if (!q || q.total === null || q.total <= 0 || q.used === null) return null;
  return Math.min(100, Math.round((q.used / q.total) * 100));
});

/// #426: store nearly full → amber at 71-90 %, red beyond. `ok` ≤ 70 %.
const quotaState = computed<"ok" | "warn" | "critical">(() => {
  const p = usagePercent.value;
  if (p === null) return "ok";
  if (p > 90) return "critical";
  if (p > 70) return "warn";
  return "ok";
});

const warningText = computed(() =>
  quotaState.value === "critical" ? t("quotaCritical") : quotaState.value === "warn" ? t("quotaWarning") : ""
);
</script>

<template>
  <div class="rounded-md bg-card-hover p-3 text-sm">
    <div class="flex justify-between">
      <span class="text-muted">{{ t("quota") }}</span>
      <span class="tabular-nums">{{ quotaTotal(quota) }}</span>
    </div>
    <div class="flex justify-between">
      <span class="text-muted">{{ t("used") }}</span>
      <span class="tabular-nums">{{ quotaUsed(quota) }}</span>
    </div>
    <div class="flex justify-between">
      <span class="text-muted">{{ t("free") }}</span>
      <span class="tabular-nums">{{ quotaFree(quota) }}</span>
    </div>
    <div v-if="usagePercent !== null" class="mt-2 h-1.5 overflow-hidden rounded-full bg-line">
      <div
        class="h-full rounded-full transition-all duration-300"
        :class="quotaState === 'ok' ? 'bg-primary' : quotaState === 'warn' ? 'bg-warning' : 'bg-error'"
        :style="{ width: usagePercent + '%' }"
      ></div>
    </div>
    <p
      v-if="warningText"
      class="mt-1.5 text-xs font-medium"
      :class="quotaState === 'critical' ? 'text-error' : 'text-warning'"
    >
      {{ warningText }}
    </p>
  </div>

  <div>
    <label class="mb-1 block text-[11px] font-medium uppercase tracking-wide text-muted">
      {{ t("setQuota") }}
    </label>
    <div class="flex gap-2">
      <input
        type="number"
        :value="edits.quotaValue"
        @input="edits.quotaValue = ($event.target as HTMLInputElement).valueAsNumber"
        :disabled="isUnlimited"
        min="0"
        step="0.1"
        class="input flex-1"
      />
      <select
        v-model="edits.quotaUnit"
        class="input w-28 shrink-0"
      >
        <option value="gb">{{ t("gb") }}</option>
        <option value="mb">{{ t("mb") }}</option>
        <option value="unlimited">{{ t("unlimited") }}</option>
      </select>
      <button type="button" class="btn btn-outline shrink-0" @click="save">
        {{ t("save") }}
      </button>
    </div>
  </div>
</template>
