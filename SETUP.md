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
| `tapt.mediaUrl` | `~/.gradle/gradle.properties` or `-Ptapt.mediaUrl=...` | Default: `https://media.tapt.mafz.al`. |
| `app/google-services.json` | Download from the Firebase console (gitignored) | Build warns. Push is off; everything else works. |
| `tapt.apiHost`, `tapt.apiHostDev`, `tapt.apiScheme`, `tapt.apiVersion` | Same as `tapt.mediaUrl` | Defaults: `https`, `api.tapt.mafz.al`, `v1.4.8`. |

### `tapt.mediaUrl`

This is the backend's `MEDIA_URL`: the public domain of the R2 bucket, with no
trailing slash. It defaults to `https://media.tapt.mafz.al`. The server
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
   **`al.mafz.tapt`**. That is the applicationId. The Java package is still
   `com.linute.linute`, which Firebase doesn't care about.
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
`HttpUrl.Builder.host()`, which rejects `host:port`, so the server must listen
on the scheme's default port. Cleartext HTTP is blocked at targetSdk 35; debug
builds allow it for `api.gettapt` only (`app/src/debug/res/xml/network_security_config.xml`).

### Running against a local backend

The backend's local mode (`NODE_ENV=local`, see its README) serves HTTP on
port 80 and routes by host name, so requests must arrive as `api.gettapt`.

1. Start the backend: `docker compose up -d`, `pnpm install`, copy
   `.env.local.example` to `.env.local`, `pnpm start`.
2. Build and install:

   ```sh
   ./gradlew :app:installDebug -Ptapt.apiScheme=http \
       -Ptapt.apiHost=api.gettapt -Ptapt.apiHostDev=api.gettapt
   ```

3. Point `api.gettapt` at the Mac inside the emulator. `10.0.2.2` is the host's
   loopback. This needs a `google_apis` image (not `google_apis_playstore`):

   ```sh
   emulator -avd tapt35 -writable-system      # every launch from now on
   adb root && adb remount && adb reboot      # first time only
   adb root && adb remount
   adb shell "echo '10.0.2.2 api.gettapt' >> /system/etc/hosts"
   ```

`pnpm smoke` in the backend creates users at Smoke Test University with the
password `pass1234`, which is the quickest way to get an account to log in with.

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
- **Tested against a local backend only.** `api.tapt.mafz.al` had no DNS
  record when this was written. On an API 35 emulator against the backend's
  local mode (backend commit `9cbd3d5e`), these work:
  - email login, the socket handshake, the campus feed and the badge counts
  - likes and comments (saved to Mongo), chat rooms, history and sending
  - a REST 401 from a bad token logs out and returns to the pre-login screen

  Earlier, without a server: camera, saving to `Pictures/Tapt`, the gallery
  picker (`READ_MEDIA_*`) and the editor's tools.

  Not covered: posts with an image (the local backend has no R2 credentials,
  so it answers 500), push, sign-up past the college step (see Backend
  requests), video recording and the FFmpeg path.

## Domains and names

`linute.com` and `tapt.io` are gone. The app now uses:

- `api.tapt.mafz.al` for REST and Socket.IO
- `media.tapt.mafz.al` for media
- `https://www.tapt.mafz.al/terms-of-service` and `/privacy-policy`, served by
  the backend's www app
- `support@tapt.mafz.al`, the backend's `config.email.support`, for the
  feedback, help and account-deletion emails

Kept on purpose:

- The `linute://…/view/<id>` deep-link scheme. It is app-local, and the
  backend generates these links (`models/activities.js`).
- The Java package `com.linute.linute`. The installed app id is `al.mafz.tapt`.
- `MainContent/Global/GlobalFragment.java` has a hard-coded 2016 sample
  article with a `ccnycampus.org` image. It is test data and harmless.

## Backend requests

Things the app needs from the server. The backend session picks these up.

- [ ] Enable `allowEIO3: true` in `socket.js`. Optional: the app now uses
      socket.io-client 2.1.2, which speaks Engine.IO v4 natively.
- [ ] Give production Firebase service-account credentials
      (`GOOGLE_APPLICATION_CREDENTIALS`) for the project above.
- [ ] Fill the R2 bucket (`media.tapt.mafz.al`) with the files the server's data refers to, under
      the folders listed above.
- [ ] For the sign-up field lockdown: `POST /v1.4.8/users` (sign-up) sends
      `email`, `password`, `firstName`, `lastName`, `timeZone`, `college` and
      optionally `profileImage` (base64 JPEG). No request has been seen to fail
      yet. Bodies will be recorded here if one does.
- [ ] Production has no searchable colleges, so nobody can sign up.
      `GET https://api.tapt.mafz.al/v1.4.8/colleges?name=university&limit=2`
      (also `a`, `new`, `college`, `hunter`) returns `{"colleges":[],...,"totalCount":0}`.
      Expected: the colleges imported (`pnpm import-colleges`) with `keywords`
      filled, or at least the colleges the friends need.
- [ ] College search finds nothing for the seeded test colleges.
      `GET /v1.4.8/colleges?name=Test&limit=30` returns `{"colleges":[],...,"totalCount":0}`
      although "Test University" and "Smoke Test University" exist. The search
      matches `keywords`, and the seed data (including the smoke test's
      `seedCollege`) leaves `keywords` empty. Expected: the seeded colleges are
      searchable by name, so sign-up can be tested locally.
- [ ] Device header name. The app sends the device id as `UUID` (unchanged
      since 2016), while `sessionHeader` reads `req.headers.udid`, so every REST
      request gets `req.session.id = null` (the server logs `session null`).
      Requests still succeed. Which side should change is a backend decision.
      The socket handshake sends `udid` correctly.
