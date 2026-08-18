// TEST — iOS port created by opencode. Not a production build.
import SwiftUI
import UniformTypeIdentifiers

struct FilesView: View {
    @ObservedObject var viewModel: FilesViewModel
    @State private var showNewFolder = false
    @State private var newFolderName = ""
    @State private var showRename = false
    @State private var renameTarget: WebDavEntry?
    @State private var renameNewName = ""
    @State private var showDeleteConfirm = false
    @State private var deleteTarget: WebDavEntry?
    @State private var showShareSheet = false
    @State private var shareTarget: WebDavEntry?
    @State private var showFilePicker = false
    @State private var showActionSheet = false
    @State private var actionTarget: WebDavEntry?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if let impersonation = viewModel.targetUser {
                    ImpersonationBanner(username: impersonation) { viewModel.setTargetUser(nil) }
                }
                if viewModel.offline {
                    Banner(text: "files_offline_banner".localized, style: .warning)
                }
                if let error = viewModel.errorMessage {
                    ErrorBanner(message: error) { viewModel.clearError() }
                }
                if !viewModel.searchQuery.isEmpty || viewModel.searching {
                    searchBar
                }
                if viewModel.transferProgress != nil {
                    ProgressView(value: Double(viewModel.transferProgress!.transferred), total: Double(max(viewModel.transferProgress!.total, 1)))
                        .padding(.horizontal)
                }
                if viewModel.searchQuery.isEmpty {
                    fileList
                } else {
                    searchResultsList
                }
            }
            .navigationTitle(pathTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if viewModel.path != "/" {
                        Button { viewModel.listFolder(parentPath) } label: {
                            Image(systemName: "chevron.left")
                        }
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button("search".localized) { viewModel.search("") }
                        Button("new_folder".localized) { showNewFolder = true }
                        Button("upload".localized) { showFilePicker = true }
                    } label: {
                        Image(systemName: "plus.circle")
                    }
                }
            }
            .sheet(isPresented: $showNewFolder) {
                NewFolderSheet(name: $newFolderName) {
                    viewModel.mkdir(newFolderName) { newFolderName = ""; showNewFolder = false }
                } onCancel: { showNewFolder = false }
            }
            .sheet(isPresented: $showRename) {
                RenameSheet(name: $renameNewName) {
                    if let entry = renameTarget { viewModel.rename(entry, newName: renameNewName) }
                    showRename = false
                } onCancel: { showRename = false }
            }
            .sheet(isPresented: $showShareSheet) {
                if let entry = shareTarget {
                    ShareSheetView(entry: entry, viewModel: viewModel) { showShareSheet = false }
                }
            }
            .confirmationDialog("delete_confirm".localized + " \(deleteTarget?.name ?? "")", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
                Button("delete".localized, role: .destructive) { if let e = deleteTarget { viewModel.delete(e) } }
                Button("cancel".localized, role: .cancel) {}
            }
            .confirmationDialog("overwrite".localized, isPresented: Binding(
                get: { viewModel.pendingUpload != nil },
                set: { if !$0 { viewModel.clearPendingUpload() } }
            ), titleVisibility: .visible) {
                Button("overwrite".localized) { viewModel.confirmUpload() }
                Button("cancel".localized, role: .cancel) { viewModel.clearPendingUpload() }
            }
            .fileImporter(isPresented: $showFilePicker, allowedContentTypes: [.item]) { result in
                if case .success(let url) = result, url.startAccessingSecurityScopedResource() {
                    let name = url.lastPathComponent
                    let data = (try? Data(contentsOf: url)) ?? Data()
                    url.stopAccessingSecurityScopedResource()
                    viewModel.uploadData(data, targetDir: viewModel.path, name: name)
                }
            }
            .onChange(of: viewModel.downloadedData) { data in
                if data != nil { viewModel.clearDownloaded() }
            }
        }
        .onAppear { viewModel.refresh() }
    }

    private var fileList: some View {
        Group {
            if viewModel.entries.isEmpty && !viewModel.loading {
                ContentUnavailableView("folder_empty_title".localized, systemImage: "folder", description: Text("folder_empty_hint".localized))
            } else {
                List(viewModel.entries) { entry in
                    FileRow(entry: entry) { viewModel.open(entry) }
                        .contextMenu {
                            if entry.isVirtualLink, let paired = entry.pairedPath {
                                Button("jump_to_writable_part".localized) { viewModel.listFolder(paired) }
                            }
                            if !entry.isDir {
                                Button("download".localized) { viewModel.downloadToDownloads(entry) }
                                Button("share".localized) { shareTarget = entry; showShareSheet = true }
                            }
                            Button("rename".localized) { renameTarget = entry; renameNewName = entry.name; showRename = true }
                            Button("delete".localized, role: .destructive) { deleteTarget = entry; showDeleteConfirm = true }
                        }
                }
                .refreshable { viewModel.refresh() }
            }
        }
    }

    private var searchResultsList: some View {
        Group {
            if viewModel.searching {
                ProgressView().frame(maxWidth: .infinity).padding()
            } else if viewModel.searchResults.isEmpty {
                ContentUnavailableView("no_matches".localized, systemImage: "magnifyingglass", description: Text("no_matches_hint".localized))
            } else {
                List(viewModel.searchResults) { entry in
                    FileRow(entry: entry) { viewModel.open(entry) }
                        .contextMenu {
                            if entry.isVirtualLink, let paired = entry.pairedPath {
                                Button("jump_to_writable_part".localized) { viewModel.listFolder(paired) }
                            }
                            if !entry.isDir {
                                Button("download".localized) { viewModel.downloadToDownloads(entry) }
                                Button("share".localized) { shareTarget = entry; showShareSheet = true }
                            }
                            Button("rename".localized) { renameTarget = entry; renameNewName = entry.name; showRename = true }
                            Button("delete".localized, role: .destructive) { deleteTarget = entry; showDeleteConfirm = true }
                        }
                }
            }
        }
    }

    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
            TextField("search_placeholder".localized, text: Binding(
                get: { viewModel.searchQuery },
                set: { viewModel.search($0) }
            ))
            .autocapitalization(.none)
            if !viewModel.searchQuery.isEmpty {
                Button { viewModel.clearSearch() } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                }
            }
        }
        .padding(8)
        .background(.fill)
        .cornerRadius(8)
        .padding(.horizontal)
        .padding(.vertical, 4)
    }

    private var pathTitle: String {
        let parts = viewModel.path.split(separator: "/").map(String.init)
        return parts.last ?? "Files"
    }
    private var parentPath: String {
        let parts = viewModel.path.split(separator: "/").map(String.init)
        return parts.count <= 1 ? "/" : "/" + parts.dropLast().joined(separator: "/")
    }
}

struct ImpersonationBanner: View {
    let username: String
    let onStop: () -> Void
    var body: some View {
        HStack {
            Image(systemName: "person.circle")
            Text(String(format: "impersonation_notice".localized, username))
                .font(.caption)
            Spacer()
            Button("stop_impersonation".localized, action: onStop)
                .font(.caption.bold())
        }
        .padding(8)
        .background(Color.orange.opacity(0.15))
    }
}
