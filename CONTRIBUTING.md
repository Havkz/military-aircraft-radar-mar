# Contributing

Thanks for helping improve Military Aircraft Radar.

## Before opening an issue

- Search existing issues first.
- Use the provided bug or feature template.
- Remove exact locations, device identifiers, tokens, and other personal data from logs and screenshots.
- Remember that incomplete ADS-B coverage or an upstream classification error may not be an app defect.

## Development setup

### Android

1. Use JDK 17 and a current Android Studio release.
2. Install Android SDK 35.
3. Fork and clone the repository.
4. Create a focused branch from `main`.
5. Build and test on Android 8.0 or newer.

### iOS

1. Use a current macOS and Xcode release.
2. Install XcodeGen with `brew install xcodegen`.
3. Run `xcodegen generate` inside `ios/`.
4. Open `ios/MAR.xcodeproj` and build for iOS 16 or newer.

Physical-device installation requires an Apple signing identity and an appropriate provisioning profile. Never commit Apple signing material.

## Pull requests

- Keep each pull request focused on one change.
- Explain the user-visible impact, affected platform, and how the change was tested.
- Do not commit APKs, keystores, credentials, local configuration, or generated build directories.
- Preserve the app's privacy model: no analytics, advertising, or unrelated tracking dependencies.
- Update the README or privacy documentation when behavior or data handling changes.

By submitting a contribution, you agree that it may be distributed under the repository's MIT License.
