/// L19-N1: Escape is the expected way to close menus/modals on the desktop.
///
/// Every open overlay registers a close callback here; App.vue installs a
/// single global `keydown` listener that pops and invokes the most recently
/// registered one, so repeated Escape presses peel overlays off in reverse
/// opening order instead of closing everything at once.
type EscapeCloser = () => void;

const stack: EscapeCloser[] = [];

let installed = false;

/// Register the closer of a freshly opened overlay. Returns the unregister
/// function — call it when the overlay closes or the component unmounts.
export function registerEscapeCloser(close: EscapeCloser): () => void {
  stack.push(close);
  return () => {
    const index = stack.lastIndexOf(close);
    if (index >= 0) stack.splice(index, 1);
  };
}

/// Install the single global listener (idempotent; called once from App.vue).
export function installEscapeHandler(): void {
  if (installed) return;
  installed = true;
  window.addEventListener("keydown", (event) => {
    if (event.key !== "Escape") return;
    const top = stack[stack.length - 1];
    if (!top) return;
    event.preventDefault();
    event.stopPropagation();
    top();
  });
}
