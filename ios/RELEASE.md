# iOS release signing

An iPhone/iPad release cannot be signed with the Android keystore. Apple requires a separate Apple Developer identity.

1. Join the Apple Developer Program.
2. Register the bundle ID `de.julien.flightradius.ios`.
3. Create an Apple Distribution certificate and provisioning profile.
4. On macOS, run `xcodegen generate` in `ios/`.
5. Select the Apple team in Xcode and archive the `MAR` scheme for **Any iOS Device**.
6. Use **Distribute App** to export for TestFlight/App Store or registered-device testing.

Never commit `.p12`, `.mobileprovision`, Apple private keys, or their passwords. A GitHub-hosted IPA is installable only when its provisioning method authorizes the receiving device; TestFlight/App Store distribution is the normal public route.
