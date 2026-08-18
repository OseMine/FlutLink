// TEST — iOS port created by opencode. Not a production build.
import SwiftUI

enum FlutTheme: String, CaseIterable {
    case operationflut, midnight, system

    var colorScheme: ColorScheme? {
        switch self {
        case .operationflut, .midnight: return .dark
        case .system: return nil
        }
    }

    var accentColor: Color {
        switch self {
        case .operationflut: return Color(hue: 266/360, saturation: 0.7, brightness: 0.9)
        case .midnight: return Color(hue: 220/360, saturation: 0.6, brightness: 0.85)
        case .system: return .blue
        }
    }

    func resolvedAccent(hue: Double?) -> Color {
        if let h = hue { return Color(hue: h/360, saturation: 0.7, brightness: 0.9) }
        return accentColor
    }

    func background(for scheme: ColorScheme) -> Color {
        switch self {
        case .operationflut: return Color(red: 9/255, green: 8/255, blue: 33/255)
        case .midnight: return Color(red: 0.05, green: 0.05, blue: 0.12)
        case .system: return scheme == .dark ? Color(red: 0.1, green: 0.1, blue: 0.12) : Color(.systemBackground)
        }
    }
}
