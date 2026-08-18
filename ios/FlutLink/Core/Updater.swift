// TEST — iOS port created by opencode. Not a production build.
import Foundation

/// A downloadable IPA in a GitHub release.
struct AppUpdate: Identifiable {
    let version: String
    let ipaUrl: String
    var id: String { version }
}

/// Self-updater for the iOS client: checks the FlutLink GitHub releases for a
/// newer version with an IPA asset. On iOS there is no silent install — the
/// update flow opens the GitHub release page (or downloads the IPA for
/// AltStore / Sideloadly re-signing).
final class Updater {
    private let repo: String

    init(repo: String = "OseMine/FlutLink") {
        self.repo = repo
    }

    /// Query the latest GitHub release. Returns an ``AppUpdate`` when a newer
    /// version with an IPA asset exists, `nil` when up to date.
    func checkForUpdate(currentVersion: String) async throws -> AppUpdate? {
        let url = URL(string: "https://api.github.com/repos/\(repo)/releases/latest")!
        var request = URLRequest(url: url)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            return nil
        }
        let release = try JSONDecoder().decode(GithubRelease.self, from: data)
        let tag = release.tagName.replacingOccurrences(of: "^v", with: "", options: .regularExpression)
        guard Self.compareVersions(tag, Self.baseVersion(currentVersion)) > 0 else {
            return nil
        }
        guard let ipa = release.assets.first(where: { $0.name.lowercased().hasSuffix(".ipa") }) else {
            return nil
        }
        return AppUpdate(version: tag, ipaUrl: ipa.browserDownloadUrl)
    }

    // MARK: - Helpers

    /// Normalize "1.0.0-debug" → "1.0.0".
    static func baseVersion(_ version: String) -> String {
        version.components(separatedBy: CharacterSet(charactersIn: "-+")).first ?? version
    }

    /// Compare dotted versions; "1.0.0" > "0.1.0".
    static func compareVersions(_ a: String, _ b: String) -> Int {
        let pa = a.split(separator: ".").map { Int($0) ?? 0 }
        let pb = b.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(pa.count, pb.count) {
            let x = i < pa.count ? pa[i] : 0
            let y = i < pb.count ? pb[i] : 0
            if x != y { return x < y ? -1 : 1 }
        }
        return 0
    }
}
