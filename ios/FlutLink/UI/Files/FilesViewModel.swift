// TEST — iOS port created by opencode. Not a production build.
import Foundation

@MainActor
final class FilesViewModel: ObservableObject {
    private let sessionManager: SessionManager

    @Published var path = "/"
    @Published var entries: [WebDavEntry] = []
    @Published var loading = false
    @Published var errorMessage: String?
    @Published var offline = false
    @Published var quota: Quota?

    @Published var targetUser: String?
    @Published var searchQuery = ""
    @Published var searchResults: [WebDavEntry] = []
    @Published var searching = false

    @Published var lastShare: Share?
    @Published var shares: [Share] = []
    @Published var sharesLoading = false
    @Published var pendingUpload: PendingUpload?
    @Published var downloadedData: Data?
    @Published var downloadedFileName: String?
    @Published var shareData: Data?
    @Published var shareFileName: String?
    @Published var toastMessage: String?
    @Published var transferProgress: TransferProgress?

    private let listCache = ListCache()
    private var searchTask: Task<Void, Never>?

    var session: AuthSession? { sessionManager.session }

    var sessionKey: String? {
        session.map { "\($0.baseUrl)|\($0.username)|\(targetUser ?? "")" }
    }

    private var isAdmin: Bool {
        guard let s = session else { return false }
        return sessionManager.accounts.contains { $0.username == s.username && $0.instanceUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) == s.baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) && $0.isAdmin }
    }

    init(sessionManager: SessionManager) {
        self.sessionManager = sessionManager
    }

    func setTargetUser(_ userId: String?) {
        guard let s = session else { return }
        let target = (userId != s.username) ? userId : nil
        if target != nil && !isAdmin {
            errorMessage = NSLocalizedString("error_not_admin_impersonation", comment: "")
            return
        }
        targetUser = target
        path = "/"
        listFolder("/")
    }

    func refresh() {
        listFolder(path)
        refreshQuota()
    }

    func listFolder(_ folderPath: String) {
        guard let s = session else { return }
        loading = true; errorMessage = nil
        Task {
            do {
                let api = FlutCloudApi(session: s)
                let dav = WebDavApi(session: s)
                let result = try await dav.list(path: folderPath, targetUser: targetUser)
                    .sorted { a, b in
                        if a.isDir != b.isDir { return a.isDir }
                        return a.name.lowercased() < b.name.lowercased()
                    }
                entries = result
                offline = false
                if let key = sessionKey {
                    listCache.write(accountKey: key, path: folderPath, entries: result)
                }
                path = folderPath
                _ = api
            } catch let error as ApiException {
                errorMessage = Self.mapError(error)
            } catch {
                if let key = sessionKey, let cached = listCache.read(accountKey: key, path: folderPath) {
                    entries = cached
                    offline = true
                    path = folderPath
                } else {
                    errorMessage = Self.mapError(error)
                }
            }
            loading = false
        }
    }

    func refreshQuota() {
        guard let s = session else { return }
        Task { quota = await FlutCloudApi(session: s).getCurrentQuota() }
    }

    func open(_ entry: WebDavEntry) {
        if entry.isDir {
            listFolder(entry.path)
        } else {
            downloadAndOpen(entry)
        }
    }

    private func downloadAndOpen(_ entry: WebDavEntry) {
        guard let s = session else { return }
        loading = true; errorMessage = nil
        Task {
            do {
                let dav = WebDavApi(session: s)
                let data = try await dav.downloadToFile(path: entry.path, targetUser: targetUser) { [weak self] transferred, total in
                    Task { @MainActor in self?.transferProgress = TransferProgress(transferred: transferred, total: total) }
                }
                downloadedData = data
                downloadedFileName = entry.name
            } catch {
                errorMessage = Self.mapError(error)
            }
            loading = false; transferProgress = nil
        }
    }

    func downloadToDownloads(_ entry: WebDavEntry) {
        guard let s = session else { return }
        loading = true; errorMessage = nil
        Task {
            do {
                let dav = WebDavApi(session: s)
                let data = try await dav.downloadToFile(path: entry.path, targetUser: targetUser) { [weak self] transferred, total in
                    Task { @MainActor in self?.transferProgress = TransferProgress(transferred: transferred, total: total) }
                }
                let tempUrl = FileManager.default.temporaryDirectory.appendingPathComponent(entry.name)
                try data.write(to: tempUrl)
                toastMessage = String(format: NSLocalizedString("downloaded_to_downloads", comment: ""), entry.name)
            } catch {
                errorMessage = Self.mapError(error)
            }
            loading = false; transferProgress = nil
        }
    }

    func downloadAndShare(_ entry: WebDavEntry) {
        guard let s = session else { return }
        loading = true; errorMessage = nil
        Task {
            do {
                let dav = WebDavApi(session: s)
                let data = try await dav.downloadToFile(path: entry.path, targetUser: targetUser)
                shareData = data
                shareFileName = entry.name
            } catch {
                errorMessage = Self.mapError(error)
            }
            loading = false
        }
    }

    func mkdir(_ name: String, onDone: @escaping () -> Void = {}) {
        guard let s = session, !name.trimmingCharacters(in: .whitespaces).isEmpty,
              name != ".", name != "..", !name.contains("/") else {
            errorMessage = NSLocalizedString("error_invalid_folder_name", comment: "")
            return
        }
        loading = true; errorMessage = nil
        Task {
            do {
                let dav = WebDavApi(session: s)
                let target = path == "/" ? "/\(name)" : "\(path)/\(name)"
                try await dav.mkdir(path: target, targetUser: targetUser)
                listFolder(path)
                onDone()
            } catch {
                errorMessage = Self.mapError(error)
            }
            loading = false
        }
    }

    func rename(_ entry: WebDavEntry, newName: String) {
        guard !newName.trimmingCharacters(in: .whitespaces).isEmpty, newName != entry.name, let s = session else { return }
        loading = true; errorMessage = nil
        Task {
            do {
                let dav = WebDavApi(session: s)
                let newPath = entry.path.deletingLastPathComponent + "/" + newName
                try await dav.rename(path: entry.path, newPath: newPath, targetUser: targetUser)
                listFolder(path)
            } catch {
                errorMessage = Self.mapError(error)
            }
            loading = false
        }
    }

    func delete(_ entry: WebDavEntry) {
        guard let s = session else { return }
        loading = true; errorMessage = nil
        Task {
            do {
                let dav = WebDavApi(session: s)
                try await dav.delete(path: entry.path, targetUser: targetUser)
                entries.removeAll { $0.path == entry.path }
            } catch {
                errorMessage = Self.mapError(error)
            }
            loading = false
        }
    }

    func createShare(entry: WebDavEntry, shareType: Int, shareWith: String? = nil, password: String? = nil, expireDate: String? = nil, publicUpload: Bool = false) {
        guard let s = session else { return }
        let with = shareWith?.trimmingCharacters(in: .whitespaces)
        if shareType < 3, with?.isEmpty ?? true {
            errorMessage = NSLocalizedString("share_recipient_required", comment: "")
            return
        }
        loading = true; errorMessage = nil
        Task {
            do {
                let api = FlutCloudApi(session: s)
                lastShare = try await api.createShare(path: entry.path, shareType: shareType, shareWith: with, password: password?.isEmpty == true ? nil : password, expireDate: expireDate?.isEmpty == true ? nil : expireDate, publicUpload: publicUpload)
                await loadShares(entry)
            } catch {
                errorMessage = Self.mapError(error)
            }
            loading = false
        }
    }

    func loadShares(_ entry: WebDavEntry) async {
        guard let s = session else { return }
        sharesLoading = true
        do {
            shares = try await FlutCloudApi(session: s).listShares(path: entry.path)
        } catch {
            errorMessage = Self.mapError(error)
        }
        sharesLoading = false
    }

    func deleteShare(_ share: Share) {
        guard let s = session else { return }
        Task {
            do {
                try await FlutCloudApi(session: s).deleteShare(shareId: share.id)
                shares.removeAll { $0.id == share.id }
            } catch {
                errorMessage = Self.mapError(error)
            }
        }
    }

    func resetShares() { shares = []; sharesLoading = false }

    func uploadData(_ data: Data, targetDir: String, name: String, contentType: String = "application/octet-stream") {
        guard let s = session else { return }
        loading = true; errorMessage = nil
        Task {
            do {
                let dav = WebDavApi(session: s)
                let remotePath = targetDir == "/" ? "/\(name)" : "\(targetDir)/\(name)"
                let exists = try await dav.exists(path: remotePath, targetUser: targetUser)
                if exists {
                    pendingUpload = PendingUpload(targetDir: targetDir, name: name, data: data, contentType: contentType)
                    loading = false
                    return
                }
                try await dav.upload(path: remotePath, data: data, contentType: contentType, targetUser: targetUser)
                listFolder(path)
            } catch {
                errorMessage = Self.mapError(error)
            }
            loading = false
        }
    }

    func confirmUpload() {
        guard let pending = pendingUpload else { return }
        pendingUpload = nil
        uploadData(pending.data, targetDir: pending.targetDir, name: pending.name, contentType: pending.contentType)
    }

    func clearPendingUpload() { pendingUpload = nil }

    func search(_ query: String) {
        guard let s = session else { return }
        searchTask?.cancel()
        searchQuery = query
        if query.trimmingCharacters(in: .whitespaces).isEmpty {
            searchResults = []; searching = false; return
        }
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            searching = true; errorMessage = nil
            do {
                searchResults = try await WebDavApi(session: s).search(query: query.trimmingCharacters(in: .whitespaces), targetUser: targetUser)
            } catch {
                errorMessage = Self.mapError(error)
            }
            searching = false
        }
    }

    func clearSearch() { searchQuery = ""; searchResults = [] }
    func clearError() { errorMessage = nil }
    func clearDownloaded() { downloadedData = nil; downloadedFileName = nil }
    func clearShareData() { shareData = nil; shareFileName = nil }
    func clearLastShare() { lastShare = nil }
    func clearToast() { toastMessage = nil }

    static func mapError(_ error: Error) -> String {
        if let api = error as? ApiException {
            switch api {
            case .flutCloudAppMissing: return NSLocalizedString("error_flutcloud_app_missing", comment: "")
            case .api(let msg, let code, _):
                switch code {
                case "target_exists": return String(format: NSLocalizedString("error_target_exists", comment: ""), msg)
                case "ocs_error": return String(format: NSLocalizedString("error_ocs", comment: ""), msg)
                default:
                    if code.hasPrefix("http_") { return String(format: NSLocalizedString("error_http", comment: ""), msg) }
                    return msg
                }
            case .network(let err): return String(format: NSLocalizedString("error_network_reach_detail", comment: ""), err.localizedDescription)
            }
        }
        return error.localizedDescription
    }
}

private extension String {
    var deletingLastPathComponent: String {
        (self as NSString).deletingLastPathComponent
    }
}
