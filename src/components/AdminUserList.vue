<script setup lang="ts">
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import { initials } from "../lib/format";
import Icon from "./Icon.vue";

defineProps<{
  users: string[];
  selectedId: string | null;
  loading: boolean;
  hasMore: boolean;
}>();

const emit = defineEmits<{
  select: [userId: string];
  "load-more": [];
}>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);
</script>

<template>
  <div class="card h-full min-h-0 overflow-y-auto !rounded-md">
    <ul v-if="users.length" class="divide-y divide-line">
      <li v-for="userId in users" :key="userId">
        <button
          type="button"
          class="flex w-full items-center gap-3 px-3 py-2.5 text-left transition"
          :class="
            selectedId === userId
              ? 'border-l-2 border-primary bg-primary/8'
              : 'border-l-2 border-transparent hover:bg-card-hover'
          "
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
      </li>
    </ul>
    <button
      v-if="hasMore && users.length"
      type="button"
      class="btn btn-outline m-3 !w-[calc(100%-1.5rem)]"
      :disabled="loading"
      @click="emit('load-more')"
    >
      {{ loading ? t("loading") : t("loadMore") }}
    </button>
    <div v-if="!users.length" class="flex flex-col items-center px-4 py-8 text-center">
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
