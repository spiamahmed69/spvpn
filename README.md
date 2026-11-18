# SP VPN

A real, working Android VPN client with a premium dark navy / blue / purple
interface. Built on the open-source [BongoVPN](https://github.com/bongotech-ai/bongovpn)
library (Apache 2.0), which is itself based on
[ics-openvpn](https://github.com/schwabe/ics-openvpn). SP VPN is a real
OpenVPN client — it is **not** a connection simulator.

- **App name:** SP VPN
- **Package:** `com.spsiam.spvpn`
- **Version:** 1.0.0 (versionCode 1)
- **Min SDK:** 24 (Android 7.0) · **Target/Compile SDK:** 37
- **Developer:** SP — Siam Ahmed

## Features

- Real OpenVPN connect / disconnect (BongoVPN 1.0.3, `ai.bongotech:bongovpn:1.0.3`)
- Large animated circular Connect button with a progress ring
- Connection status indicator, connection duration timer, live
  download/upload speed and session totals
- Public IP display (fetched live from `https://api.ipify.org`, shown as
  "Unavailable" on failure — never faked)
- Server selection cards (country flag, host, status dot)
- Import your own `.ovpn` config files (validated: must contain `client`
  and a `<ca>` block, the same rule the library applies); imported configs
  are stored in the app's private internal storage only
- Auto-reconnect: up to 3 retries, 5 seconds apart, after an unexpected drop
- DNS settings (Primary/Secondary, default 8.8.8.8 / 1.1.1.1)
- Connection error handling — real errors are shown, never hidden
- VPN permission management (system VPN permission + notification permission)
- Light / Dark / System theme, Settings and About pages

## ⚠️ Important limitation — read before use

**SP VPN ships no VPN server of its own.** The only bundled endpoint is the
public VPNGate relay `public-vpn-187.opengw.net:443` (Japan, TCP, bundled
`japan.ovpn`) that came with the upstream BongoVPN sample. It is a
third-party public relay that **may be offline, slow, or rotated at any
time**. It uses VPNGate's standard public credentials (`vpn` / `vpn`) from
the upstream sample.

If that relay fails, the app will show the real connection error instead of
pretending to connect. To get a working connection, **import your own
working `.ovpn` file** from your own VPN provider or server (tap
"Import .ovpn config" on the dashboard) and select it.

## DNS settings — how they actually work

The BongoVPN library has **no DNS-setter API**. Custom DNS is applied by
appending `dhcp-option DNS <primary>` (and secondary, if set) lines to the
`.ovpn` text before it is attached with `attachFromString`. The VPN server
may push its own DNS and override these values. This is stated in the app
(Settings → DNS settings) and in `MainActivity.java` (`attachCurrentServer`).

## Build

Requires JDK 17 and Gradle 9.6.0.

```bash
gradle assembleDebug
```

The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions (`.github/workflows/build.yml`) builds the same APK on every
push to `main` and uploads it as the `SP-VPN-apk` artifact. Note: workflow
files cannot be pushed through the GitHub API, so this file must be added
via the GitHub website the first time. The repo also omits the binary
Gradle wrapper jar and the legacy `.webp` launcher icons (GitHub API pushes
are text-only); the build uses `gradle/actions/setup-gradle` with Gradle
9.6.0 instead of `./gradlew`, and the launcher icon is the vector drawable
`ic_sp_logo`.

## Use

1. Install the APK and open SP VPN.
2. Grant the VPN permission when Android asks (tap Connect again if the
   first tap only opened the permission dialog).
3. Tap the big **Connect** button. Japan (VPNGate) is selected by default.
4. Or tap **Import .ovpn config**, pick a `.ovpn` file from your provider,
   then select it and tap Connect.

## Security

- `.ovpn` contents, usernames and passwords are never written to logs, never
  shown in Toasts, and never used in file names. Imported configs live only
  in the app's private internal storage (`getFilesDir()/imported/`).
- The bundled `vpn` / `vpn` credentials are VPNGate's public demo
  credentials already present in the upstream sample — not private keys.

## Attribution & license

- BongoVPN — Copyright 2026 Bongo Tech (Jubayer Hossain / Bongo iOTech Ltd),
  Apache License 2.0. Source: https://github.com/bongotech-ai/bongovpn
- ics-openvpn — see `NOTICE`.
- SP VPN's own code is released under the Apache License 2.0 (see `NOTICE`).
  The upstream repository contains no `LICENSE` file; its README states
  Apache 2.0, and that statement is preserved here.
