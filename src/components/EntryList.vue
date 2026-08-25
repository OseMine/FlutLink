<script setup lang="ts">
import { computed } from "vue";
import { useUiStore } from "../stores/ui";
import type { Share, WebDavEntry } from "../lib/ipc";
import { translate } from "../lib/i18n";
import { formatBytes } from "../lib/format";
import { sortEntries, type EntrySortKey } from "../lib/sort";
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
    sortKey?: EntrySortKey;
    sortAsc?: boolean;
    kbdIndex?: number;
  }>(),
  { selectable: true, sortKey: "name", sortAsc: true, kbdIndex: -1 }
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
  toggleSort: [key: EntrySortKey];
}>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

function formatMtime(mtime: string | null): string {
  if (!mtime) return "—";
  const date = new Date(mtime);
  return isNaN(date.getTime()) ? mtime : date.toLocaleString();
}

const sortedEntries = computed(() =>
  sortEntries(props.entries, props.sortKey ?? "name", props.sortAsc ?? true)
);

function toggleSort(key: EntrySortKey) {
  emit("toggleSort", key);
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
        <tr class="text-left text-[11px] uppercase tracking-wide text-muted">
          <th v-if="selectable" class="w-8 px-3 py-2"></th>
          <th class="px-3 py-2 font-medium">
            <button type="button" class="uppercase tracking-wide transition hover:text-fg" @click="toggleSort('name')">
              {{ t("name") }} {{ sortKey === "name" ? (sortAsc ? "▲" : "▼") : "" }}
            </button>
          </th>
          <th class="w-28 px-3 py-2 font-medium">
            <button type="button" class="uppercase tracking-wide transition hover:text-fg" @click="toggleSort('size')">
              {{ t("size") }} {{ sortKey === "size" ? (sortAsc ? "▲" : "▼") : "" }}
            </button>
          </th>
          <th class="w-44 px-3 py-2 font-medium">
            <button type="button" class="uppercase tracking-wide transition hover:text-fg" @click="toggleSort('mtime')">
              {{ t("modified") }} {{ sortKey === "mtime" ? (sortAsc ? "▲" : "▼") : "" }}
            </button>
          </th>
          <th class="w-40 px-3 py-2 font-medium">{{ t("kind") }}</th>
          <th class="w-32 px-3 py-2 font-medium"></th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(entry, i) in sortedEntries"
          :key="entry.path"
          class="border-t border-line transition-colors hover:bg-card-hover"
          :class="[
            selectable && selected.has(entry.path) ? 'bg-primary/10' : '',
            i === kbdIndex ? 'bg-primary/15' : '',
          ]"
          @contextmenu="emit('contextmenu', $event, entry)"
        >
          <td v-if="selectable" class="px-3 py-2">
            <input
              type="checkbox"
              class="checkbox"
              :checked="selected.has(entry.path)"
              @change="emit('toggleSelect', entry.path)"
            />
          </td>
          <td class="max-w-0 px-3 py-2">
            <button type="button" class="flex w-full items-center gap-2 text-left transition hover:text-primary" @click="emit('open', entry)">
              <span class="flex w-5 shrink-0 justify-center">
                <img
                  v-if="props.thumbs?.get(entry.path)"
                  :src="props.thumbs.get(entry.path)"
                  class="h-5 w-5 rounded object-cover"
                  alt=""
                />
                <Icon v-else-if="entry.isDir" name="folder" :size="18" class="text-muted" />
                <Icon v-else name="file" :size="18" class="text-muted" />
              </span>
              <span class="truncate">{{ entry.name }}</span>
              <span v-if="props.searching" class="shrink-0 truncate text-xs text-muted/80">
                {{ parentPath(entry.path) }}
              </span>
            </button>
          </td>
          <td class="px-3 py-2 tabular-nums text-muted">{{ entry.isDir ? "—" : formatBytes(entry.size) }}</td>
          <td class="px-3 py-2 text-muted">{{ formatMtime(entry.mtime) }}</td>
          <td class="px-3 py-2">
            <!-- Status badges: neutral surface + colored dot -->
            <span v-if="entry.isResource" class="badge normal-case" :title="entry.linkTarget ? t('linkTargetTo') + ' ' + entry.linkTarget : undefined">
              <span class="badge-dot bg-info"></span>
              {{ t("resource") }}
            </span>
            <span v-else-if="entry.isPart" class="badge normal-case" :title="entry.linkTarget ? t('linkTargetTo') + ' ' + entry.linkTarget : undefined">
              <span class="badge-dot bg-success"></span>
              {{ t("part") }}
            </span>
            <span v-else class="text-xs text-muted/70">{{ t("sync") }}</span>
            <button
              v-if="entry.pairedPath"
              type="button"
              class="action-badge ml-1"
              :title="t('openPaired') + ' ' + entry.pairedPath"
              @click.stop="emit('pair', entry)"
            >
              ↔
            </button>
          </td>
          <td class="px-3 py-2 text-right">
            <span v-if="shareStatus(entry.path)?.status === 'loading'" class="text-xs text-muted">…</span>
            <span
              v-else-if="shareStatus(entry.path)?.status === 'done'"
              class="inline-flex justify-end text-success"
              :title="t('linkCopied')"
            >
              <Icon name="check" :size="15" />
            </span>
            <span v-else-if="shareStatus(entry.path)?.status === 'error'" class="inline-flex justify-end text-error">
              <Icon name="close" :size="15" />
            </span>
            <button
              v-if="shareStatus(entry.path)?.status === 'done'"
              type="button"
              class="action-badge"
              :title="shareStatus(entry.path)?.value ?? ''"
              @click.stop="emit('copyLink', entry.path)"
            >
              ⧉
            </button>
            <button
              v-else-if="!shareStatus(entry.path)"
              type="button"
              class="action-badge"
              @click.stop="emit('createLink', entry)"
            >
              {{ t("link") }}
            </button>
            <span
              v-if="props.sharesByPath?.get(entry.path)?.length"
              class="badge mr-1 !normal-case"
              :title="t('sharesCount').replace('{count}', String(props.sharesByPath?.get(entry.path)?.length ?? 0))"
            >
              {{ props.sharesByPath?.get(entry.path)?.length }}
            </span>
            <button
              type="button"
              class="action-badge"
              @click.stop="emit('share', entry)"
            >
              <Icon name="share" :size="13" />
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
        v-for="(entry, i) in sortedEntries"
        :key="entry.path"
        class="group relative flex cursor-pointer flex-col items-center gap-1 rounded-md border p-3 text-center transition"
        :class="[
          selectable && selected.has(entry.path)
            ? 'border-primary/60 bg-primary/10'
            : 'border-line bg-card hover:border-line-strong hover:bg-card-hover',
          i === kbdIndex ? '!border-primary ring-1 ring-primary' : '',
        ]"
        @click="selectable && $event.detail === 1 && emit('toggleSelect', entry.path)"
        @dblclick="emit('open', entry)"
        @contextmenu="emit('contextmenu', $event, entry)"
      >
        <input
          v-if="selectable"
          type="checkbox"
          class="checkbox absolute right-1.5 top-1.5 z-10"
          :checked="selected.has(entry.path)"
          @click.stop
          @change="emit('toggleSelect', entry.path)"
        />
        <span
          v-if="props.sharesByPath?.get(entry.path)?.length"
          class="badge absolute left-1.5 top-1.5 !normal-case"
          :title="t('sharesCount').replace('{count}', String(props.sharesByPath?.get(entry.path)?.length ?? 0))"
        >
          {{ props.sharesByPath?.get(entry.path)?.length }}
        </span>
        <div class="relative">
          <img
            v-if="props.thumbs?.get(entry.path)"
            :src="props.thumbs.get(entry.path)"
            class="mt-4 h-16 w-16 rounded object-cover"
            alt=""
          />
          <Icon v-else :name="entry.isDir ? 'folder' : 'file'" :size="34" class="mt-4 text-muted" />
        </div>
        <p class="w-full truncate text-xs" :title="entryPreview(entry)">{{ entry.name }}</p>
        <p class="w-full truncate text-[10px] tabular-nums text-muted">
          {{ entry.isDir ? "—" : formatBytes(entry.size) }}
        </p>
        <p
          v-if="props.searching"
          class="w-full truncate text-[10px] text-muted/80"
          :title="parentPath(entry.path)"
        >
          {{ parentPath(entry.path) }}
        </p>

        <!-- U2/#363: the overlay stays mounted and only fades via opacity —
             no hidden→flex display flip per item, so hovering never forces a
             re-layout/compositing round-trip. -->
        <div
          class="pointer-events-none absolute inset-0 z-20 flex items-center justify-center gap-1.5 rounded-md bg-scrim/55 opacity-0 transition-opacity duration-150 group-hover:pointer-events-auto group-hover:opacity-100"
          @click.stop
          @dblclick.stop
        >
          <button
            type="button"
            class="grid h-7 w-7 place-items-center rounded-sm border border-line-strong bg-card text-fg transition hover:border-primary hover:bg-primary hover:text-on-primary"
            :title="t('open')"
            @click="emit('open', entry)"
          >
            <Icon name="open" :size="14" />
          </button>
          <button
            v-if="!entry.isDir"
            type="button"
            class="grid h-7 w-7 place-items-center rounded-sm border border-line-strong bg-card text-fg transition hover:border-primary hover:bg-primary hover:text-on-primary"
            :title="t('download')"
            @click="emit('download', entry)"
          >
            <Icon name="download" :size="14" />
          </button>
          <button
            type="button"
            class="grid h-7 w-7 place-items-center rounded-sm border border-line-strong bg-card text-fg transition hover:border-primary hover:bg-primary hover:text-on-primary"
            :title="shareStatus(entry.path)?.status === 'done' ? t('linkCopied') : t('link')"
            @click="shareStatus(entry.path)?.status === 'done' ? emit('copyLink', entry.path) : emit('createLink', entry)"
          >
            <Icon :name="shareStatus(entry.path)?.status === 'done' ? 'check' : 'link'" :size="14" />
          </button>
          <button
            type="button"
            class="grid h-7 w-7 place-items-center rounded-sm border border-line-strong bg-card text-fg transition hover:border-primary hover:bg-primary hover:text-on-primary"
            :title="t('share')"
            @click="emit('share', entry)"
          >
            <Icon name="share" :size="14" />
          </button>
          <button
            type="button"
            class="grid h-7 w-7 place-items-center rounded-sm border border-line-strong bg-card text-fg transition hover:border-primary hover:bg-primary hover:text-on-primary"
            :title="t('rename')"
            @click="emit('rename', entry)"
          >
            <Icon name="edit" :size="14" />
          </button>
          <button
            type="button"
            class="grid h-7 w-7 place-items-center rounded-sm border border-error/50 bg-card text-error transition hover:bg-error hover:text-white"
            :title="t('delete')"
            @click="emit('delete', entry)"
          >
            <Icon name="delete" :size="14" />
          </button>
        </div>

        <button
          v-if="entry.pairedPath"
          type="button"
          class="action-badge absolute bottom-1 right-1 z-10"
          :title="t('openPaired') + ' ' + entry.pairedPath"
          @click.stop="emit('pair', entry)"
        >
          ↔
        </button>
      </div>
    </div>
  </div>
</template>
