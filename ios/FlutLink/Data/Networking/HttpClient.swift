// TEST — iOS port created by opencode. Not a production build.
import Foundation

enum HttpClient {
    static let userAgent = "FlutLink-iOS/\(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0")"

    static func makeRequest(
        url: URL,
        method: String = "GET",
        session: AuthSession,
        headers: [String: String] = [:],
        body: Data? = nil
    ) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("Basic \(Data("\(session.username):\(session.token)".utf8).base64EncodedString())", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }
        if let body = body {
            request.httpBody = body
        }
        return request
    }

    static func ocsApiRequest(
        url: URL,
        method: String = "GET",
        session: AuthSession,
        formFields: [(String, String)]? = nil,
        headers: [String: String] = [:]
    ) -> URLRequest {
        var request = makeRequest(url: url, method: method, session: session, headers: headers)
        request.setValue("true", forHTTPHeaderField: "OCS-APIRequest")
        if let fields = formFields {
            var components = URLComponents(url: url, resolvingAgainstBaseURL: false)!
            if method == "GET" || method == "DELETE" {
                var items = components.queryItems ?? []
                for (key, value) in fields {
                    items.append(URLQueryItem(name: key, value: value))
                }
                components.queryItems = items
                request.url = components.url
            } else {
                let formBody = fields.map { "\($0.0)=\($0.1.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? $0.1)" }
                    .joined(separator: "&")
                request.httpBody = formBody.data(using: .utf8)
                request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
            }
        }
        return request
    }

    static func davRequest(
        url: URL,
        method: String = "GET",
        session: AuthSession,
        targetUser: String? = nil,
        headers: [String: String] = [:]
    ) -> URLRequest {
        var request = makeRequest(url: url, method: method, session: session, headers: headers)
        if let target = targetUser, target != session.username {
            request.setValue(target, forHTTPHeaderField: "Impersonate-User")
        }
        return request
    }

    static func perform(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiException.network(underlying: NSError(domain: "HttpClient", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid response type"]))
        }
        return (data, httpResponse)
    }
}

extension String {
    var urlEncoded: String {
        addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? self
    }
}
