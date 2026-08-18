// TEST — iOS port created by opencode. Not a production build.
import Foundation
import Combine

final class SettingsStore: ObservableObject {
    private let defaults = UserDefaults.standard

    @Published var themePreference: String {
        didSet { defaults.set(themePreference, forKey: Keys.themePreference) }
    }

    @Published var accentHue: Double? {
        didSet {
            if let h = accentHue {
                defaults.set(h, forKey: Keys.accentHue)
            } else {
                defaults.removeObject(forKey: Keys.accentHue)
            }
        }
    }

    @Published var defaultServerUrl: String {
        didSet { defaults.set(defaultServerUrl, forKey: Keys.defaultServerUrl) }
    }

    private enum Keys {
        static let themePreference = "flutlink_theme_preference"
        static let accentHue = "flutlink_accent_hue"
        static let defaultServerUrl = "flutlink_default_server_url"
    }

    init() {
        self.themePreference = UserDefaults.standard.string(forKey: Keys.themePreference) ?? "system"
        self.accentHue = UserDefaults.standard.object(forKey: Keys.accentHue) as? Double
        self.defaultServerUrl = UserDefaults.standard.string(forKey: Keys.defaultServerUrl) ?? ""
    }
}
