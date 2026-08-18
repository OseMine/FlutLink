// TEST — iOS port created by opencode. Not a production build.
import Foundation
import Combine

final class SessionManager: ObservableObject {
    private let accountStore: AccountStore

    @Published private(set) var session: AuthSession?
    @Published private(set) var accounts: [AccountMeta] = []

    init(accountStore: AccountStore) {
        self.accountStore = accountStore
    }

    @MainActor
    func init_() async {
        accounts = accountStore.loadAccounts()
        restoreSession()
        await refreshAdminFlags()
    }

    @MainActor
    func refreshAdminFlags() async {
        var updated = accounts
        for (index, account) in accounts.enumerated() {
            guard let token = accountStore.tokenFor(account) else { continue }
            let s = AuthSession(baseUrl: account.instanceUrl, username: account.username, token: token)
            let api = FlutCloudApi(session: s)
            let isAdmin = await api.isAdmin()
            updated[index] = AccountMeta(
                username: account.username,
                instanceUrl: account.instanceUrl,
                displayName: account.displayName,
                isAdmin: isAdmin,
                isActive: account.isActive
            )
        }
        if updated != accounts {
            updateAccounts(updated)
        }
    }

    @MainActor
    func addAccount(meta: AccountMeta, token: String) {
        var withoutOld = accounts.filter {
            !($0.username == meta.username && $0.instanceUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) == meta.instanceUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
        }
        let updatedMeta = AccountMeta(
            username: meta.username,
            instanceUrl: meta.instanceUrl,
            displayName: meta.displayName,
            isAdmin: meta.isAdmin,
            isActive: true
        )
        withoutOld.append(updatedMeta)
        let updated = withoutOld.map { AccountMeta(username: $0.username, instanceUrl: $0.instanceUrl, displayName: $0.displayName, isAdmin: $0.isAdmin, isActive: $0.username == meta.username) }
        accountStore.saveAccounts(updated)
        accountStore.saveToken(meta, token: token)
        accounts = updated
        session = AuthSession(baseUrl: meta.instanceUrl, username: meta.username, token: token)
    }

    @MainActor
    func updateAccounts(_ accounts: [AccountMeta]) {
        accountStore.saveAccounts(accounts)
        self.accounts = accounts
    }

    @MainActor
    func switchAccount(_ meta: AccountMeta) {
        let updated = accounts.map { AccountMeta(username: $0.username, instanceUrl: $0.instanceUrl, displayName: $0.displayName, isAdmin: $0.isAdmin, isActive: $0.username == meta.username) }
        updateAccounts(updated)
        restoreSession()
    }

    @MainActor
    func signOut() {
        let updated = accounts.map { AccountMeta(username: $0.username, instanceUrl: $0.instanceUrl, displayName: $0.displayName, isAdmin: $0.isAdmin, isActive: false) }
        updateAccounts(updated)
        session = nil
    }

    @MainActor
    func removeAccount(_ meta: AccountMeta) {
        accountStore.deleteToken(meta)
        let updated = accounts.filter {
            !($0.username == meta.username && $0.instanceUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) == meta.instanceUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
        }
        updateAccounts(updated)
        if updated.isEmpty {
            session = nil
        } else if session?.username == meta.username {
            let next = updated.first(where: \.isActive) ?? updated[0]
            if let token = accountStore.tokenFor(next) {
                session = AuthSession(baseUrl: next.instanceUrl, username: next.username, token: token)
            }
        }
    }

    private func restoreSession() {
        guard let active = accounts.first(where: \.isActive),
              let token = accountStore.tokenFor(active) else {
            session = nil
            return
        }
        session = AuthSession(baseUrl: active.instanceUrl, username: active.username, token: token)
    }
}
