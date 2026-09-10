<script setup lang="ts">
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import { initials } from "../lib/format";
import Icon from "./Icon.vue";

const props = defineProps<{
  users: string[];
  selectedId: string | null;
  selectedIds: string[];
  loading: boolean;
  hasMore: boolean;
}>();

const emit = defineEmits<{
  select: [userId: string];
  "toggle-select": [userId: string];
  "select-all": [];
  "clear-selection": [];
  "load-more": [];
}>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const allChecked = () =>
  props.users.length > 0 && props.users.every((u) => props.selectedIds.includes(u));
</script>

<template>
  <div class="card flex h-full min-h-0 flex-col overflow-hidden !rounded-md">
    <div
      class="flex items-center justify-between gap-2 border-b border-line px-3 py-2"
      :class="selectedIds.length ? 'bg-primary/8' : ''"
    >
      <label class="flex flex-1 items-center gap-2 text-xs text-muted">
        <input
          type="checkbox"
          class="h-3.5 w-3.5 accent-primary"
          :checked="allChecked()"
          :disabled="!users.length"
          @change="emit(allChecked() ? 'clear-selection' : 'select-all', undefined)"
        />
        <span>{{ selectedIds.length ? `${selectedIds.length} ${t("selected")}` : t("selectedNone") }}</span>
      </label>
      <button
        v-if="selectedIds.length"
        type="button"
        class="text-xs text-muted underline-offset-2 hover:underline"
        @click="emit('clear-selection', undefined)"
      >
        {{ t("clearSelection") }}
      </button>
    </div>

    <ul v-if="users.length" class="min-h-0 flex-1 divide-y divide-line overflow-y-auto">
      <li v-for="userId in users" :key="userId">
        <div
          class="flex w-full items-center gap-1 px-3 py-2.5 transition"
          :class="
            selectedId === userId
              ? 'border-l-2 border-primary bg-primary/8'
              : 'border-l-2 border-transparent hover:bg-card-hover'
          "
        >
          <input
            type="checkbox"
            class="h-3.5 w-3.5 shrink-0 accent-primary"
            :checked="selectedIds.includes(userId)"
            @change="emit('toggle-select', userId)"
          />
          <button
            type="button"
            class="flex min-w-0 flex-1 items-center gap-3 text-left"
            @click="emit('select', userId)"
          >
            <span
              class="grid h-8 w-8 shrink-0 place-items-center rounded-full text-xs font-semibold"
              :class="
                selectedId === userId
                  ? 'bg-primary/20 text-primary'
                  : 'bg-card-hover text-muted'
              "
              aria-hidden="true"
            >
              {{ initials(userId) }}
            </span>
            <span class="min-w-0 flex-1 truncate text-sm font-medium">
              {{ userId }}
            </span>
          </button>
        </div>
      </li>
    </ul>
    <div v-if="hasMore && users.length" class="border-t border-line p-3">
      <button
        type="button"
        class="btn btn-outline w-full"
        :disabled="loading"
        @click="emit('load-more')"
      >
        {{ loading ? t("loading") : t("loadMore") }}
      </button>
    </div>
    <div v-if="!users.length" class="flex flex-1 flex-col items-center px-4 py-8 text-center">
      <Icon
        :name="loading ? 'sync' : 'person'"
        :size="28"
        class="mb-2 text-muted/40"
        :class="{ 'animate-spin': loading }"
      />
      <p class="text-sm text-muted">
        {{ loading ? t("loading") : t("searchUsersHint") }}
      </p>
    </div>
  </div>
</template>