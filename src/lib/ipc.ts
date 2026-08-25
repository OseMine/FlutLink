import { invoke } from "@tauri-apps/api/core";
import { currentLang, translateError } from "./i18n";

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
  linkTarget: string | null;
  pairedPath: string | null;
}

export interface WebDavListResult {
  entries: WebDavEntry[];
  /** True when the listing was served from the offline cache (server unreachable). */
  stale: boolean;
}

export interface StorageResult {
  quota: UserQuota | null;
  /** True when the quota was served from the offline cache (server unreachable). */
  stale: boolean;
}

export interface AdminUsersResult {
  users: string[];
  /** True when a full page was returned and more users can be loaded. */
  hasMore: boolean;
}

export interface Share {
  id: number;
  shareType: number;
  path: string | null;
  shareWith: string | null;
  shareWithDisplayname: string | null;
  permissions: number | null;
  url: string | null;
  hasPassword: boolean | null;
  expiration: string | null;
}

export interface CreateShareOptions {
  shareType?: number;
  shareWith?: string;
  password?: string;
  expireDate?: string;
  publicUpload?: boolean;
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
  /** user@instance of saved accounts whose keyring token could not be read. */
  tokenMissing: string[];
}

export type SyncState = "idle" | "syncing" | "paused" | "error";

export interface SyncFolderStatus {
  folderId: string;
  accountKey: string;
  localPath: string;
  remotePath: string;
  paused: boolean;
  followSymlinks: boolean;
  state: SyncState;
  pendingUploads: number;
  pendingDownloads: number;
  pendingDeletes: number;
  failures: number;
  lastError: { code: string; detail?: string | null } | null;
  lastSyncedAt: number | null;
}

export interface AppError {
  code: string;
  message: string;
  detail?: string | null;
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

export interface UpdateStatus {
  code: string;
  assetName?: string | null;
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

/** A folder shared publicly as a whole (guest access, no account). */
export interface GuestShare {
  token: string;
  name: string;
  owner: string;
  ownerDisplay: string | null;
  category: string | null;
  url: string;
  downloadBase: string;
  mtime: number | null;
}

/** One entry inside a public share folder. */
export interface GuestEntry {
  name: string;
  path: string;
  isDir: boolean;
  size: number | null;
  mtime: number | null;
  contentType: string | null;
}

/** Folder listing inside a public share. */
export interface GuestListing {
  token: string;
  name: string;
  path: string;
  entries: GuestEntry[];
}

function describe(e: unknown): string {
  if (typeof e === "string") return e;
  const err = e as Partial<AppError>;
  if (err?.code) {
    return translateError(currentLang(), err.code, err.detail ?? err.message);
  }
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

  accountSwitch: (username: string, instanceUrl: string) =>
    invoke<AccountMeta>("account_switch", { username, instanceUrl }),

  accountRemove: (username: string, instanceUrl: string) =>
    invoke<AccountMeta[]>("account_remove", { username, instanceUrl }),

  accountStorage: () => invoke<StorageResult>("account_storage"),

  accountFilterInfo: () =>
    invoke<AccountFilterInfo | null>("account_filter_info"),

  webdavList: (path: string, targetUser?: string) =>
    invoke<WebDavListResult>("webdav_list", { path, targetUser }),

  webdavSearch: (query: string, targetUser?: string) =>
    invoke<WebDavEntry[]>("webdav_search", { query, targetUser }),

  webdavCreateShare: (
    path: string,
    options: CreateShareOptions,
    targetUser?: string
  ) =>
    invoke<Share>("webdav_create_share", {
      path,
      targetUser,
      options: {
        shareType: options.shareType,
        shareWith: options.shareWith,
        password: options.password,
        expireDate: options.expireDate,
        publicUpload: options.publicUpload,
      },
    }),

  webdavListShares: (path?: string, targetUser?: string) =>
    invoke<Share[]>("webdav_list_shares", { path, targetUser }),

  webdavDeleteShare: (shareId: number, targetUser?: string) =>
    invoke<void>("webdav_delete_share", { shareId, targetUser }),

  webdavUploadFile: (
    remotePath: string,
    localPath: string,
    targetUser?: string,
    overwrite = false
  ) => invoke<void>("webdav_upload_file", { remotePath, localPath, targetUser, overwrite }),

  webdavDownloadFile: (remotePath: string, localPath: string, targetUser?: string) =>
    invoke<void>("webdav_download_file", { remotePath, localPath, targetUser }),

  openRemoteFile: (remotePath: string, targetUser?: string) =>
    invoke<void>("open_remote_file", { remotePath, targetUser }),

  webdavDownloadZip: (remotePath: string, localPath: string, targetUser?: string) =>
    invoke<void>("webdav_download_zip", { remotePath, localPath, targetUser }),

  webdavThumbnail: (path: string, size?: number, targetUser?: string) =>
    invoke<string | null>("webdav_thumbnail", { path, size, targetUser }),

  webdavDelete: (path: string, targetUser?: string) =>
    invoke<void>("webdav_delete", { path, targetUser }),

  webdavBulkDelete: (paths: string[], targetUser?: string) =>
    invoke<void>("webdav_bulk_delete", { paths, targetUser }),

  webdavBulkDownload: (targets: BulkTarget[], destDir: string, targetUser?: string) =>
    invoke<void>("webdav_bulk_download", { targets, destDir, targetUser }),

  webdavUploadLocalPaths: (
    localPaths: string[],
    remoteDir: string,
    targetUser?: string,
    overwrite = false
  ) =>
    invoke<void>("webdav_upload_local_paths", {
      localPaths,
      remoteDir,
      targetUser,
      overwrite,
    }),

  webdavMkdir: (path: string, targetUser?: string) =>
    invoke<void>("webdav_mkdir", { path, targetUser }),

  webdavRename: (path: string, newName: string, targetUser?: string) =>
    invoke<void>("webdav_rename", { path, newName, targetUser }),

  // Guest access (complete public shares, no account required):
  guestVerifyServer: () => invoke<void>("guest_verify_server"),

  guestListShares: () => invoke<GuestShare[]>("guest_list_shares"),

  guestListEntries: (token: string, path?: string) =>
    invoke<GuestListing>("guest_list_entries", { token, path }),

  guestDownloadFile: (token: string, remotePath: string, localPath: string) =>
    invoke<void>("guest_download_file", { token, remotePath, localPath }),

  guestOpenFile: (token: string, remotePath: string) =>
    invoke<void>("guest_open_file", { token, remotePath }),

  adminListUsers: (
    search: string,
    limit?: number,
    offset?: number
  ): Promise<AdminUsersResult> =>
    invoke<AdminUsersResult>("admin_list_users", { search, limit, offset }),

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

  adminListGroups: (search: string) =>
    invoke<string[]>("admin_list_groups", { search }),

  adminCreateGroup: (groupId: string) =>
    invoke<string>("admin_create_group", { groupId }),

  adminAddGroupMember: (groupId: string, userId: string) =>
    invoke<string>("admin_add_group_member", { groupId, userId }),

  adminRemoveGroupMember: (groupId: string, userId: string) =>
    invoke<string>("admin_remove_group_member", { groupId, userId }),

  syncList: () => invoke<SyncFolderStatus[]>("sync_list"),

  syncAdd: (localPath: string, followSymlinks?: boolean) =>
    invoke<SyncFolderStatus>("sync_add", {
      localPath,
      followSymlinks: followSymlinks ?? false,
    }),

  syncRemove: (folderId: string) => invoke<void>("sync_remove", { folderId }),

  syncSetPaused: (folderId: string, paused: boolean) =>
    invoke<void>("sync_set_paused", { folderId, paused }),

  syncTrigger: () => invoke<void>("sync_trigger"),

  checkUpdate: () => invoke<ReleaseInfo | null>("check_update"),

  downloadAndInstallUpdate: () =>
    invoke<void>("download_and_install_update"),
};
