# Third-party notices

## adb-kt 1.0.13

- Source: https://github.com/rhythmcache/adb-kt
- Version: `v1.0.13`
- License: Apache License 2.0
- Purpose: ADB TLS pairing and shell-v2 transport over Android Wireless debugging.
- App usage: the app calls only TLS pair/connect APIs and submits one constant command, `cmd display power-off 0`.
- License: Apache-2.0; the full license is packaged at `app/src/main/assets/adb-kt-LICENSE.txt`.

## spake2-kt 1.0.2

- Source: https://github.com/rhythmcache/spake2-kt/tree/v1.0.2
- License: Apache-2.0; the full license is packaged at `app/src/main/assets/spake2-kt-LICENSE.txt`.
- Purpose: SPAKE2 pairing support used by `adb-kt`.

## Bouncy Castle 1.86

- Source: https://www.bouncycastle.org/
- License: MIT; the license text is retained in the APK at `META-INF/LICENSE.md`.
- Purpose: TLS 1.3 transport and crypto dependencies used by `adb-kt`.
