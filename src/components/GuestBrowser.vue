<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { save } from "@tauri-apps/plugin-dialog";
import "@material/web/button/filled-button.js";
import "@material/web/button/outlined-button.js";
import "@material/web/button/text-button.js";
import "@material/web/iconbutton/icon-button.js";
import "@material/web/textfield/outlined-text-field.js";
import Icon from "./Icon.vue";
import { useUiStore } from "../stores/ui";
import { useAccountsStore } from "../stores/accounts";
import { translate } from "../lib/i18n";
import { formatBytes } from "../lib/format";
import { api, invokeError, type GuestEntry, type GuestShare } from "../lib/ipc";

const emit = defineEmits<{ exit: [] }>();

const ui = useUiStore();
const accounts = useAccountsStore();
const t = (key: string) => translate(ui.lang, key);
const isAdmin = computed(() => !!accounts.active?.isAdmin);

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

// Admin: category dialog
const showCategoryDialog = ref(false);
const newCategoryName = ref("");
const newCategoryPrefixless = ref(false);

// Admin: share-category assignment dropdown (token -> open state)
const assigningToken = ref<string | null>(null);
const assigningLoading = ref(false);

// Admin: lock state per share (token -> locked paths)
const lockedPaths = ref<Map<string, string[]>>(new Map());
const lockBusy = ref<string | null>(null);

const visibleShares = computed(() =>
  activeCategory.value === null
    ? shares.value
    : shares.value.filter((s) => s.category === activeCategory.value),
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

// ---------------------------------------------------------------------------
// Admin: category management
// ---------------------------------------------------------------------------

async function createCategory() {
  const name = newCategoryName.value.trim();
  if (!name) return;
  try {
    await api.guestAdminSetCategory(name, newCategoryPrefixless.value);
    if (!categories.value.includes(name)) categories.value.push(name);
    newCategoryName.value = "";
    newCategoryPrefixless.value = false;
    showCategoryDialog.value = false;
    ui.toast(t("guestAdminCategoryCreated"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  }
}

async function deleteCategory(name: string) {
  try {
    await api.guestAdminDeleteCategory(name);
    categories.value = categories.value.filter((c) => c !== name);
    if (activeCategory.value === name) activeCategory.value = null;
    ui.toast(t("guestAdminCategoryDeleted"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  }
}

// ---------------------------------------------------------------------------
// Admin: share-to-category assignment
// ---------------------------------------------------------------------------

function toggleAssignDropdown(token: string) {
  assigningToken.value = assigningToken.value === token ? null : token;
}

async function assignToCategory(token: string, category: string) {
  assigningLoading.value = true;
  try {
    await api.guestAdminAssignCategory(token, category);
    const s = shares.value.find((sh) => sh.token === token);
    if (s) s.category = category;
    if (!categories.value.includes(category)) categories.value.push(category);
    assigningToken.value = null;
    ui.toast(t("guestAdminShareAssigned"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    assigningLoading.value = false;
  }
}

async function unassignFromCategory(token: string) {
  assigningLoading.value = true;
  try {
    await api.guestAdminUnassignCategory(token);
    const s = shares.value.find((sh) => sh.token === token);
    if (s) s.category = null;
    assigningToken.value = null;
    ui.toast(t("guestAdminShareUnassigned"), "success");
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    assigningLoading.value = false;
  }
}

// ---------------------------------------------------------------------------
// Admin: lock / unlock subfolders
// ---------------------------------------------------------------------------

function isLocked(entryPath: string): boolean {
  if (!share.value) return false;
  const locks = lockedPaths.value.get(share.value.token);
  if (!locks) return false;
  const needle = entryPath.replace(/^\/+/, "");
  return locks.some((l) => {
    const prefix = l.replace(/^\/+/, "");
    return needle === prefix || needle.startsWith(prefix + "/");
  });
}

async function toggleLock(entry: GuestEntry) {
  if (!share.value || !entry.isDir) return;
  lockBusy.value = entry.path;
  try {
    if (isLocked(entry.path)) {
      const locks = await api.guestAdminUnlockPath(share.value.token, entry.path);
      lockedPaths.value.set(share.value.token, locks);
      ui.toast(t("guestAdminPathUnlocked"), "success");
    } else {
      const locks = await api.guestAdminLockPath(share.value.token, entry.path);
      lockedPaths.value.set(share.value.token, locks);
      ui.toast(t("guestAdminPathLocked"), "success");
    }
  } catch (e) {
    ui.toast(invokeError(e).message, "error");
  } finally {
    lockBusy.value = null;
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
        <md-outlined-button v-if="isAdmin && !share" @click="showCategoryDialog = true">
          {{ t("guestAdminCategoryCreate") }}
        </md-outlined-button>
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
          <span
            v-for="category in categories"
            :key="category"
            class="group relative inline-flex items-center"
          >
            <button
              class="rounded-full border px-3 py-1 text-xs font-medium transition"
              :class="activeCategory === category
                ? 'border-primary bg-primary-container text-on-primary-container'
                : 'border-outline-variant text-on-surface-variant hover:bg-surface-container-high'"
              @click="setCategory(category)"
            >
              {{ category }}
            </button>
            <button
              v-if="isAdmin"
              class="ml-0.5 flex h-4 w-4 items-center justify-center rounded-full text-[10px] text-on-surface-variant opacity-0 transition hover:bg-error-container hover:text-on-error-container group-hover:opacity-100"
              :title="t('guestAdminCategoryDelete')"
              @click.stop="deleteCategory(category)"
            >
              &times;
            </button>
          </span>
        </div>

        <p v-if="visibleShares.length === 0 && !loading" class="mt-8 text-center text-sm text-on-surface-variant">
          {{ t("guestEmpty") }}
        </p>

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <div
            v-for="entry in visibleShares"
            :key="entry.token"
            class="relative flex items-center gap-3 rounded-lg border border-outline-variant bg-surface-container p-4 text-left transition hover:bg-surface-container-high"
          >
            <button class="flex min-w-0 flex-1 items-center gap-3" @click="enter(entry)">
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

            <!-- Admin: category assignment dropdown -->
            <div v-if="isAdmin" class="relative shrink-0">
              <md-icon-button
                :title="t('guestAdminAssignCategory')"
                @click.stop="toggleAssignDropdown(entry.token)"
              >
                <Icon name="edit" :size="16" />
              </md-icon-button>
              <div
                v-if="assigningToken === entry.token"
                class="absolute right-0 top-full z-10 mt-1 w-44 rounded-lg border border-outline-variant bg-surface-container shadow-lg"
              >
                <button
                  v-if="entry.category"
                  class="block w-full px-3 py-2 text-left text-xs text-error transition hover:bg-error-container hover:text-on-error-container"
                  @click="unassignFromCategory(entry.token)"
                  :disabled="assigningLoading"
                >
                  {{ t("guestAdminUnassignCategory") }}
                </button>
                <button
                  v-for="cat in categories"
                  :key="cat"
                  class="block w-full px-3 py-2 text-left text-xs transition hover:bg-surface-container-high"
                  :class="cat === entry.category ? 'font-semibold text-primary' : 'text-on-surface'"
                  @click="assignToCategory(entry.token, cat)"
                  :disabled="assigningLoading"
                >
                  {{ cat }}
                </button>
              </div>
            </div>
          </div>
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
          <span
            v-if="entry.isDir && isLocked(entry.path)"
            class="shrink-0 rounded bg-error-container px-1.5 py-0.5 text-[10px] font-semibold text-on-error-container"
          >
            {{ t("guestAdminLocked") }}
          </span>
          <span class="hidden w-24 shrink-0 text-right text-xs tabular-nums text-on-surface-variant sm:block">
            {{ entry.isDir ? "—" : formatBytes(entry.size) }}
          </span>
          <!-- Admin: lock/unlock toggle for directories -->
          <md-icon-button
            v-if="isAdmin && entry.isDir"
            class="shrink-0"
            :title="isLocked(entry.path) ? t('guestAdminUnlock') : t('guestAdminLock')"
            :disabled="lockBusy === entry.path"
            @click="toggleLock(entry)"
          >
            <Icon :name="isLocked(entry.path) ? 'unlock' : 'lock'" :size="18" />
          </md-icon-button>
          <md-icon-button
            v-else-if="!entry.isDir"
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

    <!-- Admin: create category dialog -->
    <div
      v-if="showCategoryDialog"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      @click.self="showCategoryDialog = false"
    >
      <div class="w-80 rounded-xl bg-surface-container p-6 shadow-xl">
        <h3 class="mb-4 text-sm font-semibold text-on-surface">
          {{ t("guestAdminCategoryCreate") }}
        </h3>
        <md-outlined-text-field
          :label="t('guestAdminCategoryName')"
          :value="newCategoryName"
          @input="newCategoryName = ($event.target as HTMLInputElement).value"
          class="mb-3 block w-full"
        />
        <label class="mb-4 flex items-center gap-2 text-xs text-on-surface-variant">
          <input
            type="checkbox"
            v-model="newCategoryPrefixless"
            class="h-4 w-4 accent-primary"
          />
          {{ t("guestAdminCategoryPrefixless") }}
        </label>
        <div class="flex justify-end gap-2">
          <md-text-button @click="showCategoryDialog = false">
            {{ t("cancel") }}
          </md-text-button>
          <md-filled-button @click="createCategory" :disabled="!newCategoryName.trim()">
            {{ t("create") }}
          </md-filled-button>
        </div>
      </div>
    </div>
  </main>
</template>
