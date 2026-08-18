// TEST — iOS port created by opencode. Not a production build.
import Foundation

enum ApiException: LocalizedError {
    case api(message: String, code: String = "api_error", statusCode: Int = 0)
    case flutCloudAppMissing
    case network(underlying: Error)

    var message: String {
        switch self {
        case .api(let message, _, _):
            return message
        case .flutCloudAppMissing:
            return "This server does not run the FlutCloud app."
        case .network(let underlying):
            return underlying.localizedDescription
        }
    }

    var code: String {
        switch self {
        case .api(_, let code, _):
            return code
        case .flutCloudAppMissing:
            return "flutcloud_app_missing"
        case .network:
            return "network_error"
        }
    }

    var statusCode: Int {
        switch self {
        case .api(_, _, let statusCode):
            return statusCode
        default:
            return 0
        }
    }
}
