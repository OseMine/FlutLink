import { invoke } from "@tauri-apps/api/core";

export interface AccountMeta {
  username: string;
  instanceUrl: string;
  displayName: string | null;
  isAdmin: boolean;
  isActive: boolean;
}

export interface WebDavEntry {
  name: string;
  path: string;
  isDir: boolean;
  size: number | null;
  mtime: string | null;
  etag: string | null;
  contentType: string | null;
  isResource: boolean;
  isPart: boolean;
}

export interface OcsUser {
  id: string;
  displayName: string | null;
  isAdmin: boolean;
}

export interface UserQuota {
  total: number | null;
  used: number | null;
  free: number | null;
  relative: number | null;
}

export interface UserDetails {
  id: string;
  displayName: string | null;
  email: string | null;
  quota: UserQuota | null;
  groups: string[];
  enabled: boolean;
}

export type SyncState = "idle" | "syncing" | "paused" | "error";

export interface SyncFolderStatus {
  folderId: string;
  accountKey: string;
  localPath: string;
  remotePath: string;
  paused: boolean;
  state: SyncState;
  pendingUploads: number;
  pendingDownloads: number;
  pendingDeletes: number;
  failures: number;
  lastError: string | null;
  lastSyncedAt: number | null;
}

export interface AppError {
  code: string;
  message: string;
}

function describe(e: unknown): string {
  if (typeof e === "string") return e;
  const err = e as Partial<AppError>;
  return err?.message ?? "Unknown error";
}

export function invokeError(e: unknown): Error {
  return new Error(describe(e));
}

export const api = {
  accountAdd: (instanceUrl: string, username: string, token: string) =>
    invoke<AccountMeta>("account_add", { instanceUrl, username, token }),

  accountList: () => invoke<AccountMeta[]>("account_list"),

  accountActive: () => invoke<AccountMeta | null>("account_active"),

  accountSwitch: (username: string) =>
    invoke<AccountMeta>("account_switch", { username }),

  accountRemove: (username: string) =>
    invoke<AccountMeta[]>("account_remove", { username }),

  accountStorage: () => invoke<UserQuota | null>("account_storage"),

  webdavList: (path: string, targetUser?: string) =>
    invoke<WebDavEntry[]>("webdav_list", { path, targetUser }),

  webdavCreateShare: (path: string) =>
    invoke<string>("webdav_create_share", { path }),

  adminListUsers: (search: string) =>
    invoke<string[]>("admin_list_users", { search }),

  adminGetUser: (userId: string) =>
    invoke<UserDetails>("admin_get_user", { userId }),

  adminSetUserQuota: (userId: string, quota: string) =>
    invoke<string>("admin_set_user_quota", { userId, quota }),

  adminEditUser: (userId: string, key: string, value: string) =>
    invoke<string>("admin_edit_user", { userId, key, value }),

  adminCreateUser: (userId: string, password: string, displayName?: string) =>
    invoke<string>("admin_create_user", { userId, password, displayName }),

  adminDeleteUser: (userId: string) =>
    invoke<string>("admin_delete_user", { userId }),

  syncList: () => invoke<SyncFolderStatus[]>("sync_list"),

  syncAdd: (localPath: string) =>
    invoke<SyncFolderStatus>("sync_add", { localPath }),

  syncRemove: (folderId: string) => invoke<void>("sync_remove", { folderId }),

  syncSetPaused: (folderId: string, paused: boolean) =>
    invoke<void>("sync_set_paused", { folderId, paused }),

  syncTrigger: () => invoke<void>("sync_trigger"),
};
