# Building and configuring the Tapt Android app

This is the 2016 Tapt (Linute) Android app, updated to build on current tools
and to talk to the modernized backend at `api.tapt.mafz.al`. It is meant to be
sideloaded by a few people, not published.

## Toolchain

- JDK 17 or 21 (`JAVA_HOME`). Android Studio's bundled JBR works.
- Android SDK with platform 35 and build-tools 35. Put its location in
  `local.properties` (`sdk.dir=/path/to/sdk`), which is gitignored. Android
  Studio writes this file for you.
- The Gradle wrapper downloads Gradle 8.14.3. AGP is 8.13.

```sh
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`assembleRelease` builds an unsigned, minified APK. There is no release signing
config. For a handful of friends the debug APK is enough.

## Settings and secrets

Nothing secret is committed. Set these outside the repo.

| What | Where | Without it |
|---|---|---|
| `tapt.mediaUrl` | `~/.gradle/gradle.properties` or `-Ptapt.mediaUrl=...` | Build warns. Images and videos don't load. |
| `app/google-services.json` | Download from the Firebase console (gitignored) | Build warns. Push is off; everything else works. |
| `tapt.apiHost`, `tapt.apiHostDev`, `tapt.apiScheme`, `tapt.apiVersion` | Same as `tapt.mediaUrl` | Defaults: `https`, `api.tapt.mafz.al`, `v1.4.8`. |

### `tapt.mediaUrl`

This is the backend's `MEDIA_URL`: the HTTPS base of the S3 bucket, with no
trailing slash, for example `https://<bucket>.s3.amazonaws.com`. The server
stores only file names. The app adds this base and a folder (see
`UtilsAndHelpers/Utils.java`):

```
events/original/   events/video/     trends/            messages/original/
messages/video/    profiles/original/ profiles/anonymous/ rooms/original/
rooms/thumbnail/   filters/          memes/             comments/original/
articles/original/ articles/video/
```

The app hard-codes no file names. Every name it loads comes from the server,
so the bucket only needs whatever the server's data refers to.

### Firebase (push)

1. Create a Firebase project and add an Android app with package name
   **`com.linute.linute`**. The applicationId was not renamed.
2. Download `google-services.json` into `app/`. Don't commit it.
3. Give the backend a service account for that project (see Backend requests).

The app receives FCM data-only messages in
`API/MyFirebaseMessagingService.java`. It sends its FCM token to the server as
`deviceToken` in the Socket.IO handshake, not over REST, and reconnects when
the token rotates.

### Pointing at another backend

```sh
./gradlew :app:assembleDebug -Ptapt.apiHost=api.example.test
```

The value is a bare host name. `API_Methods.get()` passes it to
`HttpUrl.Builder.host()`, which rejects `host:port`. Cleartext HTTP is blocked
at targetSdk 35, so a local server needs HTTPS (for example a tunnel) or a
debug-only network security config, which this repo doesn't have.

## Known gaps

- **Video processing.** `com.writingminds:FFmpegAndroid` runs a bundled ffmpeg
  binary from app storage. Android 10+ blocks executing files from app data
  at targetSdk 29+, and the binary is 32-bit ARM only. Expect video posting,
  video edits and video sharing to fail until it is replaced.
- **Uploads stop in the background.** `UploadIntentService` is a plain
  started service, so Android 8+ may stop it about a minute after the app
  leaves the foreground.
- **Open-source attributions.** One license string in `values/strings.xml` is
  longer than aapt2 allows, so it shows as `STRING_TOO_LARGE`.
- **Not tested against the backend.** `api.tapt.mafz.al` had no DNS record
  when this was written. On an API 35 emulator with a fake session (no
  server), these work without crashing:
  - launch, pre-login, login and the first sign-up screen
  - the main feed shell, with the location and notification prompts
  - camera, saving to `Pictures/Tapt`, the gallery picker (`READ_MEDIA_*`),
    the editor's tools, and the upload service's failure notification

  The socket retries quietly while the host doesn't resolve. Video recording
  and the FFmpeg path were not exercised.

## Old domains still referenced (for the owner)

These point at the dead `tapt.io` domain. They were left unchanged because the
new URLs aren't known yet:

- `support@tapt.io`
  - `MainContent/MainActivity.java` (FiveStarsDialog feedback address)
  - `LoginAndSignup/PreLoginActivity.java` (help email on the login screen)
  - `UtilsAndHelpers/FiveStarRater/FiveStarsDialog.java` (fallback address)
  - `res/values/strings.xml`, the account-deletion text
- `https://www.tapt.io/terms-of-service` and `https://www.tapt.io/privacy-policy`
  - `LoginAndSignup/PreloginChoicesFragment.java`
  - `MainContent/Settings/SettingActivity.java`
- `MainContent/Global/GlobalFragment.java` has a hard-coded 2016 sample
  article with a `ccnycampus.org` image. It is test data and harmless.

The `linute://article` deep-link scheme in the manifest is app-local, so it
still works.

## Backend requests

Things the app needs from the server. The backend session picks these up.

- [ ] Enable `allowEIO3: true` in `socket.js`. Optional: the app now uses
      socket.io-client 2.1.2, which speaks Engine.IO v4 natively.
- [ ] Give production Firebase service-account credentials
      (`GOOGLE_APPLICATION_CREDENTIALS`) for the project above.
- [ ] Fill the S3 bucket with the files the server's data refers to, under
      the folders listed above.
- [ ] For the sign-up field lockdown: `POST /v1.4.8/users` (sign-up) sends
      `email`, `password`, `firstName`, `lastName`, `timeZone`, `college` and
      optionally `profileImage` (base64 JPEG). No request has been seen to fail
      yet. Bodies will be recorded here if one does.
