// TEST — iOS port created by opencode. Not a production build.
import Foundation
import Security

final class AccountStore: ObservableObject {
    private let defaults = UserDefaults.standard
    private static let accountsKey = "flutlink_accounts"

    func saveAccounts(_ accounts: [AccountMeta]) {
        if let data = try? JSONEncoder().encode(StoredAccounts(accounts: accounts)) {
            defaults.set(data, forKey: Self.accountsKey)
        }
    }

    func loadAccounts() -> [AccountMeta] {
        guard let data = defaults.data(forKey: Self.accountsKey),
              let stored = try? JSONDecoder().decode(StoredAccounts.self, from: data) else {
            return []
        }
        return stored.accounts
    }

    func saveToken(_ meta: AccountMeta, token: String) {
        let key = meta.key
        let data = token.data(using: .utf8)!
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.flutcloud.flutlink",
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        SecItemDelete(query as CFDictionary)
        SecItemAdd(query as CFDictionary, nil)
    }

    func tokenFor(_ meta: AccountMeta) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.flutcloud.flutlink",
            kSecAttrAccount as String: meta.key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }

    func deleteToken(_ meta: AccountMeta) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.flutcloud.flutlink",
            kSecAttrAccount as String: meta.key
        ]
        SecItemDelete(query as CFDictionary)
    }
}

// MARK: - AccountMeta

struct AccountMeta: Codable, Identifiable, Equatable {
    let username: String
    let instanceUrl: String
    let displayName: String?
    let isAdmin: Bool
    let isActive: Bool

    var id: String { key }
    var key: String { "\(username)@\(instanceUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")))" }
}

private struct StoredAccounts: Codable {
    let accounts: [AccountMeta]
}
