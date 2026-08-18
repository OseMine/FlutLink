// TEST — iOS port created by opencode. Not a production build.
import Foundation

final class FlutCloudApi {
    private let session: AuthSession

    init(session: AuthSession) {
        self.session = session
    }

    func withSession(_ session: AuthSession) -> FlutCloudApi {
        FlutCloudApi(session: session)
    }

    // MARK: - OCS Helpers

    private func parseOcs(_ data: Data) throws -> (AnyCodable?, String?) {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let ocs = json["ocs"] as? [String: Any],
              let meta = ocs["meta"] as? [String: Any] else {
            return (nil, "Invalid OCS response")
        }
        let status = meta["status"] as? String
        let code = meta["statuscode"] as? String ?? "\(meta["statuscode"] ?? "")"
        let message = meta["message"] as? String ?? "Unknown OCS error"
        let ok = status?.lowercased() == "ok" || code == "100" || code == "200"
        let ocsData = ocs["data"]
        let wrappedData: AnyCodable? = ocsData != nil ? AnyCodable(ocsData) : nil
        return (wrappedData, ok ? nil : message)
    }

    private func executeOcs(
        method: String,
        path: String,
        formFields: [(String, String)]? = nil
    ) async throws -> AnyCodable {
        let url = URL(string: "\(session.normalizedBaseUrl)\(path)")!
        var request = HttpClient.ocsApiRequest(url: url, method: method, session: session, formFields: formFields)
        let (data, response) = try await HttpClient.perform(request)
        let (ocsData, ocsError) = try parseOcs(data)
        if response.statusCode >= 400 {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw ApiException.api(message: ocsError ?? "Server answered \(response.statusCode): \(body)", code: "http_\(response.statusCode)", statusCode: response.statusCode)
        }
        if let err = ocsError {
            throw ApiException.api(message: err, code: "ocs_error", statusCode: response.statusCode)
        }
        guard let result = ocsData else {
            throw ApiException.api(message: "Missing OCS data")
        }
        return result
    }

    // MARK: - FlutCloud Verification

    func verifyServer() async throws {
        let data = try await executeOcs(method: "GET", path: "/ocs/v2.php/cloud/capabilities?format=json")
        let has = data.value as? [String: Any]
        let caps = has?["capabilities"] as? [String: Any]
        guard caps?["flutcloud"] != nil else {
            throw ApiException.flutCloudAppMissing
        }
    }

    // MARK: - Current User

    func getCurrentUser() async throws -> SessionUser {
        let data = try await executeOcs(method: "GET", path: "/ocs/v2.php/cloud/user?format=json")
        guard let dict = data.value as? [String: Any] else {
            throw ApiException.api(message: "Missing user data")
        }
        guard let id = dict["id"] as? String else {
            throw ApiException.api(message: "Missing user id")
        }
        return SessionUser(
            id: id,
            displayName: dict["display-name"] as? String,
            isAdmin: dict["isAdmin"] as? Bool ?? false
        )
    }

    func getCurrentQuota() async -> Quota? {
        guard let data = try? await executeOcs(method: "GET", path: "/ocs/v2.php/cloud/user?format=json"),
              let dict = data.value as? [String: Any],
              let quotaDict = dict["quota"] as? [String: Any] else {
            return nil
        }
        let qd = AnyCodable(quotaDict)
        return Quota(
            total: qd.totalBytes,
            used: qd.usedBytes,
            free: qd.freeBytes,
            relative: qd.relativePercent
        )
    }

    func isAdmin() async -> Bool {
        do {
            _ = try await executeOcs(method: "GET", path: "/ocs/v1.php/cloud/users?format=json&limit=1")
            return true
        } catch let error as ApiException {
            if error.statusCode == 401 || error.statusCode == 403 || error.code == "ocs_error" {
                return false
            }
            return false
        } catch {
            return false
        }
    }

    func ping() async -> AppInfoDto? {
        guard let data = try? await executeOcs(method: "GET", path: "/ocs/v2.php/apps/flutcloud/api/v1/ping") else {
            return nil
        }
        return try? JSONDecoder().decode(AppInfoDto.self, from: JSONSerialization.data(withJSONObject: data.value ?? [:]))
    }

    // MARK: - OCS Provisioning API

    func listUsersPage(search: String, offset: Int, limit: Int = 200) async throws -> [String] {
        var path = "/ocs/v1.php/cloud/users?format=json&limit=\(limit)&offset=\(offset)"
        if !search.isEmpty { path += "&search=\(search.urlEncoded)" }
        let data = try await executeOcs(method: "GET", path: path)
        guard let dict = data.value as? [String: Any],
              let users = dict["users"] as? [String] else {
            return []
        }
        return users
    }

    func getUser(userId: String) async throws -> ManagedUser {
        let data = try await executeOcs(method: "GET", path: "/ocs/v1.php/cloud/users/\(userId.urlEncoded)?format=json")
        guard let dict = data.value as? [String: Any] else {
            throw ApiException.api(message: "Missing user data")
        }
        let quotaDict = dict["quota"] as? [String: Any]
        let qd = quotaDict.map { AnyCodable($0) }
        return ManagedUser(
            id: dict["id"] as? String ?? userId,
            displayName: dict["display-name"] as? String,
            email: dict["email"] as? String,
            quota: qd.flatMap { Quota(total: $0.totalBytes, used: $0.usedBytes, free: $0.freeBytes, relative: $0.relativePercent) },
            groups: dict["groups"] as? [String] ?? [],
            enabled: dict["enabled"] as? Bool ?? true
        )
    }

    func createUser(userId: String, password: String, displayName: String? = nil) async throws {
        var fields: [(String, String)] = [("userid", userId), ("password", password)]
        if let dn = displayName, !dn.isEmpty { fields.append(("displayName", dn)) }
        _ = try await executeOcs(method: "POST", path: "/ocs/v1.php/cloud/users?format=json", formFields: fields)
    }

    func deleteUser(userId: String) async throws {
        _ = try await executeOcs(method: "DELETE", path: "/ocs/v1.php/cloud/users/\(userId.urlEncoded)?format=json")
    }

    func updateUser(userId: String, key: String, value: String) async throws {
        _ = try await executeOcs(method: "PUT", path: "/ocs/v1.php/cloud/users/\(userId.urlEncoded)?format=json", formFields: [("key", key), ("value", value)])
    }

    func setUserQuota(userId: String, quotaBytes: Int64?) async throws {
        let value = quotaBytes?.description ?? "none"
        try await updateUser(userId: userId, key: "quota", value: value)
    }

    // MARK: - Groups

    func listGroups(search: String = "") async throws -> [String] {
        let limit = 200
        var all: [String] = []
        var seen = Set<String>()
        var offset = 0
        while true {
            var path = "/ocs/v1.php/cloud/groups?format=json&limit=\(limit)&offset=\(offset)"
            if !search.isEmpty { path += "&search=\(search.urlEncoded)" }
            let data = try await executeOcs(method: "GET", path: path)
            guard let dict = data.value as? [String: Any],
                  let groups = dict["groups"] as? [String] else { break }
            let newGroups = groups.filter { seen.insert($0).inserted }
            if newGroups.isEmpty { break }
            all.append(contentsOf: newGroups)
            if newGroups.count < limit { break }
            offset += limit
        }
        return all
    }

    func createGroup(groupId: String) async throws {
        _ = try await executeOcs(method: "POST", path: "/ocs/v1.php/cloud/groups?format=json", formFields: [("groupid", groupId)])
    }

    func addGroupMember(groupId: String, userId: String) async throws {
        _ = try await executeOcs(method: "POST", path: "/ocs/v1.php/cloud/groups/\(groupId.urlEncoded)?format=json", formFields: [("userid", userId)])
    }

    func removeGroupMember(groupId: String, userId: String) async throws {
        _ = try await executeOcs(method: "DELETE", path: "/ocs/v1.php/cloud/groups/\(groupId.urlEncoded)/users/\(userId.urlEncoded)?format=json")
    }

    // MARK: - Shares

    func createShare(
        path: String,
        shareType: Int,
        shareWith: String? = nil,
        password: String? = nil,
        expireDate: String? = nil,
        publicUpload: Bool = false,
        permissions: Int64? = nil
    ) async throws -> Share {
        var fields: [(String, String)] = [("path", path), ("shareType", "\(shareType)")]
        if let sw = shareWith { fields.append(("shareWith", sw)) }
        if let pw = password, !pw.isEmpty { fields.append(("password", pw)) }
        if let ed = expireDate, !ed.isEmpty { fields.append(("expireDate", ed)) }
        let perms: Int64? = permissions ?? (publicUpload ? 15 : (shareType == 3 ? 1 : nil))
        if let p = perms { fields.append(("permissions", "\(p)")) }
        let data = try await executeOcs(method: "POST", path: "/ocs/v2.php/apps/files_sharing/api/v1/shares?format=json", formFields: fields)
        guard let dict = data.value as? [String: Any],
              let share = try? JSONDecoder().decode(ShareDto.self, from: JSONSerialization.data(withJSONObject: dict)),
              let id = share.id else {
            throw ApiException.api(message: "Share endpoint returned no share data")
        }
        return Share(
            id: id,
            shareType: Int(share.shareType ?? 0),
            path: share.path,
            shareWith: share.shareWith,
            shareWithDisplayName: share.shareWithDisplayName,
            permissions: share.permissions,
            url: share.url,
            hasPassword: share.hasPassword,
            expiration: share.expiration
        )
    }

    func listShares(path: String? = nil) async throws -> [Share] {
        var urlPath = "/ocs/v2.php/apps/files_sharing/api/v1/shares?format=json"
        if let p = path { urlPath += "&path=\(p.urlEncoded)" }
        let data = try await executeOcs(method: "GET", path: urlPath)
        guard let arr = data.value as? [[String: Any]] else { return [] }
        return arr.compactMap { dict in
            guard let dto = try? JSONDecoder().decode(ShareDto.self, from: JSONSerialization.data(withJSONObject: dict)),
                  let id = dto.id else { return nil }
            return Share(
                id: id,
                shareType: Int(dto.shareType ?? 0),
                path: dto.path,
                shareWith: dto.shareWith,
                shareWithDisplayName: dto.shareWithDisplayName,
                permissions: dto.permissions,
                url: dto.url,
                hasPassword: dto.hasPassword,
                expiration: dto.expiration
            )
        }
    }

    func deleteShare(shareId: Int64) async throws {
        _ = try await executeOcs(method: "DELETE", path: "/ocs/v2.php/apps/files_sharing/api/v1/shares/\(shareId)?format=json")
    }

    // MARK: - Virtual Links & Parts

    func listLinks() async throws -> [FlutCloudItemDto] {
        let data = try await executeOcs(method: "GET", path: "/ocs/v2.php/apps/flutcloud/api/v1/links")
        guard let arr = data.value as? [[String: Any]] else { return [] }
        return arr.compactMap { try? JSONDecoder().decode(FlutCloudItemDto.self, from: JSONSerialization.data(withJSONObject: $0)) }
    }

    func createLink(name: String) async throws {
        _ = try await executeOcs(method: "POST", path: "/ocs/v2.php/apps/flutcloud/api/v1/links", formFields: [("name", name)])
    }

    func deleteLink(name: String) async throws {
        _ = try await executeOcs(method: "DELETE", path: "/ocs/v2.php/apps/flutcloud/api/v1/links/\(name.urlEncoded)")
    }

    func listParts() async throws -> [FlutCloudItemDto] {
        let data = try await executeOcs(method: "GET", path: "/ocs/v2.php/apps/flutcloud/api/v1/parts")
        guard let arr = data.value as? [[String: Any]] else { return [] }
        return arr.compactMap { try? JSONDecoder().decode(FlutCloudItemDto.self, from: JSONSerialization.data(withJSONObject: $0)) }
    }

    func createPart(name: String) async throws {
        _ = try await executeOcs(method: "POST", path: "/ocs/v2.php/apps/flutcloud/api/v1/parts", formFields: [("name", name)])
    }
}
