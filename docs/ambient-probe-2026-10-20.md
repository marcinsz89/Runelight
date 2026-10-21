# Ambient capability experiment — 2026-10-20

Status: debug-only investigation; production Ambient and sound remain disabled.
No LED output was issued by the probe. Raw logs and aggregate JSON evidence stay
under ignored `local-test-results/2026-10-20-ambient-probe/`; no recordings or
diagnostics were uploaded. The source baseline is commit `53b8cbd` plus the
uncommitted debug probe and planning notes.

Device: AYN Thor Max, Android 13/API 33, firmware
`Thor_V1.0.0.377_20260206_165408_user`. Runtime logical IDs in this session:
top 0 (1920×1080), bottom 4 (1240×1080), density 369 dpi. Discover IDs at runtime;
these values are evidence, not product constants.

## Demonstrated capabilities

| Backend / source | Controls | Result and limits |
|---|---|---|
| MediaProjection / top | Bottom | PASS: independently changing split-color fixtures proved top capture; changing the bottom fixture did not change sampled colors. |
| MediaProjection / bottom | — | NOT PROVEN. Passing a display ID only supplies requested dimensions in this probe; it does not select the projection source. The attempted replacement stalled and is not bottom-routing evidence. |
| Accessibility / top | Bottom | PASS: red/blue aggregates approximately `[255,0,0]` / `[1,1,254]`. Requires explicitly enabling the debug Accessibility service. |
| Accessibility / bottom | Top | PASS: green/magenta, then yellow/blue aggregates followed live changes. Home preserved capture and generation; explicit Stop passed. |
| Accessibility / bottom, 500 ms requests | Top | Approximately 1.9 samples/s in a short run. |
| Accessibility / bottom, 250 ms requests | Top | FAIL: error 3 (screenshot interval too short) after one sample. |
| Accessibility / bottom, 350 ms requests | Top | PASS in a short bounded run: 16 samples, approximately 370 ms latest interval / 2.83 samples/s. This is not a sustained performance guarantee. |
| MediaProjection video + playback recorder | Bottom | PASS with a separate-UID known capturable tone: correct top split colors, quiet/loud/silent audio phases. Production LED sound reaction is not yet integrated. |
| Accessibility / bottom + playback recorder | Top | PASS in bounded runs at 500 ms screenshot requests; bottom color changes and nonzero audio coexist. Approximately 1.87–1.9 screenshot samples/s. |
| Sound off during projection | Bottom | PASS: same session generation; subsequent yellow/cyan fixture changes still arrived. |
| Sound off during Accessibility / bottom | Top | PASS on repeat without UI automation: same generation, new yellow/cyan bottom colors sampled after audio stopped. |
| Home/reopen and ADB UI display moves with audio | Both | PASS in the projection probe: same generation and later changed source colors plus nonzero audio. This does not establish Pathfinder shortcut behavior. |
| Disable debug Accessibility in Settings | Top | PASS: screenshot session reports disconnected, releases projection and stops. No fallback or automatic restart. |
| Cancel projection consent, then fresh consent | Bottom | PASS: cancel creates no session; a subsequent explicit grant starts valid top capture; Stop releases it. |
| Explicit Stop after combined session | Bottom | PASS: aggregate state stopped and Android reported no active MediaProjection. |

An initial metrics bug requested a lower-screen aspect ratio for top projection,
letterboxing the image and reducing full red/blue means to about 166. Explicit
display WindowContext maximum metrics corrected the request to 64×36 for top
and 64×55 for bottom. The final projection sampled `[255,0,0]` / `[1,1,255]`,
then yellow/cyan correctly. Requested dimensions alone never prove the source.
Early gray-fixture results were invalid because of a debug fixture bug; the
fixture was corrected and a launch/recreation pixel regression was added.

Projection delivery is content-dependent. The cumulative `measured_fps` field
from static patterns does not measure maximum throughput or a product target.
The final combined session's first sample arrived after 219 ms; this is one
startup observation, not a latency guarantee.

## Framework restart during live projection replacement

Starting a new consented projection while a prior projection was active was
followed by a stalled startup and an Android system-server watchdog at 17:57:21.
The owner independently confirmed an unrequested interface crash/restart.
Kernel uptime continued, distinguishing this from a full kernel reboot.

The local watchdog report shows `android.anim` waiting on DisplayManager's
`SyncRoot` while handling display removal, with other system threads blocked.
The report truncates before relevant lock-owner stacks. It does not establish
the complete deadlock cycle or assign the underlying vendor/framework cause.
Do not claim the original Pathfinder incident has been solved by this probe.

Do not repeat overlapping projection replacement as a routine test. Serialize
session ownership, preserve valid sessions during parameter/UI updates, reject
late callbacks, and verify complete Stop before starting a fresh session. Respect
platform invalidation and fresh-consent requirements rather than persisting grants.

## Playback audio — separate-source follow-up

The final shared session returned 1,291 recorder reads alongside video. All
observed RMS/peak values were zero. The fixture used a bounded 440 Hz playback
tone with alternating amplitude, `USAGE_GAME` and capture allowed. Media was
initially muted; a low-volume 2/15 test also returned zeros while Android reported
the fixture AudioTrack started. The original muted volume was restored.

The follow-up isolated playback in the test APK's separate Android UID. Its
standalone Activity uses Android/Java only: a first Kotlin version failed because
standalone test-APK launches do not load the target app's Kotlin runtime. That
invalid run captured launcher audio and is not counted as controlled-tone proof.

The corrected fixture explicitly allows playback capture in its test manifest,
AudioManager and `USAGE_GAME` AudioAttributes. Its four-second loop contains a
quiet 440 Hz tone, a three-times-louder tone and two seconds of silence. Playback
is bounded to at most 120 seconds and stops on Activity destruction. The debug
recorder optionally filters by the fixture's runtime UID so launcher music cannot
contaminate the experiment. Do not hardcode that diagnostic UID into the product.

Controlled top video + audio passed: red/blue means `[255,0,0]` / `[1,1,255]`,
quiet RMS approximately 0.0474, loud RMS approximately 0.1427 and exact-zero silent
phases across multiple cycles. The same session survived Home/reopen and ADB root
task moves between displays. Subsequent green/magenta source changes arrived
alongside nonzero audio. These moves do not test Pathfinder's actual shortcut.

Bottom Accessibility + audio also passed at 500 ms requests. Green/magenta then
yellow/cyan changes followed bottom content while the separate tone remained
capturable. A repeated session preserved its generation after sound-off and
sampled new yellow/cyan content. Disabling the debug Accessibility service through
Settings then produced `debug_accessibility_disconnected` and released projection.
Cancelling a fresh projection prompt created no new session; a subsequent fresh
grant recovered correct top capture, followed by verified explicit Stop.

This proves a usable playback signal and coexistence on this Thor; it does not
explain the earlier same-app zeros or prove capture from every game. A
representative game, opt-out/silence handling, sustained performance and actual
LED sound modulation remain to test. Never fall back to the microphone.
Android documents the permission and source-policy requirements in its
[playback capture guide](https://developer.android.com/media/platform/av-capture).

### Test-tool interference and diagnostic cleanup

The first combined run ended after a UI hierarchy dump, with the enabled debug
Accessibility service temporarily disconnected. The observed timing and a clean
repeat without dumps support test-tool interference: ordinary UiAutomation
[suppresses Accessibility services by default](https://developer.android.com/reference/android/app/UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).
That run does not establish an OEM background-service failure. Use passive ADB
aggregates/screenshots during Accessibility capture, or a scoped instrumentation
client with `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`. The local QA helper now
refuses a hierarchy dump while its report indicates active screenshot capture.

An inactive `audio_off` command had also overwritten the terminal report with
an empty service's status. Debug Stop/audio-off now leave terminal evidence intact
when no session is active. An on-device regression verified byte-identical
terminal JSON after both commands, no running probe service and no projection.

## AmbiLuma live comparison

The owner installed and started AmbiLuma during this investigation. A read-only
ADB snapshot found package `com.bitmonkeygames.ambiodin`, version 5.3.4 (237),
target SDK 36. Android reported it as the active MediaProjection owner.

- `.AmbiOdinService` and `.InProgressRecordingNotificationService` were both
  foreground services. The controller had a UI binding; the recording service
  was started with a recording action.
- Its virtual display named `AmbiLuma` requested 1088×720, with
  `mDisplayIdToMirror=0` and WindowManager mirroring enabled. That supports the
  standard top-screen projection approach; it is not proof of bottom capture.
- It held a partial wake lock named `AmbiOdin::BackgroundWakeLock`.
- Its declared permissions did not include `RECORD_AUDIO`, and Shizuku permission
  was not granted. These observations do not establish its sound implementation
  or show which features were active.

No AmbiLuma settings were changed, APK extracted, or implementation copied.
Service existence and virtual-display configuration do not establish correct LED
colors, effective sample rate, idle efficiency, or stability. Its advertised
60 fps is not a measured comparison result.

## Direction and remaining gates

Prefer MediaProjection for the common case: controls below, top game as source.
Keep bottom Ambient optional through explicit Accessibility setup, with a slower
rate and honest availability. The user chooses Top/Bottom; backend details stay
internal. Keep sound as an independent intensity overlay and share a valid
projection session where applicable. Standard effects require neither capture
nor Accessibility setup.

Controlled nonzero playback capture and sharing with bottom screenshots are now
demonstrated. Before production integration, serialize ownership and establish
plain-effect/output invariants from the POC plan. Before reliability acceptance: actual LED mapping,
exact black, smoothing/held targets, representative games, Pathfinder's actual
shortcut, consent revoke/denial, individual panel off, sleep/wake, process loss,
and the planned long-run CPU/memory/frame-pacing comparison remain unpassed.

## Build and regression verification

- Java 17; `./scripts/dev.sh build :app:assembleDebugAndroidTest --offline` passed.
- Installed debug APK SHA-256:
  `94e8009316fde201bbf068b775deb516a87771f1c94bfd9d23837e3efd2e70f5`.
- Final on-device instrumentation rerun: **3 tests passed** (two native UI
  recreation tests and the split-fixture launch/recreation pixel test). An earlier
  run was interrupted by opening Settings and was discarded, not counted as a pass.
- `./scripts/dev.sh release --offline` passed, including shrinking/vital lint.
  Release manifest, R8 mapping, DEX descriptors and named resources contain no
  debug probe components. Release APK SHA-256:
  `09fbfecbf864bb8b55214fbaffbdaca3e9a894b78a8e4b4a54342689a47bee34`.
- Full debug lint was not rerun; inherited failures remain documented separately.
  Existing JVM tests were not rerun for this debug-source-only experiment.
- Probe capture is stopped. The enabled Accessibility list again contains only
  the original Pathfinder and Odin monitor services. AmbiLuma was left running
  as requested by the owner for comparison.

### Follow-up build and final device state

- Java 17; `./scripts/dev.sh build :app:assembleDebugAndroidTest --offline` passed
  after the debug control/UID-filter changes. No dependency or production-source
  change was needed for this follow-up.
- Installed debug APK SHA-256:
  `27ebcf1c63b3d9bd21a5f202aab7c93c81f7e58b8973f4c6aaa35135e8039136`.
- Test APK SHA-256:
  `7236aae2a711068bd62c5373d8dc5e28ac109488e4458da8bd8f6940fef474fb`.
  The separate tone Activity ships only in this test APK, not the app APK.
- Device checks above passed on their specified builds; the earlier 10-test
  UI/animation instrumentation run and 65 JVM passes remain recorded in the
  [native increment](native-ui-increment.md). They were not rerun for these
  debug/test-only edits. Full lint/release were not rerun in this follow-up.
- Capture and tone are stopped; Android reports no active projection or probe
  service. Only the original Pathfinder/Odin Accessibility services remain
  enabled. Media volume was restored to its original zero. Saved owner controls
  remain Pulse, left/right 74/31, speed 46; normal Runelight is open below.
- A retained Git stash checkpoint preserves the work. Files were reapplied for
  continued development. Nothing was staged, committed or pushed by this work.
