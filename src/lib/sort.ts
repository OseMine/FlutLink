import type { WebDavEntry } from "./ipc";

export type EntrySortKey = "name" | "size" | "mtime";

/// Shared entry-sort comparator (folders first, then by `key`/direction).
/// Single source of truth for FileExplorer's keyboard navigation order and
/// EntryList's display order so both can never drift apart (L12-N5).
export function compareEntries(
  a: WebDavEntry,
  b: WebDavEntry,
  key: EntrySortKey,
  asc: boolean
): number {
  if (key === "size") {
    const av = a.size ?? 0;
    const bv = b.size ?? 0;
    return asc ? av - bv : bv - av;
  }
  if (key === "mtime") {
    const av = a.mtime ? new Date(a.mtime).getTime() : 0;
    const bv = b.mtime ? new Date(b.mtime).getTime() : 0;
    return asc ? av - bv : bv - av;
  }
  const av = a.name.toLowerCase();
  const bv = b.name.toLowerCase();
  return asc ? av.localeCompare(bv) : bv.localeCompare(av);
}

/// Folders first, then files — each group sorted with `compareEntries`.
export function sortEntries(
  entries: WebDavEntry[],
  key: EntrySortKey,
  asc: boolean
): WebDavEntry[] {
  const dirs = entries.filter((e) => e.isDir);
  const others = entries.filter((e) => !e.isDir);
  const cmp = (a: WebDavEntry, b: WebDavEntry) => compareEntries(a, b, key, asc);
  dirs.sort(cmp);
  others.sort(cmp);
  return [...dirs, ...others];
}
