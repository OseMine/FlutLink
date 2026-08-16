// Material 3 ripple: a circular state layer that expands from the pointer.
// A single delegated listener keeps every `button` / `[role="button"]` /
// anchor interactive without touching each template.
const RIPPLE_SELECTOR = "button, [role='button'], a";

function spawnRipple(el: HTMLElement, clientX: number, clientY: number) {
  const rect = el.getBoundingClientRect();
  const size = Math.max(rect.width, rect.height);
  const span = document.createElement("span");
  span.className = "m3-ripple";
  span.style.width = `${size}px`;
  span.style.height = `${size}px`;
  span.style.left = `${clientX - rect.left - size / 2}px`;
  span.style.top = `${clientY - rect.top - size / 2}px`;
  el.appendChild(span);
  span.addEventListener("animationend", () => span.remove());
}

export function initRipple() {
  document.addEventListener("pointerdown", (e) => {
    if (!(e.target instanceof Element)) return;
    const host = e.target.closest<HTMLElement>(RIPPLE_SELECTOR);
    if (!host) return;
    // The ripple is clipped to the host (like an M3 state layer). Only touch
    // positioning/clipping when the host does not already establish its own
    // box — never override `position: fixed` / `absolute` / `sticky` or an
    // existing `overflow` (R7-4), which could clip dropdowns and tooltips.
    if (getComputedStyle(host).position === "static") {
      host.style.position = "relative";
    }
    if (getComputedStyle(host).overflow === "visible") {
      host.style.overflow = "hidden";
    }
    spawnRipple(host, e.clientX, e.clientY);
  });
}
