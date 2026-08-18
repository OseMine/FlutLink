// TEST — iOS port created by opencode. Not a production build.
import SwiftUI

struct ErrorBanner: View {
    let message: String
    let onDismiss: () -> Void

    var body: some View {
        HStack {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(.red)
            Text(message)
                .font(.caption)
                .lineLimit(3)
            Spacer()
            Button { onDismiss() } label: {
                Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
            }
        }
        .padding(8)
        .background(Color.red.opacity(0.1))
        .cornerRadius(8)
        .padding(.horizontal)
    }
}

struct Banner: View {
    let text: String
    enum Style { case warning, info }
    var style: Style = .info
    var body: some View {
        HStack {
            Image(systemName: style == .warning ? "wifi.slash" : "info.circle")
            Text(text).font(.caption)
            Spacer()
        }
        .padding(8)
        .background(style == .warning ? Color.orange.opacity(0.1) : Color.blue.opacity(0.1))
        .cornerRadius(8)
        .padding(.horizontal)
    }
}

struct FileRow: View {
    let entry: WebDavEntry
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                Image(systemName: fileIcon)
                    .font(.title2)
                    .foregroundStyle(iconColor)
                    .frame(width: 28)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        Text(entry.name)
                            .font(.body)
                            .lineLimit(1)
                            .foregroundStyle(.primary)
                        if entry.isVirtualLink {
                            Text("virtual".localized)
                                .font(.caption2)
                                .padding(.horizontal, 4)
                                .padding(.vertical, 1)
                                .background(Color.purple.opacity(0.15))
                                .cornerRadius(4)
                        }
                        if entry.isResource {
                            Text("R")
                                .font(.caption2).bold()
                                .padding(.horizontal, 3)
                                .background(Color.blue.opacity(0.15))
                                .cornerRadius(4)
                        }
                        if entry.isPart {
                            Text("W")
                                .font(.caption2).bold()
                                .padding(.horizontal, 3)
                                .background(Color.green.opacity(0.15))
                                .cornerRadius(4)
                        }
                    }
                    HStack(spacing: 8) {
                        if let size = entry.size, !entry.isDir {
                            Text(formatBytes(size))
                                .font(.caption2).foregroundStyle(.secondary)
                        }
                        if let mtime = entry.mtime {
                            Text(formatMtime(mtime))
                                .font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                }
                Spacer()
                if entry.isDir {
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var fileIcon: String {
        if entry.isDir { return "folder.fill" }
        if entry.isVirtualLink { return "link" }
        let ext = (entry.name as NSString).pathExtension.lowercased()
        switch ext {
        case "jpg", "jpeg", "png", "gif", "webp", "svg", "heic": return "photo"
        case "mp4", "mov", "avi", "mkv": return "video"
        case "mp3", "wav", "flac", "m4a", "aac": return "music.note"
        case "pdf": return "doc.richtext"
        case "zip", "tar", "gz", "7z", "rar": return "archivebox"
        case "doc", "docx", "txt", "md", "rtf": return "doc.text"
        case "xls", "xlsx", "csv": return "tablecells"
        case "ppt", "pptx", "key": return "presentation"
        case "py", "js", "ts", "swift", "kt", "java", "rs", "go", "rb": return "chevron.left.forwardslash.chevron.right"
        default: return "doc"
        }
    }

    private var iconColor: Color {
        if entry.isDir { return .blue }
        if entry.isVirtualLink { return .purple }
        let ext = (entry.name as NSString).pathExtension.lowercased()
        switch ext {
        case "jpg", "jpeg", "png", "gif", "webp", "svg", "heic": return .green
        case "mp4", "mov", "avi", "mkv": return .pink
        case "mp3", "wav", "flac", "m4a", "aac": return .orange
        case "pdf": return .red
        default: return .gray
        }
    }
}

struct ShareSheetView: View {
    let entry: WebDavEntry
    @ObservedObject var viewModel: FilesViewModel
    let onDone: () -> Void

    @State private var shareType = 3
    @State private var shareWith = ""
    @State private var password = ""
    @State private var expireDate = ""
    @State private var publicUpload = false

    var body: some View {
        NavigationStack {
            Form {
                Section("share_existing_shares".localized) {
                    if viewModel.sharesLoading {
                        ProgressView()
                    } else if viewModel.shares.isEmpty {
                        Text("share_no_shares_yet".localized).foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.shares) { share in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(share.shareWithDisplayName ?? share.shareWith ?? "share_type_generic".localized)
                                        .font(.body)
                                    HStack(spacing: 8) {
                                        Text(shareTypeLabel(share.shareType))
                                        if share.hasPassword == true { Text("share_meta_has_password".localized).font(.caption2) }
                                        if let exp = share.expiration { Text(String(format: "share_meta_expires".localized, exp)).font(.caption2) }
                                    }
                                    .font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                if let url = share.url {
                                    Button { UIPasteboard.general.string = url } label: {
                                        Image(systemName: "doc.on.doc")
                                    }
                                }
                                Button("share_revoke".localized, role: .destructive) { viewModel.deleteShare(share) }
                                    .buttonStyle(.bordered).controlSize(.small).tint(.red)
                            }
                        }
                    }
                }
                Section("new_share".localized) {
                    Picker("Type", selection: $shareType) {
                        Text("share_type_public_link".localized).tag(3)
                        Text("share_type_user".localized).tag(0)
                        Text("share_type_group".localized).tag(1)
                    }
                    if shareType < 3 {
                        TextField("share_recipient".localized, text: $shareWith)
                            .autocapitalization(.none)
                    }
                    if shareType == 3 {
                        SecureField("share_password_optional".localized, text: $password)
                        TextField("share_expiry_optional".localized, text: $expireDate)
                            .autocapitalization(.none)
                        Toggle("share_public_upload".localized, isOn: $publicUpload)
                    }
                }
            }
            .navigationTitle(entry.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("cancel".localized, action: onDone) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("create".localized) {
                        viewModel.createShare(entry: entry, shareType: shareType, shareWith: shareType < 3 ? shareWith : nil, password: password.isEmpty ? nil : password, expireDate: expireDate.isEmpty ? nil : expireDate, publicUpload: publicUpload)
                        onDone()
                    }
                }
            }
            .onAppear { Task { await viewModel.loadShares(entry) } }
        }
    }

    private func shareTypeLabel(_ type: Int) -> String {
        switch type {
        case 0: return "share_type_user".localized
        case 1: return "share_type_group".localized
        case 3: return "share_type_public_link".localized
        default: return "share_type_generic".localized
        }
    }
}

struct NewFolderSheet: View {
    @Binding var name: String
    let onCreate: () -> Void
    let onCancel: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                TextField("folder_name".localized, text: $name)
                    .autocapitalization(.none)
            }
            .navigationTitle("new_folder".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("cancel".localized, action: onCancel) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("create".localized, action: onCreate).disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}

struct RenameSheet: View {
    @Binding var name: String
    let onRename: () -> Void
    let onCancel: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                TextField("new_name".localized, text: $name)
                    .autocapitalization(.none)
            }
            .navigationTitle("rename".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("cancel".localized, action: onCancel) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("rename".localized, action: onRename).disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}

// MARK: - Format helpers

func formatBytes(_ bytes: Int64?) -> String {
    guard let b = bytes else { return "" }
    if b < 1024 { return "\(b) B" }
    let units = ["KB", "MB", "GB", "TB"]
    var value = Double(b)
    var unit = -1
    while value >= 1024 && unit < units.count - 1 { value /= 1024; unit += 1 }
    return String(format: "%.1f %@", value, units[unit])
}

func formatMtime(_ mtime: String?) -> String {
    guard let s = mtime, !s.isEmpty else { return "" }
    let formatter = DateFormatter()
    formatter.dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    guard let date = formatter.date(from: s) else { return s }
    let out = DateFormatter()
    out.dateFormat = "MMM d, yyyy"
    return out.string(from: date)
}
