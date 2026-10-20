# Capture/service reference review — 2026-10-20

There are useful fixes to adapt from DuoFrost, and useful ownership patterns in
ScreenStream and Android's recorder. Use small reviewed changes with regressions;
do not import another application's engine or its configuration surface.

This is source research and a proposed implementation direction. No engine code,
permissions, device settings or dependencies changed. No comparator APK was built
or tested. It does not establish Thor capture or audio reliability.

## Sources and reproducibility

Anonymous public HTTP reads confirmed these branch heads on October 20:

| Reference | Revision inspected | Purpose |
|---|---|---|
| [Tufein/DuoFrost](https://github.com/Tufein/DuoFrost/tree/5bf04b9cd8cbbf462d8c697f431e73b187dce1a1) | `5bf04b9cd8cbbf462d8c697f431e73b187dce1a1`, main, October 19 | Closest fork; compare fixes against R1–R14 |
| [dkrivoruchko/ScreenStream](https://github.com/dkrivoruchko/ScreenStream/tree/3e835c8c3cdf3e60aae1cc0de7d4d5e96003f26a) | `3e835c8c3cdf3e60aae1cc0de7d4d5e96003f26a`, master, October 14 | Foreground capture, session generations, observable state |
| [Android SystemUI recorder](https://android.googlesource.com/platform/frameworks/base/+/android-13.0.0_r75/packages/SystemUI/src/com/android/systemui/screenrecord/) | `android-13.0.0_r75` | Android 13 resource/recorder lifetime; distinguish system privileges |

DuoFrost's head is the same revision used for the earlier bounded comparison;
this pass inspects additional recovery, audio, rendering and permission paths.
Main includes development changes beyond the published 2.5.1 release. Do not
attribute every inspected fix to the installed release.

Sources were read, never executed. Public metadata, selected downloaded files
and revision records are under ignored
`local-test-results/2026-10-20-reference-research/`; DuoFrost's existing pinned
reference was reused. No authenticated GitHub connector, `gh`, fetch/pull/push,
upload or commit was used.

## DuoFrost: adapt these changes

| Candidate | Evidence in the pinned revision | Runelight decision / regression |
|---|---|---|
| Audio reader owns its recorder and cleanup | `tools/AudioCaptureSession.kt`, `tools/AudioAnalyzer.kt`, `AudioCaptureSessionTest` | Adapt for R7: terminate nonpositive reads; stop/release before bounded join; close once; discard old samples. Test failed start, read errors, blocked read, consumer failure and replacement. Propagate errors to sound state; logging alone is insufficient. |
| Capture callbacks belong to a generation | `tools/ScreenAnalyser.kt`: `captureGeneration`, listener detachment, stale screenshot cleanup | Adapt for R4: late callbacks release their own resources without scheduling or changing a replacement session. Test Stop/start and display/source changes during an in-flight screenshot. |
| Correct hue and sampling dimensions | `tools/ScreenSampling.kt`, `ScreenSamplingTest` | Adapt pure math for R8/R9: wrap negative hue, preserve grayscale/black, retain a sampling grid for custom + single-color. Test known colors and invalid input. |
| Desired-running intent survives ordinary destruction | `services/ServiceRecoveryStore.kt`, `ServiceRecoveryPolicy.kt`, policy/codec tests | Adapt a much smaller state model for R2/R3. Persist valid configuration and explicit Stop, never projection grants. Do not bring schedules, scenes, timers, mute or preset fallbacks into recovery. |
| Stop clears output without a delayed race | `services/LightingStopper.kt`; `LEDService.cleanupAndStop()` | Adapt immediate teardown and the bounded all-zone off write when no service remains. Serialize against a replacement Start; an old cleanup must never darken a new session. Test retained hardware output after process loss. |
| Capture loss becomes a visible reconnect state | `LEDService.replaceMediaProjection()` / `awaitProjectionConsent()` | Adapt R4 feedback: invalidate capture, clear output, show an action to reconnect. Do not silently retry a consumed grant or claim capture is active. |
| Paired hardware writes and per-zone remembered colors | `tools/LedController.kt`: `setLedColorDual`; `LedFrameCache.kt`, its tests | Adapt R11 after mapping our driver state. Preserve four zones and distinct L/R colors; suppress unchanged final frames. Pure frame tests plus physical zone checks are required. |
| Dialog destruction is not acknowledgement | `ui/DuoFrostAlertDialog.kt`; `StartupDialogLifecycleTest` | Retain the invariant for any future dialog. Our inline color editor and removal of welcome prompts already avoid the corresponding windows. No need to import the old dialog stack. |

Source links: [audio session](https://github.com/Tufein/DuoFrost/blob/5bf04b9cd8cbbf462d8c697f431e73b187dce1a1/app/src/main/java/io/github/tufein/duofrost/tools/AudioCaptureSession.kt),
[capture analyzer](https://github.com/Tufein/DuoFrost/blob/5bf04b9cd8cbbf462d8c697f431e73b187dce1a1/app/src/main/java/io/github/tufein/duofrost/tools/ScreenAnalyser.kt),
[sampling](https://github.com/Tufein/DuoFrost/blob/5bf04b9cd8cbbf462d8c697f431e73b187dce1a1/app/src/main/java/io/github/tufein/duofrost/tools/ScreenSampling.kt),
[recovery](https://github.com/Tufein/DuoFrost/blob/5bf04b9cd8cbbf462d8c697f431e73b187dce1a1/app/src/main/java/io/github/tufein/duofrost/services/ServiceRecoveryStore.kt),
[Stop](https://github.com/Tufein/DuoFrost/blob/5bf04b9cd8cbbf462d8c697f431e73b187dce1a1/app/src/main/java/io/github/tufein/duofrost/services/LightingStopper.kt),
[service](https://github.com/Tufein/DuoFrost/blob/5bf04b9cd8cbbf462d8c697f431e73b187dce1a1/app/src/main/java/io/github/tufein/duofrost/services/LEDService.kt),
[driver](https://github.com/Tufein/DuoFrost/blob/5bf04b9cd8cbbf462d8c697f431e73b187dce1a1/app/src/main/java/io/github/tufein/duofrost/tools/LedController.kt).

### Gaps that make a bulk merge unsuitable

- **Audio setup remains incomplete in reviewed app source:** the main manifest
  has no `RECORD_AUDIO`, and no declaration/request of that permission was found
  under `app/src/main`. Recorder-session fixes do not supply the required user
  permission. A merged comparator manifest was not built in this review.
- **Display enforcement remains:** `MainActivity.onCreate()` calls
  `maybeRelaunchOnCorrectDisplay()`. That function still starts a NEW_TASK on the
  saved display then calls `finish()`, now with a retry guard and preserved
  extras. That guard is not evidence that R14 is fixed. Keep our Activity's
  existing respect for Android/Pathfinder task placement.
- **Bottom-source projection remains unproven:** `ScreenAnalyzer` passes its
  display ID to Accessibility screenshots, but `createVirtualDisplay()` supplies
  dimensions and AUTO_MIRROR, not a physical-source selection. Changing metrics
  is not proof of capturing the selected panel.
- **Held-frame convergence still needs work:** analyzer callbacks suppress
  unchanged colors, while `AmbientAnimation.updateColors()` advances smoothing
  only when called. Keep the target independently and finish smoothing without
  requiring another changed input frame (R10).
- Audio failure currently goes to logs and zero intensity; our independent sound
  overlay needs a visible inactive/error state and a defined base-effect fallback.
  Source/recorder failure and genuine silence are different observations.
- Recovery references scenes, schedules, presets, timers and widgets. Battery
  exemptions, hiding Recents and repeated recovery mechanisms are not substitutes
  for correct ownership or reasons to retain those features.

Evidence: [main manifest](https://github.com/Tufein/DuoFrost/blob/5bf04b9cd8cbbf462d8c697f431e73b187dce1a1/app/src/main/AndroidManifest.xml),
[MainActivity](https://github.com/Tufein/DuoFrost/blob/5bf04b9cd8cbbf462d8c697f431e73b187dce1a1/app/src/main/java/io/github/tufein/duofrost/MainActivity.kt),
[AmbientAnimation](https://github.com/Tufein/DuoFrost/blob/5bf04b9cd8cbbf462d8c697f431e73b187dce1a1/app/src/main/java/io/github/tufein/duofrost/animations/AmbientAnimation.kt).

## Other implementations: borrow ownership, not a framework

**ScreenStream:** `ProjectionCoordinator` separates foreground promotion from
projection acquisition, tracks pending/active generations, rejects busy starts,
registers callbacks before pipeline startup and returns structured failure states.
Its MJPEG engine serializes events on a HandlerThread and publishes state through
StateFlow. Those boundaries fit Runelight's needs. A small service-owned session
and observable engine state are sufficient; no streaming/network/analytics,
dependency injection framework or multi-module application is needed.
[Projection coordinator](https://github.com/dkrivoruchko/ScreenStream/blob/3e835c8c3cdf3e60aae1cc0de7d4d5e96003f26a/common/src/main/java/info/dvkr/screenstream/common/module/ProjectionCoordinator.kt),
[MJPEG engine](https://github.com/dkrivoruchko/ScreenStream/blob/3e835c8c3cdf3e60aae1cc0de7d4d5e96003f26a/mjpeg/src/main/java/info/dvkr/screenstream/mjpeg/internal/MjpegStreamingService.kt).

Do not copy all compatibility branches: its short projection-acquisition retry is
restricted to pre-Android-14; its optional microphone path is irrelevant to our
playback-only sound. Any retry needs a reproduced Thor failure and cancellation
checks. A pattern is not a blanket certification of the implementation.

**Android 13 SystemUI:** `ScreenInternalAudioRecorder.end()` stops/releases its
recorders before joining the read thread. It supports the ordering needed for R7;
we should also bound the wait. `ScreenMediaRecorder.prepare()` instead creates a
projection through internal binder APIs. SystemUI's privileged capture/bootstrap
is not a permission-free route available to Runelight. We need neither its media
encoding/muxing nor saved recordings; process frame samples/audio levels in memory.
[Audio recorder](https://android.googlesource.com/platform/frameworks/base/+/android-13.0.0_r75/packages/SystemUI/src/com/android/systemui/screenrecord/ScreenInternalAudioRecorder.java),
[screen recorder](https://android.googlesource.com/platform/frameworks/base/+/android-13.0.0_r75/packages/SystemUI/src/com/android/systemui/screenrecord/ScreenMediaRecorder.java).

**Android foreground-service guidance:** initiate from a visible user action,
promote promptly with only the types needed by the active work, then acquire
capture after consent. Keep plain LED operation independent of a capture grant.
The existing `specialUse` declaration needs a description of the retained LED
use case after cleanup. WorkManager periodic work has a 15-minute minimum and
inexact execution; it is unsuitable for an LED/capture loop or a restart watchdog.
[Service launch](https://developer.android.com/develop/background-work/services/fgs/launch),
[types](https://developer.android.com/develop/background-work/services/fgs/service-types),
[WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

## Proposed minimum-setup contract

These are acceptance targets, not claims that the current internal APK meets them.
Choose capture backends internally from proven capabilities; the user chooses
Top/Bottom and sound, not MediaProjection versus Accessibility or performance tiers.

| User action | Setup we should require |
|---|---|
| Install/open; Solid/Rainbow/Breathing/Pulse | No Accessibility, Usage Access, overlay, storage, boot, root, ADB or battery-exemption requirement. Offer notifications in context for background status/Stop. |
| Start Ambient | Explain and request the selected backend's actual requirement on first use. Prefer normal screen-capture consent for a demonstrated supported source. |
| Enable React to sound | Request `RECORD_AUDIO` and valid playback-capture consent; reuse an eligible active session where possible. Do not reset the effect or capture a microphone. |
| Select bottom capture | First prove backend routing on Thor. If only Accessibility works, offer its explained one-time enablement for this capability; do not require it for the rest of the app or silently switch backends. |
| Close or move UI | Continue the authorized session without further setup or a new consent prompt solely due to Activity recreation. |
| Capture stopped/revoked/process lost | Honest reconnect state and a direct action for fresh consent. No invisible re-consent or repeated failing restarts. |
| OEM battery interference is demonstrated | One contextual troubleshooting action to the relevant setting, only if needed. Do not begin with a compulsory checklist of exemptions. |

Android requires `RECORD_AUDIO`, approved MediaProjection consent and compatible
source-app policy for playback capture. Some apps prohibit capture; zero samples
alone cannot distinguish unsupported playback from actual silence. Keep the base
effect usable and avoid claiming that no sound means a recorder failure.
[Playback capture](https://developer.android.com/media/platform/av-capture).

Projection consent is not a durable preference. Android 14+ enforces single-use
grant/virtual-display rules; keep a valid session alive across UI/parameter changes
and resize its existing output where supported. After invalidation, request fresh
consent. This is separate from the owner's Android 13 screen-move failure.
[Projection lifecycle](https://developer.android.com/media/grow/media-projection).

On Android 13, denied notification permission need not prevent starting a
foreground service, but its notification must still be supplied. Denial removes
normal notification-drawer controls; explain that consequence without blocking
plain lighting. [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

Android 13's Active apps Stop can kill the process without a callback; inspect
`ApplicationExitInfo.REASON_USER_REQUESTED` on return before deciding whether to
restore. Treat that separately from task swipe, OS reclaim and Settings Force
Stop. Do not promise an unkillable app or hardware-off delivery after an abrupt
kill when the vendor LEDs may retain their last values.
[User stopping](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping).
Battery exemption is partial, not a general survival guarantee.
[Doze behavior](https://developer.android.com/training/monitoring-device-state/doze-standby).

## Ownership and next bounded work

| Invariant | Implementation target |
|---|---|
| State | One service owns runtime resources/state; persist configuration and wanted-running separately. Activity owns view/editor drafts only. |
| Feedback | Stopped, Starting, Running, Needs consent and Failed reflect resource outcomes. Sound failure is separate from the base effect. |
| Blast radius | Trace shared driver/capture types and retire old receivers, entry points, migrations and permissions deliberately. No reintroduction of deferred automation through copied recovery code. |
| Timing | Serialize Start/Stop/replace; invalidate generations before teardown; release/unblock before bounded waits; no stale output after Stop or newer Start. |

Implementation order:

1. Complete the keep/remove/freeze inventory; trace shared types before retiring
   background hooks and permissions. Keep useful capture/math references outside
   compiled source sets when frozen. No bulk service merge.
2. Stabilize plain effects: explicit Stop, minimal persisted intent, observable
   service state, serialized driver, all-zone off and idle Solid. Add regressions
   for delayed cleanup versus a newer Start and no-service Stop after process loss.
3. Run a focused Thor capability experiment before final capture backend choice:
   distinct top/bottom test frames, UI on either panel, first use with minimal
   grants, then sound alongside capture. Record required setup and actual output.
4. Adapt sampling/capture-session fixes and implement the sound overlay. Test
   revocation, failed reads, late callbacks, constant-frame convergence and effect
   changes without capture restarts. Keep unsupported combinations explicit.
5. Validate fresh-install setup, notification denial, Home/Back/task swipe,
   Pathfinder, each panel off, sleep/wake, process loss, Active apps Stop and Force
   Stop separately. Compare normal battery settings before any exemption. Finish
   long game sessions/performance checks in the POC plan.

No comparator tests were run in this research pass. Inspected test names include
`failedReadsDoNotSpinAndAlwaysReleaseTheRecorder`,
`stopUnblocksAReadBeforeJoiningAndDropsItsLateSample`,
`aReaderFinishingAfterItsStopCannotAffectTheReplacement`,
`customSingleColorStillSamplesAGrid`, and
`explicitStopBlocksStickyRestoreIncludingScheduledEffects`.
These are regression ideas to adapt to retained behavior, not evidence that our
engine has them or that a comparator works on the owner's device.

DuoFrost is GPLv3, ScreenStream's inspected root license is MIT and the inspected
AOSP files carry Apache 2.0 headers. Preserve authorship and applicable notices
with any copied implementation. This pass copied no comparator code into the app.
