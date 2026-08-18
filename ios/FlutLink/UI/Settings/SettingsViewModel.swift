// TEST — iOS port created by opencode. Not a production build.
import Foundation

@MainActor
final class SettingsViewModel: ObservableObject {
    private let sessionManager: SessionManager
    private let settingsStore: SettingsStore

    @Published var accounts: [AccountMeta] = []
    @Published var themePreference = "system"
    @Published var accentHue: Double?
    @Published var serverInfo: AppInfoDto?
    @Published var toastMessage: String?

    let appVersion: String

    init(sessionManager: SessionManager, settingsStore: SettingsStore) {
        self.sessionManager = sessionManager
        self.settingsStore = settingsStore
        self.appVersion = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        self.themePreference = settingsStore.themePreference
        self.accentHue = settingsStore.accentHue
    }

    func loadServerInfo() {
        guard let s = sessionManager.session else { return }
        Task { serverInfo = await FlutCloudApi(session: s).ping() }
    }

    func setThemePreference(_ pref: String) {
        themePreference = pref
        settingsStore.themePreference = pref
    }

    func setAccentHue(_ hue: Double?) {
        accentHue = hue
        settingsStore.accentHue = hue
    }

    func switchAccount(_ meta: AccountMeta) {
        sessionManager.switchAccount(meta)
        toastMessage = String(format: NSLocalizedString("account_switched_to", comment: ""), meta.username)
    }

    func removeAccount(_ meta: AccountMeta) {
        sessionManager.removeAccount(meta)
    }

    func signOut() {
        sessionManager.signOut()
    }

    func clearToast() { toastMessage = nil }
}
