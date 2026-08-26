<script setup lang="ts">
import { computed, ref } from "vue";
import type { UserDetails } from "../lib/ipc";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import { initials } from "../lib/format";

const props = defineProps<{ user: UserDetails }>();

const emit = defineEmits<{
  browse: [userId: string];
  save: [];
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

// The footer button stays disabled until an input actually differs from the
// loaded details (or a new password was typed).
const dirty = computed(
  () =>
    password.value.length > 0 ||
    displayName.value !== (props.user.displayName ?? "") ||
    email.value !== (props.user.email ?? "")
);

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);
</script>

<template>
  <div class="flex h-full flex-col">
    <!-- Header: identity + quick actions -->
    <div class="flex items-center gap-3 border-b border-line pb-3">
      <span
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/15 text-sm font-semibold text-primary"
        aria-hidden="true"
      >
        {{ initials(user.displayName || user.id) }}
      </span>
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-base font-medium">{{ user.displayName || user.id }}</h3>
        <p class="truncate text-sm text-muted">{{ user.id }}</p>
        <span class="badge mt-1 normal-case">
          <span class="badge-dot" :class="user.enabled ? 'bg-success' : 'bg-error'"></span>
          {{ user.enabled ? t("enabled") : t("disabled") }}
        </span>
      </div>
      <div class="flex shrink-0 items-center gap-1.5">
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

    <!-- Scrollable form sections -->
    <div class="min-h-0 flex-1 space-y-5 overflow-y-auto pt-4">
      <section>
        <h4 class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-muted">
          {{ t("general") }}
        </h4>
        <div class="space-y-3">
          <div>
            <label class="mb-1 block text-[11px] font-medium uppercase tracking-wide text-muted">
              {{ t("displayName") }}
            </label>
            <input v-model="displayName" type="text" class="input w-full" />
          </div>
          <div>
            <label class="mb-1 block text-[11px] font-medium uppercase tracking-wide text-muted">
              {{ t("email") }}
            </label>
            <input v-model="email" type="email" class="input w-full" />
          </div>
        </div>
        <!-- Quota summary + editor (slot keeps the original space-y rhythm) -->
        <div class="mt-3">
          <slot />
        </div>
      </section>

      <section>
        <h4 class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-muted">
          {{ t("security") }}
        </h4>
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
          <button
            type="button"
            class="btn btn-outline h-[34px] shrink-0 px-2"
            @click="showPassword = !showPassword"
          >
            {{ showPassword ? t("hide") : t("show") }}
          </button>
        </div>
      </section>

      <section>
        <h4 class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-muted">
          {{ t("groups") }}
        </h4>
        <div v-if="user.groups.length" class="flex flex-wrap gap-1.5">
          <span v-for="group in user.groups" :key="group" class="badge normal-case">
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
          <button
            type="button"
            class="btn btn-outline shrink-0"
            @click="emit('add-to-group', groupInput)"
          >
            {{ t("addToGroup") }}
          </button>
          <button
            type="button"
            class="btn btn-outline shrink-0"
            @click="emit('create-group', groupInput)"
          >
            {{ t("createGroup") }}
          </button>
        </div>
      </section>
    </div>

    <!-- Footer: single save action -->
    <div class="flex items-center justify-end gap-2 border-t border-line pt-3">
      <button
        type="button"
        class="btn btn-primary"
        :disabled="!dirty"
        @click="emit('save')"
      >
        {{ t("saveChanges") }}
      </button>
    </div>
  </div>
</template>
