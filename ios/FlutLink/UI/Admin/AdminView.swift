// TEST — iOS port created by opencode. Not a production build.
import SwiftUI

struct AdminView: View {
    @ObservedObject var viewModel: AdminViewModel
    let onViewFiles: (String) -> Void
    @State private var showCreateUser = false
    @State private var showGroupSheet = false
    @State private var groupTarget: ManagedUser?
    @State private var showDeleteConfirm = false
    @State private var deleteTarget: ManagedUser?
    @State private var showQuotaSheet = false
    @State private var quotaTarget: ManagedUser?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if let error = viewModel.errorMessage {
                    ErrorBanner(message: error) { viewModel.clearError() }
                }
                HStack {
                    TextField("search_users".localized, text: $viewModel.search)
                        .textFieldStyle(.roundedBorder)
                        .autocapitalization(.none)
                        .onSubmit { viewModel.loadUsers() }
                    Button("search".localized) { viewModel.loadUsers() }
                        .buttonStyle(.bordered)
                }
                .padding()
                if viewModel.users.isEmpty && !viewModel.loading {
                    ContentUnavailableView("no_users_found".localized, systemImage: "person.3", description: Text("no_users_hint".localized))
                } else {
                    List(viewModel.users) { user in
                        UserRow(user: user) {
                            groupTarget = user; showGroupSheet = true
                        } onDelete: {
                            deleteTarget = user; showDeleteConfirm = true
                        } onQuota: {
                            quotaTarget = user; showQuotaSheet = true
                        } onToggleEnabled: { enabled in
                            viewModel.setEnabled(user, enabled: enabled)
                        } onViewFiles: {
                            onViewFiles(user.id)
                        }
                    }
                    .refreshable { viewModel.refresh() }
                    if viewModel.hasMore {
                        Button("admin_load_more".localized) { viewModel.loadMore() }
                            .buttonStyle(.bordered)
                            .padding()
                    }
                }
            }
            .navigationTitle("tab_admin".localized)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showCreateUser = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showCreateUser) { CreateUserSheet(viewModel: viewModel) { showCreateUser = false } }
            .sheet(isPresented: $showGroupSheet) {
                if let user = groupTarget {
                    GroupSheet(user: user, viewModel: viewModel) { showGroupSheet = false }
                }
            }
            .sheet(isPresented: $showQuotaSheet) {
                if let user = quotaTarget {
                    QuotaSheet(user: user, viewModel: viewModel) { showQuotaSheet = false }
                }
            }
            .confirmationDialog("delete_user".localized, isPresented: $showDeleteConfirm, titleVisibility: .visible) {
                Button("delete_user".localized, role: .destructive) { if let u = deleteTarget { viewModel.deleteUser(u) } }
                Button("cancel".localized, role: .cancel) {}
            }
        }
        .onAppear { /* require search term — do not auto-load all users */ }
    }
}

struct UserRow: View {
    let user: ManagedUser
    let onGroups: () -> Void
    let onDelete: () -> Void
    let onQuota: () -> Void
    let onToggleEnabled: (Bool) -> Void
    let onViewFiles: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(user.displayName ?? user.id).font(.headline)
                Spacer()
                Toggle("", isOn: Binding(
                    get: { user.enabled },
                    set: { onToggleEnabled($0) }
                ))
                .labelsHidden()
                .tint(.green)
            }
            Text(user.email ?? "no_email".localized).font(.caption).foregroundStyle(.secondary)
            if let q = user.quota {
                Text(formatQuotaLabel(q)).font(.caption2).foregroundStyle(.secondary)
            }
            HStack {
                ForEach(user.groups.prefix(3), id: \.self) { g in
                    Text(g).font(.caption2).padding(.horizontal, 6).padding(.vertical, 2).background(.fill).cornerRadius(4)
                }
                if user.groups.count > 3 { Text("+\(user.groups.count - 3)").font(.caption2) }
            }
            HStack(spacing: 12) {
                Button("admin_manage_groups".localized, action: onGroups).font(.caption)
                Button("view_files".localized, action: onViewFiles).font(.caption)
                Button("quota_custom".localized, action: onQuota).font(.caption)
                Spacer()
                Button("delete_user".localized, action: onDelete).font(.caption).foregroundStyle(.red)
            }
        }
        .padding(.vertical, 4)
    }

    private func formatQuotaLabel(_ q: Quota) -> String {
        if let total = q.total, total > 0 {
            return "\(formatBytes(q.used)) / \(formatBytes(q.total))"
        }
        if let used = q.used { return "\(formatBytes(used)) used" }
        return "quota_unknown".localized
    }
}

private func formatBytes(_ bytes: Int64?) -> String {
    guard let b = bytes else { return "" }
    if b < 1024 { return "\(b) B" }
    let units = ["KB", "MB", "GB", "TB"]
    var value = Double(b)
    var unit = -1
    while value >= 1024 && unit < units.count - 1 { value /= 1024; unit += 1 }
    return String(format: "%.1f %@", value, units[unit])
}

struct CreateUserSheet: View {
    @ObservedObject var viewModel: AdminViewModel
    @State private var userId = ""
    @State private var password = ""
    @State private var displayName = ""
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                TextField("user_id".localized, text: $userId)
                SecureField("password".localized, text: $password)
                TextField("display_name_optional".localized, text: $displayName)
            }
            .navigationTitle("create_user".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("cancel".localized, action: onDone) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("create".localized) { viewModel.createUser(userId: userId, password: password, displayName: displayName); onDone() }
                        .disabled(userId.isEmpty || password.isEmpty)
                }
            }
        }
    }
}

struct GroupSheet: View {
    let user: ManagedUser
    @ObservedObject var viewModel: AdminViewModel
    @State private var groupName = ""
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("admin_groups_title".localized.replacingOccurrences(of: "%1$s", with: user.id)) {
                    if user.groups.isEmpty {
                        Text("admin_no_groups".localized).foregroundStyle(.secondary)
                    } else {
                        ForEach(user.groups, id: \.self) { g in
                            HStack {
                                Text(g)
                                Spacer()
                                Button("remove".localized, role: .destructive) { viewModel.removeFromGroup(user, group: g) }
                            }
                        }
                    }
                }
                Section {
                    TextField("admin_group_name_label".localized, text: $groupName)
                    Button("admin_create_group".localized) { viewModel.createGroup(groupName); groupName = "" }
                    Button("admin_add_to_group".localized) { viewModel.addToGroup(user, group: groupName); groupName = "" }
                        .disabled(groupName.isEmpty)
                }
            }
            .navigationTitle("admin_manage_groups".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("close".localized, action: onDone) } }
        }
    }
}

struct QuotaSheet: View {
    let user: ManagedUser
    @ObservedObject var viewModel: AdminViewModel
    @State private var selectedQuota: QuotaPreset = .unlimited
    @State private var customValue = ""
    @State private var isGB = true
    let onDone: () -> Void

    enum QuotaPreset: String { case unlimited, g1, g5, g10, custom }

    var body: some View {
        NavigationStack {
            Form {
                Section("quota_custom_title".localized) {
                    Picker("", selection: $selectedQuota) {
                        Text("quota_unlimited".localized).tag(QuotaPreset.unlimited)
                        Text("quota_1gb".localized).tag(QuotaPreset.g1)
                        Text("quota_5gb".localized).tag(QuotaPreset.g5)
                        Text("quota_10gb".localized).tag(QuotaPreset.g10)
                        Text("quota_custom".localized).tag(QuotaPreset.custom)
                    }
                    .pickerStyle(.inline)
                    if selectedQuota == .custom {
                        HStack {
                            TextField("quota_custom_value".localized, text: $customValue)
                                .keyboardType(.decimalPad)
                            Picker("", selection: $isGB) {
                                Text("quota_unit_gb".localized).tag(true)
                                Text("quota_unit_mb".localized).tag(false)
                            }
                            .pickerStyle(.segmented)
                        }
                    }
                }
            }
            .navigationTitle("quota_custom_title".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("cancel".localized, action: onDone) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("quota_set".localized) {
                        let bytes: Int64? = {
                            switch selectedQuota {
                            case .unlimited: return nil
                            case .g1: return 1_073_741_824
                            case .g5: return 5_368_709_120
                            case .g10: return 10_737_418_240
                            case .custom:
                                guard let val = Double(customValue) else { return nil }
                                return Int64(val * (isGB ? 1_073_741_824 : 1_048_576))
                            }
                        }()
                        viewModel.setQuota(user, quotaBytes: bytes)
                        onDone()
                    }
                }
            }
        }
    }
}
