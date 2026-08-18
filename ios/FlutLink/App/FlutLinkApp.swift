// TEST — iOS port created by opencode. Not a production build.
import SwiftUI

@main
struct FlutLinkApp: App {
    @StateObject private var accountStore = AccountStore()
    @StateObject private var sessionManager: SessionManager
    @StateObject private var settingsStore = SettingsStore()

    init() {
        let store = AccountStore()
        _accountStore = StateObject(wrappedValue: store)
        _sessionManager = StateObject(wrappedValue: SessionManager(accountStore: store))
        _settingsStore = StateObject(wrappedValue: SettingsStore())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(sessionManager)
                .environmentObject(settingsStore)
                .task { await sessionManager.init_() }
        }
    }
}

struct ContentView: View {
    @EnvironmentObject var sessionManager: SessionManager
    @EnvironmentObject var settingsStore: SettingsStore

    var body: some View {
        Group {
            if sessionManager.session != nil {
                HomeView(sessionManager: sessionManager, settingsStore: settingsStore) {
                    sessionManager.signOut()
                }
            } else {
                LoginView(viewModel: LoginViewModel(sessionManager: sessionManager, settingsStore: settingsStore)) {}
            }
        }
        .preferredColorScheme(resolvedColorScheme)
    }

    private var resolvedColorScheme: ColorScheme? {
        FlutTheme(rawValue: settingsStore.themePreference)?.colorScheme
    }
}
