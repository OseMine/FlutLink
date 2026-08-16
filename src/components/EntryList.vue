<script setup lang="ts">
import { computed, ref } from "vue";
import { useUiStore } from "../stores/ui";
import type { Share, WebDavEntry } from "../lib/ipc";
import { translate } from "../lib/i18n";
import { formatBytes } from "../lib/format";
import Icon from "./Icon.vue";

const props = withDefaults(
  defineProps<{
    entries: WebDavEntry[];
    viewMode: "list" | "grid";
    selected: Set<string>;
    shareState: Map<
      string,
      { status: "loading" | "done" | "error"; value?: string }
    >;
    selectable?: boolean;
    searching?: boolean;
    thumbs?: Map<string, string>;
    sharesByPath?: Map<string, Share[]>;
  }>(),
  { selectable: true }
);

const emit = defineEmits<{
  open: [entry: WebDavEntry];
  toggleSelect: [path: string];
  contextmenu: [e: MouseEvent, entry: WebDavEntry];
  rename: [entry: WebDavEntry];
  createLink: [entry: WebDavEntry];
  copyLink: [path: string];
  pair: [entry: WebDavEntry];
  download: [entry: WebDavEntry];
  delete: [entry: WebDavEntry];
  share: [entry: WebDavEntry];
}>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const sortKey = ref<"name" | "size" | "mtime">("name");
const sortAsc = ref(true);

function formatMtime(mtime: string | null): string {
  if (!mtime) return "—";
  const date = new Date(mtime);
  return isNaN(date.getTime()) ? mtime : date.toLocaleString();
}

const sortedEntries = computed(() => {
  const dirs = props.entries.filter((e) => e.isDir);
  const others = props.entries.filter((e) => !e.isDir);
  const cmp = (a: WebDavEntry, b: WebDavEntry): number => {
    if (sortKey.value === "size") {
      const av = a.size ?? 0;
      const bv = b.size ?? 0;
      return sortAsc.value ? av - bv : bv - av;
    }
    if (sortKey.value === "mtime") {
      const av = a.mtime ? new Date(a.mtime).getTime() : 0;
      const bv = b.mtime ? new Date(b.mtime).getTime() : 0;
      return sortAsc.value ? av - bv : bv - av;
    }
    const av = a.name.toLowerCase();
    const bv = b.name.toLowerCase();
    return sortAsc.value ? av.localeCompare(bv) : bv.localeCompare(av);
  };
  dirs.sort(cmp);
  others.sort(cmp);
  return [...dirs, ...others];
});

function toggleSort(key: "name" | "size" | "mtime") {
  if (sortKey.value === key) sortAsc.value = !sortAsc.value;
  else {
    sortKey.value = key;
    sortAsc.value = true;
  }
}

function shareStatus(path: string) {
  return props.shareState.get(path);
}

function entryPreview(entry: WebDavEntry): string {
  const parts = [entry.name];
  if (!entry.isDir) parts.push(formatBytes(entry.size));
  const mtime = formatMtime(entry.mtime);
  if (mtime !== "—") parts.push(mtime);
  if (entry.isResource) parts.push(t("resource"));
  else if (entry.isPart) parts.push(t("part"));
  return parts.join(" — ");
}

function parentPath(path: string): string {
  const idx = path.lastIndexOf("/");
  return idx > 0 ? path.slice(0, idx) : "/";
}
</script>

<template>
  <!-- List view -->
  <div v-if="viewMode === 'list'" class="px-4 py-2">
    <table class="w-full text-sm">
      <thead>
        <tr class="text-left text-xs uppercase tracking-wide text-on-surface-variant">
          <th v-if="selectable" class="w-8 px-3 py-2"></th>
          <th class="px-3 py-2 font-medium">
            <button class="uppercase tracking-wide hover:text-on-surface" @click="toggleSort('name')">
              {{ t("name") }} {{ sortKey === "name" ? (sortAsc ? "▲" : "▼") : "" }}
            </button>
          </th>
          <th class="w-28 px-3 py-2 font-medium">
            <button class="uppercase tracking-wide hover:text-on-surface" @click="toggleSort('size')">
              {{ t("size") }} {{ sortKey === "size" ? (sortAsc ? "▲" : "▼") : "" }}
            </button>
          </th>
          <th class="w-44 px-3 py-2 font-medium">
            <button class="uppercase tracking-wide hover:text-on-surface" @click="toggleSort('mtime')">
              {{ t("modified") }} {{ sortKey === "mtime" ? (sortAsc ? "▲" : "▼") : "" }}
            </button>
          </th>
          <th class="w-40 px-3 py-2 font-medium">{{ t("kind") }}</th>
          <th class="w-32 px-3 py-2 font-medium"></th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="entry in sortedEntries"
          :key="entry.path"
          class="border-t border-outline-variant/60 hover:bg-surface-container-high/40"
          :class="selectable && selected.has(entry.path) ? 'bg-primary-container/40' : ''"
          @contextmenu="emit('contextmenu', $event, entry)"
        >
          <td v-if="selectable" class="px-3 py-2">
            <input
              type="checkbox"
              class="accent-primary"
              :checked="selected.has(entry.path)"
              @change="emit('toggleSelect', entry.path)"
            />
          </td>
          <td class="px-3 py-2">
            <button class="flex items-center gap-2 text-left text-on-surface hover:text-on-surface" @click="emit('open', entry)">
              <span class="flex w-5 justify-center">
                <img
                  v-if="props.thumbs?.get(entry.path)"
                  :src="props.thumbs.get(entry.path)"
                  class="h-5 w-5 rounded object-cover"
                  alt=""
                />
                <Icon v-else-if="entry.isDir" name="folder" :size="20" class="text-on-surface-variant" />
                <Icon v-else name="file" :size="20" class="text-on-surface-variant" />
              </span>
              <span class="truncate">{{ entry.name }}</span>
              <span v-if="props.searching" class="truncate text-xs text-on-surface-variant">
                {{ parentPath(entry.path) }}
              </span>
            </button>
          </td>
          <td class="px-3 py-2 text-on-surface-variant">{{ entry.isDir ? "—" : formatBytes(entry.size) }}</td>
          <td class="px-3 py-2 text-on-surface-variant">{{ formatMtime(entry.mtime) }}</td>
          <td class="px-3 py-2">
            <span
              v-if="entry.isResource"
              class="rounded bg-info-container px-1.5 py-0.5 text-[10px] font-semibold uppercase text-on-info-container"
              :title="entry.linkTarget ? t('linkTargetTo') + ' ' + entry.linkTarget : undefined"
            >
              {{ t("resource") }}
            </span>
            <span
              v-else-if="entry.isPart"
              class="rounded bg-success-container px-1.5 py-0.5 text-[10px] font-semibold uppercase text-on-success-container"
              :title="entry.linkTarget ? t('linkTargetTo') + ' ' + entry.linkTarget : undefined"
            >
              {{ t("part") }}
            </span>
            <span v-else class="text-xs text-outline">{{ t("sync") }}</span>
            <button
              v-if="entry.pairedPath"
              class="ml-1 rounded border border-outline px-1.5 py-0.5 text-[10px] text-on-surface-variant hover:bg-surface-container-high"
              :title="t('openPaired') + ' ' + entry.pairedPath"
              @click.stop="emit('pair', entry)"
            >
              ↔
            </button>
          </td>
          <td class="px-3 py-2 text-right">
            <span v-if="shareStatus(entry.path)?.status === 'loading'" class="text-xs text-on-surface-variant">…</span>
            <span
              v-else-if="shareStatus(entry.path)?.status === 'done'"
              class="flex justify-end text-success"
              :title="t('linkCopied')"
            >
              <Icon name="check" :size="16" />
            </span>
            <span v-else-if="shareStatus(entry.path)?.status === 'error'" class="flex justify-end text-error">
              <Icon name="close" :size="16" />
            </span>
            <button
              v-if="shareStatus(entry.path)?.status === 'done'"
              class="ml-1 rounded border border-outline px-1.5 py-0.5 text-[10px] text-on-surface-variant hover:bg-surface-container-high"
              :title="shareStatus(entry.path)?.value ?? ''"
              @click.stop="emit('copyLink', entry.path)"
            >
              ⧉
            </button>
            <button
              v-else-if="!shareStatus(entry.path)"
              class="rounded-md border border-outline px-2 py-0.5 text-xs text-on-surface-variant hover:bg-surface-container-high"
              @click.stop="emit('createLink', entry)"
            >
              {{ t("link") }}
            </button>
            <span
              v-if="props.sharesByPath?.get(entry.path)?.length"
              class="mr-1 inline-flex rounded-full bg-primary-container px-2 py-0.5 text-[10px] font-semibold text-on-primary-container"
              :title="t('sharesCount').replace('{count}', String(props.sharesByPath?.get(entry.path)?.length ?? 0))"
            >
              {{ props.sharesByPath?.get(entry.path)?.length }}
            </span>
            <button
              class="inline-flex items-center gap-1 rounded-md border border-outline px-2 py-0.5 text-xs text-on-surface-variant hover:bg-surface-container-high"
              @click.stop="emit('share', entry)"
            >
              <Icon name="share" :size="14" />
              {{ t("share") }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <!-- Grid view -->
  <div v-else class="p-4">
    <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
      <div
        v-for="entry in sortedEntries"
        :key="entry.path"
        class="group relative flex cursor-pointer flex-col items-center gap-1 rounded-lg border p-3 text-center transition"
        :class="selectable && selected.has(entry.path) ? 'border-primary bg-primary-container/40' : 'border-outline-variant bg-surface-container hover:bg-surface-container-high/60'"
        @click="selectable && emit('toggleSelect', entry.path)"
        @dblclick="emit('open', entry)"
        @contextmenu="emit('contextmenu', $event, entry)"
      >
        <input
          v-if="selectable"
          type="checkbox"
          class="accent-primary absolute right-1.5 top-1.5"
          :checked="selected.has(entry.path)"
          @click.stop
          @change="emit('toggleSelect', entry.path)"
        />
        <div class="relative">
          <img
            v-if="props.thumbs?.get(entry.path)"
            :src="props.thumbs.get(entry.path)"
            class="mt-4 h-16 w-16 rounded object-cover"
            alt=""
          />
          <Icon v-else :name="entry.isDir ? 'folder' : 'file'" :size="36" class="mt-4 text-on-surface-variant" />
        </div>
        <p class="w-full truncate text-xs text-on-surface" :title="entryPreview(entry)">{{ entry.name }}</p>
        <p class="w-full truncate text-[10px] text-on-surface-variant">
          {{ entry.isDir ? "—" : formatBytes(entry.size) }}
        </p>
        <p
          v-if="props.searching"
          class="w-full truncate text-[10px] text-on-surface-variant"
          :title="parentPath(entry.path)"
        >
          {{ parentPath(entry.path) }}
        </p>
        <div
          class="absolute inset-0 hidden items-center justify-center gap-1.5 rounded-lg bg-black/50 group-hover:flex"
          @click.stop
          @dblclick.stop
        >
          <button
            class="flex h-7 w-7 items-center justify-center rounded-md bg-surface-container text-on-surface shadow-m3-1 transition hover:bg-primary hover:text-on-primary"
            :title="t('open')"
            @click="emit('open', entry)"
          >
            <Icon name="open" :size="16" />
          </button>
          <button
            v-if="!entry.isDir"
            class="flex h-7 w-7 items-center justify-center rounded-md bg-surface-container text-on-surface shadow-m3-1 transition hover:bg-primary hover:text-on-primary"
            :title="t('download')"
            @click="emit('download', entry)"
          >
            <Icon name="download" :size="16" />
          </button>
          <button
            class="flex h-7 w-7 items-center justify-center rounded-md bg-surface-container text-on-surface shadow-m3-1 transition hover:bg-primary hover:text-on-primary"
            :title="shareStatus(entry.path)?.status === 'done' ? t('linkCopied') : t('link')"
            @click="shareStatus(entry.path)?.status === 'done' ? emit('copyLink', entry.path) : emit('createLink', entry)"
          >
            <Icon :name="shareStatus(entry.path)?.status === 'done' ? 'check' : 'link'" :size="16" />
          </button>
          <button
            class="flex h-7 w-7 items-center justify-center rounded-md bg-surface-container text-on-surface shadow-m3-1 transition hover:bg-primary hover:text-on-primary"
            :title="t('share')"
            @click="emit('share', entry)"
          >
            <Icon name="share" :size="16" />
          </button>
          <button
            class="flex h-7 w-7 items-center justify-center rounded-md bg-surface-container text-on-surface shadow-m3-1 transition hover:bg-primary hover:text-on-primary"
            :title="t('rename')"
            @click="emit('rename', entry)"
          >
            <Icon name="edit" :size="16" />
          </button>
          <button
            class="flex h-7 w-7 items-center justify-center rounded-md bg-surface-container text-error shadow-m3-1 transition hover:bg-error hover:text-on-error"
            :title="t('delete')"
            @click="emit('delete', entry)"
          >
            <Icon name="delete" :size="16" />
          </button>
        </div>
        <button
          v-if="entry.pairedPath"
          class="absolute bottom-1 right-1 rounded border border-outline px-1 py-0.5 text-[10px] text-on-surface-variant hover:bg-surface-container-high"
          :title="t('openPaired') + ' ' + entry.pairedPath"
          @click.stop="emit('pair', entry)"
        >
          ↔
        </button>
      </div>
    </div>
  </div>
</template>
