# Schall for Android

The phone app for [Schall](https://github.com/pxldi/schall), the self-hosted
music collection manager. It answers the review queue with the audio preview,
watches downloads and wants, and follows artists. Kotlin, Jetpack Compose,
Material 3.

## Sign in

Open Settings → Phone on the Schall web app, add a phone, and scan the QR code
it shows. The code carries the server address and a token minted for this
phone. The token is stored in the app's private storage and revoked from the
same page on the web.

On Android 17 the app asks for local network access the first time it opens.
Without it Android drops every connection to a server on the same Wi-Fi and
the sign-in times out after 15 s; DNS still answers, so nothing else hints at
it. Grant it, or allow it later under the app's permissions.

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
