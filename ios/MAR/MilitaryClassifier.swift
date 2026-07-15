import Foundation

enum MilitaryClassifier {
    private static let militaryTypes: Set<String> = [
        "A400", "C130", "C30J", "C17", "C5", "C5M", "C2", "C27J",
        "KC10", "K35R", "KC46", "K767", "E3CF", "E3TF", "E6", "E8",
        "P3", "P8", "P1", "B1", "B2", "B52", "TU95", "T160", "V22",
        "A50", "IL78", "A10", "F4", "F5", "F14", "F15", "F16", "F18",
        "F22", "F35", "AV8B", "EUFI", "RFAL", "M200", "MG29", "TORN",
        "JAS39", "SU24", "SU25", "SU27", "SU30", "SU34", "SU35", "FA50",
        "J10", "J11", "J20", "J31", "T38", "TEX2", "HAWK", "L159",
        "M346", "T50", "PC21", "H64", "AH64", "H53", "CH53", "NH90",
        "TIGR", "A129", "AH1", "KA50", "KA52", "MI24", "MI28", "RQ4",
        "MQ9", "Q4"
    ]

    private static let militaryCallsignPrefixes: Set<String> = [
        "RCH", "RRR", "CNV", "PAT", "EVAC", "FORTE", "NCHO", "GAF", "BAF",
        "FAF", "IAM", "ASY", "NOW", "AME", "CTM"
    ]

    static func isMilitary(_ json: [String: Any]) -> Bool {
        let flags = (json["dbFlags"] as? NSNumber)?.intValue ?? 0
        if flags & 1 == 1 { return true }

        let type = (json["t"] as? String ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if militaryTypes.contains(type) { return true }

        let callsign = (json["flight"] as? String ?? "")
            .filter { !$0.isWhitespace }.uppercased()
        return militaryCallsignPrefixes.contains { prefix in
            callsign.hasPrefix(prefix)
                && callsign.dropFirst(prefix.count).contains(where: \.isNumber)
        }
    }
}
