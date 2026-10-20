# Runelight inherited-code review — 2026-10-20

Review only. No application code, package identity, settings, or feature scope was changed.

The fork was cloned into the workspace without overwriting the existing handoff.
Reviewed baseline: `marcinsz89/Runelight`, commit
`1baddf1644ff0d7edd1bd0f4ba02f7eb6c8e3cfa` (Bifrost 1.3.1).
The fork has no additional commits at this snapshot. `origin` points to the fork.
No commit or push was made.

The handoff was read in full. Its architecture, feature lists and implementation
phases are proposals, not approved requirements. This review establishes defects
and evidence before deciding the product scope or design.

## Evidence and limitations

- **Source-confirmed:** behavior directly visible in this baseline; some narrow
  logic paths were also exercised in an isolated JVM harness below.
- **Owner evidence:** Thor Max running **Android 13**; bottom-display launch issues
  and a supplied DuoFrost log excerpt dated October 14.
- **Comparator source:** DuoFrost `5bf04b9cd8cbbf462d8c697f431e73b187dce1a1`
  fetched October 20. Only selected capture, service and dialog changes were
  compared. This is not a certification of DuoFrost or its published APKs.
- **Unverified:** physical LED behavior, device performance, actual Bifrost fatal
  exception, exact installed DuoFrost build, firmware build number and the
  bottom-launch workaround setting. ADB returned an empty device list.
- No screenshots, audio, private device data or logs were uploaded to another
  service. AmbiLuma internals and relative quality remain unverified.

P1 below means a core reliability/functionality issue to address before relying
on the affected mode. P2 means a bounded correctness, efficiency or compatibility
defect. Severity does not imply the symptom was reproduced on a physical Thor.

## Findings affecting Android 13 and the proposed core

### R1 — P1: alert windows outlive their Activity

**Source:** `ui/BifrostAlertDialog.kt:28-70`,
`MainActivity.kt:804-810,2396-2417,4305-4321`.

The helper checks `isFinishing/isDestroyed` before showing an alert, but retains
no lifecycle cleanup for a dialog already shown. MainActivity's destruction
cleanup dismisses the artwork sheet, not these alerts. Destroying/recreating the
host after showing the welcome or another alert can leak its window. A guard at
show time cannot handle a future destruction.

**Owner evidence:** at 09:24:53 the supplied DuoFrost log reports app windows in
display 0 that belong to display 4; at 09:24:54.211 it reports
`android.view.WindowLeaked` for MainActivity with `Dialog.show` in its allocation
trace and `handleRelaunchActivity` in the lifecycle stack. This directly supports
a dialog/recreation defect in that run. It does **not** identify the original
Bifrost crash, establish a fatal exception, identify the obfuscated dialog method,
or prove Pathfinder caused it.

**Small correction boundary:** make alert lifetime follow the host lifecycle;
teardown must not count as a user confirming or dismissing a one-time notice.
Exercise creation, display movement/recreation and destruction while an alert is
visible. Inspect other independently created dialogs as well.

DuoFrost's current `ui/DuoFrostAlertDialog.kt:70-78` adds an ON_DESTROY dismissal.
Its changelog describes this in 2.5.1, dated October 16. The supplied October 14
excerpt predates that release date and has no version metadata; the owner's
report of a later recurrence remains important but needs a matching newer trace.

### R2 — P1: normal task dismissal deliberately stops lighting

**Source:** `MainActivity.kt:4227-4232`,
`services/LEDService.kt:376-400,1166-1184,416-419,593`.

Manual starts set `allowBackgroundRun` only when boot auto-start or the external
API is enabled. Otherwise `onTaskRemoved` calls cleanup, and a periodic task
check independently stops lighting when no app task remains. Therefore a normal
user who enables neither unrelated feature loses LEDs after dismissing the task.
This is explicit application policy, not evidence of an OEM process kill.

All normal starts return START_NOT_STICKY; null starts stop immediately. That is
a separate recovery limitation and does not explain every disappearance.

**Small correction boundary:** separate continuation from boot/API preferences;
record explicit enabled intent independently of UI/task existence. Decide
process-restoration policy only alongside a valid configuration and consent
model. START_STICKY alone cannot restore a lost capture grant.

### R3 — P1: Activity pause can discard Stop or interrupt a restart permanently

**Source:** `services/ServiceController.kt:24-31,50-109`,
`MainActivity.kt:820-834,2374-2388`.

Start and Stop wait 100 ms in an Activity-owned Handler. `onPause` cancels all
pending operations. Pressing Stop and immediately leaving the Activity can
cancel the only stop request. Restarts first stop the service, then wait before
starting it again; a pause during that gap invalidates the restart token, leaving
lighting off. Display changes and permission/settings navigation can pause the
Activity, so these are relevant beyond a fast Home press.

**Executed evidence:** the unchanged ServiceController was compiled against a
small deterministic fake Handler/Activity. Stop followed by cancellation and a
1-second scheduler advance produced **zero stop calls**. Restart advanced through
its first stop, followed by cancellation, produced **one stop and zero starts**.
This proves the controller paths, not Android scheduling or physical LED output.

**Small correction boundary:** explicit Stop must reach the service immediately;
configuration edits should be owned/applied by the running service. Do not let UI
lifetime own an unfinished stop/start sequence.

Related source-confirmed coupling: `MainActivity.kt:2343-2350` synchronizes the
toggle programmatically on resume while its listener at 820-834 remains active.
When that changes the checked state, it invokes user Start/Stop logic. Recreating
the UI around a running capture can therefore request consent or reissue a start
as a side effect of displaying state. Synchronizing a control must not issue a
new lighting command. The unguarded initial Spinner callbacks at 2608-2623 and
2648-2686 also need recreation tests, since they can restart a running effect.

### R4 — P1: capture loss leaves stale output and an active-looking service

**Source:** `tools/ScreenAnalyser.kt:172-174,238-248,284-300`,
`services/LEDService.kt:511-512,1348-1380`,
`animations/AmbientAnimation.kt:73-76`.

MediaProjection's onStop only stops ScreenAnalyzer. It does not notify the
animation/service, clear cached grants, clear the last LED colors or update
visible state. Accessibility capture failures retry with capped delay indefinitely
without exposing a failure reason or invalidating stale output. Meanwhile service
and tile state use `LEDService.isRunning`, set before any valid frame arrives.

The same observability gap exists for hardware: `LedController.kt:38-46,63,217-225`
silently no-ops when the vendor binder is absent and only logs transaction errors.
The service can still appear active with no usable output.

**Small correction boundary:** propagate capture/hardware availability and terminal
failures; distinguish unavailable frames from black content. Define a stale-frame
output policy and require an actionable recovery path. Preserve the existing
exact-black-to-zero behavior while adding failure handling.

### R5 — P1: bottom-source selection is not wired into MediaProjection capture

**Source:** `MainActivity.kt:3185-3194,4150-4156,4173-4176`,
`services/LEDService.kt:1827-1842,1856-1866`,
`tools/ScreenAnalyser.kt:117-123,176-182,253-255`.

On the Android 13 path, `ambientDisplayId` changes metrics but never selects a
source for MediaProjection. The ID is actually passed to the platform only in
AccessibilityService.takeScreenshot. Default Ambient uses MediaProjection, so
selecting Bottom does not implement the promised source switch there.
AmbiAurora also fails to receive a display ID at construction.

Changing the bottom-source checkbox requests projection consent based on the
effect enum rather than the chosen capture backend, including when Ambient is
configured to use Accessibility.

**Small correction boundary:** resolve a supported backend for the requested
source and report an unavailable combination clearly. Keep the Activity's display
independent of the sampled display. Verify with distinct red/blue content on the
two physical screens, from either UI display.

### R6 — P1 if audio modes are retained: playback capture permission is missing

**Source:** `AndroidManifest.xml:5-22`, `tools/AudioAnalyzer.kt:42-65,138-142`,
`animations/AudioReactiveAnimation.kt:88-105`.

The manifest has no RECORD_AUDIO permission and the Activity has no request flow
for it. MediaProjection consent alone does not grant playback capture permission.
AudioRecord construction/start can fail; AudioAnalyzer logs the failure and
returns without informing its caller, leaving the outer animation/service active.

**Small correction boundary:** request only the permissions required for the
chosen mode and propagate startup failure. Do not confuse playback capture with
permission to use a physical microphone; retain the intended playback source.
See [Android playback capture requirements](https://developer.android.com/media/platform/av-capture#building_a_capture_app).

### R7 — P1 if audio modes are retained: failed audio reads can spin at urgent priority

**Source:** `tools/AudioAnalyzer.kt:88-133,145-169`.

The read loop handles only positive results. A repeated error result immediately
retries with no wait or termination, on a THREAD_PRIORITY_URGENT_AUDIO thread.
An exception also exits without resetting running or cleaning up in a finally.
Stop joins a potentially blocked reader before stopping/releasing its recorder.

**Small correction boundary:** end the session on terminal reads; report failure,
release on every exit and unblock capture before joining it. Verify injected read
errors and rapid stop/start before measuring resource use on-device.

### R8 — P2: Ambient deduplication prevents smoothing and live brightness settling

**Source:** `tools/ScreenAnalyser.kt:216-219,325-328`,
`animations/AmbientAnimation.kt:41-43,90-129` and
`animations/AmbiAuroraAnimation.kt:68-73,204-226`.

The analyzer emits a color only when it changes. Ambient advances smoothing only
when it receives that emission. A black-to-solid-red transition at default
response 0.5 advances red from 0 to **128**, then identical red frames are
discarded; it does not converge to 255. A live brightness change on unchanged
content also has no callback to advance it. AmbiAurora's color interpolation has
the same dependency even though it has a separate audio update loop.

**Executed evidence:** the exact production interpolation function was extracted
and compiled in Kotlin; its first step is 128. The missing subsequent steps are
confirmed by the source callback/deduplication flow, not a full Android test.

**Small correction boundary:** retain the latest target and continue rendering
until settled, then sleep; deduplicate final output, not the input required for
convergence. Verify constant-color holds and parameter changes without new frames.

### R9 — P2: saturation boost converts magenta/pink hues incorrectly

**Source:** `tools/ScreenAnalyser.kt:499-516`.

When red is the maximum channel and blue exceeds green, Kotlin remainder yields
a negative hue. The sector selection treats it as the first sector instead of
wrapping it into 0..360. Saturated magenta can become red with boost enabled.

**Executed evidence:** the exact production function and constants were extracted
and compiled in Kotlin with a minimal RGB helper. Input **#FF00FF** at boost
**0.5** produces **#FF0000**. A color already fully saturated should retain its
magenta hue. This check does not test the display/capture color space.

**Small correction boundary:** normalize hue and test red/blue boundary colors,
grayscale, black and already-saturated colors. DuoFrost contains a targeted fix;
this does not require adopting its service or UI.

### R10 — P2: custom sampling is bypassed in single-color mode

**Source:** `tools/ScreenAnalyser.kt:100-108,193-199,306-310`.

Single-color capture is reduced to 1x1 before checking custom sampling. Turning
both options on subsequently runs the weighted sampler over that one pixel; it
cannot reject black bars or choose vivid pixels across a grid as advertised.

**Small correction boundary:** keep a small grid when custom sampling is active
and combine its regions into one output color. Verify a bright subject with black
bars and compare single/dual outputs. DuoFrost contains a targeted sampling fix.

### R11 — P2: Static mode continuously resends settled output

**Source:** `animations/StaticAnimation.kt:48-74`,
`tools/LedController.kt:202-219`, `animations/AmbientAnimation.kt:140-158`.

Static runs on the main Looper every 30 ms, interpolates even after settling and
sends separate left/right commands every time. The driver only suppresses an
identical immediately previous command within 16 ms. Left and right commands
alternate and have different paths/zone masks, so that filter cannot suppress
the steady stream. Nominally this schedules about 33 renders and 67 binder calls
per second before execution overhead; these are code-derived rates, not measured
Thor timings. Actual vendor shell execution and gameplay impact were not measured.

Ambient and most other paired effects also use two transactions despite an
existing `setLedColorDual` function. Current driver crossfade bookkeeping retains
only one RGB triple and can re-emit one color across both selected sticks after
a dual write (`LedController.kt:131-178`). A blind switch to the dual function
therefore still needs per-zone/crossfade verification.

**Small correction boundary:** stop scheduling a settled static effect; wake on
parameters/status changes, batch paired output and compare the final per-zone
frame. Keep all zones and separate left/right colors correct during transitions.

## Compatibility and optional inherited surfaces

### R12 — P2: cached MediaProjection grants are replayed after consumption

**Source:** `services/LEDService.kt:855-883,988-1000,1047-1063`,
`MainActivity.kt:4124-4128`.

Sampler/effect changes and battery override restoration can stop projection and
call getMediaProjection again with lastProjectionData. The Activity also retains
and resends its grant after a stop/start. For applications targeting Android 14+
on Android 14+, replaying the same consent Intent or creating a second virtual
display from one projection violates the single-use session contract.
This app targets SDK 36.

This restriction is **not an explanation of the owner's Android 13 launch
incident**. It is a confirmed compatibility defect in the shipped target range.
Failure can be caught and become silent fallback/failed activation; do not label
every path a fatal crash. See [Android's consent rules](https://developer.android.com/media/grow/media-projection#user_consent).

**Small correction boundary:** retain a valid active session across parameter
edits; consume a grant once and require fresh consent after invalidation. A
single service-owned projection lifetime should also report onStop.

### R13 — P2 if import/plugins remain: archive expansion is unbounded

**Source:** `presets/PresetArchiveTransfer.kt:144-162,191-219`,
`BackupArchiveTransfer.kt:156-178`, `plugins/PluginRepository.kt:130-148`.

ZIP imports copy every expanded entry into byte arrays and retain them all before
validating their manifest. The plugin download's 8 MB compressed size limit and
artwork's individual size checks do not limit expanded ZIP memory. A corrupt or
highly compressed bundle can exhaust the process. Duplicate entries overwrite
earlier entries silently. Preset import also continues parsing and importing
artwork after recording a schema/version error.

**Small correction boundary if retained:** bound total expanded bytes, entry count
and manifest size while reading; reject invalid metadata before side effects.
This is not a claim of arbitrary filesystem write: artwork storage validates
filenames and canonical containment (`presets/PresetVisuals.kt:246-255`).

## Ownership map and review implications

| Invariant | Current owners | Consequence |
|---|---|---|
| Desired configuration | Activity fields, presets/preferences, service fields, app-profile/schedule/external overrides | UI reconstruction or a later command can replace effective state |
| Enabled intent | Mostly in-memory service flag and UI toggle; boot/API preferences influence continuation | UI absence, explicit Stop and process death are conflated |
| Capture lifetime | Activity stores consent; service stores/recreates projection; analyzer owns virtual display/callback | No complete invalidation/status path; stopped grants can be replayed |
| Output | Effect callbacks on main/capture/render threads, driver transaction lock, service cleanup/crossfade | Transaction locking does not define which session may still write |
| Feedback | Running Boolean, generic notification, logs | Active service can mean stalled capture, failed audio or missing hardware |
| Timing | Debounced UI commands, nested service delays, asynchronous capture/audio, periodic task checks | Pause and teardown ordering can lose intent or leave stale work/output |

The engine has reusable pieces: explicit RGB/zone writes, an existing paired
transaction helper, small capture targets, a pure effect enum and several simple
effects. Large files and optional features raise maintenance cost but are not
themselves proof of crashes. This review does not justify a wholesale rewrite or
arbitrary effect removal.

## Device investigation still needed

The owner suspects Pathfinder interaction. Pathfinder documents moving Android
tasks between displays; that is a useful reproduction variable, not proof of
fault. See its [repository](https://github.com/KaitonGxx/thor-pathfinder).

Run on the owner's Android 13 firmware with one LED controller active at a time:

1. Record the exact Bifrost/DuoFrost APK version, firmware build, launch-screen
   preference, capture backend, workaround setting and Pathfinder version/config.
2. Launch on each screen with the welcome alert still pending, then repeat after
   acknowledging it. Compare direct launch with Pathfinder stopped and enabled.
   Test moving an already-open app separately from initial launch.
3. Collect the complete bounded trace around the incident, including AndroidRuntime
   and the crash buffer. A WindowLeaked allocation trace alone is insufficient to
   establish why a process exited. Preserve the release mapping for the exact APK
   before interpreting obfuscated symbols. Bifrost also has a local CrashReporter.
4. Separately test Home/Back, task swipe, memory cleaner, Stop then Home, parameter
   changes then Home, capture revocation, and sleep/wake. Do not combine these into
   a single undiagnosed 'background crash'.
5. Hold red, green, blue, magenta, black and split left/right frames; repeat on the
   selected source screen while the UI is on the other screen. Test a constant
   image while changing brightness/smoothing.
6. Measure CPU/memory, capture frequency, binder/output rate and game frame times
   under a repeatable workload. Host builds and logic checks cannot establish
   battery cost or gaming smoothness.

## Validation record

- Java: Microsoft OpenJDK 17.0.16. Gradle wrapper: 8.13; AGP: 8.13.2;
  Kotlin Android plugin: 2.0.21; compile/target SDK 36; min SDK 33.
- Initial sandboxed Gradle run was blocked by cache permissions. Retried with
  approved cache/network access. Gradle downloaded its distribution and installed
  the missing SDK 36 platform and Build Tools 35 using existing accepted licenses.
- Baseline command (application sources unchanged):

  ```sh
  JAVA_HOME=/Users/mszydlowski/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home \
  ANDROID_HOME=/Users/mszydlowski/Library/Android/sdk \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
  ```

  **Debug APK built. All 60 unit tests passed, zero failures/errors/skips.**
  The combined command exited 1 because **lint failed: 3 errors, 401 warnings**.
  Two errors at `AudioAnalyzer.kt:55` concern the missing RECORD_AUDIO permission
  and runtime permission handling, corroborating R6. The third is
  StartActivityAndCollapseDeprecated at `BifrostTileService.kt:87`; that call is
  already guarded to run below API 34, while the PendingIntent overload is used
  on newer versions. Review/suppress or correct that precise compatibility
  diagnostic; it is not proof of an Android 13 tile crash. No lint baseline was
  added to hide these findings.

  Generated reports: `app/build/reports/tests/testDebugUnitTest/index.html` and
  `app/build/reports/lint-results-debug.html`. APK:
  `app/build/outputs/apk/debug/app-debug.apk` (original Bifrost debug identity).
- Release validation command: `./gradlew :app:assembleRelease --console=plain`
  with the same Java/SDK environment: **passed**, including R8/resource shrinking
  and lintVitalRelease. Output: `app/build/outputs/apk/release/app-release-unsigned.apk`.
  A successful release/vital-lint task does not override the full debug-lint
  failure above. No release signing key or signing configuration was added.
- Isolated source checks: **four checks passed**, using the unchanged
  ServiceController with a deterministic scheduler and exact extracted Kotlin
  math. Temporary harness: `/tmp/runelight-review-checks/run.py`. The harness is
  not an Android emulator or device test and does not change app test sources.
- `adb devices -l`: completed with **no device attached**. No APK was installed;
  no physical LED, display-routing, crash, recovery or performance test ran.
- Existing tests cover manifests, preset migration, external contracts, plugins,
  scheduling and crossfade math. They do not cover the critical service/capture
  lifecycle or physical vendor output paths identified here.

The next decision should use these findings and a bounded device reproduction.
Product scope, redesign, new architecture and feature deletion remain open.
