import SwiftUI
import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        _ = CallIntegrationManager.shared
        VoipPushManager.shared.start()
        return true
    }

    func applicationWillTerminate(_ application: UIApplication) {
        NotificationCenter.default.post(
            name: .esdiacCallKitEndRequested,
            object: nil
        )
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    guard url.scheme == "esdiac" else { return }
                    switch url.host {
                    case "endcall":
                        NotificationCenter.default.post(
                            name: .esdiacCallKitEndRequested,
                            object: nil
                        )
                    case "speaker":
                        NotificationCenter.default.post(
                            name: .esdiacCallKitSpeakerToggleRequested,
                            object: nil
                        )
                    default:
                        break
                    }
                }
        }
    }
}
