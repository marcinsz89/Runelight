# Thor baseline device session — 2026-10-20

## Build, installation and scope

Tested the unchanged Bifrost baseline from the owner's active Fork checkout,
commit `1baddf1644ff0d7edd1bd0f4ba02f7eb6c8e3cfa`, with development documents/tooling
uncommitted. No application source or build configuration was changed.

- `./scripts/dev.sh build --offline`: PASS, Java 17, cached tasks up to date.
- `adb -s <thor> install -r app/build/outputs/apk/debug/app-debug.apk`: PASS.
- Package `com.moonbench.bifrost.debug`, version **1.3.1-debug**, code **16**.
- APK SHA-256: `c22225f36cb8f5bfe990a15e657dbce7a262e8f5354096a3048d0d9d914691cd`.
- Android 13 / API 33, firmware `Thor_V1.0.0.377_20260206_165408_user`.
- Pathfinder 1.1.0 installed and its screen-swap action observed in device logs.
- Display 0: 1920 × 1080; display 4: 1240 × 1080; density 369 dpi.

This is an installed inherited debug build, **not the proposed Runelight POC**.
The permission prompt for notifications was allowed. Optional Accessibility and
Usage Access onboarding was skipped; neither was enabled by this test.
The live session included owner interaction, including the Pathfinder button;
it is not an automated, input-isolated test run.

## Observations

| Check | Result / limit |
|---|---|
| Launch requesting display 4 | Onboarding/main UI ultimately appears on display 0; lower launch is not honored with the default saved preference |
| Main UI on display 0 | PASS smoke check: screenshot, settings tabs and scrolling usable |
| Ambient service/capture | Foreground LEDService and a screen-capture projection observed; logs report AMBIENT started |
| Physical LED colors/output | NOT VERIFIED; service/projection existence does not establish correct hardware output |
| Owner's Pathfinder move, 0 → 4 | FAIL: UI task disappears, service subsequently stops |
| Direct Android task move, 0 → 4, service stopped | FAIL: same app-requested finish/task removal without invoking Pathfinder's swap shortcut |
| Fatal crash in this session | No new fatal exception, crash-buffer entry or package exit record; app process survives both observed failures |
| Final state | Debug app reopened on display 0; no LEDService and no active media projection |

## Reproduced display failure

At device time **13:31:53**, the owner pressed Pathfinder's screen-move button.
The full log records `PathfinderSwap` moving the Bifrost task from display 0 to 4.
Android recreated MainActivity on display 4. Bifrost then issued its own
`FLAG_ACTIVITY_NEW_TASK` launch, and the system delivered a new intent to the same
activity record while moving/recreating it back on display 0. Android recorded
`wm_finish_activity` with reason **app-request**, followed by final destruction
and removal of the task. InputDispatcher also logged a transient display-0/4
window mismatch during the sequence.

At **13:31:55.396**, LEDService logged:

```text
activityCheckRunnable: activity not running & no background run → stopping
cleanupAndStop: STOPPING SERVICE
```

The process remained alive. There was no current-session `WindowLeaked` or fatal
exception captured. The full crash buffer contains older, unrelated system/app
failures; these must not be attributed to this reproduction.

At **13:33:08**, an independent move via Android's `am display move-stack`
command reproduced the new-intent → app-requested finish → task-removal sequence
with the lighting service already stopped. This establishes that Ambient capture
is not required for this failure, and that invoking Pathfinder's shortcut is not
required. Pathfinder remained installed/enabled: this is **not** a disabled versus
enabled comparison or proof that all reported screen-move incidents share one cause.

### Source explanation and priority

**R14 — P1: saved-display enforcement can close a task moved between screens.**

`MainActivity.onCreate` calls `maybeRelaunchOnCorrectDisplay()` before completing
initialization. In `MainActivity.kt:3203`, that method reads `thor_bottom_screen`
(default false), selects display 0 when false, launches MainActivity with
`FLAG_ACTIVITY_NEW_TASK` on the selected display, then calls `finish()` at line
3227. MainActivity uses `singleTop`. The observed system events show the relaunch
reusing the existing activity record, which is then finished. This explains the
captured UI disappearance; it does not require a Java process crash.

The resulting service shutdown corroborates **R2**: the two-second activity check
in `LEDService.kt:380` stops lighting when the UI task disappears unless background
run is allowed. No explicit Stop action was part of the owner's screen move.

Fix direction: distinguish an initial launch preference from an external move.
Allow the host Activity to recreate on the destination screen; avoid repeatedly
enforcing a saved screen in `onCreate` and avoid finishing a reused task. Keep the
service independent of UI task lifetime and preserve explicit Stop semantics.
Retain R1 dialog-lifetime tests: absence of a leaked window in this incident does
not invalidate the separate earlier DuoFrost report or inherited source finding.

## Evidence and follow-up

Raw logs, app preference snapshot, UI XML, screenshots and activity/service dumps
are stored locally under ignored
`local-test-results/2026-10-20-baseline-device/`. No device serial is included in
this tracked report; no raw evidence was uploaded or committed.

Follow-up regressions: top/bottom fresh launch, external moves both ways with the
saved preference opposite the destination, open settings/dialogs, engine stopped
and running, no capture restart, settings retained and explicit Stop still final.
Compare Pathfinder enabled/disabled separately. Repeat on a debug build containing
the eventual fix before marking D01/D02 passed.

Solid/four-zone correctness, bottom Ambient source, audio, sleep/wake, OEM task
cleanup, long-duration performance and Android 14+ remain untested. JVM tests and
full lint were not rerun in this device-only task; the earlier 60-test pass and
known lint failures remain the build baseline. No source fix, commit or push was
made during this session.
