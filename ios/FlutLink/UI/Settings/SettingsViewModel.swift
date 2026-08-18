// TEST — iOS port created by opencode. Not a production build.
import Foundation
import UIKit

@MainActor
final class SettingsViewModel: ObservableObject {
    private let sessionManager: SessionManager
    private let settingsStore: SettingsStore
    private let updater = Updater()

    @Published var accounts: [AccountMeta] = []
    @Published var themePreference = "system"
    @Published var accentHue: Double?
    @Published var serverInfo: AppInfoDto?
    @Published var toastMessage: String?
    @Published var update: AppUpdate?
    @Published var checkingUpdate = false

    let appVersion: String

    init(sessionManager: SessionManager, settingsStore: SettingsStore) {
        self.sessionManager = sessionManager
        self.settingsStore = settingsStore
        self.appVersion = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        self.themePreference = settingsStore.themePreference
        self.accentHue = settingsStore.accentHue
        self.accounts = sessionManager.accounts
    }

    func loadServerInfo() {
        guard let s = sessionManager.session else { return }
        accounts = sessionManager.accounts
        Task { serverInfo = await FlutCloudApi(session: s).ping() }
    }

    func checkForUpdate() {
        guard !checkingUpdate else { return }
        checkingUpdate = true
        Task {
            do {
                if let found = try await updater.checkForUpdate(currentVersion: appVersion) {
                    update = found
                } else {
                    toastMessage = String(format: NSLocalizedString("update_up_to_date", comment: ""), appVersion)
                }
            } catch {
                toastMessage = NSLocalizedString("update_check_failed", comment: "")
            }
            checkingUpdate = false
        }
    }

    func dismissUpdate() { update = nil }

    func openUpdateURL() {
        guard let url = URL(string: "https://github.com/OseMine/FlutLink/releases/latest") else { return }
        UIApplication.shared.open(url)
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
