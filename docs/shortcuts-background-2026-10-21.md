# Notification shortcuts and background feedback — 2026-10-21

This increment was prepared without a connected device, then installed and
checked after the Thor was reconnected alongside the
[Beat response](beat-mode-2026-10-21.md). The prior accepted baseline is `8555527`.

## Scope and ownership

- State: `LEDService` still owns lighting/capture/audio. Notifications and Quick
  Settings observe its runtime; neither is a keep-alive or an automatic Start.
- Feedback: the active notification names the acknowledged effect and reports
  sound reaction, paused sound or the permission/reconnect action needed. A cold
  start says Starting until output is accepted. Engine failure produces a
  dismissible stopped notice when notifications are allowed.
- Blast radius: shortcut placement, controls entry intents and notification
  publication only. Capture, audio, vendor output and saved controls are unchanged.
  `START_NOT_STICKY`, manual recovery and explicit Stop retain their policies.
- Timing: notification updates run on the service looper and compare presentation
  values, avoiding reposts for ordinary parameter/sample updates. Teardown cannot
  repost active status. A late menu callback only reports placement results; it
  cannot start lighting. Menus close with the host. No display is forced.

## Changes

The header gains a 48dp More options button beside appearance. Its two actions
are Add Quick Settings shortcut and Notification settings. Existing effect,
brightness and editor positions/heights are unchanged. The placement request is
explicit and uses Android 13's system confirmation; cancellation is quiet, and
unavailable placement offers manual Quick Settings Edit instructions. There is
no prompt on startup, wake or resume.

The existing Quick Settings tile opens the native controls, shows Off/Starting
or the observed effect, and asks for unlock when necessary. Opening never starts
lighting or requests capture. Active-tile metadata makes service-driven refresh
requests effective. AndroidX `TileServiceCompat` handles the Android 13/14 launch
overloads, removing the direct deprecated call without suppressing lint. The
already-resolved Core 1.13.1 provides this API; no dependency/version change.

Foreground promotion still supplies the required notification even if drawer
permission is denied. Optional updates and failure notices check notification
permission; denial does not stop lighting. Start/Stop remains in the app, and the
active notification has a direct immutable service Stop action. Failure notices
are dismissible and open controls; they contain no Start action. Explicit Stop
removes any notification, and a later Start replaces an old failure notice.
Failure notices use a separate notification ID so asynchronous foreground-notice
removal cannot remove a newly posted recovery message. Obsolete consent rejected
after explicit Stop does not post a failure notice.

Notification placement and Activity appearance remain OS/OEM decisions. The new
notice is available after a handled failure while the process lives; an abrupt
process kill cannot execute failure publication or guarantee physical LED-off.

## Verification

- Java 17 offline: 115 JVM tests passed with the Beat increment; debug/test/release
  assembly, shrinking and vital lint completed. Full lint now has one inherited
  AudioAnalyzer MissingPermission error and 409 warnings. No suppression/baseline.
- Thor: notification construction, direct Stop clearing wanted intent/pending
  consent, obsolete-consent notice suppression, stopped shortcut entry, saved-mode
  restoration and recorder updates passed. See the Beat report for the exact
  combined-run failure and corrected popup assertion rerun.
- The lower-panel menu is physically visible and fits. Add shortcut was exercised
  with an already-added tile; first-add/cancel was not established. The actual
  Runelight rune tile showed Off on the last Quick Settings page. Tapping it
  collapsed the shade and returned to controls below, with no LEDService/capture
  start. No tile order was changed. Secure-lock behavior remains untested.
- The initial ADB shell Stop call was refused because LEDService is non-exported;
  its nonzero/error text was noticed after the install step. The follow-up used
  the app identity and confirmed projection null, and the real notification-Stop
  regression passed. This is not counted as a clean pre-install Stop check.
- A Gradle sandbox/cache denial was resolved through normal escalation. One
  validation retry was blocked when automatic approval review exhausted its usage
  window; after the owner resumed, the command ran. No workaround bypass was used.

## Device acceptance still required

1. First-add/cancel/re-add the shortcut from both panels. Verify the rune, Off/Starting/
   effect label, panel collapse and return to the existing controls without an
   effect restart or display relaunch loop. Check secure-lock opening separately.
2. Observe notification effect/sound changes. Tap controls, then Stop; verify
   physical darkness and no relight after Home, screen movement or sleep.
3. Revoke capture. Verify Ambient stops, its failure notice opens manual recovery,
   and sound-only failure leaves the base effect running with useful status.
   Deny notification permission separately; plain lighting must remain usable.
4. Sleep/wake with each panel separately and both together, Ambient with/without
   sound, Home before lock, Pathfinder and OLED cleanup. Record whether capture
   remains valid, stops or stalls, and distinguish that from task visibility.
5. Ordinary reclaim, task swipe, Active apps Stop and Force Stop are separate
   cases. Keep normal battery settings for the first long-game session.

Proposed later sleep policy is dark while asleep and quiet resume where an
existing session remains valid. It is **not implemented** here. The shared lower-
screen task-restoration behavior also remains open. Do not add wake launches,
grant replay, periodic restarts or battery exemptions in lieu of device evidence.

## API references

- [Tile placement](https://developer.android.com/reference/android/app/StatusBarManager#requestAddTileService(android.content.ComponentName,%20java.lang.CharSequence,%20android.graphics.drawable.Icon,%20java.util.concurrent.Executor,%20java.util.function.Consumer))
- [Tile lifecycle and launch](https://developer.android.com/reference/android/service/quicksettings/TileService)
- [AndroidX launch compatibility](https://developer.android.com/reference/androidx/core/service/quicksettings/TileServiceCompat)
- [User-requested foreground-service stop](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)
