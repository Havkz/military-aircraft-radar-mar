# Military Aircraft Radar (MAR)

[![Android](https://img.shields.io/badge/Android-8.0%2B-4F8A65?logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)
[![License: MIT](https://img.shields.io/badge/License-MIT-527AA3.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-4.0-D98245)](app/build.gradle)

Military Aircraft Radar is a native Android and iOS project that monitors nearby ADS-B traffic and alerts you when an aircraft marked as military enters the selected radius. It uses a restrained OLED-friendly interface, live telemetry, and direct links to external flight trackers.

> [!IMPORTANT]
> MAR is an independent hobby project. It is not affiliated with ADSB.lol, Flightradar24, ADS-B Exchange, any armed forces, or any government organization. Do not use it for safety-critical, operational, or navigational decisions.

## Features

- Filters nearby ADS-B records using the military flag supplied by ADSB.lol
- Adjustable monitoring radius from 10 to 300 km
- Refresh intervals of 10, 30, or 60 seconds
- Individual aircraft notifications containing callsign, distance, altitude, and the ADSB.lol source label
- Reposts a dismissed aircraft alert after five minutes while monitoring remains active
- Opens notification targets at the aircraft position in Flightradar24 or by ICAO address in the installed ADS-B Exchange web app, with browser fallbacks
- Live aircraft list with animated detail views
- OLED dark, light, and system themes
- English, Mandarin Chinese, Hindi, Spanish, French, Arabic, Bengali, Portuguese, Russian, and German, plus automatic system-language selection
- Aviation units (NM / ft) or metric units (km / m)
- Foreground monitoring service with reboot reminder
- Stops monitoring and clears MAR notifications when the app task is closed
- No account, API key, analytics SDK, or advertising SDK required

## How it works

1. Android provides the device's current location while monitoring is enabled.
2. MAR requests nearby aircraft from the public ADSB.lol endpoint over HTTPS.
3. The app keeps records whose `dbFlags` value contains the military bit.
4. New aircraft entering the selected radius trigger an individual notification.
5. Tapping an aircraft notification opens the tracker selected in Settings.

The Flightradar24 and ADS-B Exchange integrations are outbound tracker links only. MAR does not scrape either service. ADS-B Exchange currently distributes its Android experience as an installable web app rather than a native application; MAR detects that WebAPK dynamically.

## Requirements

- Android 8.0 (API 26) or newer
- Internet connection
- Location permission
- Notification permission on Android 13 or newer

## Downloads

Android APKs and iOS build artifacts are published on the GitHub Releases page. The iOS IPA is an unsigned device build: it must be signed with the user's own Apple identity or a valid provisioning profile before an iPhone or iPad will install it. This does not require publishing MAR in the App Store.

## Build from source

### Android

1. Install a current Android Studio release with JDK 17.
2. Install Android SDK 35 through the SDK Manager.
3. Clone this repository and open its root directory in Android Studio.
4. Allow Gradle to synchronize the project.
5. Select **Build > Build APK(s)**.

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

The project uses Android Gradle Plugin 8.7.3, `compileSdk 35`, `targetSdk 35`, and `minSdk 26`. Release builds must be signed with your own keystore. Never commit signing material to the repository.

### iPhone and iPad

The native SwiftUI port lives in [`ios/`](ios/). It requires macOS, Xcode, XcodeGen, and iOS 16 or newer. See [`ios/README.md`](ios/README.md) for build instructions and [`ios/RELEASE.md`](ios/RELEASE.md) for Apple signing requirements.

iOS stops live scanning when the app is no longer active because Apple does not provide an Android-style indefinite foreground service for this use case.

## Permissions

| Permission | Purpose |
| --- | --- |
| Internet | Retrieve live ADS-B aircraft data and open tracker links |
| Fine/coarse location | Center the selected monitoring radius on the device |
| Notifications | Display the foreground-service indicator and aircraft alerts |
| Foreground service/location | Keep active monitoring running under Android restrictions |
| Vibration | Optional vibration for new aircraft alerts |
| Receive boot completed | Remind the user to restart monitoring after a reboot |

## Privacy

MAR has no developer-operated backend and includes no analytics or advertising libraries. While monitoring is active, the device location and selected radius are sent to ADSB.lol as part of the nearby-aircraft request. Aircraft telemetry and app preferences are stored locally on the device.

Read [PRIVACY.md](PRIVACY.md) before using or distributing the app. External services have their own privacy policies and terms.

## Accuracy and limitations

ADS-B and MLAT coverage is incomplete. Aircraft may be missing, delayed, incorrectly classified, or operating without a visible transponder. The military classification is inherited from upstream data and is not independently verified by MAR. Android may also stop background work when aggressive battery optimization is enabled.

## Contributing and security

Bug reports and focused pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before contributing. Please report security issues privately as described in [SECURITY.md](SECURITY.md).

## License

Released under the [MIT License](LICENSE).
