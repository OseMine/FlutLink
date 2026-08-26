<script setup lang="ts">
import { reactive, watch } from "vue";
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

type QuotaPresetId = "1gb" | "5gb" | "10gb" | "unlimited" | "custom";
const QUOTA_PRESETS: { id: Exclude<QuotaPresetId, "unlimited" | "custom">; value: number; unit: "gb" }[] = [
  { id: "1gb", value: 1, unit: "gb" },
  { id: "5gb", value: 5, unit: "gb" },
  { id: "10gb", value: 10, unit: "gb" },
];

const edits = reactive({
  quotaValue: null as number | null,
  quotaUnit: "gb" as "gb" | "mb" | "unlimited",
  quotaPreset: "custom" as QuotaPresetId,
});

function setQuotaFromTotal(total: number | null) {
  if (total === null || total < 0) {
    edits.quotaValue = null;
    edits.quotaUnit = "unlimited";
    edits.quotaPreset = "unlimited";
    return;
  }
  const gbValue = Math.round((total / GB) * 10) / 10;
  const preset = QUOTA_PRESETS.find(
    (p) => p.value === gbValue && p.unit === "gb"
  );
  if (preset) {
    edits.quotaValue = gbValue;
    edits.quotaUnit = "gb";
    edits.quotaPreset = preset.id;
    return;
  }
  if (total >= GB) {
    edits.quotaUnit = "gb";
    edits.quotaValue = gbValue;
  } else {
    edits.quotaUnit = "mb";
    edits.quotaValue = Math.round((total / MB) * 10) / 10;
  }
  edits.quotaPreset = "custom";
}

// Initial mount + every parent-side `revision` bump (user selection).
watch(
  () => props.revision,
  () => setQuotaFromTotal(props.quota?.total ?? null),
  { immediate: true }
);

watch(
  () => edits.quotaPreset,
  (preset) => {
    if (preset === "unlimited") {
      edits.quotaValue = null;
      edits.quotaUnit = "unlimited";
      return;
    }
    if (preset === "custom") return;
    const found = QUOTA_PRESETS.find((p) => p.id === preset);
    if (found) {
      edits.quotaValue = found.value;
      edits.quotaUnit = found.unit;
    }
  }
);

watch(
  () => [edits.quotaValue, edits.quotaUnit] as const,
  ([value, unit]) => {
    if (edits.quotaPreset === "unlimited") return;
    if (unit === "unlimited") {
      edits.quotaPreset = "unlimited";
      return;
    }
    if (edits.quotaPreset === "custom") return;
    const matches = QUOTA_PRESETS.some(
      (p) => p.value === value && p.unit === unit
    );
    if (!matches) edits.quotaPreset = "custom";
  }
);

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
  </div>

  <div>
    <label class="mb-1 block text-[11px] font-medium uppercase tracking-wide text-muted">
      {{ t("setQuota") }}
    </label>
    <div class="flex gap-2">
      <select
        v-model="edits.quotaPreset"
        class="input w-32 shrink-0"
      >
        <option value="1gb">1 GB</option>
        <option value="5gb">5 GB</option>
        <option value="10gb">10 GB</option>
        <option value="unlimited">{{ t("unlimited") }}</option>
        <option value="custom">{{ t("custom") }}</option>
      </select>
      <input
        type="number"
        :value="edits.quotaValue"
        @input="edits.quotaValue = ($event.target as HTMLInputElement).valueAsNumber"
        :disabled="edits.quotaUnit === 'unlimited'"
        min="0"
        step="0.1"
        class="input flex-1"
      />
      <select
        v-model="edits.quotaUnit"
        :disabled="edits.quotaUnit === 'unlimited'"
        class="input w-24 shrink-0"
      >
        <option value="gb">{{ t("gb") }}</option>
        <option value="mb">{{ t("mb") }}</option>
        <option value="unlimited">{{ t("unlimited") }}</option>
      </select>
      <button type="button" class="btn btn-primary shrink-0" @click="save">
        {{ t("save") }}
      </button>
    </div>
  </div>
</template>
