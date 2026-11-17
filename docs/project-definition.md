# Project Definition Contract

**Date:** 2026-11-08

- **Problem/outcome:** provide a separate Android app that can run `cmd display power-off 0` locally, with the panel off while the current Android user session remains unlocked.
- **Primary user:** the phone owner, who can enable Android Developer options and authorize Wireless debugging. This is directly specified by the user.
- **Workflow:** enable Wireless debugging; pair this app with Android's six-digit pairing flow; connect to the current local ADB port; explicitly tap **Display off**.
- **Delivery:** native Android APK; an earlier version was installed on an iQOO I2405 / Android 16. This UI revision awaits on-device layout review. The screen-off operation remains user-triggered.
- **Requirements:** REQ-1 standalone project, separate from MirrorDash and Shizuku; REQ-2 local TLS ADB pairing; REQ-3 fixed screen-off command only; REQ-4 keep pairing material private and provide revocation guidance; REQ-5 discover current ADB pairing/connect ports automatically when available, with manual fallback; REQ-6 clear setup and connection states; REQ-7 keep the routine **Open Developer options**, **Find ports**, and **Display off** actions visible without scrolling for paired users, while placing pairing and manual diagnostics in a collapsible section.
- **Non-goals:** map mirroring, Tripper communication, Shizuku, arbitrary shell, lock-screen bypass, automatic screen-off, background navigation, and generalized ADB terminal.
- **Assumptions:** Android Wireless debugging remains enabled while the command is invoked; its local connection port may change; user enters Settings' displayed pairing/connection values locally and does not send pairing credentials in chat.
- **Decision:** use the Android Wireless debugging pairing protocol through `adb-kt` v1.0.13 (Apache-2.0); use Android NSD/mDNS to discover the phone's pairing/connect ports and match resolved hosts to the phone's Wi-Fi addresses; use only loopback (`127.0.0.1`) for ADB; store the ADB private key under `noBackupFilesDir`; exclude app data from backup. The pairing code remains manual because Android displays it as a short-lived user authorization secret. The app does not request general overlay access or run a background service.
- **Risk:** Wireless debugging authorizes shell-level access to the phone for this app's ADB identity. The app narrows its own callable command to one constant; the user can revoke the system authorization in Developer options.
- **UI decision:** put Developer options and port refresh beside the primary Display off action. Default pairing and manual-connection tools to collapsed after pairing; leave them expanded for first-time setup. Display off performs the existing TLS connection itself, so the manual Connect control remains a troubleshooting tool.

## Trace

`REQ-1..6 -> DEC-1 (local ADB over Wireless debugging and NSD) -> IMP: app/src/main/java/com/dharmil/displayoff -> TST: Gradle build, APK install, on-device mDNS and display-off acceptance pending.`
`REQ-7 -> UI decision -> IMP: app/src/main/java/com/dharmil/displayoff/MainActivity.kt -> TST: build verified; on-device short-screen layout review pending.`
