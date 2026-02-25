import SwiftUI
import UserNotifications

@main
struct iOSApp: App {
    init() {
        _ = CallIntegrationManager.shared
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in
            // Permission result is handled by the system.
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
