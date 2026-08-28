/// #408: global keyboard shortcuts (Ctrl/Cmd+F, Ctrl/Cmd+N, Ctrl/Cmd+A,
/// F5, Delete). Components register handlers per action and the one global
/// keydown listener routes matching key combos to them.
///
/// This mirrors the escape-stack pattern in `escape.ts`: components register
/// while mounted and unregister on unmount, so the "hot" handlers always
/// belong to the currently visible view.
export type ShortcutAction =
  | "focus-search"
  | "new-folder"
  | "select-all"
  | "refresh"
  | "delete-selection";

const registry = new Map<ShortcutAction, Set<() => unknown>>();

/// Register a handler for a global shortcut. Returns an unregister function.
export function registerShortcut(
  action: ShortcutAction,
  handler: () => unknown
): () => void {
  let set = registry.get(action);
  if (!set) {
    set = new Set();
    registry.set(action, set);
  }
  set.add(handler);
  return () => {
    registry.get(action)?.delete(handler);
  };
}

function run(action: ShortcutAction) {
  const set = registry.get(action);
  if (!set) return;
  for (const handler of [...set]) handler();
}

let installed = false;

/// Install the single global keydown listener (idempotent, like
/// `installEscapeHandler`). Skipped while the user is typing in a field so
/// text entry keeps its natural behavior.
export function installShortcutHandler() {
  if (installed) return;
  installed = true;
  window.addEventListener("keydown", (e: KeyboardEvent) => {
    const target = e.target as HTMLElement | null;
    const typing =
      !!target &&
      (target.tagName === "INPUT" ||
        target.tagName === "TEXTAREA" ||
        target.tagName === "SELECT" ||
        target.isContentEditable);

    if (e.key === "F5" && !typing) {
      e.preventDefault();
      run("refresh");
      return;
    }

    // Delete/Backspace act on the file selection unless a field is focused.
    if (
      (e.key === "Delete" || e.key === "Backspace") &&
      !typing &&
      !e.ctrlKey &&
      !e.metaKey &&
      !e.altKey
    ) {
      e.preventDefault();
      run("delete-selection");
      return;
    }

    const mod = e.ctrlKey || e.metaKey;
    if (!mod || e.altKey || e.isComposing) return;
    // Ctrl/Cmd+A inside a field keeps its native select-all-text behavior.
    if (typing) return;
    switch (e.key.toLowerCase()) {
      case "f":
        e.preventDefault();
        run("focus-search");
        break;
      case "n":
        e.preventDefault();
        run("new-folder");
        break;
      case "a":
        e.preventDefault();
        run("select-all");
        break;
    }
  });
}