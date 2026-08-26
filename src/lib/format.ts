export function formatBytes(bytes: number | null): string {
  if (bytes === null) return "—";
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(1)} ${units[unit]}`;
}

/// Up to two leading alphanumerics of `name`, uppercased — used as the text
/// inside avatar circles where no profile image exists.
export function initials(name: string): string {
  const chars = name.replace(/[^a-zA-Z0-9]/g, "");
  return chars.slice(0, 2).toUpperCase();
}
