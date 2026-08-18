// TEST — iOS port created by opencode. Not a production build.
import Foundation

@MainActor
final class AdminViewModel: ObservableObject {
    private let sessionManager: SessionManager

    @Published var users: [ManagedUser] = []
    @Published var loading = false
    @Published var errorMessage: String?
    @Published var search = ""
    @Published var hasMore = false

    private var offset = 0
    private var searchTerm = ""

    var session: AuthSession? { sessionManager.session }

    init(sessionManager: SessionManager) {
        self.sessionManager = sessionManager
    }

    func refresh() { loadUsers() }

    func loadUsers() {
        guard let s = session else { return }
        loading = true; errorMessage = nil
        users = []; hasMore = false
        searchTerm = search.trimmingCharacters(in: .whitespaces)
        offset = 0
        Task {
            do {
                try await loadPage(s, append: false)
            } catch {
                errorMessage = LoginViewModel.mapError(error)
            }
            loading = false
        }
    }

    func loadMore() {
        guard let s = session, !loading else { return }
        loading = true; errorMessage = nil
        Task {
            do {
                try await loadPage(s, append: true)
            } catch {
                errorMessage = LoginViewModel.mapError(error)
            }
            loading = false
        }
    }

    private func loadPage(_ s: AuthSession, append: Bool) async throws {
        let api = FlutCloudApi(session: s)
        let page = try await api.listUsersPage(search: searchTerm, offset: offset)
        var managed: [ManagedUser] = []
        for id in page {
            if let user = try? await api.getUser(userId: id) {
                managed.append(user)
            } else {
                managed.append(ManagedUser(id: id, displayName: nil, email: nil, quota: nil, groups: [], enabled: true))
            }
        }
        users = append ? users + managed : managed
        hasMore = page.count == 200
        offset += page.count
    }

    func createUser(userId: String, password: String, displayName: String?) {
        guard !userId.trimmingCharacters(in: .whitespaces).isEmpty, !password.isEmpty, let s = session else { return }
        loading = true; errorMessage = nil
        Task {
            do {
                try await FlutCloudApi(session: s).createUser(userId: userId.trimmingCharacters(in: .whitespaces), password: password, displayName: displayName?.trimmingCharacters(in: .whitespaces).isEmpty == true ? nil : displayName)
                loadUsers()
            } catch {
                errorMessage = LoginViewModel.mapError(error)
            }
            loading = false
        }
    }

    func deleteUser(_ user: ManagedUser) {
        guard let s = session else { return }
        if user.id == s.username {
            errorMessage = NSLocalizedString("cannot_delete_self", comment: "")
            return
        }
        loading = true; errorMessage = nil
        Task {
            do {
                try await FlutCloudApi(session: s).deleteUser(userId: user.id)
                users.removeAll { $0.id == user.id }
            } catch {
                errorMessage = LoginViewModel.mapError(error)
            }
            loading = false
        }
    }

    func setQuota(_ user: ManagedUser, quotaBytes: Int64?) {
        guard let s = session else { return }
        loading = true; errorMessage = nil
        Task {
            do {
                try await FlutCloudApi(session: s).setUserQuota(userId: user.id, quotaBytes: quotaBytes)
                loadUsers()
            } catch {
                errorMessage = LoginViewModel.mapError(error)
            }
            loading = false
        }
    }

    func setEnabled(_ user: ManagedUser, enabled: Bool) {
        guard let s = session else { return }
        if user.id == s.username && !enabled {
            errorMessage = NSLocalizedString("cannot_disable_self", comment: "")
            return
        }
        loading = true; errorMessage = nil
        Task {
            do {
                try await FlutCloudApi(session: s).updateUser(userId: user.id, key: "enabled", value: enabled ? "1" : "0")
                loadUsers()
            } catch {
                errorMessage = LoginViewModel.mapError(error)
            }
            loading = false
        }
    }

    func addToGroup(_ user: ManagedUser, group: String) {
        let g = group.trimmingCharacters(in: .whitespaces)
        guard !g.isEmpty, let s = session else { return }
        loading = true; errorMessage = nil
        Task {
            do {
                try await FlutCloudApi(session: s).addGroupMember(groupId: g, userId: user.id)
                loadUsers()
            } catch {
                errorMessage = LoginViewModel.mapError(error)
            }
            loading = false
        }
    }

    func removeFromGroup(_ user: ManagedUser, group: String) {
        guard let s = session else { return }
        loading = true; errorMessage = nil
        Task {
            do {
                try await FlutCloudApi(session: s).removeGroupMember(groupId: group, userId: user.id)
                loadUsers()
            } catch {
                errorMessage = LoginViewModel.mapError(error)
            }
            loading = false
        }
    }

    func createGroup(_ name: String) {
        let g = name.trimmingCharacters(in: .whitespaces)
        guard !g.isEmpty, let s = session else { return }
        loading = true; errorMessage = nil
        Task {
            do {
                try await FlutCloudApi(session: s).createGroup(groupId: g)
            } catch {
                errorMessage = LoginViewModel.mapError(error)
            }
            loading = false
        }
    }

    func clearError() { errorMessage = nil }
}
