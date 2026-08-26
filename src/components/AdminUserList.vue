<script setup lang="ts">
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";

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
  <div class="card min-h-0 overflow-y-auto !rounded-md">
    <ul v-if="users.length" class="divide-y divide-line">
      <li v-for="userId in users" :key="userId">
        <button
          type="button"
          class="w-full px-4 py-2 text-left text-sm transition hover:bg-card-hover"
          :class="selectedId === userId ? 'bg-primary/10 text-primary' : ''"
          @click="emit('select', userId)"
        >
          {{ userId }}
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
    <p v-if="!users.length" class="p-4 text-sm text-muted">
      {{ loading ? t("loading") : t("noUsersYet") }}
    </p>
  </div>
</template>
