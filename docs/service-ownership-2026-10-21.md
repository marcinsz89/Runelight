# Service ownership and plain-effect handoff — 2026-10-21

Scope: a bounded ownership increment before production Ambient/audio integration.
The source remains based on `53b8cbd` plus the uncommitted UI/output/probe work.
The [cleanup inventory](cleanup-inventory.md) records the broader retirement work;
it is not yet complete. No commit, push or upload was performed.

Follow-up: the [state/policy increment](service-state-2026-10-21.md) completes
durable intent, observed runtime and removal of active legacy service policies.
The pending statements below describe this earlier output/handoff increment.

## State, feedback, blast radius and timing

- Active effect/resources remain owned by `LEDService` on the main looper. UI
  configuration remains in `runelight_controls`; no capture grants are persisted.
  Durable wanted-running intent and a structured runtime model are still pending.
- Feedback: observed effect/running state, actual driver frame submissions through
  a recording Binder, handler queue state and real Thor lifecycle tests. Binder
  submission is not a physical hardware acknowledgement.
- Shared driver shutdown affects every old animation reference. Retained plain
  effects use main-loop callbacks; unsupported legacy capture/policy branches
  remain compiled and must be retired/replaced before production capture.
- Stop cancels pending service transitions, clears and closes output before slower
  teardown, clears projection/grant references, and ends the foreground service.
  No delayed shutdown can darken or stop a newer Start. A closed driver rejects
  late color/level/fade/cleanup calls. A newer full Start gets a fresh driver if
  Android delivers it before destroying the stopping service instance.

## Implemented behavior

- Removed the task-existence shutdown check and the `onTaskRemoved` stop policy.
  Lighting continuation no longer depends on boot/API/background-run switches.
  The old extra constant remains only for compiled legacy callers. The legacy
  profile/lease timer remains explicitly separate retirement work.
- Replaced posted teardown plus a 120 ms delayed shutdown with immediate teardown.
  `onDestroy` tears down ownership before unregistering other callbacks. Stop uses
  the current Android service start ID so older stop work cannot stop a newer
  delivered request. `START_NOT_STICKY` remains intentional; process recovery is
  not implemented by this increment.
- Solid now becomes idle after convergence instead of ticking forever behind
  driver deduplication. Color/level edits wake it. Low-value integer channels make
  progress to their exact targets, including black; unused alpha is ignored.
- A plain-effect change previously called `stop()`, which wrote black, then waited
  100 ms to start the next effect. Retained effects now provide `stopForHandoff()`:
  it cancels callbacks without clearing the current frame. The service starts the
  replacement immediately, holding output until its first frame. Pulse begins in
  its lit phase; its later dark phases remain part of the effect. Explicit Stop
  still clears all zones. No new crossfade subsystem was introduced.

## Verification

The initial ownership debug build (`3c9140ed…`) passed four output tests and three
separately completed service/native tests: rapid Stop/Start and late update,
removing the actual UI task without legacy background flags, and native mode
selection/Stop. An initial combined run stalled between ActivityScenario launches
while Cocoon launched its external-display home; it was interrupted and is not
counted as a completed pass. The individual runs completed normally.

After the no-blackout handoff and early driver-close changes:

- Java 17 `./scripts/dev.sh build :app:assembleDebugAndroidTest --offline`: PASS.
- Five `AnimationOutputTest` cases: PASS. The handoff case covers all 16 outgoing/
  incoming plain-effect combinations, no inserted black frame, and explicit off.
  Other cases cover timer cancellation, Solid idle/re-edit/low-value convergence,
  four-zone independent levels and rejection of a stopped driver's late writes.
- `LightingServiceLifecycleTest#rapidStopStartAndLateParameterUpdatesRespectTheLastCommand`:
  PASS in its own bounded instrumentation process.
- `RunelightActivityTest#runningModeChangesReachTheRequestedAnimationAndStop`:
  PASS in its own bounded instrumentation process.
- JVM suite: 65 tests, zero failures/errors. Full debug lint remains FAIL with
  the same two inherited errors: `AudioAnalyzer` runtime `MissingPermission` and
  `BifrostTileService` `StartActivityAndCollapseDeprecated`. No baseline/suppression
  was added. A release/vital-lint pass does not resolve these full-lint errors.
- Installed final debug APK SHA-256:
  `bdf8d3aabf2a3caf13ed0efbca02fe675f0910ccad7b9d5f57da213266b119b9`.

- `./scripts/dev.sh release --offline`: PASS, including R8/resource shrinking
  and vital lint. The merged release manifest and R8 mapping contain none of the
  debug probe or separate tone fixture components. Release APK SHA-256:
  `3d101679d2dee48c9f42de0ad300f02044e5f8e869fb5a725345806952724a7c`.
- Final test APK SHA-256:
  `cb483c97979a7491b7093d9fceb83b6e984f08502ef4a86ddb6ceb580d52f36d`.

On 2026-10-21 the owner confirmed on the physical LEDs that the forced off/on
pause is gone when switching between Solid, Rainbow, Breathing and Pulse.
This scoped visual pass does not certify every hardware failure or sustained
gameplay performance.
The owner's current controls were restored after instrumentation and the updated
debug UI opened below. No capture or Accessibility configuration was changed.

## Home / sleep / wake investigation

Owner report: Home on the lower display, then lock/unlock, unexpectedly reveals
Runelight again. This reproduced with lighting stopped, no probe/projection and
only the original Pathfinder/Odin Accessibility services enabled.

| Controlled case | After Home | After sleep/wake |
|---|---|---|
| Runelight, lower screen | Cocoon external-display home visible | Existing Runelight task visible again |
| Android Settings, lower screen | Cocoon external-display home visible | Existing Settings task visible again |
| Installed Bifrost debug baseline, lower screen | Cocoon external-display home visible | Existing Bifrost MainActivity task visible again |
| Same Runelight build, top screen | Home visible | Home remains visible; Runelight stays minimized |

The installed Bifrost debug baseline initially had its default top-launch
preference and redirected/finished the requested lower launch. For a valid lower
comparison, only its debug `thor_bottom_screen` preference was temporarily enabled
and auto-start disabled. MainActivity was verified visible below before Home.
Its original preference file was restored byte-for-byte afterward and the debug
baseline stopped. This is not a claim about another untested release/configuration.

The captured Runelight sequence has no new Runelight `START` after wake. It shows
Android hiding the secondary Cocoon home task when adding its primary home task.
The measurements establish a shared lower-display/focus restoration symptom;
they do not identify the complete launcher/vendor/Pathfinder mechanism or prove
that every earlier Bifrost setup behaved the same. The unlock issue remains OPEN.
No speculative Activity finish/relaunch/lock-screen workaround was added.

Owner follow-up on 2026-10-21: other apps also change visibility on wake. Some
reappear on the lower screen; others do the opposite, depending on what was open
before Home and lock. This corroborates a shared task/display restoration symptom
and suggests prior task history is relevant. It does not isolate the responsible
launcher, firmware or Pathfinder component. Keep this as an unresolved device
integration investigation; do not add a Runelight-specific finish/relaunch workaround
on this evidence alone. A later controlled comparison should vary the previous
foreground app and display, and record task order before Home, after Home and
after wake alongside whether a new Activity start actually occurred.

Local evidence, original settings, aggregate task snapshots and test transcripts
remain in ignored `local-test-results/2026-10-20-ambient-probe/` and
`local-test-results/2026-10-21-ownership/`. The sleep/wake runs used ADB Sleep/Wake
and dismissal of the existing non-secure keyguard; a physical power-button cycle
is a separate comparison if its behavior differs.

## Ambient sampling and the inherited capture switch

The owner requested Bifrost-like Custom Sampling as a center HomeKit-style tile.
The saved design now calls this **Whole screen**: enabled samples throughout both
image halves; disabled samples edge regions. Both retain Left/Right output levels
and change sampling inside the existing capture session. This is planned for the
new Ambient pipeline, not an enabled control in the current native build.

In inherited Bifrost, `ambilight_use_media_projection=true` supplies MediaProjection
to `ScreenAnalyzer`; false supplies no projection and uses Accessibility
`takeScreenshot(displayId, …)`. Disabling it changes the mechanism and permission
requirements; it does not stop reading the screen. Neither path here saves video.
Playback audio separately requires valid projection consent in the tested design.

Bifrost's default capture size is 2×1. Custom Sampling uses a 32-pixel-wide grid
and selects/weights brighter, more saturated/colorful pixels (default top 30% by
weight, not the spatial top of the screen). The old default is not a defined edge
strip algorithm. Review this intent and math rather than copying its known bugs.
