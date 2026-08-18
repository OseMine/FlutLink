// TEST — iOS port created by opencode. Not a production build.
import Foundation
import CommonCrypto

final class ListCache {
    private let cacheDir: URL

    init() {
        let paths = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)
        cacheDir = paths[0].appendingPathComponent("cache/listings", isDirectory: true)
        try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
    }

    func read(accountKey: String, path: String) -> [WebDavEntry]? {
        let file = cacheDir.appendingPathComponent(sha256("\(accountKey)|\(path)") + ".json")
        guard let data = try? Data(contentsOf: file) else { return nil }
        return try? JSONDecoder().decode([WebDavEntry].self, from: data)
    }

    func write(accountKey: String, path: String, entries: [WebDavEntry]) {
        let file = cacheDir.appendingPathComponent(sha256("\(accountKey)|\(path)") + ".json")
        if let data = try? JSONEncoder().encode(entries) {
            try? data.write(to: file)
        }
    }

    private func sha256(_ text: String) -> String {
        let data = text.data(using: .utf8)!
        var hash = [UInt8](repeating: 0, count: 32)
        data.withUnsafeBytes {
            _ = CC_SHA256($0.baseAddress, CC_LONG(data.count), &hash)
        }
        return hash.map { String(format: "%02x", $0) }.joined()
    }
}
