# Implementation evidence

**Checked:** 2026-11-08

- Android 11+ Wireless debugging requires device-side pairing with a pairing code or QR flow and can be revoked in Developer options. Android's current guidance: https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi
- The connected iQOO I2405 (Android API 36) reports `power-off DISPLAY_ID` as a supported `cmd display` subcommand. This was checked with `adb shell cmd display help`; the display-off command itself was not run during implementation.
- `adb-kt` v1.0.13 documents Android 11+ TLS wireless pairing, TLS connection and shell-v2 client APIs; its repository declares Apache-2.0: https://github.com/rhythmcache/adb-kt/tree/v1.0.13
- AOSP documents `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp` service advertisements over mDNS, including their dynamic ports: https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/docs/dev/adb_wifi.md
- Android NSD provides service discovery APIs; Android 16 with target SDK 36 continues to grant local network access through `INTERNET` unless opted into the restriction: https://developer.android.com/develop/connectivity/wifi/use-nsd and https://developer.android.com/privacy-and-security/local-network-permission
- Bouncy Castle 1.86 is listed as the current Java release by its maintainer, dated 2026-09-11, and the maintainer describes it as a hardening release: https://www.bouncycastle.org/download/bouncy-castle-java/

## Decision

Use the library's paired TLS transport with a local loopback endpoint. Android NSD/mDNS discovers the pairing and connect ports and filters them to services resolved to this phone's Wi-Fi addresses; manual port entry remains as fallback. The app accepts only six-digit pairing codes, does not log them, and clears the input after each pairing attempt. It exposes no arbitrary shell field; the sole submitted command is `cmd display power-off 0`.

## Unverified

- The library has a small public contributor/user footprint. Its pairing and transport paths were inspected at source and the published artifact resolved successfully, but its upstream test suite was not run here.
- Pairing and screen-off acceptance still require the owner to enable Wireless debugging and tap the app's controls. The NSD port discovery has not yet been observed on the target iQOO device. We have not run the display-off command from the new app.
