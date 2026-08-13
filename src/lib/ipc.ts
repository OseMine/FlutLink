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

  adminSetUserQuota: (userId: string, quotaBytes: number) =>
    invoke<string>("admin_set_user_quota", { userId, quotaBytes }),

  adminEditUser: (userId: string, key: string, value: string) =>
    invoke<string>("admin_edit_user", { userId, key, value }),
};
