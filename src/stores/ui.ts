import { defineStore } from "pinia";
import { ref } from "vue";
import { currentLang, LANG_KEY, type Lang } from "../lib/i18n";

export type Theme = "operationflut" | "midnight" | "system";

export interface Toast {
  id: number;
  message: string;
  type: "info" | "success" | "error";
}

const THEME_KEY = "flutlink.theme";

function read<T>(key: string): T | null {
  const raw = localStorage.getItem(key);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

export const useUiStore = defineStore("ui", () => {
  const lang = ref<Lang>(currentLang());
  const theme = ref<Theme>(read<Theme>(THEME_KEY) ?? "operationflut");
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

  return { lang, theme, toasts, setLang, setTheme, toast, dismiss };
});
