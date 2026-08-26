<script setup lang="ts">
import { ref } from "vue";
import type { UserDetails } from "../lib/ipc";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";

defineProps<{ user: UserDetails }>();

const emit = defineEmits<{
  browse: [userId: string];
  "save-field": [key: "displayname" | "email" | "password"];
  "toggle-enabled": [];
  remove: [];
  "add-to-group": [group: string];
  "remove-from-group": [group: string];
  "create-group": [group: string];
}>();

const displayName = defineModel<string>("displayName", { required: true });
const email = defineModel<string>("email", { required: true });
const password = defineModel<string>("password", { required: true });
const groupInput = defineModel<string>("groupInput", { required: true });

const showPassword = ref(false);

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);
</script>

<template>
  <div class="flex items-start justify-between gap-2">
    <div class="min-w-0">
      <h3 class="truncate text-base font-medium">{{ user.displayName || user.id }}</h3>
      <p class="text-sm text-muted">{{ user.id }}</p>
      <p class="mt-1">
        <span class="badge normal-case">
          <span class="badge-dot" :class="user.enabled ? 'bg-success' : 'bg-error'"></span>
          {{ user.enabled ? t("enabled") : t("disabled") }}
        </span>
      </p>
    </div>
    <div class="flex shrink-0 flex-col items-end gap-1.5">
      <button type="button" class="btn btn-outline h-7 text-xs" @click="emit('browse', user.id)">
        {{ t("browseFiles") }}
      </button>
      <button type="button" class="btn btn-outline h-7 text-xs" @click="emit('toggle-enabled')">
        {{ user.enabled ? t("disableAccount") : t("enableAccount") }}
      </button>
      <button type="button" class="btn btn-danger h-7 text-xs" @click="emit('remove')">
        {{ t("deleteUser") }}
      </button>
    </div>
  </div>

  <div class="mt-4 space-y-3">
    <div>
      <label class="mb-1 block text-[11px] font-medium uppercase tracking-wide text-muted">
        {{ t("displayName") }}
      </label>
      <div class="flex gap-2">
        <input
          v-model="displayName"
          type="text"
          class="input flex-1"
        />
        <button type="button" class="btn btn-primary shrink-0" @click="emit('save-field', 'displayname')">
          {{ t("save") }}
        </button>
      </div>
    </div>

    <div>
      <label class="mb-1 block text-[11px] font-medium uppercase tracking-wide text-muted">
        {{ t("email") }}
      </label>
      <div class="flex gap-2">
        <input
          v-model="email"
          type="email"
          class="input flex-1"
        />
        <button type="button" class="btn btn-primary shrink-0" @click="emit('save-field', 'email')">
          {{ t("save") }}
        </button>
      </div>
    </div>

    <div>
      <label class="mb-1 block text-[11px] font-medium uppercase tracking-wide text-muted">
        {{ t("password") }}
      </label>
      <div class="flex gap-2">
        <input
          v-model="password"
          :type="showPassword ? 'text' : 'password'"
          :placeholder="t('passwordPlaceholder')"
          class="input flex-1"
        />
        <button type="button" class="btn btn-outline h-[34px] shrink-0 px-2" @click="showPassword = !showPassword">
          {{ showPassword ? t("hide") : t("show") }}
        </button>
        <button type="button" class="btn btn-primary shrink-0" @click="emit('save-field', 'password')">
          {{ t("save") }}
        </button>
      </div>
    </div>

    <div>
      <p class="mb-1.5 text-[11px] font-medium uppercase tracking-wide text-muted">
        {{ t("groups") }}
      </p>
      <div v-if="user.groups.length" class="flex flex-wrap gap-1.5">
        <span
          v-for="group in user.groups"
          :key="group"
          class="badge normal-case"
        >
          {{ group }}
          <button
            type="button"
            class="leading-none transition hover:text-error"
            :title="t('removeFromGroup')"
            @click="emit('remove-from-group', group)"
          >
            ×
          </button>
        </span>
      </div>
      <p v-else class="text-xs text-muted/80">{{ t("noGroups") }}</p>
      <div class="mt-2 flex gap-2">
        <input
          v-model="groupInput"
          type="text"
          :placeholder="t('groupName')"
          class="input flex-1"
          @keyup.enter="emit('add-to-group', groupInput)"
        />
        <button type="button" class="btn btn-primary shrink-0" @click="emit('add-to-group', groupInput)">
          {{ t("addToGroup") }}
        </button>
        <button type="button" class="btn btn-outline shrink-0" @click="emit('create-group', groupInput)">
          {{ t("createGroup") }}
        </button>
      </div>
    </div>

    <!-- Quota summary + editor (slot keeps the original space-y rhythm) -->
    <slot />
  </div>
</template>
