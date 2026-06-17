import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        // Hand the shared UI a real Spectra client backed by Meta's iOS SDK,
        // so the "Glasses (real)" toggle is enabled.
        MainViewControllerKt.MainViewController(bridge: SpectraBridge())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}