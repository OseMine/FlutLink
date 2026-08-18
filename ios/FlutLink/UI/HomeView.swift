// TEST — iOS port created by opencode. Not a production build.
import SwiftUI

enum FlutTab: String, CaseIterable {
    case files, admin, settings
}

struct HomeView: View {
    @ObservedObject var sessionManager: SessionManager
    @ObservedObject var settingsStore: SettingsStore
    let onSignOut: () -> Void

    @State private var selectedTab: FlutTab = .files

    private var filesVM: FilesViewModel { FilesViewModel(sessionManager: sessionManager) }
    private var adminVM: AdminViewModel { AdminViewModel(sessionManager: sessionManager) }
    private var settingsVM: SettingsViewModel { SettingsViewModel(sessionManager: sessionManager, settingsStore: settingsStore) }

    var body: some View {
        TabView(selection: $selectedTab) {
            FilesView(viewModel: filesVM)
                .tabItem { Label("tab_files".localized, systemImage: "folder") }
                .tag(FlutTab.files)
            if isAdmin {
                AdminView(viewModel: adminVM)
                    .tabItem { Label("tab_admin".localized, systemImage: "person.3") }
                    .tag(FlutTab.admin)
            }
            SettingsView(viewModel: settingsVM, onSignOut: onSignOut)
                .tabItem { Label("tab_settings".localized, systemImage: "gearshape") }
                .tag(FlutTab.settings)
        }
    }

    private var isAdmin: Bool {
        sessionManager.accounts.contains { $0.isActive && $0.isAdmin }
    }
}
