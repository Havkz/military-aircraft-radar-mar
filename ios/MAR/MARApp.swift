import SwiftUI
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        let tracker = UserDefaults.standard.string(forKey: "tracker") ?? "flightradar"
        let hex = info["hex"] as? String ?? ""
        let lat = info["lat"] as? Double ?? 0
        let lon = info["lon"] as? Double ?? 0
        let target = tracker == "adsbexchange"
            ? "https://globe.adsbexchange.com/?icao=\(hex)"
            : "https://www.flightradar24.com/\(lat),\(lon)/10"
        if let url = URL(string: target) { await UIApplication.shared.open(url) }
    }
}

@main
struct MARApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var store = RadarStore()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView().environmentObject(store).preferredColorScheme(.dark)
        }
        .onChange(of: scenePhase) { phase in
            if phase != .active { store.setMonitoring(false) }
        }
    }
}
