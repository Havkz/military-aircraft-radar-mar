import Foundation

struct Aircraft: Identifiable, Hashable {
    let id: String
    let callsign: String
    let registration: String
    let type: String
    let distanceKm: Double
    let altitudeFt: Double?
    let speedKnots: Double
    let track: Double
    let squawk: String
    let latitude: Double
    let longitude: Double
    let seen: Double

    init?(json: [String: Any]) {
        guard let hex = json["hex"] as? String,
              MilitaryClassifier.isMilitary(json),
              let distanceNm = (json["dst"] as? NSNumber)?.doubleValue,
              let latitude = (json["lat"] as? NSNumber)?.doubleValue,
              let longitude = (json["lon"] as? NSNumber)?.doubleValue else { return nil }
        id = hex.replacingOccurrences(of: "~", with: "")
        callsign = (json["flight"] as? String)?.trimmingCharacters(in: .whitespaces) ?? ""
        registration = json["r"] as? String ?? ""
        type = json["t"] as? String ?? ""
        distanceKm = distanceNm * 1.852
        if let number = json["alt_baro"] as? NSNumber { altitudeFt = number.doubleValue }
        else if (json["alt_baro"] as? String) == "ground" { altitudeFt = 0 }
        else { altitudeFt = nil }
        speedKnots = (json["gs"] as? NSNumber)?.doubleValue ?? 0
        track = (json["track"] as? NSNumber)?.doubleValue ?? 0
        squawk = json["squawk"] as? String ?? "—"
        self.latitude = latitude
        self.longitude = longitude
        seen = (json["seen"] as? NSNumber)?.doubleValue ?? 0
    }
}
