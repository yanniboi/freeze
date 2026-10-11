# Freeze: touchscreen lock for Android (plan)

A small Android app that stops the touchscreen working while a call is in progress,
so a child can carry the phone around without ending the call. It has no ads, no
analytics, and no `INTERNET` permission.

## 1. What it does

1. **Home screen widget**: tap it to start Freeze. A persistent notification appears.
2. **Notification**: has a **Lock** / **Unlock** action, plus **Close** (shown only
   while unlocked) to stop the app and remove the notification.
3. **Lock**: puts a full-screen, see-through overlay on top of every app, including
   the dialer or video call app. The overlay absorbs all touches.
4. **Unlock**: pull down the notification shade, which still sits above the overlay,
   and tap **Unlock**.
5. **Quick Settings tile**: a "Freeze" tile in the pull-down shade that locks and
   unlocks directly. It's the fastest way to lock during a call because it doesn't
   need the widget first.
6. **Auto-unlock**: when the call ends, the lock turns itself off.

### Typical flow during a call

```
Before or during the call: tap the widget  ->  notification appears ("Ready")
Go back to the call screen
Pull down the shade -> tap "Lock" -> swipe the shade back up
   ... the child carries the phone around; touches do nothing ...
Pull down the shade -> tap "Unlock"
```

Or, with the tile:

```
On the call screen: pull down Quick Settings -> tap "Freeze" -> swipe the shade back up
   ... locked ...
Call ends -> unlocks automatically   (or tap the tile / "Unlock" in the shade)
```

The lock has to be turned on from the shade, not from the widget. If the widget
locked the screen, the overlay would land on the home screen and you couldn't get
back to the call. That's why the widget only starts Freeze. **Decision: the widget
never locks by itself.**

### States

```
           widget tap                 notif "Lock" / tile tap
  [Off] ─────────────────> [Ready] ─────────────────────────> [Locked]
    ^                        │  ^                                  │
    │      notif "Close"     │  │  notif "Unlock" / tile tap /     │
    └────────────────────────┘  └──── call ended (auto-unlock) ────┘

  [Off] ── tile tap ──> [Locked]   (the tile skips Ready)
```

- **Off**: nothing is running.
- **Ready**: a foreground service is running and the notification shows "Lock" and "Close".
- **Locked**: the service is running, the overlay is attached, and the notification shows "Unlock".

## 2. How it works

| Concern | Choice | Why |
|---|---|---|
| Language / build | Kotlin, Gradle (Kotlin DSL), Android Studio | Standard and simple |
| minSdk | 26 (Android 8.0) | First version with `TYPE_APPLICATION_OVERLAY` |
| targetSdk | Latest stable SDK | Required for modern devices and Play-style behaviour |
| UI | Plain Views/XML, no Compose | Tiny APK, almost no dependencies; there's only one settings screen |
| Dependencies | `androidx.core` only (for `NotificationCompat` and `ContextCompat`) | Fewer dependencies means less to trust |
| Overlay | `WindowManager.addView` with `TYPE_APPLICATION_OVERLAY` | Shows above all apps and below the status bar and notification shade, which is exactly the layering we need |
| Keep-alive | Foreground service, `foregroundServiceType="specialUse"` | Keeps the overlay alive while other apps are in front. Android 14+ requires a declared service type |
| Widget | `AppWidgetProvider` + `RemoteViews` | No Glance/Compose dependency needed |
| Privacy | No `INTERNET` permission | The OS guarantees the app can't send anything anywhere |

### Permissions

| Permission | Purpose | How it's granted |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw the overlay over other apps | The user turns on "Display over other apps" in system settings, opened via `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` |
| `POST_NOTIFICATIONS` | Show the control notification (Android 13+) | Runtime permission prompt |
| `FOREGROUND_SERVICE` | Run the foreground service | Granted at install |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Service type required on Android 14+ | Granted at install |

Detecting the end of a call needs **no extra permission**. Instead of
`READ_PHONE_STATE`, the app watches the audio mode (see 4.9). This also catches
WhatsApp and other internet calls, which the telephony call-state API doesn't see.

## 3. Project layout

Package name: `io.github.yanniboi.freeze` (can be changed).

```
freeze/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/io/github/yanniboi/freeze/
│       │   ├── MainActivity.kt          # setup screen: permissions, help, test button
│       │   ├── FreezeService.kt         # foreground service; owns state + notification
│       │   ├── OverlayController.kt     # adds/removes the touch-eating window
│       │   ├── FreezeNotifications.kt   # channel + notification builder
│       │   ├── FreezeWidgetProvider.kt  # home screen widget
│       │   ├── Permissions.kt           # canDrawOverlays / notification checks
│       │   ├── FreezeTileService.kt     # Quick Settings tile
│       │   └── CallMonitor.kt           # watches audio mode → auto-unlock on call end
│       └── res/
│           ├── layout/activity_main.xml
│           ├── layout/widget_freeze.xml
│           ├── xml/freeze_widget_info.xml
│           ├── drawable/ic_lock.xml, ic_lock_open.xml, ic_stat_freeze.xml
│           └── values/strings.xml, colors.xml, themes.xml
└── .github/workflows/build.yml          # (optional) builds the APK on push
```

## 4. Components in detail

### 4.1 `AndroidManifest.xml` (key parts)

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<!-- Deliberately NO android.permission.INTERNET -->

<application ...>
    <activity android:name=".MainActivity" android:exported="true"> <!-- LAUNCHER --> </activity>

    <service
        android:name=".FreezeService"
        android:exported="false"
        android:foregroundServiceType="specialUse">
        <property
            android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
            android:value="User-controlled touchscreen lock overlay" />
    </service>

    <service
        android:name=".FreezeTileService"
        android:exported="true"
        android:icon="@drawable/ic_stat_freeze"
        android:label="@string/tile_label"
        android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
        <intent-filter>
            <action android:name="android.service.quicksettings.action.QS_TILE" />
        </intent-filter>
        <meta-data android:name="android.service.quicksettings.TOGGLEABLE_TILE"
                   android:value="true" />
    </service>

    <receiver android:name=".FreezeWidgetProvider" android:exported="true">
        <intent-filter>
            <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
        </intent-filter>
        <meta-data android:name="android.appwidget.provider"
                   android:resource="@xml/freeze_widget_info" />
    </receiver>
</application>
```

### 4.2 `FreezeService`

- Handles four intent actions: `ACTION_START` (go to Ready), `ACTION_LOCK`,
  `ACTION_UNLOCK`, and `ACTION_STOP`.
- Calls `startForeground(...)` straight away in `onStartCommand` on every start,
  to meet the 5–10 second deadline that `startForegroundService` imposes.
- Keeps `isLocked` in memory and exposes it through a small companion `StateFlow`
  (or a static field) so the widget, tile and activity can read it.
- On lock, checks `Settings.canDrawOverlays()`. If the permission is missing, it
  opens `MainActivity` instead of crashing.
- `ACTION_STOP` unlocks, then calls `stopForeground(STOP_FOREGROUND_REMOVE)` and `stopSelf()`.
- Returns `START_NOT_STICKY`. If the process dies, the phone must **never** come back
  in the Locked state without the user asking for it.
- `onDestroy()` always removes the overlay, as a safety net.

### 4.3 `OverlayController`

```kotlin
val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or      // don't steal key focus / volume keys
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,   // optional, see settings
    PixelFormat.TRANSLUCENT
).apply {
    if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0   // cover full screen incl. bar areas
}

val view = FrameLayout(context).apply {
    setBackgroundColor(0x14000000)            // faint ~8% black tint (decided) so you can tell it's on
    setOnTouchListener { _, _ -> true }       // swallow every touch
    // + small lock badge in a corner that fades to low alpha after ~2s
}
windowManager.addView(view, params)
```

- The overlay *consumes* touches; it doesn't pass them through. That means
  Android 12's "untrusted touch" blocking, which only affects windows touches
  pass through, doesn't apply.
- Removing the overlay must be idempotent: guard with `view.isAttachedToWindow`.
- The overlay handles rotation by itself because it uses `MATCH_PARENT`.

### 4.4 Notification (`FreezeNotifications`)

- One channel, "Touch lock", with `IMPORTANCE_LOW`: no sound or vibration, but
  still visible in the shade.
- Built with `setOngoing(true)`, `setCategory(CATEGORY_SERVICE)`,
  `setVisibility(VISIBILITY_PUBLIC)` and `setOnlyAlertOnce(true)`.
- **Ready**: title "Freeze ready", actions **Lock** and **Close**.
- **Locked**: title "Touchscreen locked", action **Unlock**.
- **Tapping the notification body toggles the lock.** This matters because Android
  12+ hides action buttons on collapsed notifications, so tapping the body is the
  one-tap path. Do **not** make the body open `MainActivity` while locked: the
  activity would open underneath the overlay and you couldn't use it.
- All `PendingIntent`s use `FLAG_IMMUTABLE` and target `FreezeService` through
  `PendingIntent.getForegroundService` / `getService`.
- On Android 14+, users can swipe away foreground-service notifications. Set a
  `deleteIntent` that posts the notification again, so the Unlock control can't
  be lost.

### 4.5 Widget (`FreezeWidgetProvider`)

- A 1×1 widget showing a lock icon and a "Freeze" label.
- On tap, it calls `PendingIntent.getForegroundService(ctx, ..., Intent(ACTION_START), FLAG_IMMUTABLE)`.
  Starting a foreground service from a widget tap is one of the allowed
  exemptions to Android 12+'s background-start limits.
- If permissions are missing, the tap opens `MainActivity` instead. Check this
  in the service and redirect from there.
- Optional: change the icon to match the state by calling
  `AppWidgetManager.updateAppWidget` from the service whenever the state changes.

### 4.6 `MainActivity` (setup screen)

- Shows a checklist with a status and a "Grant" button for each item:
  - "Display over other apps", which opens `ACTION_MANAGE_OVERLAY_PERMISSION` with `package:` URI.
  - "Notifications" (Android 13+), which asks for the `POST_NOTIFICATIONS` runtime permission.
- Short how-to text, including "Add the widget to your home screen".
- A "Test lock for 5 seconds" button that locks and then unlocks automatically,
  to make testing safe.
- Settings, saved in `SharedPreferences`:
  - Keep the screen on while locked (default: on).
  - Overlay tint strength: none / **faint (default)** / dim.
  - Emergency unlock gesture on/off (see 4.7).
  - Unlock automatically when the call ends (default: on, see 4.9).
- A "How to add the Quick Settings tile" hint. On Android 13+ you can use
  `StatusBarManager.requestAddTileService()`, which shows a one-tap "Add tile"
  prompt from the setup screen.

### 4.7 Emergency unlock (safety net)

This covers the case where the notification is somehow lost, so you're never
stuck with an unusable phone:

- **Tap the top-right corner 5 times within 3 seconds.** That's hard for a toddler
  to do by accident and easy for an adult. It's on by default and can be turned
  off in settings.
- A reboot always clears the lock, because the service is `START_NOT_STICKY`.

### 4.8 Quick Settings tile (`FreezeTileService`)

- The tile is **active** while locked and **inactive** otherwise. Its label is "Freeze"
  and its subtitle is "Locked" or "Off" (subtitle needs API 29+).
- **Tap**: toggles the lock. If Freeze is Off, it starts the service and goes
  straight to Locked, skipping Ready, because you're already on the call screen
  when you tap it.
- `onStartListening()` reads the current state and calls `qsTile.updateTile()`. When
  the state changes, the service calls `TileService.requestListeningState(ctx, component)`
  so the tile refreshes even while the shade is closed.
- Starting a foreground service from a tile tap: tile clicks get a temporary
  exemption from the background-start limits, so `startForegroundService` should
  work. **Verify this on Android 14+.** If it's blocked, the fallback is
  `startActivityAndCollapse(PendingIntent)` to an invisible trampoline activity
  that starts the service and finishes immediately.
- If permissions are missing, the tap opens `MainActivity` using
  `startActivityAndCollapse` instead.

### 4.9 Auto-unlock when the call ends (`CallMonitor`)

- Watches `AudioManager.mode` instead of the telephony call state:
  - `MODE_IN_CALL` covers regular phone calls.
  - `MODE_IN_COMMUNICATION` covers WhatsApp, Signal, Meet, Teams, Messenger and
    other VoIP calls.
  - This needs no permission. `READ_PHONE_STATE` would only cover regular phone calls.
- API 31+: `AudioManager.addOnModeChangedListener(executor, listener)`.
  API 26–30: poll `audioManager.mode` every ~2 seconds, only while locked. The cost
  is negligible.
- Rule: once the lock has seen an active call (whether it was active when the
  screen was locked or started afterwards), the screen unlocks after the mode has
  been back at `MODE_NORMAL` for **3 seconds**. The delay smooths over brief mode
  changes during hold, call switching and similar.
- If you lock with no call active (for example, to let a child watch a video), the
  lock never unlocks itself.
- When it auto-unlocks, the service goes back to Ready, the notification updates,
  and there's a short vibration so you know touch is back.
- The monitor only runs while the screen is Locked, and it's unregistered on unlock.

## 5. Build phases

Each phase ends with something you can install and try.

| # | Phase | Deliverable |
|---|---|---|
| 0 | **Skeleton** | Android Studio "Empty Views Activity" project, Gradle Kotlin DSL, package name, `.gitignore`, builds a debug APK |
| 1 | **Core overlay** | `OverlayController` + `FreezeService` + the "Test lock 5s" button in `MainActivity`; overlay-permission flow |
| 2 | **Notification controls** | Channel, Ready/Locked notifications, Lock/Unlock/Close actions, tap-to-toggle body, re-post on dismissal, `POST_NOTIFICATIONS` request |
| 3 | **Widget** | `FreezeWidgetProvider`, widget layout and preview, redirect to setup when permissions are missing |
| 4 | **Quick Settings tile** | `FreezeTileService`, tile state sync, "Add tile" prompt on the setup screen |
| 5 | **Auto-unlock on call end** | `CallMonitor` (audio-mode listener plus polling fallback), 3 s debounce, vibration, settings toggle |
| 6 | **Safety & polish** | Emergency corner-tap unlock, keep-screen-on option, tint option, app icon, state-aware widget icon |
| 7 | **CI** (optional) | GitHub Actions workflow that builds the APK |

## 6. Testing checklist

Run these on an emulator (API 26, 31, 34 and latest) and on your own phone:

- [ ] Overlay covers the whole screen, including the notch/cutout and the nav bar area, in portrait and landscape.
- [ ] While locked, taps, swipes and long-presses do nothing in the dialer, WhatsApp, Meet and similar apps.
- [ ] The notification shade can be pulled down while locked, and Unlock works.
- [ ] Volume buttons still change the call volume while locked.
- [ ] Swiping the notification away re-posts it, and the corner-tap unlock works.
- [ ] Killing the app (Settings → Force stop) or rebooting leaves the phone unlocked.
- [ ] The widget starts Freeze. Without permissions, it opens the setup screen.
- [ ] Tile: starting from Off it locks directly; it toggles correctly; its state matches after notification actions; it works on Android 14+ (background-start rules).
- [ ] Auto-unlock: it unlocks about 3 s after a regular phone call ends and after a WhatsApp call ends. Putting the call on hold doesn't unlock it. Locking with no call active never auto-unlocks.
- [ ] Screen timeout behaviour while locked, with keep-screen-on both on and off.
- [ ] Locked state with the keyguard on: turn the screen off, then wake it while on a call.
- [ ] Battery: no wakelocks or CPU use while Ready or Locked. The overlay is static.

## 7. Known limitations and risks

1. **The notification shade still works, for kids too.** The call app's own
   notification has a "Hang up" button. In practice small children rarely swipe
   down precisely from the top edge. A stricter mode that also blocks the shade
   would need an `AccessibilityService` with `TYPE_ACCESSIBILITY_OVERLAY` and a
   different unlock method, such as a volume-key combo. That adds complexity and,
   for sideloaded apps, the "restricted settings" hurdle. **Not planned for v1.**
2. **System navigation still works.** Home, back and recents gestures and the
   navigation buttons sit above app overlays. They don't end calls, and the
   overlay stays on top of whatever app comes to the front, so the screen stays
   locked.
3. **The power button can end calls.** Some phones have an "Accessibility → Power
   button ends call" setting. Make sure it's **off**. An app can't intercept the
   power button.
4. **Behaviour on the lock screen.** When the keyguard is showing, app overlays
   may be hidden. Keep-screen-on (on by default) avoids the screen timing out
   during the call. This needs testing on your device.
5. **Sideloading.** On some recent Android versions the "Display over other apps"
   toggle may be greyed out for an APK that didn't come from a store. Fix it with
   App info → ⋮ → **Allow restricted settings**, then grant the permission.
6. **OEM battery managers.** Xiaomi, Huawei and Samsung battery managers sometimes
   kill foreground services. If that happens, exempt Freeze from battery
   optimisation. Because the service is `START_NOT_STICKY`, a kill unlocks the
   phone rather than trapping you.

## 8. Distribution

- Build a release APK signed with your own keystore. Keep the keystore out of git.
- Install with `adb install app-release.apk`, or copy the APK to the phone.
- Optional: a GitHub Actions workflow builds the APK on every push and attaches
  it to a release.

## 9. Decisions

- **Widget**: only starts Freeze and shows the notification. It never locks by itself.
- **Overlay look**: faint tint (~8% black) plus a small lock badge that fades.
  Tint strength can be changed in settings.
- **Quick Settings tile**: included in v1 (phase 4).
- **Auto-unlock on call end**: included in v1 (phase 5). It uses the audio mode, so
  it needs no extra permission and also covers VoIP calls.
