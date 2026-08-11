# Privacy

Last updated: August 3, 2026

Military Aircraft Radar (MAR) is an open-source Android and iOS application. It does not include advertising, analytics, crash-reporting, or user-account SDKs, and the project maintainer does not operate a backend service for the app.

## Data processed on the device

MAR processes the device's current location, the configured monitoring radius, aircraft telemetry returned by the data provider, notification state, and app preferences. These values are used to perform nearby-aircraft monitoring and render the app's live views.

App preferences, recent aircraft state, map display options, and map bookmarks are stored locally in platform-managed application or WebView storage. Search terms and aircraft filters remain on the device. If the optional “include filters in map URL” setting is enabled, the active filters are encoded only in the local WebView URL fragment; URL fragments are not sent in HTTP requests. An optional ADS-B Exchange API key is stored in Android's private no-backup directory. These values can be removed by clearing the Android app's data or uninstalling the app.

When an aircraft information panel is opened, Android may download up to 30 days of publicly available Airplanes.live Globe trace files for that aircraft. MAR detects landing transitions on-device, associates them with the bundled OurAirports directory, and displays the most frequent destination cities. The derived result is cached in private app storage for 24 hours. It is not uploaded by MAR.

## Data sent to third parties

While monitoring is active, Android sends the device latitude and longitude plus a nearby query radius to ADSB.lol and Airplanes.live. The query uses the selected alert radius during background monitoring and expands to 250 NM while the map tab is visible; the alert radius itself does not change. Airplanes.live is queried every 180 seconds by default, or every 1.2 seconds only after the user enables the warned Business-rate option. Android also requests ADSB.lol's global military feed, which does not include device location. If the user configures an official ADS-B Exchange API key, Android sends the same nearby query and that credential directly to the ADS-B Exchange API. iOS currently uses ADSB.lol. Each provider can also receive the connection's IP address.

If worldwide squawk notifications are configured, Android sends the user's list of four-digit squawk codes to the global squawk endpoints of ADSB.lol. Codes are combined into requests and checked once per minute; very large lists are split into rate-limited batches. Airplanes.live is used only as a fallback at the rate allowed by the selected plan, and ADS-B Exchange is included only when the user has configured an official API key. These worldwide requests do not include the device location. Returned aircraft are checked locally for an exact match before a notification is sent. Alert deduplication state remains in memory on the device.

When the Android map tab is first opened, its WebView loads Leaflet 1.9.4 from the unpkg CDN and requests OpenStreetMap tiles for the visible area. Those services therefore receive the requested resource or tile coordinates, network metadata, and IP address. MAR does not prefetch map areas for offline use.

When the user chooses to open an aircraft in Flightradar24 or ADS-B Exchange, Android opens the selected service's app or website with aircraft-identifying or map-position information in the link. Those services process data under their own terms and privacy policies.

When the user selects an aircraft with a registration, Android sends that registration and, when available, its known type to Planespotters.net and Planespotting.be to obtain a registration-specific thumbnail and missing aircraft metadata such as operator, type, MSN, or status. Loading the returned remote thumbnail also exposes normal network metadata and the IP address to its image host. Results are kept only in the current in-memory map session and ordinary WebView/network cache; MAR does not bulk-download either service's photo or fleet database. The separate outbound photo-search links send the same registration when explicitly opened.

Official provider information:

- ADSB.lol privacy and license: https://www.adsb.lol/privacy-license/
- Airplanes.live privacy: https://airplanes.live/privacy/
- Airplanes.live API terms and limits: https://airplanes.live/api-guide/
- Airplanes.live Terms of Use: https://airplanes.live/terms-of-use/
- OpenStreetMap tile usage policy: https://operations.osmfoundation.org/policies/tiles/
- unpkg privacy policy: https://www.cloudflare.com/privacypolicy/
- Flightradar24 Terms of Service: https://www.flightradar24.com/terms-of-service
- ADS-B Exchange / JETNET Terms of Use: https://www.jetnet.com/legal/terms-of-use
- Planespotters.net: https://www.planespotters.net/
- Planespotting.be: https://www.planespotting.be/

## Permissions

Location is requested for radius-based monitoring. Notification permissions support aircraft alerts on both platforms. Vibration, foreground-service, and boot-completed permissions support Android alerts and background-operation requirements. MAR does not request contacts, camera, microphone, phone, or shared-storage access.

## Android backups

Android or the device manufacturer may back up app data according to the user's device and account settings. This process is controlled by the platform provider, not by MAR's maintainer.

## Changes

Material privacy changes should be documented in this file and the repository history.

## Contact

Open a GitHub issue for general privacy questions. Do not include precise locations or other sensitive personal data in a public issue. Use GitHub's private vulnerability reporting for security-sensitive matters.
