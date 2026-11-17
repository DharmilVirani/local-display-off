# Display Off

A small Android app that locally pairs with Android Wireless debugging and sends one fixed command: `cmd display power-off 0`. It is a separate project; it does not use MirrorDash, Shizuku, a PC, or an arbitrary shell UI.

## Phone setup

1. On first use, tap **Open Developer options**, open **Wireless debugging**, and turn it on.
2. In Android Settings, tap **Pair device with pairing code**. In Display Off, open **Pairing & troubleshooting**, tap **Find ports**, enter the six-digit code, then tap **Pair this app**. Pairing is saved, and the code is cleared afterward.
3. For normal use, open Display Off and tap the visible **Display off** button. The app refreshes the current Wireless debugging port on open or resume and connects automatically when sending the command.
4. **Open Developer options** and **Find ports** are also on the main screen. If automatic discovery fails, use **Pairing & troubleshooting** to enter the port manually or test the connection.

The pairing code and pairing port are needed only for first-time pairing or after revocation. The connection port can change; the app tries to rediscover it automatically using Android's ADB mDNS service. If discovery fails on a network, enter the current port manually. Android may disable Wireless debugging after Wi-Fi disconnects or a reboot; enable it again if needed. The pairing itself remains until forgotten or revoked.

## Security and revocation

- This app only connects to `127.0.0.1` and offers one fixed command. It has no general shell field.
- The local ADB identity is kept in the app's private, non-backed-up storage.
- To revoke it, use **Developer options → Wireless debugging → Paired devices** to forget the app's pairing, or use **Revoke debugging authorizations**. The app's **Forget this app's local key** button deletes its local key; Android's pairing entry should also be revoked in Settings.
- Wireless debugging grants an ADB identity shell-level authority while authorized. Turn Wireless debugging off when it is not needed.

## Build

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Implementation record

See [the Project Definition Contract](docs/project-definition.md) and [research notes](docs/research.md). `adb-kt` v1.0.13 is pinned from [rhythmcache/adb-kt](https://github.com/rhythmcache/adb-kt), licensed Apache-2.0. Its SPAKE2 dependency is also pinned. Bouncy Castle is pinned to 1.86, the current upstream hardening release. The app uses the libraries' Wireless debugging TLS pairing and ADB transport APIs; it does not bundle a native ADB executable. Third-party notices are packaged in the APK assets.
