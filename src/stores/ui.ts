import { defineStore } from "pinia";
import { ref } from "vue";
import { api } from "../lib/ipc";
import { currentLang, LANG_KEY, type Lang } from "../lib/i18n";
import type { EntrySortKey } from "../lib/sort";

export type Theme = "midnight" | "light" | "system";
export type ViewMode = "list" | "grid";

export interface Toast {
  id: number;
  message: string;
  type: "info" | "success" | "error";
}

/// #368: view/sort preferences of the file explorer survive app restarts and
/// tab switches (the `v-if` in App.vue destroys the component otherwise).
export interface FilesViewPrefs {
  viewMode: ViewMode;
  sortKey: EntrySortKey;
  sortAsc: boolean;
}

/// #416: a quick-access folder bookmark (persisted, max. 20 entries).
export interface FolderBookmark {
  path: string;
  label: string;
}

const THEME_KEY = "flutlink.theme";
const GUEST_KEY = "flutlink.guestMode";
const FILES_VIEW_KEY = "flutlink.filesView";
const BOOKMARKS_KEY = "flutlink.bookmarks";
const SHARE_NOTIFY_KEY = "flutlink.shareNotify";
const DISKMOUNT_KEY = "flutlink.diskMount";
const DISKMOUNT_CACHE_KEY = "flutlink.diskMountCache";
const AUTOSTART_KEY = "flutlink.autostart";
const MAX_BOOKMARKS = 20;

const DEFAULT_FILES_VIEW: FilesViewPrefs = {
  viewMode: "list",
  sortKey: "name",
  sortAsc: true,
};

function normalizeFilesView(value: FilesViewPrefs | null): FilesViewPrefs {
  if (!value) return { ...DEFAULT_FILES_VIEW };
  return {
    viewMode: value.viewMode === "grid" ? "grid" : "list",
    sortKey:
      value.sortKey === "size" || value.sortKey === "mtime"
        ? value.sortKey
        : "name",
    sortAsc: typeof value.sortAsc === "boolean" ? value.sortAsc : true,
  };
}

function read<T>(key: string): T | null {
  const raw = localStorage.getItem(key);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

// Only Deep Midnight, Bright Daylight and System remain; legacy values
// ("operationflut"/"dark") resolve to the surviving dark theme.
function normalizeTheme(value: Theme | null): Theme {
  return value === "light" || value === "system" ? value : "midnight";
}

// #416: dedupe + cap bookmarks on load so corrupt storage can never crash the app.
function normalizeBookmarks(value: FolderBookmark[] | null): FolderBookmark[] {
  if (!Array.isArray(value)) return [];
  const seen = new Set<string>();
  const out: FolderBookmark[] = [];
  for (const b of value) {
    if (!b || typeof b.path !== "string" || !b.path || seen.has(b.path)) continue;
    seen.add(b.path);
    out.push({
      path: b.path,
      label: typeof b.label === "string" && b.label ? b.label : b.path,
    });
    if (out.length >= MAX_BOOKMARKS) break;
  }
  return out;
}

export const useUiStore = defineStore("ui", () => {
  const lang = ref<Lang>(currentLang());
  const theme = ref<Theme>(normalizeTheme(read<Theme>(THEME_KEY)));
  // Guest mode (read-only browsing of public shares without an account).
  const guestMode = ref<boolean>(read<boolean>(GUEST_KEY) ?? false);
  // Persisted file-explorer layout preferences (#368).
  const filesView = ref<FilesViewPrefs>(normalizeFilesView(read<FilesViewPrefs>(FILES_VIEW_KEY)));
  const toasts = ref<Toast[]>([]);
  const bookmarks = ref<FolderBookmark[]>(normalizeBookmarks(read<FolderBookmark[]>(BOOKMARKS_KEY)));
  // #410: share notifications (persisted).
  const shareNotify = ref<boolean>(read<boolean>(SHARE_NOTIFY_KEY) ?? true);
  // Disk-mount preference (persisted).
  const diskMount = ref<boolean>(read<boolean>(DISKMOUNT_KEY) ?? false);
  // Local cache folder for the mounted drive (persisted, empty = not set).
  const diskMountCachePath = ref<string>(read<string>(DISKMOUNT_CACHE_KEY) ?? "");
  // Autostart preference (backend-backed, persisted in settings.json).
  const autostart = ref<boolean>(false);
  let nextId = 1;

  function setLang(value: Lang) {
    lang.value = value;
    localStorage.setItem(LANG_KEY, JSON.stringify(value));
  }

  function setTheme(value: Theme) {
    theme.value = value;
    localStorage.setItem(THEME_KEY, JSON.stringify(value));
  }

  function setGuestMode(value: boolean) {
    guestMode.value = value;
    localStorage.setItem(GUEST_KEY, JSON.stringify(value));
  }

  function setFilesView(patch: Partial<FilesViewPrefs>) {
    filesView.value = normalizeFilesView({ ...filesView.value, ...patch });
    localStorage.setItem(FILES_VIEW_KEY, JSON.stringify(filesView.value));
  }

  function toast(message: string, type: Toast["type"] = "info") {
    // L24-N7: dedupe — don't stack a second identical toast while the first is
    // still visible (e.g. the impersonation "enter a search term" hint on
    // repeated Enter/refresh presses).
    if (toasts.value.some((t) => t.message === message && t.type === type)) return;
    const id = nextId++;
    toasts.value.push({ id, message, type });
    window.setTimeout(() => {
      toasts.value = toasts.value.filter((t) => t.id !== id);
    }, 4000);
  }

  function dismiss(id: number) {
    toasts.value = toasts.value.filter((t) => t.id !== id);
  }

  // #416: folder bookmarks (persisted to localStorage like filesView).
  function isBookmarked(path: string): boolean {
    return bookmarks.value.some((b) => b.path === path);
  }

  function addBookmark(path: string, label: string) {
    if (isBookmarked(path)) return;
    bookmarks.value = [...bookmarks.value, { path, label: label || path }].slice(0, MAX_BOOKMARKS);
    localStorage.setItem(BOOKMARKS_KEY, JSON.stringify(bookmarks.value));
  }

  function removeBookmark(path: string) {
    bookmarks.value = bookmarks.value.filter((b) => b.path !== path);
    localStorage.setItem(BOOKMARKS_KEY, JSON.stringify(bookmarks.value));
  }

  // #410 (L24-F2): the backend settings file is the source of truth for the
  // sync worker. Persist the toggle immediately and keep gutter/localStorage
  // mirrored without awaiting — the SettingsModal also seeds this ref from
  // `getSettings` on open (`inheritShareNotify`).
  function setShareNotify(value: boolean) {
    shareNotify.value = value;
    localStorage.setItem(SHARE_NOTIFY_KEY, JSON.stringify(value));
    void api.setShareNotify(value).catch(() => {
      // The toggle stays optimistic; the backend worker continues with its own
      // persisted flag until the settings dialog is reopened and re-seeded.
    });
  }

  // L24-F2: adopt the backend's persisted flags (called when the settings
  // dialog opens or after binding an account).
  function inheritShareNotify(enabled: boolean) {
    shareNotify.value = enabled;
    localStorage.setItem(SHARE_NOTIFY_KEY, JSON.stringify(enabled));
  }

  function setDiskMount(value: boolean) {
    diskMount.value = value;
    localStorage.setItem(DISKMOUNT_KEY, JSON.stringify(value));
  }

  function setDiskMountCachePath(value: string) {
    diskMountCachePath.value = value;
    localStorage.setItem(DISKMOUNT_CACHE_KEY, JSON.stringify(value));
  }

  function setAutostart(value: boolean) {
    autostart.value = value;
    localStorage.setItem(AUTOSTART_KEY, JSON.stringify(value));
    void api.setAutostart(value).catch(() => {});
  }

  function inheritAutostart(enabled: boolean) {
    autostart.value = enabled;
    localStorage.setItem(AUTOSTART_KEY, JSON.stringify(enabled));
  }

  return {
    lang,
    theme,
    guestMode,
    filesView,
    toasts,
    bookmarks,
    shareNotify,
    diskMount,
    diskMountCachePath,
    autostart,
    setLang,
    setTheme,
    setGuestMode,
    setFilesView,
    setShareNotify,
    inheritShareNotify,
    setDiskMount,
    setDiskMountCachePath,
    setAutostart,
    inheritAutostart,
    toast,
    dismiss,
    isBookmarked,
    addBookmark,
    removeBookmark,
  };
});
