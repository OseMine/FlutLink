import { invoke } from "@tauri-apps/api/core";
import { currentLang, translateError } from "./i18n";

/// #399: Buffer the last network-level IPC failure so an error toast can offer
/// a "Retry" action that re-invokes the exact same command without the caller
/// having to capture and replay its arguments manually.
interface FailedCall {
  cmd: string;
  args: Record<string, unknown>;
  at: number;
}

/// L24-F5: only *idempotent read* commands may be retried automatically —
/// re-running a mutation (delete/copy/move/share-create/upload) after a lost
/// response could double-apply it on the server. Every command name in the
/// api wrapper above is a read.
const RETRY_SAFE_COMMANDS = new Set([
  "get_flutcloud_url",
  "account_list",
  "account_filter_info",
  "account_storage",
  "webdav_list",
  "webdav_search",
  "webdav_list_shares",
  "webdav_thumbnail",
  "file_history_list",
  "sync_log_list",
  "sync_list",
  "sync_synced_paths",
  "guest_verify_server",
  "guest_list_shares",
  "guest_list_entries",
  "guest_admin_list_locks",
  "admin_list_users",
  "admin_get_user",
  "admin_list_groups",
  "mount_default_cache",
  "get_mount_status",
  "check_update",
]);

/// Notified after a buffered command was retried successfully, so the view
/// that originally failed can reload and leave its error state (L24-F5: the
/// retry result must reach the original caller, even though the IPC layer
/// cannot return the value to a component that already unwound its promise).
type RetrySuccessHandler = (cmd: string) => void;
const retrySuccessHandlers: RetrySuccessHandler[] = [];

export function onRetrySuccess(handler: RetrySuccessHandler): void {
  retrySuccessHandlers.push(handler);
}

function notifyRetrySuccess(cmd: string) {
  for (const handler of retrySuccessHandlers) handler(cmd);
}

let lastFailed: FailedCall | null = null;
const RETRY_WINDOW_MS = 60_000;

function tauri<T>(cmd: string, args: Record<string, unknown> = {}): Promise<T> {
  return invoke<T>(cmd, args).catch((e: unknown) => {
    // Only network-level failures (`http` in the backend) are worth retrying;
    // logic errors (404, forbidden, ...) would fail again identically. And only
    // idempotent reads may be re-run — see RETRY_SAFE_COMMANDS (L24-F5).
    if (
      RETRY_SAFE_COMMANDS.has(cmd) &&
      typeof e === "object" &&
      e !== null &&
      "code" in e &&
      (e as Partial<AppError>).code === "http"
    ) {
      lastFailed = { cmd, args, at: Date.now() };
    }
    throw e;
  });
}

/// Re-invoke the last buffered read command. Returns true on success (and
/// clears the buffer), false when there is nothing buffered or the retry fails.
export async function retryLast(): Promise<boolean> {
  const failed = lastFailed;
  if (!failed || Date.now() - failed.at > RETRY_WINDOW_MS) return false;
  try {
    const result = await invoke(failed.cmd, failed.args);
    lastFailed = null;
    notifyRetrySuccess(failed.cmd);
    // The value is delivered to the waiting views via onRetrySuccess; the
    // boolean is the button's own success signal.
    void result;
    return true;
  } catch {
    lastFailed = { ...failed, at: Date.now() };
    return false;
  }
}

/// True while a recent network failure is buffered that a Retry button could
/// act on.
export function canRetry(): boolean {
  return lastFailed !== null && Date.now() - lastFailed.at <= RETRY_WINDOW_MS;
}

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
  uidOwner: string | null;
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

export interface ShareUpdateOptions {
  // undefined keeps the current value; "" clears password/expiry.
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

export interface MountStatus {
  isMounted: boolean;
  mountPoint: string | null;
  serverUrl: string | null;
  cacheDir: string;
}

export type SyncState = "idle" | "syncing" | "paused" | "error";

export interface SyncFolderStatus {
  folderId: string;
  accountKey: string;
  localPath: string;
  remotePath: string;
  paused: boolean;
  followSymlinks: boolean;
  uploadPaused: boolean;
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

// #427: recently-opened remote file.
export interface FileHistoryEntry {
  path: string;
  name: string;
  openedAt: number;
}

// #407: sync log entry.
export interface SyncLogEntry {
  timestamp: number;
  folderId: string;
  action: string;
  path: string;
  result: string;
  detail?: string | null;
}

// #410 (L24-F2): mirror of the Rust `AppSettings` serialize model
// (src-tauri/src/settings.rs, camelCase).
export interface AppSettings {
  shareNotifyEnabled: boolean;
  shareSeen: Record<string, number[]>;
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
  getFlutcloudUrl: () => tauri<string>("get_flutcloud_url"),

  accountAdd: (instanceUrl: string, username: string, token: string) =>
    tauri<AccountMeta>("account_add", { instanceUrl, username, token }),

  registerUser: (input: {
    instanceUrl: string;
    username: string;
    password: string;
    displayName?: string;
    adminUsername: string;
    adminPassword: string;
  }) => tauri<AccountMeta>("register_user", input),

  accountList: () => tauri<AccountMeta[]>("account_list"),

  accountSwitch: (username: string, instanceUrl: string) =>
    tauri<AccountMeta>("account_switch", { username, instanceUrl }),

  accountRemove: (username: string, instanceUrl: string) =>
    tauri<AccountMeta[]>("account_remove", { username, instanceUrl }),

  accountStorage: () => tauri<StorageResult>("account_storage"),

  accountFilterInfo: () =>
    tauri<AccountFilterInfo | null>("account_filter_info"),

  webdavList: (path: string, targetUser?: string) =>
    tauri<WebDavListResult>("webdav_list", { path, targetUser }),

  webdavSearch: (query: string, targetUser?: string) =>
    tauri<WebDavEntry[]>("webdav_search", { query, targetUser }),

  webdavCreateShare: (
    path: string,
    options: CreateShareOptions,
    targetUser?: string
  ) =>
    tauri<Share>("webdav_create_share", {
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
    tauri<Share[]>("webdav_list_shares", { path, targetUser }),

  webdavDeleteShare: (shareId: number, targetUser?: string) =>
    tauri<void>("webdav_delete_share", { shareId, targetUser }),

  // #406: password/expiry/public-upload changes; undefined leaves the server
  // value untouched, "" clears password/expiry.
  webdavUpdateShare: (
    shareId: number,
    options: ShareUpdateOptions,
    targetUser?: string
  ) =>
    tauri<void>("webdav_update_share", {
      shareId,
      targetUser,
      update: {
        password: options.password,
        expireDate: options.expireDate,
        publicUpload: options.publicUpload,
      },
    }),

  webdavUploadFile: (
    remotePath: string,
    localPath: string,
    targetUser?: string,
    overwrite = false
  ) => tauri<void>("webdav_upload_file", { remotePath, localPath, targetUser, overwrite }),

  webdavDownloadFile: (remotePath: string, localPath: string, targetUser?: string) =>
    tauri<void>("webdav_download_file", { remotePath, localPath, targetUser }),

  openRemoteFile: (remotePath: string, targetUser?: string) =>
    tauri<void>("open_remote_file", { remotePath, targetUser }),

  // #427: recently opened files.
  fileHistoryList: () => tauri<FileHistoryEntry[]>("file_history_list"),
  fileHistoryClear: () => tauri<void>("file_history_clear"),

// #410: share notification toggle (+ L24-F2: the backend flag is the single
  // source of truth, this command persists it; `getSettings` reads it back).
  setShareNotify: (enabled: boolean) => tauri<void>("set_share_notify", { enabled }),
  getSettings: () => tauri<AppSettings>("get_settings"),

  // #421: synced paths for status icons.
  syncSyncedPaths: (accountKey: string) => tauri<string[]>("sync_synced_paths", { accountKey }),

  // #407: sync log.
  syncLogList: (limit?: number) => tauri<SyncLogEntry[]>("sync_log_list", { limit }),
  syncLogClear: () => tauri<void>("sync_log_clear"),

  webdavDownloadZip: (remotePath: string, localPath: string, targetUser?: string) =>
    tauri<void>("webdav_download_zip", { remotePath, localPath, targetUser }),

  webdavThumbnail: (path: string, size?: number, targetUser?: string) =>
    tauri<string | null>("webdav_thumbnail", { path, size, targetUser }),

  webdavDelete: (path: string, targetUser?: string) =>
    tauri<void>("webdav_delete", { path, targetUser }),

  webdavBulkDelete: (paths: string[], targetUser?: string) =>
    tauri<void>("webdav_bulk_delete", { paths, targetUser }),

  webdavBulkDownload: (targets: BulkTarget[], destDir: string, targetUser?: string) =>
    tauri<void>("webdav_bulk_download", { targets, destDir, targetUser }),

  webdavUploadLocalPaths: (
    localPaths: string[],
    remoteDir: string,
    targetUser?: string,
    overwrite = false
  ) =>
    tauri<void>("webdav_upload_local_paths", {
      localPaths,
      remoteDir,
      targetUser,
      overwrite,
    }),

  webdavMkdir: (path: string, targetUser?: string) =>
    tauri<void>("webdav_mkdir", { path, targetUser }),

  webdavRename: (path: string, newName: string, targetUser?: string) =>
    tauri<void>("webdav_rename", { path, newName, targetUser }),

  webdavCopy: (source: string, destFolder: string, targetUser?: string) =>
    tauri<void>("webdav_copy", { source, destFolder, targetUser }),

  webdavMove: (source: string, destFolder: string, targetUser?: string) =>
    tauri<void>("webdav_move", { source, destFolder, targetUser }),

  // Guest access (complete public shares, no account required):
  guestVerifyServer: () => tauri<void>("guest_verify_server"),

  guestListShares: () => tauri<GuestShare[]>("guest_list_shares"),

  guestListEntries: (token: string, path?: string) =>
    tauri<GuestListing>("guest_list_entries", { token, path }),

  guestDownloadFile: (token: string, remotePath: string, localPath: string) =>
    tauri<void>("guest_download_file", { token, remotePath, localPath }),

  guestOpenFile: (token: string, remotePath: string) =>
    tauri<void>("guest_open_file", { token, remotePath }),

  // Guest admin (require authenticated admin session):
  guestAdminSetCategory: (name: string, prefixless: boolean, visibility: string = "public") =>
    tauri<void>("guest_admin_set_category", { name, prefixless, visibility }),

  guestAdminDeleteCategory: (name: string) =>
    tauri<void>("guest_admin_delete_category", { name }),

  guestAdminAssignCategory: (token: string, category: string) =>
    tauri<void>("guest_admin_assign_category", { token, category }),

  guestAdminUnassignCategory: (token: string) =>
    tauri<void>("guest_admin_unassign_category", { token }),

  guestAdminLockPath: (token: string, path: string) =>
    tauri<string[]>("guest_admin_lock_path", { token, path }),

  guestAdminUnlockPath: (token: string, path: string) =>
    tauri<string[]>("guest_admin_unlock_path", { token, path }),

  /** #373: current lock list of a share, so locked folders render locked. */
  guestAdminListLocks: (token: string) =>
    tauri<string[]>("guest_admin_list_locks", { token }),

  adminListUsers: (
    search: string,
    limit?: number,
    offset?: number
  ): Promise<AdminUsersResult> =>
    tauri<AdminUsersResult>("admin_list_users", { search, limit, offset }),

  adminGetUser: (userId: string) =>
    tauri<UserDetails>("admin_get_user", { userId }),

  adminSetUserQuota: (userId: string, quota: string) =>
    tauri<string>("admin_set_user_quota", { userId, quota }),

  adminEditUser: (userId: string, key: string, value: string) =>
    tauri<string>("admin_edit_user", { userId, key, value }),

  adminCreateUser: (userId: string, password: string, displayName?: string) =>
    tauri<string>("admin_create_user", { userId, password, displayName }),

  adminDeleteUser: (userId: string) =>
    tauri<string>("admin_delete_user", { userId }),

  adminListGroups: (search: string) =>
    tauri<string[]>("admin_list_groups", { search }),

  adminCreateGroup: (groupId: string) =>
    tauri<string>("admin_create_group", { groupId }),

  adminAddGroupMember: (groupId: string, userId: string) =>
    tauri<string>("admin_add_group_member", { groupId, userId }),

  adminRemoveGroupMember: (groupId: string, userId: string) =>
    tauri<string>("admin_remove_group_member", { groupId, userId }),

  syncList: () => tauri<SyncFolderStatus[]>("sync_list"),

  syncAdd: (localPath: string, followSymlinks?: boolean) =>
    tauri<SyncFolderStatus>("sync_add", {
      localPath,
      followSymlinks: followSymlinks ?? false,
    }),

  syncRemove: (folderId: string) => tauri<void>("sync_remove", { folderId }),

  syncSetPaused: (folderId: string, paused: boolean) =>
    tauri<void>("sync_set_paused", { folderId, paused }),

  syncTrigger: () => tauri<void>("sync_trigger"),

  mountDefaultCache: () => tauri<string | null>("mount_default_cache"),

  mountDisk: (customCacheDir?: string) =>
    tauri<MountStatus>("mount_disk", { customCacheDir }),

  unmountDisk: () => tauri<void>("unmount_disk"),

  getMountStatus: () => tauri<MountStatus>("get_mount_status"),

  checkUpdate: () => tauri<ReleaseInfo | null>("check_update"),

  downloadAndInstallUpdate: () =>
    tauri<void>("download_and_install_update"),
};
