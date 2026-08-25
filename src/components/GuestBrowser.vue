<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { save } from "@tauri-apps/plugin-dialog";
import Icon from "./Icon.vue";
import { useUiStore } from "../stores/ui";
import { useAccountsStore } from "../stores/accounts";
import { translate } from "../lib/i18n";
import { formatBytes } from "../lib/format";
import { api, invokeError, type GuestEntry, type GuestShare } from "../lib/ipc";
import { registerEscapeCloser } from "../lib/escape";

// #372: `embedded` renders the browser as an admin tab while signed in —
// without it the component is the standalone signed-out guest mode view.
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

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
  // #375: never start a navigation while an action is running, and drop the
  // previous share's listing up front — no state left where old entries are
  // shown under a different share's title.
  if (busyPath.value) return;
  share.value = target;
  entries.value = [];
  path.value = "/";
  if (isAdmin.value) {
    // #373: existing server-side locks must be loaded on entry — otherwise
    // locked folders always appear unlocked until the admin toggles one.
    void api
      .guestAdminListLocks(target.token)
      .then((locks) => {
        if (share.value?.token === target.token) {
          lockedPaths.value.set(target.token, locks);
        }
      })
      .catch(() => {
        // lock state is best-effort; toggling surfaces real errors
      });
  }
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
  // #374: deleting a category irrevocably drops every share assignment of
  // that category on the server — same confirmation pattern as accounts,
  // files and sync folders.
  if (!window.confirm(t("guestAdminCategoryDeleteConfirm").replace("{name}", name))) return;
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

function closeAssignDropdown() {
  assigningToken.value = null;
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

// L19-N1/#365: Escape closes the category dialog and the assignment dropdown,
// topmost overlay first. The dropdown also closes on outside clicks.
let escapeUnregisters: (() => void)[] = [];
function clearEscapeClosers() {
  for (const off of escapeUnregisters) off();
  escapeUnregisters = [];
}
const overlayClosers = computed<(() => void)[]>(() => {
  const closers: (() => void)[] = [];
  if (showCategoryDialog.value) {
    closers.push(() => {
      showCategoryDialog.value = false;
    });
  }
  if (assigningToken.value) closers.push(closeAssignDropdown);
  return closers;
});
watch(overlayClosers, (closers) => {
  clearEscapeClosers();
  escapeUnregisters = [...closers].reverse().map((c) => registerEscapeCloser(c));
});
onUnmounted(clearEscapeClosers);

onMounted(() => void load());
</script>

<template>
  <main class="flex min-w-0 flex-1 flex-col">
    <header class="flex items-center justify-between gap-3 border-b border-line px-6 py-3">
      <div v-if="!embedded" class="flex min-w-0 items-center gap-2.5">
        <img src="/flutlink-logo.svg" alt="FlutLink" class="h-7" />
        <span class="truncate text-sm font-medium text-muted">
          {{ t("guestActiveTitle") }}
        </span>
      </div>
      <div v-else class="min-w-0">
        <h2 class="truncate text-lg font-semibold">{{ t("guestTabTitle") }}</h2>
        <p class="truncate text-xs text-muted">{{ t("guestReadOnlyHint") }}</p>
      </div>
      <div class="flex items-center gap-2">
        <button
          v-if="isAdmin && !share"
          type="button"
          class="btn btn-outline"
          @click="showCategoryDialog = true"
        >
          {{ t("guestAdminCategoryCreate") }}
        </button>
        <button
          v-if="!embedded"
          type="button"
          class="btn btn-ghost"
          @click="emit('exit')"
        >
          {{ t("guestExit") }}
        </button>
      </div>
    </header>

    <!-- Root view: every complete public share at one place -->
    <div v-if="!share" class="min-h-0 flex-1 overflow-y-auto p-6">
      <p class="mb-4 flex items-center gap-2 text-sm text-muted">
        <Icon name="lock" :size="14" />
        {{ t("guestReadOnlyHint") }}
      </p>

      <div v-if="failed" class="mx-auto mt-12 max-w-md text-center">
        <p class="text-sm text-error">{{ errorText }}</p>
        <button type="button" class="btn btn-outline mt-4" @click="load">
          {{ t("retry") }}
        </button>
      </div>

      <template v-else>
        <!-- Category micro-pills -->
        <div class="mb-6 flex flex-wrap items-center gap-2">
          <button
            type="button"
            class="pill"
            :class="activeCategory === null ? 'pill-active' : ''"
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
              type="button"
              class="pill"
              :class="activeCategory === category ? 'pill-active' : ''"
              @click="setCategory(category)"
            >
              {{ category }}
            </button>
            <button
              v-if="isAdmin"
              type="button"
              class="ml-0.5 grid h-4 w-4 place-items-center rounded-full text-[11px] text-muted opacity-0 transition hover:bg-error/15 hover:text-error group-hover:opacity-100 focus-visible:opacity-100"
              :title="t('guestAdminCategoryDelete')"
              :aria-label="t('guestAdminCategoryDelete') + ': ' + category"
              @click.stop="deleteCategory(category)"
            >
              &times;
            </button>
          </span>
        </div>

        <p v-if="visibleShares.length === 0 && !loading" class="mt-8 text-center text-sm text-muted">
          {{ t("guestEmpty") }}
        </p>

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <div
            v-for="entry in visibleShares"
            :key="entry.token"
            class="card relative flex items-center gap-3 p-4 text-left transition hover:border-line-strong hover:bg-card-hover"
          >
            <button type="button" class="flex min-w-0 flex-1 items-center gap-3" @click="enter(entry)">
              <span class="grid h-10 w-10 shrink-0 place-items-center rounded-md bg-card-hover text-primary">
                <Icon name="folder" :size="20" />
              </span>
              <span class="min-w-0 flex-1">
                <span class="block truncate text-sm font-medium">{{ entry.name }}</span>
                <span class="block truncate text-xs text-muted">
                  {{ entry.ownerDisplay || entry.owner }}
                </span>
              </span>
              <span v-if="entry.category" class="badge normal-case shrink-0">
                {{ entry.category }}
              </span>
            </button>

            <!-- Admin: category assignment dropdown -->
            <div v-if="isAdmin" class="relative shrink-0">
              <button
                type="button"
                class="icon-btn !h-7 !w-7"
                :title="t('guestAdminAssignCategory')"
                :aria-label="t('guestAdminAssignCategory')"
                :aria-expanded="assigningToken === entry.token"
                @click.stop="toggleAssignDropdown(entry.token)"
              >
                <Icon name="edit" :size="14" />
              </button>
              <!-- #365: outside-click closer sits below the menu -->
              <div
                v-if="assigningToken === entry.token"
                class="fixed inset-0 z-20"
                @click="closeAssignDropdown"
              ></div>
              <div
                v-if="assigningToken === entry.token"
                class="menu absolute right-0 top-full z-30 mt-1 w-44 py-1"
              >
                <button
                  v-if="entry.category"
                  type="button"
                  class="block w-full px-3 py-2 text-left text-xs text-error transition hover:bg-error/10"
                  :disabled="assigningLoading"
                  @click="unassignFromCategory(entry.token)"
                >
                  {{ t("guestAdminUnassignCategory") }}
                </button>
                <button
                  v-for="cat in categories"
                  :key="cat"
                  type="button"
                  class="block w-full px-3 py-2 text-left text-xs transition hover:bg-card-hover"
                  :class="cat === entry.category ? 'font-semibold text-primary' : ''"
                  :disabled="assigningLoading"
                  @click="assignToCategory(entry.token, cat)"
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
        <button type="button" class="icon-btn !h-7 !w-7 shrink-0" :title="t('back')" :aria-label="t('back')" @click="leave">
          <Icon name="back" :size="16" />
        </button>
        <button type="button" class="rounded-sm px-2 py-0.5 transition hover:bg-card-hover" :class="crumbs.length ? 'text-muted' : 'font-semibold'" @click="navigateTo('/')">
          {{ share.name }}
        </button>
        <template v-for="(crumb, index) in crumbs" :key="crumb.path">
          <span class="text-muted/60">/</span>
          <button
            type="button"
            class="rounded-sm px-2 py-0.5 transition hover:bg-card-hover"
            :class="index === crumbs.length - 1 ? 'font-semibold' : 'text-muted'"
            :disabled="index === crumbs.length - 1"
            @click="navigateTo(crumb.path)"
          >
            {{ crumb.name }}
          </button>
        </template>
      </div>

      <p v-if="entries.length === 0 && !busyPath" class="mt-8 text-center text-sm text-muted">
        {{ t("guestEmpty") }}
      </p>

      <div class="card overflow-hidden !rounded-md">
        <div
          v-for="entry in entries"
          :key="entry.path"
          class="flex items-center gap-3 border-b border-line px-4 py-2.5 transition last:border-b-0 hover:bg-card-hover"
        >
          <Icon :name="entry.isDir ? 'folder' : 'file'" :size="17" class="shrink-0 text-muted" />
          <button
            type="button"
            class="min-w-0 flex-1 truncate text-left text-sm transition hover:text-primary"
            :title="entry.name"
            @click="entry.isDir ? navigateTo(entry.path) : open(entry)"
          >
            {{ entry.name }}
          </button>
          <span v-if="entry.isDir && isLocked(entry.path)" class="badge normal-case shrink-0">
            <span class="badge-dot bg-error"></span>
            {{ t("guestAdminLocked") }}
          </span>
          <span class="hidden w-24 shrink-0 text-right text-xs tabular-nums text-muted sm:block">
            {{ entry.isDir ? "—" : formatBytes(entry.size) }}
          </span>
          <!-- Admin: lock/unlock toggle for directories -->
          <button
            v-if="isAdmin && entry.isDir"
            type="button"
            class="icon-btn !h-7 !w-7 shrink-0"
            :class="{ '!text-error': isLocked(entry.path) }"
            :title="isLocked(entry.path) ? t('guestAdminUnlock') : t('guestAdminLock')"
            :aria-label="isLocked(entry.path) ? t('guestAdminUnlock') : t('guestAdminLock')"
            :disabled="lockBusy === entry.path"
            @click="toggleLock(entry)"
          >
            <Icon :name="isLocked(entry.path) ? 'unlock' : 'lock'" :size="15" />
          </button>
          <button
            v-else-if="!entry.isDir"
            type="button"
            class="icon-btn !h-7 !w-7 shrink-0"
            :title="t('download')"
            :aria-label="t('download')"
            :disabled="busyPath !== null"
            @click="download(entry)"
          >
            <Icon name="download" :size="15" />
          </button>
          <span v-else class="w-7 shrink-0"></span>
        </div>
      </div>
    </div>

    <!-- Admin: create category dialog -->
    <div
      v-if="showCategoryDialog"
      class="fixed inset-0 z-50 flex items-center justify-center bg-scrim/60 p-4"
      @click.self="showCategoryDialog = false"
    >
      <div class="modal-surface w-80 p-6">
        <h3 class="mb-4 text-sm font-semibold">
          {{ t("guestAdminCategoryCreate") }}
        </h3>
        <input
          v-model="newCategoryName"
          type="text"
          :placeholder="t('guestAdminCategoryName')"
          class="input mb-3"
        />
        <label class="mb-4 flex cursor-pointer select-none items-center gap-2 text-xs text-muted">
          <input v-model="newCategoryPrefixless" type="checkbox" class="checkbox" />
          {{ t("guestAdminCategoryPrefixless") }}
        </label>
        <div class="flex justify-end gap-2">
          <button type="button" class="btn btn-outline" @click="showCategoryDialog = false">
            {{ t("cancel") }}
          </button>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="!newCategoryName.trim()"
            @click="createCategory"
          >
            {{ t("create") }}
          </button>
        </div>
      </div>
    </div>
  </main>
</template>
