# MAR for iOS and iPadOS

This directory contains the native SwiftUI port of Military Aircraft Radar for iPhone and iPad.

## Build requirements

- macOS with the current Xcode release
- XcodeGen (`brew install xcodegen`)
- iOS 16 or newer
- An Apple Developer team for installation on physical devices and distribution

Generate the Xcode project with `cd ios && xcodegen generate`, open `MAR.xcodeproj`, select your development team, and run the app.

The simulator CI definition is stored at `ci/ios-build.yml.example`. The repository workflow `ios-unsigned-release.yml` builds an unsigned device IPA on a GitHub-hosted macOS/Xcode runner and attaches it to the prerelease. Users must sign that IPA with their own Apple identity before iOS will install it.

## Platform limitation

iOS does not provide Android-style indefinite foreground services. MAR scans live while it is in the foreground and stops monitoring when the app leaves the active state. Background refresh opportunities are controlled by iOS and cannot provide guaranteed 10-second aircraft polling.

## Distribution

An installable IPA requires an Apple Distribution certificate, matching App ID, and provisioning profile. These credentials must be stored outside Git and supplied only to the macOS signing environment. See `RELEASE.md`.
