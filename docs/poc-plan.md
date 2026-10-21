# Runelight POC — implementation plan draft

Saved 2026-10-20. The first native UI slice is now implemented. This is the working
plan for the discussed POC; open choices below remain proposals. The source
baseline is Bifrost 1.3.1, `1baddf1644ff0d7edd1bd0f4ba02f7eb6c8e3cfa`.

## Product direction

Latest control refinement: the native configuration uses two rounded Left/Right
color tiles with independent vertical brightness adjustment, replacing the
global slider and scrolling controls. Tapping opens an inline color wheel with
live draft preview and Copy-other-side/Cancel/Apply. See the current
[design behavior](design/README.md); historical mockups retain earlier layouts.

A reliable, low-overhead Thor Max LED utility with a simple interface that fits
the lower display well, also works on top, and survives moving/recreating its UI.
The owner selected **Quick tiles**, then requested a distinct Runelight identity
with a light-related rune. See [the saved design](design/README.md).

POC scope discussed:

- Ambient, Solid, Rainbow, Breathing and Pulse.
- Ambient sampling: a center HomeKit-style **Sampling** tile cycling **Edges**,
  **Screen halves**, **Full screen**. Edges processes each outer 12.5% band;
  halves processes each side separately; full screen shares one representative
  whole-picture color. Favor colorful pixels and lift brightness/saturation while
  preserving true black. Preserve independent Left/Right levels without restarting capture.
- Independent **React to sound** on Ambient, Solid and Rainbow. Breathing/Pulse
  pause it and dim the tile while retaining the saved On preference. Returning to
  supported modes restores reaction with valid consent. The effect supplies
  color/animation; sound modulates intensity. A sensitivity control remains planned.
- Direct effect/brightness controls, relevant per-effect controls, explicit
  capture source, dark/light appearance, last-used settings and Start/Stop.
- Service continuation after normal UI dismissal, honest capture/audio/hardware
  status and a foreground notification with Stop.
- A Notification Center button shortcut for quick access to Runelight.
- Top and bottom capture only through a backend that demonstrably supports the
  selected source; a clear unavailable state instead of pretending it works.

Deferred from the POC: preset libraries, app profiles, schedules, plugins,
external API, wallpapers, artwork, import/export workflows, boot auto-start,
battery/temperature effects and unsafe performance profiles. Add the Notification
Center shortcut after core controls stabilize; resolve the existing Quick Settings
lint issue if that component is reused.

Optional later convenience: a compact home-screen widget with Lighting On/Off
and the current effect. A visible widget is not a keep-alive mechanism; engine
operation must remain independent of widgets, notifications and the Activity.
This is a brainstorming item, not an added POC requirement.

## Minimum setup and reference research

The [capture/service comparison](reviews/2026-10-20-capture-service-references.md)
identifies specific DuoFrost fixes to adapt and ScreenStream/AOSP ownership
patterns. Use reviewed components with focused regressions, not a bulk merge of
another app's service or configuration features.

- Plain effects should work without special-settings setup. Ask for notifications
  in context; denial must not be an artificial lighting prerequisite on Android 13.
- Request capture/audio capabilities only when enabling the relevant feature.
  Select the backend internally from verified source support; keep Top/Bottom as
  the user choice. No microphone fallback or compulsory Accessibility onboarding.
- Prove top/bottom routing and capture + audio coexistence on the Thor before
  finalizing the backend. Bottom may need an explicitly enabled Accessibility
  path; do not promise permission-free support before that experiment.
- Keep valid capture independent of UI moves and slider edits. Require fresh
  consent after invalidation where Android requires it; no permission bypasses.
- Test with normal battery settings first. Add contextual battery guidance only
  for demonstrated OEM interference. Treat task swipe, process reclaim, Android
  Active apps Stop and Settings Force Stop as different cases.

The UI has reached the point where the keep/remove/freeze inventory can begin.
Run the small capture-capability experiment below before committing to a backend;
it need not wait for the whole cleanup or a service rewrite. Then stabilize
plain-effect lifetime/output before integrating Ambient/audio. The full cleanup
exit remains required before POC completion.

## Next work — prove capture, then implement Ambient

The [debug capture experiment](ambient-probe-2026-10-20.md) now proves top
MediaProjection, explicit top/bottom Accessibility sampling, corrected sizing,
sound-off continuity and clean Stop. Follow-up tests now prove quiet/loud/silent
playback from a separate test-app UID, coexistence with bottom screenshots,
Home/reopen and ADB UI movement with a retained session, screenshot-permission
loss, cancelled consent and fresh-consent recovery. Bottom projection remains
unproven. An overlapping projection replacement was followed by a framework
watchdog restart. The bounded ownership/output work below now passes; proceed to
the top Ambient slice and validate a representative
game and actual Pathfinder shortcut before reliability acceptance. Do not repeat
the broad research pass or enable production Ambient from service startup alone.

Planning update after native UI baseline commit `53b8cbd`. We know the intended
pipeline and failure rules; not all required capture/audio combinations have
been demonstrated on the owner's Thor. Further broad repository research is not a
prerequisite. The remaining investigation is a bounded device experiment.

The normal first use case is **controls on the lower screen, Ambient sampling
the game on the top screen**. Moving the controls does not change the source.
Top capture should become the first working Ambient slice; the complete POC
still requires an honest decision about bottom support.

### 1. Capture capability probe — scoped experiment completed

Use an isolated debug-only probe, not the old onboarding/production Ambient path.
Declare its temporary components only in the debug source set; do not enable
Ambient in the normal UI merely because the probe starts a service. The probe
must own and release its own resources, reject late callbacks, have an explicit
Stop, and never send LED commands. Record only bounded diagnostics/aggregate
measurements locally, with no screen/audio recordings or uploads.

| Question | Experiment / evidence required |
|---|---|
| Which panel does ordinary MediaProjection capture? | Distinct known top/bottom test patterns; independently change each pattern. Launch/move the probe UI on either panel and verify sampled colors, not just output dimensions or virtual-display existence. |
| Can bottom be sampled through a supported low-setup path? | Test normal projection first; if the source cannot be selected, test explicit Accessibility screenshot capture on each runtime-discovered logical display. Record setup, errors and actual achievable cadence. No root, shell capture or hidden API as a product dependency. |
| Can sound coexist without replacing capture? | After applicable runtime permission/consent, use known capturable playback and then a representative game. Test projection video + playback audio, and Accessibility screenshots + playback audio if that backend is needed. Verify video source and audio levels independently. |
| Does the session survive ordinary UI changes? | Home/reopen and move the controls while capturing. Then revoke consent, turn panels off individually and Stop. Observe resource/session state and failure reason; keep startup waits/retries bounded. |

Deliver a small capability table: backend, source, UI display, sound state,
required prompts/settings, measured sampling cadence/latency, errors and result.
If bottom capture is unavailable or needs Accessibility, say so explicitly; keep
the top path usable. Do not choose the final backend from emulator results.

### 2. Stabilize ownership and remove retired runtime hooks

Finish the keep/remove/freeze inventory, trace shared types, and remove obsolete
entry points as their dependencies are separated. Keep each deletion/build check
small; do not rewrite the whole service or driver in one change.

The [cleanup inventory](cleanup-inventory.md) records current entry points,
shared dependencies and removal boundaries. Its first bounded ownership change
addresses delayed Stop cleanup, old output writers, task closure and Solid idle
work. The [state/policy increment](service-state-2026-10-21.md) now adds durable
wanted-running intent, observable runtime state and removal of active legacy
policies/startup receivers. Plain-effect regressions pass; implement top Ambient
next. Full legacy source/resource removal and process-loss acceptance remain open.
See the [ownership/handoff results](service-ownership-2026-10-21.md), including
the still-open lower-screen Home/sleep/wake restoration issue and matched tests
with Bifrost debug and Settings. The owner's later cross-app observation also
suggests task history matters. Investigate this as shared device integration
behavior; current evidence does not justify a Runelight-specific UI workaround.

Establish:

- Service-owned resources and observable state; separate saved configuration
  from wanted-running intent and current resource state.
- Serialized Start/Stop/replacement and one LED writer; old cleanup/callbacks
  cannot affect a new session. Explicit Stop remains final.
- Correct independent four-zone frames, bounded hardware errors, immediate off
  and no repeated writes once Solid settles.

Verify these on plain effects before connecting capture to hardware. Existing
native UI smoke passes do not satisfy these engine requirements.

### 3. Implement the top Ambient slice

The [native top-source increment](ambient-implementation-2026-10-21.md) now wires
ordinary capture consent, three live sampling modes and held-target smoothing.
Its scoped validation does not complete bottom/audio or long-session acceptance.

Pipeline: selected-source capture → aspect-correct small sample grid → left/right
color targets → time-based smoothing → optional sound intensity → global
per-side brightness → serialized changed-frame output.

- Use a small aspect-correct grid as the starting point; measure its size/rate
  rather than inheriting 2×1 capture or exposing performance profiles. Respect
  source cropping/rotation and image row/pixel strides. Test letterboxing and
  grayscale/magenta/black; exclude bars without making genuine black glow.
- Sampling updates the held target. Smoothing continues toward it independently
  of whether a new frame arrived, then becomes idle when output settles.
  Brightness changes apply to the held target immediately. Do not apply brightness
  or gamma twice. Black must remain exactly zero through every stage.
- Unchanged content is valid. Absence of a changed frame alone must not be called
  capture failure; use session callbacks, explicit errors and bounded startup/
  in-flight checks. Keep black content separate from missing/failed capture.
- Preserve valid capture across Activity recreation and parameter edits. Show
  Running only after successful startup/first valid sample and usable output.
  On capture loss, clear output and show reconnect; no silent backend fallback.
- Select capture machinery internally. Expose only source and response as the
  immediate Ambient controls; keep setup contextual and optional for other modes.

Android's [projection lifecycle](https://developer.android.com/media/grow/media-projection)
defines consent, invalidation and resize behavior. In particular, Android 14+
single-use rules are a separate compatibility check from Thor Android 13.
[Accessibility screenshots](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshot(int,%20java.util.concurrent.Executor,%20android.accessibilityservice.AccessibilityService.TakeScreenshotCallback))
take an explicit logical display ID; a successful source-specific Thor test is
still required before relying on that path.

### 4. Bottom source, sound overlay and acceptance

Integrate only combinations demonstrated by the probe. Sound modulates the
selected effect without replacing it; denied/failed sound leaves the base effect
usable with honest feedback. Playback requires the permissions and source-app
policy described in [Android's capture requirements](https://developer.android.com/media/platform/av-capture).
Some silence is legitimate; do not label every zero level as a permission error.

Run pure sampling/envelope/frame regressions, Android resource/lifecycle tests
and actual Thor output checks. Include distinct L/R colors, exact black/off,
held-frame convergence, source/UI independence, capture revoke/reconnect, late
callbacks after Stop, sound toggling, Pathfinder, sleep/wake and normal battery
settings. Finish the planned two-hour game/performance runs before calling the
POC reliable. Cadence, response/sensitivity and silence-floor tuning are measured
implementation choices; no additional research project is needed to start.

## Proposed ownership, before changing architecture

| Concern | Intended owner / invariant |
|---|---|
| User configuration | Small persisted settings model: effect, parameters, source, sound option, appearance |
| Wanted running state | Separate durable user intent; explicit Stop clears it |
| Active resources/state | Foreground service; UI lifetime does not own the engine |
| Capture/audio | Explicit sessions with start/stop/error and late-callback rejection; no persisted grants |
| Rendering | One selected effect, then optional sound intensity and global output scaling |
| Hardware output | One serialized writer with complete per-zone raw/final state and changed-frame suppression |
| Feedback | Observed engine/capture/audio/output availability, not a service-exists Boolean |

Timing rules: never queue the only Stop on an Activity-owned delay; don't restart
the entire service for a slider edit; stale session work cannot write after Stop
or after replacement. Distinguish black frames from missing frames.

Blast radius: existing startup, presets/profiles, charging overrides, external
leases, schedules, receivers and manifest permissions share service state. Trace
dependencies before extraction/removal. Keep increments reversible and preserve
the existing vendor protocol. Proposed components are boundaries, not a mandate
for a new framework or a large class hierarchy.

## Ordered increments

### Preparation — build/test and preserve decisions (this task)

- Save canonical AGENTS guidance, Java 17 wrapper, build/test guide, review,
  original handoff, plan, device matrix and selected design in this checkout.
- Re-run unchanged JVM tests, debug assembly, full lint and release assembly.
- Record actual failures and available devices; do not hide baseline lint errors.

Exit: [baseline record](baseline.md) and reproducible commands. Hardware results
remain blocked until the Thor is connected. Bounded JVM fixes can proceed while
device access is unavailable; no device-reliability claim can pass without it.

### First increment — an identifiable app and dependable Solid control

Relevant review findings: **R1, R2, R3, R4, R11**, plus device-confirmed
**R14** in the [first device session](device-session-2026-10-20.md): display
enforcement relaunches and finishes the moved UI task. Fix initial-launch versus
external-move handling as part of this increment.

1. Implement a distinct application ID before the first POC install. Proposed
   value: `io.github.marcinsz89.runelight`, debug suffix `.debug`. Keep code
   namespace migration separate if unnecessary. Audit permission/action names,
   manifest components, resources and dynamic lookups when changing identity.
2. Add regressions for explicit Stop versus pause, interrupted commands,
   programmatic UI sync, dialog destruction and settings restoration.
3. Establish service-owned runtime state and durable enabled/configuration state.
   Separate continuation from boot/API switches; do not enable boot startup.
4. Keep the hardware path, make driver unavailability visible, preserve all four
   zones, and make steady Solid output idle after settling. Verify paired writes
   retain independent colors and correct state during transitions.
5. Implement the minimal Quick tiles surface for this internal increment, with
   direct controls and no mandatory preset/onboarding dialog stack.

Exit: source regressions pass, builds pass, and on Thor Solid survives UI closure,
movement and task dismissal. Stop stays off. Missing hardware is an honest state.
This is an internal increment toward the full five-effect POC.

### Ambient increment — correct source, color and session lifetime

Relevant findings: **R4, R5, R8, R9, R10, R12**.

1. Make sampling/color math testable; cover negative hue, grayscale/black,
   left/right split, custom+single sampling and constant-frame convergence.
2. Own capture resources separately from Activity display/slider state. Keep an
   active valid projection for parameter updates; request fresh consent when
   invalidated. Test Android 14+ rules separately from the owner's Android 13 bug.
3. Wire actual top/bottom source selection to a supported backend. Handle the
   required Accessibility permission only for that backend, without silent fallback.
4. Stop stale callbacks/output and expose first-frame, failure and permission-lost
   states. Proposed behavior on lost Ambient capture: output off, effect retained,
   actionable reconnect. No silent switch to a different look.
5. Make black reach zero RGB; smooth toward held targets; apply brightness edits
   without requiring a changed input frame. Batch/suppress final output safely.

Exit: synthetic image cases pass; distinct top/bottom frames route correctly on
Thor; black is physically off; capture revoke/reconnect and UI recreation behave
predictably. A two-hour Ambient/game session and measured output/CPU behavior
are required before calling the POC reliable.

### Sound/effects increment — independent reaction on supported effects

Relevant findings: **R6, R7, R11, R12**.

1. Add playback-capture permission handling and explicit audio session failures.
   Use no microphone fallback. Reuse an appropriate valid projection session
   where supported; don't create competing screen/audio ownership.
2. Normalize/smooth an audio envelope and apply it after the selected effect.
   Verify Ambient/Solid/Rainbow with sound on and off, and Breathing/Pulse with
   sound paused while retaining saved On. Switching sound never replaces
   the chosen effect, colors, animation phase or capture source.
3. End failed reads or bounded no-data timeouts, release on every exit,
   and reject old recorder callbacks. Nonblocking zero reads are temporarily
   normal; zero-valued PCM is valid silence. On audio failure, fallback is the
   selected base effect with a visible inactive/error sound state.
4. Reuse the simple Rainbow/Breathing/Pulse behavior, correcting output and
   lifecycle ownership; avoid copying their independent main-thread loops blindly.

Exit: audio failure tests pass without spinning or leaking; actual game audio
drives intensity on Thor; effect selection remains independent. Record unsupported
games/capture policies as limitations rather than pretending silence is success.

The [first native sound slice](sound-implementation-2026-10-21.md) now passes
controlled playback, pause/resume and shared capture checks. Representative game
audio, sensitivity tuning and long-session acceptance remain pending. Investigate
the owner's intermittent stuck-white Ambient report after idle OLED cleanup;
one subsequent cleanup recovered normally, so no fix is claimed.

### Cleanup pass — after the native UI settles, before POC completion

Inventory every inherited subsystem as **keep**, **remove** or **freeze**, with
its owner, callers, startup/background hooks, permissions, resources, dependencies
and tests. Review the inventory before any broad deletion; keep working core
capture/LED code until its dependencies have been traced.

- **Remove:** obsolete UI/onboarding, preset/artwork workflows, profiles,
  schedules, wallpaper, external/plugin routes and optional effects that have no
  place in the agreed POC. Remove their entry points, manifest declarations,
  jobs/receivers, permissions, resources, dependencies and obsolete tests together.
- **Freeze:** retain only material with a concrete reference/reuse reason. Move
  it outside all compiled source/resource sets, for example `reference/legacy/`,
  with a short inventory explaining purpose, original dependencies and conditions
  for restoring it. No runtime route, registration or initialization may remain.
  Do not turn frozen code into a second framework or maintained application.
- **Keep:** the reviewed vendor protocol, retained effects, shared color/sampling
  code needed for Ambient, and relevant behavior regressions. Extract shared
  utilities before removing a legacy caller that currently owns them.

Exit: no hidden subsystem still runs or requests permissions; frozen material is
absent from debug/release classes, resources and merged manifests; retained tests,
builds and relevant Thor scenarios pass. Preserve GPL attribution. Record actual
remove/freeze decisions, rather than treating UI hiding as completed cleanup.

### POC completion — validate the retained product

Relevant findings: **R1–R13**, within retained functionality.

- Finish lower-screen Quick tiles, light/dark appearance, settings persistence,
  mode-specific permissions and actionable runtime status.
- Complete the cleanup inventory and its remove/freeze exits above. R13 is
  resolved by retiring archive/plugin entry points or by hardening any retained
  importer; hiding its button alone is insufficient.
- Reconcile the full-lint failures and review remaining warnings by impact.
  Do not spend the POC on broad dependency updates or cosmetic legacy cleanup.
- Run [the device matrix](device-test-matrix.md), including both screens,
  Pathfinder on/off comparison, permissions, task cleanup, sleep and Stop races.
- Compare a repeatable game workload with the controller stopped, Solid,
  Ambient, and Ambient+sound. Record CPU/memory, writes/capture frequency and
  frame-time evidence; avoid an arbitrary 'zero overhead' claim.

Exit: five effects plus optional sound, selected design, clean justified lint
results, two-hour core sessions, no known reproducible lifecycle crash, and an
honest list of any device/backend limitations. No release/push is part of setup.

## Open decisions / evidence to collect

- Confirm the proposed package ID before a distributable POC is installed.
- Thor firmware build, lower-screen dp/insets/font scaling, installed DuoFrost
  build and the setting that avoided the reported bottom-launch problem.
- A current Bifrost fatal trace and a post-2.5.1 DuoFrost trace. The October 14
  window-leak excerpt does not prove the October 16 fix failed or Pathfinder caused it.
- Sound's silence floor and attack/release defaults: tune on device; no additional
  advanced sound controls in the first screen. Never brighten black Ambient input.
- Exact stale-frame timeout and recovery policy, based on interruption testing.
- Confirm lower-source Accessibility capture behavior and simultaneous audio on
  Android 13. Permission/backend limitations may need an explicit POC restriction.
- Exact recovery policy after eligible process loss; explicit Stop and Force Stop
  semantics must be preserved. Do not promise resurrection after OEM/user kills.

## Status

Preparation is complete: development guidance, build helper, baseline validation,
review/handoff, device matrix and selected design are saved. UI and package-ID
work is now partially implemented in a separate native Quick tiles entry point.
See [the native UI slice](native-ui-increment.md) for its scope and checks.
Solid, Rainbow, Breathing and Pulse reuse the inherited effects; top-source
Ambient and the independent playback sound overlay are now implemented. The
five-effect POC scope is unchanged. Driver idle/per-zone fixes, plain-effect
handoff, durable wanted intent and observed first-output/failure state are now
implemented and tested in bounded runs. Active legacy service policies and four
startup/API receiver registrations are removed. See the
[current state report](service-state-2026-10-21.md) and
[sound report](sound-implementation-2026-10-21.md). Full capture/audio acceptance,
remaining legacy component cleanup and the full device acceptance matrix are
still required; the POC is not complete.
