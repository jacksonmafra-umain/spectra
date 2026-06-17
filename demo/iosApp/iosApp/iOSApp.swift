import SwiftUI
import MWDATCore

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // The Meta AI app returns here after registration / permission via a
                // plain custom URL scheme (spectrademo://…) — no Universal Link, no
                // hosted apple-app-site-association, no Associated Domains. Same
                // approach as Meta's official CameraAccess sample.
                .onOpenURL { url in
                    print("Spectra: onOpenURL raw=\(url)")
                    // Only DAT callbacks carry the metaWearablesAction query item.
                    let comps = URLComponents(url: url, resolvingAgainstBaseURL: false)
                    guard comps?.queryItems?.contains(where: { $0.name == "metaWearablesAction" }) == true else {
                        return
                    }
                    Task {
                        do {
                            let handled = try await Wearables.shared.handleUrl(url)
                            print("Spectra: handleUrl handled=\(handled)")
                        } catch {
                            print("Spectra: handleUrl error=\(error)")
                        }
                    }
                }
        }
    }
}
