# Schall for Android

The phone app for [Schall](https://github.com/pxldi/schall), the self-hosted
music collection manager. It answers the review queue with the audio preview,
watches downloads and wants, and follows artists. Kotlin and Jetpack Compose,
printed in the same Duoton inks as the web (Bricolage Grotesque, Misprint icons).

## Install

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/pxldi/schall-android"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="54" /></a>

Signed APKs are attached to each [GitHub release](https://github.com/pxldi/schall-android/releases).
The badge opens [Obtainium](https://obtainium.imranr.dev) with this repository already filled in,
so it installs the latest release and keeps it updated. Without Obtainium, download the `.apk`
from the newest release and open it on the phone.

## Sign in

Open Settings → Phone on the Schall web app, add a phone, and scan the QR code
it shows. The code carries the server address and a token minted for this
phone. The token is stored in the app's private storage and revoked from the
same page on the web.

## Build

```sh
./gradlew assembleRelease
```

The APK is `app/build/outputs/apk/release/app-release.apk`. Every build is
signed with the checked-in debug key, so an APK from CI installs over one
built here. Nothing is published to a store; a `v*` tag attaches the APK to a
GitHub release.

```sh
./gradlew test lint
```

## What it talks to

Every call is one the web makes, listed in `docs/api.md` of the Schall repo.
Updates arrive over the server's event stream while the app is in front, with
a 15 s poll as the safety net. The ntfy notification's `schall://review` link
opens the Review tab.
