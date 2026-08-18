// TEST — iOS port created by opencode. Not a production build.
import Foundation

struct AuthSession: Codable, Equatable {
    let baseUrl: String
    let username: String
    let token: String

    var normalizedBaseUrl: String {
        baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }
}
