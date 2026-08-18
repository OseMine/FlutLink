// TEST — iOS port created by opencode. Not a production build.
import SwiftUI

struct SettingsView: View {
    @ObservedObject var viewModel: SettingsViewModel
    let onSignOut: () -> Void

    @State private var showSignOutConfirm = false
    @State private var showRemoveAccount: AccountMeta?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("account".localized) {
                    if let session = viewModel.accounts.first(where: \.isActive) {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(session.displayName ?? session.username).font(.headline)
                                Text(session.instanceUrl).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text(session.isAdmin ? "Admin" : "")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    } else {
                        Text("not_signed_in".localized).foregroundStyle(.secondary)
                    }
                }
                Section("flut_cloud_app".localized) {
                    if let info = viewModel.serverInfo {
                        LabeledContent("Name", value: info.name ?? "-")
                        LabeledContent("Version", value: info.version ?? "-")
                        LabeledContent("Features", value: "\(info.features?.count ?? 0)")
                    } else {
                        Button("Load") { viewModel.loadServerInfo() }
                    }
                }
                Section("appearance".localized) {
                    Picker("theme".localized, selection: Binding(
                        get: { viewModel.themePreference },
                        set: { viewModel.setThemePreference($0) }
                    )) {
                        Text("theme_operationflut".localized).tag("operationflut")
                        Text("theme_midnight".localized).tag("midnight")
                        Text("theme_system".localized).tag("system")
                    }
                    .pickerStyle(.segmented)
                    HStack {
                        Text("accent_color".localized)
                        Spacer()
                        Slider(
                            value: Binding(
                                get: { viewModel.accentHue ?? 266 },
                                set: { viewModel.setAccentHue($0) }
                            ),
                            in: 0...360
                        )
                        .frame(width: 150)
                        Button("accent_reset".localized) { viewModel.setAccentHue(nil) }
                            .font(.caption)
                    }
                }
                Section("accounts".localized) {
                    ForEach(viewModel.accounts) { account in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(account.displayName ?? account.username).font(.body)
                                Text(account.instanceUrl).font(.caption2).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if account.isActive {
                                Text("account_active".localized.replacingOccurrences(of: "%1$s", with: ""))
                                    .font(.caption).foregroundStyle(.green)
                            } else {
                                Button("switch_account".localized) { viewModel.switchAccount(account) }
                                    .buttonStyle(.bordered).controlSize(.small)
                            }
                            Button("remove_account".localized, role: .destructive) { showRemoveAccount = account }
                                .buttonStyle(.bordered).controlSize(.small).tint(.red)
                        }
                    }
                }
                Section {
                    Button("sign_out".localized, role: .destructive) { showSignOutConfirm = true }
                }
                Section {
                    Text("flutlink_for_ios".localized)
                        .font(.caption).foregroundStyle(.secondary)
                    Text("version_format".localized.replacingOccurrences(of: "%1$s", with: viewModel.appVersion))
                        .font(.caption).foregroundStyle(.secondary)
                    Text("flutcloud_only_note".localized)
                        .font(.caption2).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("tab_settings".localized)
            .confirmationDialog("sign_out".localized, isPresented: $showSignOutConfirm, titleVisibility: .visible) {
                Button("sign_out".localized, role: .destructive) { viewModel.signOut(); onSignOut() }
                Button("cancel".localized, role: .cancel) {}
            }
            .alert("remove_account".localized, isPresented: Binding(
                get: { showRemoveAccount != nil },
                set: { if !$0 { showRemoveAccount = nil } }
            )) {
                Button("remove".localized, role: .destructive) {
                    if let acc = showRemoveAccount { viewModel.removeAccount(acc) }
                    showRemoveAccount = nil
                }
                Button("cancel".localized, role: .cancel) { showRemoveAccount = nil }
            } message: {
                if let acc = showRemoveAccount {
                    Text("remove_account_confirm".localized.replacingOccurrences(of: "%1$s", with: acc.username))
                }
            }
        }
        .onAppear { viewModel.loadServerInfo() }
    }
}
