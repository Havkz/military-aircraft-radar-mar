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
        altitudeFt = Self.altitudeFeet(
            geometric: json["alt_geom"], barometric: json["alt_baro"])
        speedKnots = (json["gs"] as? NSNumber)?.doubleValue ?? 0
        track = (json["track"] as? NSNumber)?.doubleValue ?? 0
        squawk = json["squawk"] as? String ?? "—"
        self.latitude = latitude
        self.longitude = longitude
        seen = (json["seen"] as? NSNumber)?.doubleValue ?? 0
    }

    private static func altitudeFeet(geometric: Any?, barometric: Any?) -> Double? {
        if let feet = altitudeValueFeet(geometric) { return feet }
        return altitudeValueFeet(barometric)
    }

    private static func altitudeValueFeet(_ value: Any?) -> Double? {
        if let number = value as? NSNumber {
            let feet = number.doubleValue
            return feet.isFinite ? feet : nil
        }
        if let text = value as? String, text.lowercased() == "ground" { return 0 }
        return nil
    }
}
