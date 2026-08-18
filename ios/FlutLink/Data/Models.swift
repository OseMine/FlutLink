// TEST — iOS port created by opencode. Not a production build.
import Foundation

// MARK: - User Info (OCS)

struct UserInfoDto: Codable {
    let id: String?
    let displayName: String?
    let isAdmin: Bool?
    let quota: QuotaDto?
    let email: String?

    enum CodingKeys: String, CodingKey {
        case id
        case displayName = "display-name"
        case isAdmin
        case quota
        case email
    }
}

struct QuotaDto: Codable {
    let total: AnyCodable?
    let used: AnyCodable?
    let free: AnyCodable?
    let relative: AnyCodable?

    var totalBytes: Int64? { total?.intValue }
    var usedBytes: Int64? { used?.intValue }
    var freeBytes: Int64? { free?.intValue }
    var relativePercent: Double? { relative?.doubleValue }
}

struct UserDetailsDto: Codable {
    let id: String?
    let displayName: String?
    let email: String?
    let quota: QuotaDto?
    let groups: [String]?
    let enabled: Bool?

    enum CodingKeys: String, CodingKey {
        case id
        case displayName = "display-name"
        case email, quota, groups, enabled
    }
}

struct ShareDto: Codable {
    let id: Int64?
    let shareType: Int64?
    let path: String?
    let shareWith: String?
    let shareWithDisplayName: String?
    let permissions: Int64?
    let url: String?
    let password: AnyCodable?
    let expiration: String?

    enum CodingKeys: String, CodingKey {
        case id
        case shareType = "share_type"
        case path
        case shareWith = "share_with"
        case shareWithDisplayName = "share_with_displayname"
        case permissions, url, password, expiration
    }

    var hasPassword: Bool? {
        guard let p = password else { return nil }
        if p.isNull { return false }
        return p.stringValue?.isEmpty == false
    }
}

struct AppInfoDto: Codable {
    let app: String?
    let name: String?
    let version: String?
    let features: [String]?
    let user: String?
}

struct FlutCloudItemDto: Codable {
    let name: String?
    let path: String?
    let readOnly: Bool?
}

struct CapabilitiesDto: Codable {
    let capabilities: CapabilitiesData?
}

struct CapabilitiesData: Codable {
    let flutcloud: AnyCodable?
}

// MARK: - Domain Models

struct Quota: Equatable {
    let total: Int64?
    let used: Int64?
    let free: Int64?
    let relative: Double?
}

struct WebDavEntry: Identifiable, Equatable {
    let name: String
    let path: String
    let isDir: Bool
    let size: Int64?
    let mtime: String?
    let etag: String?
    let contentType: String?
    let isResource: Bool
    let isPart: Bool
    let linkTarget: String?
    let pairedPath: String?

    var id: String { path }
    var isVirtualLink: Bool { linkTarget != nil }
}

struct Share: Identifiable, Equatable {
    let id: Int64
    let shareType: Int
    let path: String?
    let shareWith: String?
    let shareWithDisplayName: String?
    let permissions: Int64?
    let url: String?
    let hasPassword: Bool?
    let expiration: String?
}

struct SessionUser {
    let id: String
    let displayName: String?
    let isAdmin: Bool
}

struct ManagedUser: Identifiable {
    let id: String
    let displayName: String?
    let email: String?
    let quota: Quota?
    let groups: [String]
    let enabled: Bool
}

struct PendingUpload: Equatable {
    let targetDir: String
    let name: String
    let data: Data
    let contentType: String
}

struct TransferProgress: Equatable {
    let transferred: Int64
    let total: Int64
}

// MARK: - GitHub Release DTOs

struct GithubAsset: Codable {
    let name: String
    let browserDownloadUrl: String

    enum CodingKeys: String, CodingKey {
        case name
        case browserDownloadUrl = "browser_download_url"
    }
}

struct GithubRelease: Codable {
    let tagName: String
    let assets: [GithubAsset]

    enum CodingKeys: String, CodingKey {
        case tagName = "tag_name"
        case assets
    }
}

// MARK: - AnyCodable helper

struct AnyCodable: Codable, Equatable {
    let value: Any?

    var isNull: Bool { value == nil }
    var intValue: Int64? {
        if let i = value as? Int64 { return i }
        if let i = value as? Int { return Int64(i) }
        if let s = value as? String { return Int64(s) }
        if let d = value as? Double { return Int64(d) }
        return nil
    }
    var doubleValue: Double? {
        if let d = value as? Double { return d }
        if let i = value as? Int { return Double(i) }
        if let s = value as? String { return Double(s) }
        return nil
    }
    var stringValue: String? { value as? String }

    init(_ value: Any?) { self.value = value }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() { value = nil; return }
        if let i = try? container.decode(Int64.self) { value = i; return }
        if let d = try? container.decode(Double.self) { value = d; return }
        if let s = try? container.decode(String.self) { value = s; return }
        if let b = try? container.decode(Bool.self) { value = b; return }
        value = nil
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch value {
        case nil: try container.encodeNil()
        case let i as Int64: try container.encode(i)
        case let d as Double: try container.encode(d)
        case let s as String: try container.encode(s)
        case let b as Bool: try container.encode(b)
        default: try container.encodeNil()
        }
    }

    static func == (lhs: AnyCodable, rhs: AnyCodable) -> Bool {
        switch (lhs.value, rhs.value) {
        case (nil, nil): return true
        case let (l as Int64, r as Int64): return l == r
        case let (l as Double, r as Double): return l == r
        case let (l as String, r as String): return l == r
        case let (l as Bool, r as Bool): return l == r
        default: return false
        }
    }
}
