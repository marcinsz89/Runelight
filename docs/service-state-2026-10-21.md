# Service state and retirement of runtime policies — 2026-10-21

This follows the [output/handoff increment](service-ownership-2026-10-21.md).
It completes the next plain-effect ownership slice before production Ambient.
It does not enable Ambient/audio or finish the full source/resource cleanup.

Follow-up: the [UI polish](ui-polish-2026-10-21.md) keeps acknowledged Running
state through a plain-effect handoff while still awaiting the replacement frame.
Cold starts, Stop, failure and stale-token rules retain their earlier semantics.

## Ownership and timing

- Configuration remains in `runelight_controls`. `LightingIntentStore` persists
  only `wantedRunning` in a separate `runelight_runtime` file. No capture grant,
  recorder, display, external lease or other resource is saved.
- `LEDService` owns a main-looper `LightingRuntime` and publishes one lifecycle
  observed `LightingState`: Stopped, Starting, Running or Failed, with the effect
  and failure reason. The native UI no longer polls every 300 ms. The compatibility
  getters derive from this state rather than maintaining independent booleans.
- Start requires the explicit native action and a supported plain effect. Runtime
  starts at Starting; Running requires an accepted vendor Binder frame submission
  (including an unchanged previously submitted frame). This is not a physical LED
  acknowledgement. Missing/dead Binder or a rejected/throwing transaction ends the
  session with actionable output failure. Startup has a bounded five-second wait.
- Each Start/replacement and teardown advances a generation. Output callbacks,
  Binder death and startup timeout cannot change a newer or stopped session.
  Plain handoff still holds output while replacing effect timers. Stop invalidates
  callbacks, clears/closes the driver, stops the effect and removes foreground state.
- Explicit Stop clears wanted intent even without a live service, and attempts
  black without starting an effect. Unexpected destruction retains intent but
  releases resources. UI recreation/opening, null starts and parameter updates
  cannot resume lighting. `START_NOT_STICKY` remains intentional. After interruption
  the UI offers a manual Start; automatic process/Force Stop recovery is not added.

## Removed active behavior

`LEDService` no longer implements app profiles, foreground-app polling, charging/
low-battery overrides, adaptive screen brightness, plugin live policy, external
leases/crossfades, PIPBOY wake locks or the inherited capture/grant replay path.
The four retained animation implementations and serialized driver are reused.
Unsupported effects and obsolete commands cannot replace the active effect.
Old public intent constants remain only to compile legacy callers pending deletion.

Removed manifest registrations: BootReceiver, ScheduleReceiver, ExternalApiReceiver
and PackageRemovedReceiver. Removed permissions: RECEIVE_BOOT_COMPLETED, WAKE_LOCK,
PACKAGE_USAGE_STATS and the custom CONTROL_LEDS permission/use. Application startup
now cancels the old app-owned schedule PendingIntent instead of running preset
migration. Existing preferences/user files are preserved.

The Quick Settings tile now opens Runelight's native controls, uses its name/mark
and reflects service state. It cannot start an inherited preset or launch the old
MainActivity. Actual panel interaction remains unverified; the Android 13 collapse
overload still produces its known full-lint error.

Legacy source files, activities, wallpaper and Accessibility components still
compile/remain declared where noted in the [inventory](cleanup-inventory.md).
They have not been frozen. Source/resource/dependency deletion remains a separate
cleanup exit; this increment makes no claim that the APK is fully stripped.

## Verification

Java 17, offline dependencies:

- `./scripts/dev.sh unit --tests 'com.moonbench.bifrost.services.LightingRuntimeTest' --offline`:
  PASS, four state regressions for first-frame acknowledgement, replacement, Stop
  and failure/retry tokens.
- `./scripts/dev.sh unit :app:assembleDebug :app:assembleDebugAndroidTest --offline`:
  PASS. All 69 JVM tests passed, zero failures/errors.
- Thor instrumentation, separate bounded processes: six `AnimationOutputTest`
  cases passed, including rejected submission feedback, deduplication and fencing
  of closed drivers. Five `LightingServiceLifecycleTest` cases passed: rapid
  Stop/Start and late update, UI task removal, Stop without a live effect, rejection
  of old policy/command starts, and destruction/recreation without automatic resume.
  The native four-mode selection/Stop case also passed.
- One initial recreation test timed out while both physical panels were asleep;
  it is not counted as a pass. The complete suite passed with Wake/dismiss of the
  nonsecure keyguard before each case. No screen timeout or global settings were
  changed. Sleep/wake acceptance is still separate from these awake lifecycle tests.
- `./scripts/dev.sh release :app:lintDebug --offline`: release assembly, R8,
  resource shrinking and vital lint completed. The combined command exits FAIL
  on full debug lint: the same two inherited errors, `AudioAnalyzer`
  MissingPermission and `BifrostTileService` StartActivityAndCollapseDeprecated.
  No lint baseline or new suppression was added.
- Merged release manifest inspection confirms the four retired receivers and
  their removed permissions are absent. `git diff --check`: PASS.

Tested APK SHA-256:

| Artifact | SHA-256 |
|---|---|
| Installed debug | `51062b66b7c44df6b64de743118377aea24997af0b6b775ed946b30e205da577` |
| Device tests | `ab45e486e4222bcdeadbfc1d169296b2cb52ddf1a2f2b005aad0b2682c8f7ca7` |
| Release unsigned | `a56127ee22c4c16db38a33408abf841734a377df9142aa12b8f3a64782a6656b` |

Local logs, preserved owner controls, exact APKs and release mapping are under
ignored `local-test-results/2026-10-21-state/`. Nothing was uploaded or committed.

Not established by these tests: a real Binder-process death, physical delivery of
black after abrupt process loss, disk-write failure recovery, OEM cleaner/Active
apps Stop/Force Stop behavior, actual Quick Settings interaction, Pathfinder moves,
production capture/audio, Android 14+ or sustained gameplay performance. The shared
lower-screen Home/sleep/wake restoration issue remains open in the earlier report.

## Next implementation slice

Top-source Ambient with controls below, an aspect-correct sample grid, left/right
targets and independent levels, held-target smoothing, exact black and the center
Whole screen toggle. Parameter/sampling edits must keep the capture session.
Use the proven capture path; first-frame, loss/reconnect and Stop must feed the
service state model. Bottom source and sound follow their demonstrated capabilities.
