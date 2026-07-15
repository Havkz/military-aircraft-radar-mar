import CoreLocation
import Foundation
import UserNotifications

@MainActor
final class RadarStore: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var aircraft: [Aircraft] = []
    @Published var monitoring = false
    @Published var connection = "Standby"
    @Published var lastScan: Date?
    @Published var radiusKm = UserDefaults.standard.double(forKey: "radiusKm").clamped(or: 50)

    private let locationManager = CLLocationManager()
    private var location: CLLocation?
    private var timer: Timer?
    private var seen = Set<String>()

    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func setMonitoring(_ enabled: Bool) {
        monitoring = enabled
        timer?.invalidate()
        if enabled {
            locationManager.requestWhenInUseAuthorization()
            locationManager.startUpdatingLocation()
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
            poll()
            timer = Timer.scheduledTimer(withTimeInterval: refreshSeconds, repeats: true) { [weak self] _ in
                Task { @MainActor in self?.poll() }
            }
        } else {
            locationManager.stopUpdatingLocation()
            connection = "Standby"
            aircraft = []
            seen.removeAll()
            UNUserNotificationCenter.current().removeAllDeliveredNotifications()
            UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
        }
    }

    func updateRadius(_ value: Double) {
        radiusKm = value
        UserDefaults.standard.set(value, forKey: "radiusKm")
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        location = locations.last
        if monitoring && aircraft.isEmpty { poll() }
    }

    private var refreshSeconds: Double {
        let value = UserDefaults.standard.double(forKey: "refreshSeconds")
        return value == 0 ? 10 : value
    }

    private func poll() {
        guard monitoring, let location else { connection = "Waiting for location"; return }
        let radiusNm = min(250, max(1, Int(ceil(radiusKm / 1.852))))
        let endpoint = "https://api.adsb.lol/v2/lat/\(String(format: "%.5f", location.coordinate.latitude))/lon/\(String(format: "%.5f", location.coordinate.longitude))/dist/\(radiusNm)"
        guard let url = URL(string: endpoint) else { return }
        connection = "Connecting…"
        Task {
            do {
                let (data, response) = try await URLSession.shared.data(from: url)
                guard (response as? HTTPURLResponse)?.statusCode == 200,
                      let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let rows = root["ac"] as? [[String: Any]] else { throw URLError(.badServerResponse) }
                let current = rows.compactMap {
                    Aircraft(json: $0,
                             userLatitude: location.coordinate.latitude,
                             userLongitude: location.coordinate.longitude)
                }.filter { $0.distanceKm <= radiusKm }
                    .sorted { $0.distanceKm < $1.distanceKm }
                aircraft = current
                connection = "Live"
                lastScan = Date()
                for plane in current where !seen.contains(plane.id) { notify(plane) }
                seen = Set(current.map(\.id))
            } catch { connection = "Connection unavailable" }
        }
    }

    private func notify(_ plane: Aircraft) {
        let content = UNMutableNotificationContent()
        content.title = plane.callsign.isEmpty ? plane.id.uppercased() : plane.callsign
        content.body = String(format: "%.1f km • %@ • ADSB.lol", plane.distanceKm,
                              plane.altitudeFt.map { String(format: "%.0f ft", $0) } ?? "—")
        content.userInfo = ["hex": plane.id, "callsign": plane.callsign,
                            "lat": plane.latitude, "lon": plane.longitude]
        UNUserNotificationCenter.current().add(UNNotificationRequest(
            identifier: plane.id, content: content, trigger: nil))
    }
}

private extension Double {
    func clamped(or fallback: Double) -> Double { self == 0 ? fallback : min(300, max(10, self)) }
}
