# Freeze

A tiny Android app that locks the touchscreen during a call, so a child can carry the phone
around without hanging up. No ads, no tracking, and no internet permission at all.

## Using it

1. Open **Freeze** once and allow **Display over other apps** and **Notifications**.
2. Add the **Freeze tile** to Quick Settings (there's an *Add* button on Android 13+), and/or the
   **Freeze widget** to your home screen.
3. During a call, either:
   - pull down Quick Settings and tap **Freeze**, or
   - tap the widget beforehand, then on the call pull down the shade and tap **Lock**.

   Swipe the shade back up. Touches now do nothing.
4. To unlock, pull down the shade and tap **Unlock** (or tap the tile again). Freeze also unlocks
   by itself about 3 seconds after the call ends, with a short buzz.

Safety nets:

- **Emergency unlock:** tap the top-right corner 5 times within 3 seconds.
- Restarting the phone always clears the lock.

Worth knowing:

- Home/back gestures and the volume keys still work while locked. They don't end calls, and the
  lock stays on top of whatever app comes to the front.
- The power button can't be blocked. If your phone has **Settings → Accessibility → Power button
  ends call**, turn it off.
- If *Display over other apps* is greyed out for an installed APK, open **App info → ⋮ → Allow
  restricted settings** first.

## Getting a build

Every push builds a debug APK in GitHub Actions (**Actions → Debug APK → a run → Artifacts**).
Pushing a tag such as `v0.1.0` also attaches the APK to a GitHub Release, which is the easiest
way to download it on the phone.

To make successive CI builds install over each other, store one debug keystore as the
`DEBUG_KEYSTORE_BASE64` repo secret (`base64 -w0 ~/.android/debug.keystore`). Without it each build
has a different signature, and you have to uninstall the old build first.

## Building locally

Needs JDK 17+ and the Android SDK (Android Studio provides both).

```sh
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a signed release build, create `keystore.properties` next to `settings.gradle.kts`
(git-ignored) with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, then run
`./gradlew :app:assembleRelease`.

## How it works

See [PLAN.md](PLAN.md) for the design. In short:

- `FreezeService`: a foreground service that owns the notification, the overlay and the call monitor.
- `OverlayController`: a full-screen `TYPE_APPLICATION_OVERLAY` window that swallows touches. It sits
  above all apps but below the status bar, which is why the shade still works.
- `CallMonitor`: polls the audio mode while locked. A call ending (regular or VoIP) releases the lock.
- `FreezeTileService` and `FreezeWidgetProvider`: entry points that send actions to the service.
