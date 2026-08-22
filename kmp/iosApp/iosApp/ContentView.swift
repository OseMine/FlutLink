import SwiftUI
import Shared

/// Hosts the Compose Multiplatform UI exposed by the shared KMP module
/// (`MainViewController()` in `shared/src/iosMain`).
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
