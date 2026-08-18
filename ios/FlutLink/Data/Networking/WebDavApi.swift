// TEST — iOS port created by opencode. Not a production build.
import Foundation

final class WebDavApi {
    private let session: AuthSession

    init(session: AuthSession) {
        self.session = session
    }

    func withSession(_ session: AuthSession) -> WebDavApi {
        WebDavApi(session: session)
    }

    private func effectiveUser(_ targetUser: String?) -> String {
        targetUser ?? session.username
    }

    private func davRoot(_ targetUser: String? = nil) -> String {
        "\(session.normalizedBaseUrl)/remote.php/dav/files/\(effectiveUser(targetUser).urlEncoded)"
    }

    private func davUrl(path: String, targetUser: String? = nil) -> String {
        let root = davRoot(targetUser)
        if path.isEmpty || path == "/" { return root }
        return "\(root)/\(path.split(separator: "/").map { String($0).urlEncoded }.joined(separator: "/"))"
    }

    // MARK: - PROPFIND

    func list(path: String, targetUser: String? = nil) async throws -> [WebDavEntry] {
        let url = URL(string: davUrl(path: path, targetUser: targetUser))!
        var request = HttpClient.davRequest(url: url, method: "PROPFIND", session: session, targetUser: targetUser)
        request.setValue("1", forHTTPHeaderField: "Depth")
        let (data, response) = try await HttpClient.perform(request)
        guard response.statusCode == 207 || (200...299).contains(response.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw ApiException.api(message: "Server answered \(response.statusCode): \(body)", code: "http_\(response.statusCode)", statusCode: response.statusCode)
        }
        let basePath = "/remote.php/dav/files/\(effectiveUser(targetUser).urlEncoded)"
        let entries = Self.parseMultistatus(data: data, basePath: basePath)
        let normalizedPath = path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let currentPath = normalizedPath.isEmpty ? "/" : "/\(normalizedPath)"
        return entries.filter { $0.path != currentPath }
    }

    // MARK: - SEARCH

    func search(query: String, targetUser: String? = nil) async throws -> [WebDavEntry] {
        let url = URL(string: "\(session.normalizedBaseUrl)/remote.php/dav/")!
        let body = Self.searchRequestBody(user: effectiveUser(targetUser), query: query)
        var request = HttpClient.davRequest(url: url, method: "SEARCH", session: session, targetUser: targetUser)
        request.setValue("0", forHTTPHeaderField: "Depth")
        request.setValue("application/xml", forHTTPHeaderField: "Content-Type")
        request.httpBody = body.data(using: .utf8)
        let (data, response) = try await HttpClient.perform(request)
        guard response.statusCode == 207 || (200...299).contains(response.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw ApiException.api(message: "Server answered \(response.statusCode): \(body)", code: "http_\(response.statusCode)", statusCode: response.statusCode)
        }
        let basePath = "/remote.php/dav/files/\(effectiveUser(targetUser).urlEncoded)"
        return Self.parseMultistatus(data: data, basePath: basePath)
    }

    // MARK: - Exists

    func exists(path: String, targetUser: String? = nil) async throws -> Bool {
        let url = URL(string: davUrl(path: path, targetUser: targetUser))!
        var request = HttpClient.davRequest(url: url, method: "PROPFIND", session: session, targetUser: targetUser)
        request.setValue("0", forHTTPHeaderField: "Depth")
        let (_, response) = try await HttpClient.perform(request)
        switch response.statusCode {
        case 200...299, 207: return true
        case 404: return false
        default:
            throw ApiException.api(message: "Server answered \(response.statusCode).", code: "http_\(response.statusCode)", statusCode: response.statusCode)
        }
    }

    // MARK: - Upload (PUT)

    func upload(path: String, data: Data, contentType: String = "application/octet-stream", mtimeEpochSeconds: Int64? = nil, targetUser: String? = nil) async throws {
        let url = URL(string: davUrl(path: path, targetUser: targetUser))!
        var request = HttpClient.davRequest(url: url, method: "PUT", session: session, targetUser: targetUser)
        request.httpBody = data
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        if let mtime = mtimeEpochSeconds { request.setValue("\(mtime)", forHTTPHeaderField: "X-OC-MTime") }
        try await performStatusCheck(request)
    }

    func uploadStream(
        path: String,
        data: Data,
        contentType: String = "application/octet-stream",
        mtimeEpochSeconds: Int64? = nil,
        targetUser: String? = nil,
        onProgress: ((Int64, Int64) -> Void)? = nil
    ) async throws {
        try await upload(path: path, data: data, contentType: contentType, mtimeEpochSeconds: mtimeEpochSeconds, targetUser: targetUser)
        onProgress?(Int64(data.count), Int64(data.count))
    }

    // MARK: - Download (GET)

    func downloadToFile(path: String, targetUser: String? = nil, onProgress: ((Int64, Int64) -> Void)? = nil) async throws -> Data {
        let url = URL(string: davUrl(path: path, targetUser: targetUser))!
        let request = HttpClient.davRequest(url: url, method: "GET", session: session, targetUser: targetUser)
        let (tempURL, response) = try await URLSession.shared.download(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw ApiException.api(message: "Download failed: HTTP \(statusCode)", code: "http_\(statusCode)", statusCode: statusCode)
        }
        let data = try Data(contentsOf: tempURL)
        let total = Int64(data.count)
        onProgress?(total, total)
        return data
    }

    // MARK: - MKCOL

    func mkdir(path: String, targetUser: String? = nil) async throws {
        let url = URL(string: davUrl(path: path, targetUser: targetUser))!
        let request = HttpClient.davRequest(url: url, method: "MKCOL", session: session, targetUser: targetUser)
        try await performStatusCheck(request, ignoreStatus: 405)
    }

    // MARK: - DELETE

    func delete(path: String, targetUser: String? = nil) async throws {
        let url = URL(string: davUrl(path: path, targetUser: targetUser))!
        let request = HttpClient.davRequest(url: url, method: "DELETE", session: session, targetUser: targetUser)
        try await performStatusCheck(request, ignoreStatus: 404)
    }

    // MARK: - MOVE (rename)

    func rename(path: String, newPath: String, targetUser: String? = nil) async throws {
        let url = URL(string: davUrl(path: path, targetUser: targetUser))!
        var request = HttpClient.davRequest(url: url, method: "MOVE", session: session, targetUser: targetUser)
        request.setValue(davUrl(path: newPath, targetUser: targetUser), forHTTPHeaderField: "Destination")
        request.setValue("F", forHTTPHeaderField: "Overwrite")
        let (_, response) = try await HttpClient.perform(request)
        if response.statusCode == 412 {
            throw ApiException.api(message: "Destination already exists: \(newPath)", code: "target_exists", statusCode: 412)
        }
        guard response.statusCode == 200 || response.statusCode == 201 || response.statusCode == 204 || response.statusCode == 404 else {
            throw ApiException.api(message: "Rename failed: HTTP \(response.statusCode)", code: "http_\(response.statusCode)", statusCode: response.statusCode)
        }
    }

    // MARK: - Helpers

    private func performStatusCheck(_ request: URLRequest, ignoreStatus: Int? = nil) async throws {
        let (_, response) = try await HttpClient.perform(request)
        if let ignore = ignoreStatus, response.statusCode == ignore { return }
        guard (200...299).contains(response.statusCode) else {
            let body = String(data: Data(), encoding: .utf8) ?? ""
            throw ApiException.api(message: "Server answered \(response.statusCode): \(body)", code: "http_\(response.statusCode)", statusCode: response.statusCode)
        }
    }

    // MARK: - XML Parsing

    static func parseMultistatus(data: Data, basePath: String) -> [WebDavEntry] {
        let parser = WebDavXMLParser(data: data, basePath: basePath)
        return parser.parse()
    }

    private static func searchRequestBody(user: String, query: String) -> String {
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <d:searchrequest xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
          <d:basicsearch>
            <d:select>
              <d:prop>
                <d:displayname/>
                <d:getcontentlength/>
                <d:getlastmodified/>
                <d:getetag/>
                <d:getcontenttype/>
              </d:prop>
            </d:select>
            <d:from>
              <d:scope>
                <d:href>/files/\(escapeXml(user))</d:href>
                <d:depth>infinity</d:depth>
              </d:scope>
            </d:from>
            <d:where>
              <d:eq>
                <d:prop><d:displayname/></d:prop>
                <d:literal>\(escapeXml(query))</d:literal>
              </d:eq>
            </d:where>
            <d:orderby/>
          </d:basicsearch>
        </d:searchrequest>
        """
    }

    private static func escapeXml(_ text: String) -> String {
        text.replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
    }

    static func hrefPath(_ href: String) -> String {
        guard let range = href.range(of: "://") else { return href }
        let after = String(href[range.upperBound...])
        guard let slashIdx = after.firstIndex(of: "/") else { return "/" }
        return String(after[slashIdx...])
    }

    static func relativePath(href: String, basePath: String) -> String {
        let path = hrefPath(href)
        guard let range = path.range(of: basePath) else {
            let trimmed = path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            return trimmed.isEmpty ? "/" : "/" + trimmed
        }
        let after = String(path[range.upperBound...])
        let trimmed = after.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if trimmed.isEmpty { return "/" }
        return "/" + trimmed.split(separator: "/").map {
            $0.removingPercentEncoding ?? String($0)
        }.joined(separator: "/")
    }

    static func classify(_ rel: String) -> (isResource: Bool, isPart: Bool) {
        var isResource = false
        var isPart = false
        for segment in rel.split(separator: "/") {
            switch segment.lowercased() {
            case "resources": isResource = true
            case "parts": isPart = true
            default: break
            }
        }
        return (isResource, isPart)
    }

    static func resolveLinkTarget(_ rel: String) -> String? {
        let segments = rel.trimmingCharacters(in: CharacterSet(charactersIn: "/")).split(separator: "/")
        guard segments.count >= 2 else { return nil }
        let target: String
        switch segments[0].lowercased() {
        case "resources": target = "parts"
        case "parts": target = "resources"
        default: return nil
        }
        let tail = segments.dropFirst(1).map { s in String(s) }
        return "/" + ([target] + tail).joined(separator: "/")
    }

    static func pairedPath(_ rel: String) -> String? {
        let segments = rel.split(separator: "/")
        for (i, segment) in segments.enumerated() {
            let paired: String
            switch segment.lowercased() {
            case "resources": paired = "parts"
            case "parts": paired = "resources"
            default: continue
            }
            var copy = segments.map(String.init)
            copy[i] = paired
            return copy.joined(separator: "/")
        }
        return nil
    }
}

// MARK: - XML Parser

final class WebDavXMLParser: NSObject, XMLParserDelegate {
    private var entries: [WebDavEntry] = []
    private let basePath: String

    private var currentHref: String?
    private var currentIsDir = false
    private var inResourceType = false
    private var currentField: String?
    private var textBuffer = ""
    private var currentSize: Int64?
    private var currentMtime: String?
    private var currentEtag: String?
    private var currentContentType: String?

    init(data: Data, basePath: String) {
        self.basePath = basePath
        super.init()
        let parser = XMLParser(data: data)
        parser.delegate = self
        parser.parse()
    }

    func parse() -> [WebDavEntry] {
        return entries
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String: String] = [:]) {
        switch elementName {
        case "response":
            currentHref = nil; currentIsDir = false; currentSize = nil
            currentMtime = nil; currentEtag = nil; currentContentType = nil
        case "href": currentField = "href"
        case "resourcetype": inResourceType = true
        case "collection": if inResourceType { currentIsDir = true }
        case "getcontentlength": currentField = "size"
        case "getlastmodified": currentField = "mtime"
        case "getetag": currentField = "etag"
        case "getcontenttype": currentField = "contenttype"
        default: break
        }
        textBuffer = ""
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if currentField != nil { textBuffer += string }
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        if let field = currentField {
            let value = textBuffer.trimmingCharacters(in: .whitespacesAndNewlines)
            switch field {
            case "href": currentHref = value
            case "size": currentSize = Int64(value)
            case "mtime": currentMtime = value
            case "etag": currentEtag = value
            case "contenttype": currentContentType = value
            default: break
            }
            currentField = nil
        }
        switch elementName {
        case "resourcetype": inResourceType = false
        case "response":
            if let href = currentHref {
                let rel = WebDavApi.relativePath(href: href, basePath: basePath)
                if rel != "/" && !rel.isEmpty {
                    let name = String(rel.split(separator: "/").last ?? Substring(rel))
                    if !name.isEmpty {
                        let (isResource, isPart) = WebDavApi.classify(rel)
                        entries.append(WebDavEntry(
                            name: name,
                            path: rel,
                            isDir: currentIsDir,
                            size: currentSize,
                            mtime: currentMtime,
                            etag: currentEtag,
                            contentType: currentContentType,
                            isResource: isResource,
                            isPart: isPart,
                            linkTarget: WebDavApi.resolveLinkTarget(rel),
                            pairedPath: WebDavApi.pairedPath(rel)
                        ))
                    }
                }
            }
        default: break
        }
    }
}
