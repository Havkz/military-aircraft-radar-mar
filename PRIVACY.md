# Privacy

Last updated: July 14, 2026

Military Aircraft Radar (MAR) is an open-source Android and iOS application. It does not include advertising, analytics, crash-reporting, or user-account SDKs, and the project maintainer does not operate a backend service for the app.

## Data processed on the device

MAR processes the device's current location, the configured monitoring radius, aircraft telemetry returned by the data provider, notification state, and app preferences. These values are used to perform nearby-aircraft monitoring and render the app's live views.

App preferences and recent aircraft state are stored locally in platform-managed application storage. They can be removed by clearing the Android app's data or uninstalling the app.

## Data sent to third parties

Android normally requests ADSB.lol's global military feed and calculates distance on the device. If that request fails, Android's fallback request sends the device latitude, longitude, and selected radius to ADSB.lol in order to retrieve nearby aircraft. The iOS implementation currently uses the nearby-aircraft request and therefore sends those values while scanning. In all cases, ADSB.lol can receive the connection's IP address.

When the user chooses to open an aircraft in Flightradar24 or ADS-B Exchange, Android opens the selected service's app or website with aircraft-identifying or map-position information in the link. Those services process data under their own terms and privacy policies.

Official provider information:

- ADSB.lol privacy and license: https://www.adsb.lol/privacy-license/
- Flightradar24 Terms of Service: https://www.flightradar24.com/terms-of-service
- ADS-B Exchange / JETNET Terms of Use: https://www.jetnet.com/legal/terms-of-use

## Permissions

Location is requested for radius-based monitoring. Notification permissions support aircraft alerts on both platforms. Vibration, foreground-service, and boot-completed permissions support Android alerts and background-operation requirements. MAR does not request contacts, camera, microphone, phone, or shared-storage access.

## Android backups

Android or the device manufacturer may back up app data according to the user's device and account settings. This process is controlled by the platform provider, not by MAR's maintainer.

## Changes

Material privacy changes should be documented in this file and the repository history.

## Contact

Open a GitHub issue for general privacy questions. Do not include precise locations or other sensitive personal data in a public issue. Use GitHub's private vulnerability reporting for security-sensitive matters.
