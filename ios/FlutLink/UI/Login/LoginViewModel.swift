// TEST — iOS port created by opencode. Not a production build.
import Foundation

@MainActor
final class LoginViewModel: ObservableObject {
    private let sessionManager: SessionManager
    private let settingsStore: SettingsStore

    @Published var serverUrl = ""
    @Published var username = ""
    @Published var password = ""
    @Published var loading = false
    @Published var errorMessage: String?
    @Published var step: String?

    @Published var registerMode = false
    @Published var displayName = ""
    @Published var adminUsername = ""
    @Published var adminPassword = ""

    let urlLocked: Bool

    init(sessionManager: SessionManager, settingsStore: SettingsStore) {
        self.sessionManager = sessionManager
        self.settingsStore = settingsStore
        let saved = settingsStore.defaultServerUrl
        self.serverUrl = saved.isEmpty ? (Bundle.main.object(forInfoDictionaryKey: "FLUTCLOUD_URL") as? String ?? "") : saved
        self.urlLocked = !(Bundle.main.object(forInfoDictionaryKey: "FLUTCLOUD_URL") as? String ?? "").isEmpty
    }

    func toggleMode() { registerMode.toggle() }

    func signIn(onSuccess: @escaping () -> Void) {
        guard !loading else { return }
        let url = serverUrl.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let user = username.trimmingCharacters(in: .whitespaces)
        let pass = password.trimmingCharacters(in: .whitespaces)
        guard !url.isEmpty, !user.isEmpty, !pass.isEmpty else {
            errorMessage = NSLocalizedString("error_fill_fields", comment: "")
            return
        }
        let lockedUrl = (Bundle.main.object(forInfoDictionaryKey: "FLUTCLOUD_URL") as? String ?? "")
        if !lockedUrl.isEmpty && url != lockedUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) {
            errorMessage = NSLocalizedString("error_wrong_server_url", comment: "")
            return
        }
        loading = true; errorMessage = nil; step = NSLocalizedString("signing_in", comment: "")
        Task {
            do {
                let session = AuthSession(baseUrl: url, username: user, token: pass)
                let api = FlutCloudApi(session: session)
                let info = try await api.getCurrentUser()
                step = NSLocalizedString("verifying_server", comment: "")
                try await api.verifyServer()
                let existing = sessionManager.accounts.first { $0.username == user && $0.instanceUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) == url }
                let admin = await api.isAdmin() || existing?.isAdmin ?? false
                settingsStore.defaultServerUrl = url
                sessionManager.addAccount(meta: AccountMeta(username: user, instanceUrl: url, displayName: info.displayName, isAdmin: admin, isActive: true), token: pass)
                onSuccess()
            } catch {
                errorMessage = Self.mapError(error)
            }
            loading = false; step = nil
        }
    }

    func register(onSuccess: @escaping () -> Void) {
        guard !loading else { return }
        let url = serverUrl.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let user = username.trimmingCharacters(in: .whitespaces)
        let pass = password.trimmingCharacters(in: .whitespaces)
        guard !url.isEmpty, !user.isEmpty, !pass.isEmpty, !adminUsername.trimmingCharacters(in: .whitespaces).isEmpty, !adminPassword.isEmpty else {
            errorMessage = NSLocalizedString("error_fill_fields_register", comment: "")
            return
        }
        let lockedUrl = (Bundle.main.object(forInfoDictionaryKey: "FLUTCLOUD_URL") as? String ?? "")
        if !lockedUrl.isEmpty && url != lockedUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) {
            errorMessage = NSLocalizedString("error_wrong_server_url", comment: "")
            return
        }
        loading = true; errorMessage = nil
        Task {
            do {
                let adminSession = AuthSession(baseUrl: url, username: adminUsername.trimmingCharacters(in: .whitespaces), token: adminPassword)
                let adminApi = FlutCloudApi(session: adminSession)
                step = NSLocalizedString("step_verifying_server", comment: "")
                try await adminApi.verifyServer()
                step = NSLocalizedString("step_creating_account", comment: "")
                try await adminApi.createUser(userId: user, password: pass, displayName: displayName.trimmingCharacters(in: .whitespaces).isEmpty ? nil : displayName)
                step = NSLocalizedString("step_setting_up_folder", comment: "")
                await ensureProjectFolder(session: adminSession)
                step = NSLocalizedString("signing_in", comment: "")
                let session = AuthSession(baseUrl: url, username: user, token: pass)
                let api = FlutCloudApi(session: session)
                let info = try await api.getCurrentUser()
                try await api.verifyServer()
                let admin = await api.isAdmin()
                settingsStore.defaultServerUrl = url
                sessionManager.addAccount(meta: AccountMeta(username: user, instanceUrl: url, displayName: info.displayName, isAdmin: admin, isActive: true), token: pass)
                onSuccess()
            } catch {
                errorMessage = Self.mapError(error)
            }
            loading = false; step = nil
        }
    }

    func clearError() { errorMessage = nil }

    private func ensureProjectFolder(session: AuthSession) async {
        let dav = WebDavApi(session: session)
        try? await dav.mkdir(path: "/FlutLink/FlutCloud")
        let readme = "# FlutCloud — Nextcloud App\n\nShared project space of the **FlutCloud Nextcloud app**.\n"
        try? await dav.upload(path: "/FlutLink/FlutCloud/README.md", data: Data(readme.utf8))
    }

    static func mapError(_ error: Error) -> String {
        if let api = error as? ApiException {
            switch api {
            case .flutCloudAppMissing: return NSLocalizedString("error_flutcloud_app_missing", comment: "")
            case .api(let msg, _, _): return msg
            case .network: return NSLocalizedString("error_network_reach", comment: "")
            }
        }
        return error.localizedDescription
    }
}
