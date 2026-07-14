# Privacy

Last updated: July 14, 2026

Military Aircraft Radar (MAR) is an open-source Android application. It does not include advertising, analytics, crash-reporting, or user-account SDKs, and the project maintainer does not operate a backend service for the app.

## Data processed on the device

MAR processes the device's current location, the configured monitoring radius, aircraft telemetry returned by the data provider, notification state, and app preferences. These values are used to perform nearby-aircraft monitoring and render the app's live views.

App preferences and the latest aircraft list are stored locally in Android application storage. They can be removed by clearing the app's data or uninstalling the app.

## Data sent to third parties

While monitoring is active, MAR sends the device latitude, longitude, and selected radius over HTTPS to the public ADSB.lol service in order to request nearby aircraft. This means ADSB.lol can receive the IP address and requested geographic area.

When the user chooses to open an aircraft in Flightradar24 or ADS-B Exchange, Android opens the selected service's app or website with aircraft-identifying or map-position information in the link. Those services process data under their own terms and privacy policies.

## Permissions

Location is requested for radius-based monitoring. Notification, vibration, foreground-service, and boot-completed permissions support alerts and Android background-operation requirements. MAR does not request contacts, camera, microphone, phone, or storage access.

## Android backups

Android or the device manufacturer may back up app data according to the user's device and account settings. This process is controlled by the platform provider, not by MAR's maintainer.

## Changes

Material privacy changes should be documented in this file and the repository history.

## Contact

Open a GitHub issue for general privacy questions. Do not include precise locations or other sensitive personal data in a public issue. Use GitHub's private vulnerability reporting for security-sensitive matters.
