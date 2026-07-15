# Military Aircraft Radar (MAR)

[![Latest release](https://img.shields.io/github/v/release/Havkz/military-aircraft-radar-mar?include_prereleases&label=release)](https://github.com/Havkz/military-aircraft-radar-mar/releases)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-4F8A65?logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)
[![iOS 16+](https://img.shields.io/badge/iOS-16%2B-527AA3?logo=apple&logoColor=white)](ios/README.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-D98245.svg)](LICENSE)

Military Aircraft Radar is an open-source mobile app that shows nearby aircraft identified as military by its live ADS-B data provider. It can notify you when a matching aircraft enters your selected radius and can open that aircraft in an external flight tracker.

No account, API key, subscription, advertising SDK, or analytics SDK is required.

> [!IMPORTANT]
> MAR is an independent hobby project. It is not affiliated with ADSB.lol, Flightradar24, ADS-B Exchange, any armed forces, or any government organization. Do not use MAR for navigation, safety-critical decisions, or operational purposes.

## Get started

### Android: install the app

No programming tools are required to install the Android release.

1. Open the [Releases page](https://github.com/Havkz/military-aircraft-radar-mar/releases).
2. Open the newest release and expand **Assets** if the files are hidden.
3. Download the file whose name begins with `MAR-Android` and ends with `.apk`.
4. Open the downloaded APK. Android may ask you to allow installations from your browser or file manager. This permission can be disabled again after installation.
5. Open MAR and grant location and notification permissions when requested.
6. Select a monitoring radius and press **Start monitoring**.

Android may display a warning because the APK is installed outside Google Play. Only install files downloaded from this repository's official Releases page. Advanced users can verify release checksums before installation.

### iPhone and iPad

The release may contain an unsigned `.ipa`, but it cannot be installed directly. Apple requires the app to be signed with an Apple identity and a provisioning profile that authorizes the receiving device.

There is currently no one-tap public iOS installation. Users familiar with Apple sideloading can sign the IPA themselves; developers can build the native SwiftUI project on a Mac. See [the iOS guide](ios/README.md) for clear instructions and limitations.

## First use

1. Choose a radius. The slider marks **20–30 km** as a practical recommended range; the label follows the selected unit system.
2. Press **Start monitoring**. The radar begins moving only after a live data connection is available.
3. Keep the foreground-service notification enabled while monitoring is active.
4. Open **Settings** to choose units, language, theme, refresh interval, vibration, and the tracker used when an aircraft notification is selected.
5. ADS-B Exchange is recommended as the notification target because some aircraft may not be visible on Flightradar24.

MAR stops monitoring and removes its notifications when its Android app task is closed. After a phone restart, MAR can remind you to start monitoring again; it does not silently restart continuous monitoring.

## What notifications do

- Each aircraft receives its own notification.
- Distance and altitude are refreshed while the aircraft remains available in the live feed.
- The notification changes to **Out of range** when the aircraft leaves the selected radius or disappears from a successful scan.
- Dismissing a notification suppresses that aircraft for five minutes. It can return only if it is detected inside the active radius again.
- Selecting a notification opens the configured external tracker. If its app or installed web app cannot handle the link, MAR uses the browser as a fallback.

## Common questions

### Does MAR detect every military aircraft?

No. ADS-B and MLAT coverage is incomplete. Some aircraft do not transmit a usable position, may be outside receiver coverage, or may not be classified correctly by the upstream database. MAR includes military-tagged fixed-wing aircraft and helicopters when usable data is available, but complete detection cannot be guaranteed.

### Is the displayed information truly live?

MAR polls current data at the selected interval of 10, 30, or 60 seconds. “Live” therefore means regularly refreshed current data, not a continuous zero-delay stream.

### Why is location permission required?

The app needs the device position to calculate the distance between you and each aircraft. MAR does not operate a developer-controlled backend. See [Privacy](#privacy) for the exact data flow.

### Why is there a persistent Android notification?

Android requires a foreground-service notification while continuous location-based monitoring is active. Individual aircraft alerts are delivered separately.

### Why is ADS-B Exchange recommended?

Some aircraft may not be visible on Flightradar24. ADS-B Exchange can therefore be the more reliable external destination for a detected military contact. Tracker availability remains controlled by the external service.

## Main features

- Nearby military-contact monitoring using ADSB.lol live data
- Adjustable 10–300 km radius with a marked 20–30 km recommendation
- 10, 30, or 60 second refresh intervals
- Continuously updated per-aircraft notifications
- Five-minute notification-dismissal cooldown
- Animated radar, aircraft history, and settings pages with swipe navigation
- Session history with first and last in-range times
- Animated aircraft detail views
- Flightradar24 and ADS-B Exchange outbound tracker links with browser fallbacks
- OLED dark, light, and system themes
- Aviation units (NM / ft) and metric units (km / m)
- English, Mandarin Chinese, Hindi, Spanish, French, Arabic, Bengali, Portuguese, Russian, and German
- Automatic system-language selection and right-to-left layout for Arabic
- No account, analytics, advertising, or developer-operated backend

## Technical overview

### Data flow

1. Android or iOS provides the device's current location while monitoring is active.
2. MAR requests the ADSB.lol military feed over HTTPS.
3. MAR calculates the distance to each returned aircraft locally from its coordinates.
4. Contacts inside the configured radius update the radar, aircraft list, session history, and notifications.
5. Tracker links are opened only when the user explicitly selects an aircraft or notification.

The primary Android request uses ADSB.lol's dedicated military endpoint. If that request fails, MAR falls back to the nearby-aircraft endpoint and applies the military `dbFlags` bit. Flightradar24 and ADS-B Exchange are external viewing destinations only; MAR does not scrape either service.

### Accuracy limitations

Aircraft data may be missing, delayed, duplicated, or incorrect. Military classification is inherited from ADSB.lol and is not independently verified by MAR. Altitude, callsign, registration, type, and position fields may be absent. Android device manufacturers may also restrict background execution despite the foreground service.

### Platform support

| Platform | Minimum version | Monitoring behavior |
| --- | --- | --- |
| Android | Android 8.0 / API 26 | Foreground monitoring with a required persistent system notification |
| iPhone and iPad | iOS 16 | Scans while the app is active; continuous Android-style background monitoring is not available |

### Android permissions

| Permission | Why MAR requests it |
| --- | --- |
| Internet | Retrieve live aircraft data and open tracker links |
| Fine/coarse location | Calculate the distance from the device to aircraft |
| Notifications | Display the required service status and aircraft alerts |
| Foreground service/location | Keep active monitoring running under Android restrictions |
| Vibration | Provide optional vibration for a newly detected contact |
| Receive boot completed | Offer a reminder to restart monitoring after a reboot |

MAR does not request access to contacts, camera, microphone, phone calls, or shared storage.

## Build from source

### Android

1. Install a current [Android Studio](https://developer.android.com/studio) release with JDK 17.
2. In Android Studio's SDK Manager, install Android SDK 35.
3. Clone this repository:

   ```bash
   git clone https://github.com/Havkz/military-aircraft-radar-mar.git
   cd military-aircraft-radar-mar
   ```

4. Open the repository root in Android Studio and allow Gradle to synchronize.
5. Select **Build > Build APK(s)**.

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

The Android project uses Android Gradle Plugin 8.7.3, `compileSdk 35`, `targetSdk 35`, and `minSdk 26`. Release builds must be signed with a private keystore. Never commit a keystore, password, token, provisioning profile, or other credential.

### iPhone and iPad

The native SwiftUI project is stored in [`ios/`](ios/). Building it requires macOS, Xcode, XcodeGen, and an Apple signing identity for physical-device installation. Follow [ios/README.md](ios/README.md); release signing details are documented in [ios/RELEASE.md](ios/RELEASE.md).

### Repository layout

```text
app/                 Android application source and resources
ios/                 Native SwiftUI iPhone/iPad project
.github/             Issue forms, pull request template, and workflows
PRIVACY.md           Detailed data-handling notice
LEGAL.md             Provider terms, attribution, and trademark notice
SECURITY.md          Private vulnerability-reporting instructions
CONTRIBUTING.md      Development and contribution guide
```

## Privacy

MAR contains no analytics, advertising, crash-reporting, or user-account SDKs. It has no developer-operated server. While monitoring is active, location-related request data is sent directly from the device to ADSB.lol. Selecting an external tracker transfers aircraft or map-position information to that service.

Read the complete [privacy notice](PRIVACY.md) before using or redistributing the app. ADSB.lol, Flightradar24, ADS-B Exchange, Apple, Google, and device manufacturers operate under their own terms and privacy policies.

## Legal and data-source notices

MAR contains information from [ADSB.lol](https://www.adsb.lol/), which is made available under the [Open Database License (ODbL) 1.0](https://opendatacommons.org/licenses/odbl/1-0/). The app includes this attribution in Settings under **Legal & Data Sources**.

Flightradar24 and ADS-B Exchange are optional external destinations only. MAR does not retrieve, scrape, embed, or redistribute their data and does not use their logos. Their names and trademarks belong to their respective owners; no affiliation or endorsement is claimed.

Read [LEGAL.md](LEGAL.md) for the provider-by-provider review, official terms, and conditions that forks or future integrations must preserve.

## Contributing and support

Bug reports and focused contributions are welcome. Search existing issues first, remove precise locations and personal information from screenshots or logs, and follow [CONTRIBUTING.md](CONTRIBUTING.md).

Security vulnerabilities must not be posted publicly. Follow [SECURITY.md](SECURITY.md) to use GitHub's private vulnerability-reporting form.

## License and third-party software

MAR is released under the [MIT License](LICENSE). Embedded Google Material Symbols are licensed separately under Apache 2.0; details are available in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
