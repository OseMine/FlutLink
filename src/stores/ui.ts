import { defineStore } from "pinia";
import { ref } from "vue";
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

const THEME_KEY = "flutlink.theme";
const ACCENT_KEY = "flutlink.accentHue";
const GUEST_KEY = "flutlink.guestMode";
const FILES_VIEW_KEY = "flutlink.filesView";

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

export const useUiStore = defineStore("ui", () => {
  const lang = ref<Lang>(currentLang());
  const theme = ref<Theme>(normalizeTheme(read<Theme>(THEME_KEY)));
  // Accent seed hue (#371): null means "use the theme's default hue".
  const accentHue = ref<number | null>(read<number>(ACCENT_KEY) ?? null);
  // Guest mode (read-only browsing of public shares without an account).
  const guestMode = ref<boolean>(read<boolean>(GUEST_KEY) ?? false);
  // Persisted file-explorer layout preferences (#368).
  const filesView = ref<FilesViewPrefs>(normalizeFilesView(read<FilesViewPrefs>(FILES_VIEW_KEY)));
  const toasts = ref<Toast[]>([]);
  let nextId = 1;

  function setLang(value: Lang) {
    lang.value = value;
    localStorage.setItem(LANG_KEY, JSON.stringify(value));
  }

  function setTheme(value: Theme) {
    theme.value = value;
    localStorage.setItem(THEME_KEY, JSON.stringify(value));
  }

  function setAccentHue(value: number | null) {
    accentHue.value = value;
    if (value === null) localStorage.removeItem(ACCENT_KEY);
    else localStorage.setItem(ACCENT_KEY, JSON.stringify(value));
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
    const id = nextId++;
    toasts.value.push({ id, message, type });
    window.setTimeout(() => {
      toasts.value = toasts.value.filter((t) => t.id !== id);
    }, 4000);
  }

  function dismiss(id: number) {
    toasts.value = toasts.value.filter((t) => t.id !== id);
  }

  return {
    lang,
    theme,
    accentHue,
    guestMode,
    filesView,
    toasts,
    setLang,
    setTheme,
    setAccentHue,
    setGuestMode,
    setFilesView,
    toast,
    dismiss,
  };
});
