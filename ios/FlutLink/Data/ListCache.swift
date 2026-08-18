// TEST — iOS port created by opencode. Not a production build.
import Foundation
import CommonCrypto

final class ListCache {
    private let cacheDir: URL
    private let maxEntries = 500

    init() {
        let paths = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)
        cacheDir = paths[0].appendingPathComponent("cache/listings", isDirectory: true)
        try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
    }

    func read(accountKey: String, path: String) -> [WebDavEntry]? {
        let file = cacheDir.appendingPathComponent(sha256("\(accountKey)|\(path)") + ".json")
        guard let data = try? Data(contentsOf: file) else { return nil }
        let result = try? JSONDecoder().decode([WebDavEntry].self, from: data)
        if result != nil { touchFile(file) }
        return result
    }

    func write(accountKey: String, path: String, entries: [WebDavEntry]) {
        let file = cacheDir.appendingPathComponent(sha256("\(accountKey)|\(path)") + ".json")
        if let data = try? JSONEncoder().encode(entries) {
            try? data.write(to: file)
        }
        evictIfNeeded()
    }

    private func touchFile(_ url: URL) {
        try? FileManager.default.setAttributes(
            [.modificationDate: Date()],
            ofItemAtPath: url.path
        )
    }

    private func evictIfNeeded() {
        guard let files = try? FileManager.default.contentsOfDirectory(
            at: cacheDir,
            includingPropertiesForKeys: [.modificationDateKey],
            options: .skipsHiddenFiles
        ) else { return }
        guard files.count > maxEntries else { return }
        let sorted = files.sorted { a, b in
            let aDate = (try? a.resourceValues(forKeys: [.modificationDateKey]).modificationDate) ?? .distantPast
            let bDate = (try? b.resourceValues(forKeys: [.modificationDateKey]).modificationDate) ?? .distantPast
            return aDate < bDate
        }
        let toRemove = sorted.prefix(files.count - maxEntries)
        for file in toRemove {
            try? FileManager.default.removeItem(at: file)
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
