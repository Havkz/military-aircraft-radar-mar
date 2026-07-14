import SwiftUI

private let green = Color(red: 79/255, green: 138/255, blue: 101/255)
private let blue = Color(red: 82/255, green: 122/255, blue: 163/255)
private let orange = Color(red: 217/255, green: 130/255, blue: 69/255)

struct ContentView: View {
    @EnvironmentObject private var store: RadarStore
    @AppStorage("language") private var language = "system"

    var body: some View {
        TabView {
            NavigationStack { RadarScreen(language: language) }
                .tabItem { Label(T.text("radar", language), systemImage: "scope") }
            NavigationStack { AircraftListScreen(language: language) }
                .tabItem { Label(T.text("aircraft", language), systemImage: "airplane") }
            NavigationStack { SettingsScreen(language: $language) }
                .tabItem { Label(T.text("settings", language), systemImage: "gearshape") }
        }
        .tint(green)
        .environment(\.layoutDirection, T.code(language) == "ar" ? .rightToLeft : .leftToRight)
    }
}

struct RadarScreen: View {
    @EnvironmentObject private var store: RadarStore
    let language: String
    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                Text(T.text("title", language)).font(.largeTitle.bold()).frame(maxWidth: .infinity, alignment: .leading)
                RadarGraphic(active: store.monitoring).frame(height: 300)
                HStack {
                    metric(T.text("contacts", language), "\(store.aircraft.count)")
                    metric("STATUS", store.connection)
                }
                VStack(alignment: .leading) {
                    Text(T.text("radius", language)).font(.caption.bold()).foregroundStyle(.secondary)
                    Text("\(Int(store.radiusKm)) km").font(.title2.bold()).foregroundStyle(blue)
                    Slider(value: Binding(get: { store.radiusKm }, set: store.updateRadius), in: 10...300, step: 5).tint(green)
                }.card()
                Button(store.monitoring ? T.text("stop", language) : T.text("start", language)) {
                    store.setMonitoring(!store.monitoring)
                }.buttonStyle(MARButtonStyle(stopping: store.monitoring))
            }.padding()
        }.background(Color.black).navigationBarHidden(true)
    }
    private func metric(_ title: String, _ value: String) -> some View {
        VStack { Text(title).font(.caption2.bold()).foregroundStyle(.secondary); Text(value).font(.headline).foregroundStyle(green) }
            .frame(maxWidth: .infinity).card()
    }
}

struct RadarGraphic: View {
    let active: Bool
    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                let center = CGPoint(x: size.width/2, y: size.height/2)
                let radius = min(size.width, size.height) * 0.4
                for ring in 1...4 {
                    let r = radius * CGFloat(ring) / 4
                    context.stroke(Path(ellipseIn: CGRect(x: center.x-r, y: center.y-r, width: r*2, height: r*2)), with: .color(blue.opacity(0.45)))
                }
                if active {
                    let angle = timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 4.2) / 4.2 * .pi * 2
                    var beam = Path(); beam.move(to: center); beam.addLine(to: CGPoint(x: center.x + cos(angle)*radius, y: center.y + sin(angle)*radius))
                    context.stroke(beam, with: .color(green), lineWidth: 3)
                }
            }
        }.background(Color(red: 7/255, green: 12/255, blue: 17/255)).clipShape(RoundedRectangle(cornerRadius: 24))
    }
}

struct AircraftListScreen: View {
    @EnvironmentObject private var store: RadarStore
    let language: String
    var body: some View {
        List {
            if store.aircraft.isEmpty { Text(T.text("noContacts", language)).foregroundStyle(.secondary) }
            ForEach(store.aircraft) { plane in NavigationLink(value: plane) { AircraftRow(plane: plane) } }
        }.scrollContentBackground(.hidden).background(.black)
            .navigationTitle(T.text("aircraft", language)).navigationDestination(for: Aircraft.self) { AircraftDetailScreen(plane: $0) }
    }
}

struct AircraftRow: View {
    let plane: Aircraft
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(plane.callsign.isEmpty ? plane.id.uppercased() : plane.callsign).font(.title3.bold()).foregroundStyle(blue)
            Text("\(plane.type) • \(plane.registration)").font(.caption).foregroundStyle(.secondary)
            Text(String(format: "%.1f km • %@", plane.distanceKm, plane.altitudeFt.map { String(format: "%.0f ft", $0) } ?? "—"))
        }.padding(.vertical, 6)
    }
}

struct AircraftDetailScreen: View {
    let plane: Aircraft
    @AppStorage("tracker") private var tracker = "flightradar"
    var body: some View {
        ScrollView { VStack(spacing: 12) {
            Text(plane.callsign.isEmpty ? plane.id.uppercased() : plane.callsign).font(.largeTitle.bold()).foregroundStyle(blue)
            detail("Registration", plane.registration); detail("Type", plane.type)
            detail("Distance", String(format: "%.1f km", plane.distanceKm)); detail("Altitude", plane.altitudeFt.map { String(format: "%.0f ft", $0) } ?? "—")
            detail("Ground speed", String(format: "%.0f kt", plane.speedKnots)); detail("Track", String(format: "%.0f°", plane.track)); detail("Squawk", plane.squawk)
            Button("Open in \(tracker == "adsbexchange" ? "ADS-B Exchange" : "Flightradar24")") { open() }.buttonStyle(MARButtonStyle())
        }.padding() }.background(.black)
    }
    private func detail(_ key: String, _ value: String) -> some View { HStack { Text(key).foregroundStyle(.secondary); Spacer(); Text(value).bold() }.card() }
    private func open() {
        let target = tracker == "adsbexchange" ? "https://globe.adsbexchange.com/?icao=\(plane.id)" : "https://www.flightradar24.com/\(plane.latitude),\(plane.longitude)/10"
        if let url = URL(string: target) { UIApplication.shared.open(url) }
    }
}

struct SettingsScreen: View {
    @Binding var language: String
    @AppStorage("tracker") private var tracker = "flightradar"
    @AppStorage("refreshSeconds") private var refresh = 10.0
    var body: some View {
        Form {
            Section(T.text("language", language)) { Picker(T.text("language", language), selection: $language) { ForEach(MARLanguage.allCases) { Text($0.name).tag($0.rawValue) } } }
            Section(T.text("tracker", language)) { Picker(T.text("tracker", language), selection: $tracker) { Text("Flightradar24").tag("flightradar"); Text("ADS-B Exchange").tag("adsbexchange") } }
            Section("LIVE") { Picker("Refresh", selection: $refresh) { Text("10 s").tag(10.0); Text("30 s").tag(30.0); Text("60 s").tag(60.0) } }
            Section("INFO") { Text("Military Aircraft Radar – MAR\niOS 4.0\nADSB.lol") }
        }.scrollContentBackground(.hidden).background(.black).navigationTitle(T.text("settings", language)).tint(green)
    }
}

struct MARButtonStyle: ButtonStyle {
    var stopping = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.headline).frame(maxWidth: .infinity).padding().foregroundStyle(.white)
            .background(stopping ? orange : green).clipShape(RoundedRectangle(cornerRadius: 18)).opacity(configuration.isPressed ? 0.75 : 1)
    }
}

private extension View {
    func card() -> some View { padding().background(Color(red: 10/255, green: 14/255, blue: 19/255)).clipShape(RoundedRectangle(cornerRadius: 18)) }
}
