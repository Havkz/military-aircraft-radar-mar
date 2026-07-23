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

    init?(json: [String: Any], userLatitude: Double, userLongitude: Double) {
        guard let hex = json["hex"] as? String,
              MilitaryClassifier.isMilitary(json),
              let position = Self.recentPosition(json),
              let calculatedDistance = Self.distanceKm(
                fromLatitude: userLatitude, longitude: userLongitude,
                toLatitude: position.latitude, longitude: position.longitude) else { return nil }
        id = hex.replacingOccurrences(of: "~", with: "")
        callsign = (json["flight"] as? String)?.trimmingCharacters(in: .whitespaces) ?? ""
        registration = json["r"] as? String ?? ""
        type = json["t"] as? String ?? ""
        distanceKm = calculatedDistance
        altitudeFt = Self.altitudeFeet(
            geometric: json["alt_geom"], barometric: json["alt_baro"])
        speedKnots = (json["gs"] as? NSNumber)?.doubleValue ?? 0
        track = (json["track"] as? NSNumber)?.doubleValue ?? 0
        squawk = json["squawk"] as? String ?? "—"
        latitude = position.latitude
        longitude = position.longitude
        seen = (json["seen"] as? NSNumber)?.doubleValue ?? 0
    }

    private static func recentPosition(_ json: [String: Any])
        -> (latitude: Double, longitude: Double)? {
        if let latitude = (json["lat"] as? NSNumber)?.doubleValue,
           let longitude = (json["lon"] as? NSNumber)?.doubleValue,
           validCoordinate(latitude, longitude) {
            return (latitude, longitude)
        }
        guard let last = json["lastPosition"] as? [String: Any],
              let age = (last["seen_pos"] as? NSNumber)?.doubleValue,
              (0...60).contains(age),
              let latitude = (last["lat"] as? NSNumber)?.doubleValue,
              let longitude = (last["lon"] as? NSNumber)?.doubleValue,
              validCoordinate(latitude, longitude) else { return nil }
        return (latitude, longitude)
    }

    private static func validCoordinate(_ latitude: Double, _ longitude: Double) -> Bool {
        latitude.isFinite && longitude.isFinite
            && (-90...90).contains(latitude) && (-180...180).contains(longitude)
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

    private static func distanceKm(fromLatitude userLatitude: Double,
                                   longitude userLongitude: Double,
                                   toLatitude aircraftLatitude: Double,
                                   longitude aircraftLongitude: Double) -> Double? {
        guard userLatitude.isFinite, userLongitude.isFinite,
              aircraftLatitude.isFinite, aircraftLongitude.isFinite,
              (-90...90).contains(userLatitude), (-90...90).contains(aircraftLatitude),
              (-180...180).contains(userLongitude),
              (-180...180).contains(aircraftLongitude) else { return nil }

        let earthRadiusKm = 6371.0088
        let latitudeDelta = (aircraftLatitude - userLatitude) * .pi / 180
        let longitudeDelta = (aircraftLongitude - userLongitude) * .pi / 180
        let userLatitudeRadians = userLatitude * .pi / 180
        let aircraftLatitudeRadians = aircraftLatitude * .pi / 180
        let haversine = pow(sin(latitudeDelta / 2), 2)
            + cos(userLatitudeRadians) * cos(aircraftLatitudeRadians)
            * pow(sin(longitudeDelta / 2), 2)
        let boundedHaversine = min(1, max(0, haversine))
        return earthRadiusKm * 2 * atan2(
            sqrt(boundedHaversine), sqrt(1 - boundedHaversine))
    }
}
