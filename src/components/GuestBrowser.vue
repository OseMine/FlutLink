<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { save } from "@tauri-apps/plugin-dialog";
import "@material/web/button/filled-button.js";
import "@material/web/button/outlined-button.js";
import "@material/web/button/text-button.js";
import "@material/web/iconbutton/icon-button.js";
import Icon from "./Icon.vue";
import { useUiStore } from "../stores/ui";
import { translate } from "../lib/i18n";
import { formatBytes } from "../lib/format";
import { api, invokeError, type GuestEntry, type GuestShare } from "../lib/ipc";

const emit = defineEmits<{ exit: [] }>();

const ui = useUiStore();
const t = (key: string) => translate(ui.lang, key);

const shares = ref<GuestShare[]>([]);
const categories = ref<string[]>([]);
const activeCategory = ref<string | null>(null);
const loading = ref(false);
const errorText = ref("");
const failed = ref(false);

// Browsing state inside one public share (strictly read-only).
const share = ref<GuestShare | null>(null);
const path = ref("/");
const entries = ref<GuestEntry[]>([]);
const busyPath = ref<string | null>(null);

const visibleShares = computed(() =>
  activeCategory.value === null
    ? shares.value
    : shares.value.filter((s) => s.category === activeCategory.value)
);

/// Breadcrumb segments of the current browse path.
const crumbs = computed(() => {
  const parts = path.value.split("/").filter(Boolean);
  return parts.map((name, index) => ({
    name,
    path: "/" + parts.slice(0, index + 1).join("/"),
  }));
});

async function load() {
  if (loading.value) return;
  loading.value = true;
  failed.value = false;
  errorText.value = "";
  try {
    await api.guestVerifyServer();
    shares.value = await api.guestListShares();
    categories.value = [
      ...new Set(shares.value.map((s) => s.category).filter((c): c is string => c !== null)),
    ];
    if (activeCategory.value && !categories.value.includes(activeCategory.value)) {
      activeCategory.value = null;
    }
  } catch (e) {
    failed.value = true;
    errorText.value = invokeError(e).message;
  } finally {
    loading.value = false;
  }
}

function setCategory(name: string | null) {
  activeCategory.value = name;
}

async function enter(target: GuestShare) {
  share.value = target;
  await navigateTo("/");
}

async function navigateTo(target: string) {
  if (!share.value || busyPath.value) return;
  busyPath.value = target;
  try {
    const listing = await api.guestListEntries(share.value.token, target);
    entries.value = listing.entries;
    path.value = listing.path;
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    busyPath.value = null;
  }
}

function leave() {
  share.value = null;
  entries.value = [];
  path.value = "/";
}

async function download(entry: GuestEntry) {
  if (!share.value || busyPath.value) return;
  busyPath.value = entry.path;
  try {
    const dest = await save({ defaultPath: entry.name });
    if (typeof dest !== "string") return;
    await api.guestDownloadFile(share.value.token, entry.path, dest);
    ui.toast(t("fileDownloaded"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    busyPath.value = null;
  }
}

async function open(entry: GuestEntry) {
  if (!share.value || busyPath.value) return;
  busyPath.value = entry.path;
  try {
    await api.guestOpenFile(share.value.token, entry.path);
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    busyPath.value = null;
  }
}

onMounted(() => void load());
</script>

<template>
  <main class="flex min-w-0 flex-1 flex-col">
    <header class="flex items-center justify-between gap-3 border-b border-outline-variant px-6 py-3">
      <div class="flex min-w-0 items-center gap-2.5">
        <img src="/flutlink-logo.svg" alt="FlutLink" class="h-7" />
        <span class="truncate text-sm font-medium text-on-surface-variant">
          {{ t("guestActiveTitle") }}
        </span>
      </div>
      <div class="flex items-center gap-2">
        <md-outlined-button @click="emit('exit')">
          {{ t("guestExit") }}
        </md-outlined-button>
      </div>
    </header>

    <!-- Root view: every complete public share at one place -->
    <div v-if="!share" class="min-h-0 flex-1 overflow-y-auto p-6">
      <p class="mb-4 flex items-center gap-2 text-sm text-on-surface-variant">
        <Icon name="lock" :size="16" />
        {{ t("guestReadOnlyHint") }}
      </p>

      <div v-if="failed" class="mx-auto mt-12 max-w-md text-center">
        <p class="text-sm text-error">{{ errorText }}</p>
        <md-outlined-button class="mt-4" @click="load">
          {{ t("retry") }}
        </md-outlined-button>
      </div>

      <template v-else>
        <div class="mb-6 flex flex-wrap items-center gap-2">
          <button
            class="rounded-full border px-3 py-1 text-xs font-medium transition"
            :class="activeCategory === null
              ? 'border-primary bg-primary-container text-on-primary-container'
              : 'border-outline-variant text-on-surface-variant hover:bg-surface-container-high'"
            @click="setCategory(null)"
          >
            {{ t("guestAll") }}
          </button>
          <button
            v-for="category in categories"
            :key="category"
            class="rounded-full border px-3 py-1 text-xs font-medium transition"
            :class="activeCategory === category
              ? 'border-primary bg-primary-container text-on-primary-container'
              : 'border-outline-variant text-on-surface-variant hover:bg-surface-container-high'"
            @click="setCategory(category)"
          >
            {{ category }}
          </button>
        </div>

        <p v-if="visibleShares.length === 0 && !loading" class="mt-8 text-center text-sm text-on-surface-variant">
          {{ t("guestEmpty") }}
        </p>

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <button
            v-for="entry in visibleShares"
            :key="entry.token"
            class="flex items-center gap-3 rounded-lg border border-outline-variant bg-surface-container p-4 text-left transition hover:bg-surface-container-high"
            @click="enter(entry)"
          >
            <Icon name="folder" :size="28" class="text-primary shrink-0" />
            <span class="min-w-0 flex-1">
              <span class="block truncate text-sm font-medium text-on-surface">{{ entry.name }}</span>
              <span class="block truncate text-xs text-on-surface-variant">
                {{ entry.ownerDisplay || entry.owner }}
              </span>
            </span>
            <span
              v-if="entry.category"
              class="shrink-0 rounded bg-primary/30 px-1.5 py-0.5 text-[10px] font-semibold uppercase"
            >
              {{ entry.category }}
            </span>
          </button>
        </div>
      </template>
    </div>

    <!-- Browse view: read-only folder contents of one share -->
    <div v-else class="min-h-0 flex-1 overflow-y-auto p-6">
      <div class="mb-4 flex items-center gap-1 overflow-x-auto whitespace-nowrap text-sm">
        <md-icon-button class="shrink-0" :title="t('back')" @click="leave">
          <Icon name="back" :size="18" />
        </md-icon-button>
        <button class="rounded px-2 py-0.5 text-primary transition hover:bg-surface-container-high" @click="navigateTo('/')">
          {{ share.name }}
        </button>
        <template v-for="(crumb, index) in crumbs" :key="crumb.path">
          <span class="text-outline">/</span>
          <button
            class="rounded px-2 py-0.5 transition hover:bg-surface-container-high"
            :class="index === crumbs.length - 1 ? 'font-medium text-on-surface' : 'text-primary'"
            :disabled="index === crumbs.length - 1"
            @click="navigateTo(crumb.path)"
          >
            {{ crumb.name }}
          </button>
        </template>
      </div>

      <p v-if="entries.length === 0" class="mt-8 text-center text-sm text-on-surface-variant">
        {{ t("guestEmpty") }}
      </p>

      <div class="overflow-hidden rounded-lg border border-outline-variant">
        <div
          v-for="entry in entries"
          :key="entry.path"
          class="flex items-center gap-3 border-b border-outline-variant bg-surface px-4 py-2.5 last:border-b-0 hover:bg-surface-container"
        >
          <Icon :name="entry.isDir ? 'folder' : 'file'" :size="20" class="text-on-surface-variant shrink-0" />
          <button
            class="min-w-0 flex-1 truncate text-left text-sm text-on-surface"
            :title="entry.name"
            @click="entry.isDir ? navigateTo(entry.path) : open(entry)"
          >
            {{ entry.name }}
          </button>
          <span class="hidden w-24 shrink-0 text-right text-xs tabular-nums text-on-surface-variant sm:block">
            {{ entry.isDir ? "—" : formatBytes(entry.size) }}
          </span>
          <md-icon-button
            v-if="!entry.isDir"
            class="shrink-0"
            :title="t('download')"
            :disabled="busyPath !== null"
            @click="download(entry)"
          >
            <Icon name="download" :size="18" />
          </md-icon-button>
          <span v-else class="w-10 shrink-0"></span>
        </div>
      </div>
    </div>
  </main>
</template>
