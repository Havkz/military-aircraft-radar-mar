# MAR for iPhone and iPad

This directory contains the native SwiftUI version of Military Aircraft Radar for iOS and iPadOS.

## Important installation information

The unsigned IPA from GitHub Releases is a compiled app, but iOS will not install it directly. Apple requires every physical-device app to be signed with an Apple identity and a provisioning profile that authorizes the receiving device.

If you do not already use an Apple sideloading tool or Xcode, there is currently no simple one-tap installation method. The app is not distributed through the App Store or TestFlight.

## Build and install with Xcode

You need:

- A Mac with a current Xcode release
- [XcodeGen](https://github.com/yonaskolb/XcodeGen)
- An iPhone or iPad running iOS 16 or newer
- An Apple identity configured in Xcode

Then:

1. Clone the repository on the Mac.
2. Install XcodeGen:

   ```bash
   brew install xcodegen
   ```

3. Generate the Xcode project:

   ```bash
   cd military-aircraft-radar-mar/ios
   xcodegen generate
   open MAR.xcodeproj
   ```

4. In Xcode, select the MAR target and open **Signing & Capabilities**.
5. Select your Apple development team. Change the bundle identifier if Xcode reports that it is unavailable.
6. Connect and select your iPhone or iPad, then press **Run**.

A free Apple identity can normally sign a personal development build for a limited period. Apple controls those limits and may require periodic re-signing.

## Platform limitation

iOS does not provide Android-style indefinite foreground services. MAR scans while the app is active and stops live monitoring when it enters the background. Background refresh opportunities are controlled by iOS and cannot guarantee 10-second aircraft polling or continuous alerts.

## Unsigned release workflow

The repository workflow `.github/workflows/ios-unsigned-release.yml` can compile an unsigned device IPA on a GitHub-hosted macOS runner and attach it to an existing prerelease. It must be started manually with the target release tag.

The simulator validation example is stored at `ci/ios-build.yml.example`.

## Distribution and signing

Public Apple distribution normally requires the Apple Developer Program, an Apple Distribution certificate, a registered App ID, and a matching provisioning profile. Read [RELEASE.md](RELEASE.md) before preparing a distributable build.

Never commit `.p12` files, `.mobileprovision` files, private keys, certificates containing private keys, or signing passwords.
