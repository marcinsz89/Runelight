# Runelight POC — implementation plan draft

Saved 2026-10-20. The first native UI slice is now implemented. This is the working
plan for the discussed POC; open choices below remain proposals. The source
baseline is Bifrost 1.3.1, `1baddf1644ff0d7edd1bd0f4ba02f7eb6c8e3cfa`.

## Product direction

A reliable, low-overhead Thor Max LED utility with a simple interface that fits
the lower display well, also works on top, and survives moving/recreating its UI.
The owner selected **Quick tiles**, then requested a distinct Runelight identity
with a light-related rune. See [the saved design](design/README.md).

POC scope discussed:

- Ambient, Solid, Rainbow, Breathing and Pulse.
- An independent **React to sound** option on every effect, with one sensitivity
  control. The effect supplies color/animation; sound modulates intensity.
- Direct effect/brightness controls, relevant per-effect controls, explicit
  capture source, dark/light appearance, last-used settings and Start/Stop.
- Service continuation after normal UI dismissal, honest capture/audio/hardware
  status and a foreground notification with Stop.
- Top and bottom capture only through a backend that demonstrably supports the
  selected source; a clear unavailable state instead of pretending it works.

Deferred from the POC: preset libraries, app profiles, schedules, plugins,
external API, wallpapers, artwork, import/export workflows, boot auto-start,
battery/temperature effects and unsafe performance profiles. Quick Settings
integration can follow the core; its existing lint issue must be resolved if kept.

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

The UI has reached the point where the keep/remove/freeze inventory can begin
before the next engine changes. Prioritize plain-effect lifetime/output, then a
bounded capture-capability experiment, then Ambient/audio ownership. The full
cleanup exit remains required before POC completion.

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

### Sound/effects increment — optional reaction on every look

Relevant findings: **R6, R7, R11, R12**.

1. Add playback-capture permission handling and explicit audio session failures.
   Use no microphone fallback. Reuse an appropriate valid projection session
   where supported; don't create competing screen/audio ownership.
2. Normalize/smooth an audio envelope and apply it after the selected effect.
   Verify all five effects with sound on and off. Switching sound never replaces
   the chosen effect, colors, animation phase or capture source.
3. End nonpositive/error reads, release on every exit, unblock before joining,
   and reject old recorder callbacks. On audio failure, proposed fallback is the
   selected base effect with a visible inactive/error sound state.
4. Reuse the simple Rainbow/Breathing/Pulse behavior, correcting output and
   lifecycle ownership; avoid copying their independent main-thread loops blindly.

Exit: audio failure tests pass without spinning or leaking; actual game audio
drives intensity on Thor; effect selection remains independent. Record unsupported
games/capture policies as limitations rather than pretending silence is success.

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
Solid, Rainbow, Breathing and Pulse reuse the inherited engine; Ambient and the
independent sound overlay remain unavailable in this internal build. The
five-effect POC scope is unchanged. Driver idle/per-zone fixes, durable running
intent, full engine failure feedback and retirement of legacy components are
still required; the first increment is not complete yet.
