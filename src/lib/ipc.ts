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

export interface AccountFilterInfo {
  droppedCount: number;
  serverUrl: string | null;
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

export interface ReleaseInfo {
  version: string;
  name: string;
  notes: string | null;
  releaseUrl: string;
  assetName: string;
  assetUrl: string;
  assetSize: number;
  assetSha256: string | null;
}

export interface UpdateProgress {
  downloaded: number;
  total: number;
  percent: number;
}

export interface TransferProgress {
  direction: "upload" | "download" | "delete";
  path: string;
  index: number;
  totalFiles: number;
  transferred: number;
  total: number;
  percent: number;
}

export interface BulkTarget {
  path: string;
  isDir: boolean;
}

function describe(e: unknown): string {
  if (typeof e === "string") return e;
  const err = e as Partial<AppError>;
  return err?.message ?? "Unknown error";
}

export type AppErrorLike = Error & { code?: string };

export function invokeError(e: unknown): AppErrorLike {
  const err = new Error(describe(e)) as AppErrorLike;
  if (typeof e === "object" && e !== null && "code" in e) {
    err.code = (e as Partial<AppError>).code;
  }
  return err;
}

export const api = {
  getFlutcloudUrl: () => invoke<string>("get_flutcloud_url"),

  accountAdd: (instanceUrl: string, username: string, token: string) =>
    invoke<AccountMeta>("account_add", { instanceUrl, username, token }),

  registerUser: (input: {
    instanceUrl: string;
    username: string;
    password: string;
    displayName?: string;
    adminUsername: string;
    adminPassword: string;
  }) => invoke<AccountMeta>("register_user", input),

  accountList: () => invoke<AccountMeta[]>("account_list"),

  accountActive: () => invoke<AccountMeta | null>("account_active"),

  accountSwitch: (username: string, instanceUrl: string) =>
    invoke<AccountMeta>("account_switch", { username, instanceUrl }),

  accountRemove: (username: string, instanceUrl: string) =>
    invoke<AccountMeta[]>("account_remove", { username, instanceUrl }),

  accountStorage: () => invoke<UserQuota | null>("account_storage"),

  accountFilterInfo: () =>
    invoke<AccountFilterInfo | null>("account_filter_info"),

  webdavList: (path: string, targetUser?: string) =>
    invoke<WebDavEntry[]>("webdav_list", { path, targetUser }),

  webdavCreateShare: (path: string, targetUser?: string) =>
    invoke<string>("webdav_create_share", { path, targetUser }),

  webdavUploadFile: (remotePath: string, localPath: string, targetUser?: string) =>
    invoke<void>("webdav_upload_file", { remotePath, localPath, targetUser }),

  webdavDownloadFile: (remotePath: string, localPath: string, targetUser?: string) =>
    invoke<void>("webdav_download_file", { remotePath, localPath, targetUser }),

  webdavDelete: (path: string, targetUser?: string) =>
    invoke<void>("webdav_delete", { path, targetUser }),

  webdavBulkDelete: (paths: string[], targetUser?: string) =>
    invoke<void>("webdav_bulk_delete", { paths, targetUser }),

  webdavBulkDownload: (targets: BulkTarget[], destDir: string, targetUser?: string) =>
    invoke<void>("webdav_bulk_download", { targets, destDir, targetUser }),

  webdavUploadLocalPaths: (localPaths: string[], remoteDir: string, targetUser?: string) =>
    invoke<void>("webdav_upload_local_paths", {
      localPaths,
      remoteDir,
      targetUser,
    }),

  webdavMkdir: (path: string, targetUser?: string) =>
    invoke<void>("webdav_mkdir", { path, targetUser }),

  webdavRename: (path: string, newName: string, targetUser?: string) =>
    invoke<void>("webdav_rename", { path, newName, targetUser }),

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

  checkUpdate: () => invoke<ReleaseInfo | null>("check_update"),

  downloadAndInstallUpdate: () =>
    invoke<void>("download_and_install_update"),
};
