# Native top-screen Ambient — 2026-10-21

First production-path increment following the [capture experiment](ambient-probe-2026-10-20.md).
This is a top-source slice, not completion of the whole POC or a reliability claim.

## Contract and ownership

- Ambient uses ordinary MediaProjection consent and the top/default display,
  independently of where the controls are shown. The center tile labels **Top
  screen**. Bottom selection remains deferred until its own backend is integrated;
  no silent Accessibility fallback or inherited analyzer path is used.
- Explicit Start requests consent; selecting Ambient while stopped requests
  nothing. Switching a running plain effect to Ambient keeps that effect during
  the prompt. Cancelling preserves it. A valid grant replaces the effect only
  after its old timers/resources have been released.
- `LEDService` owns the animation and capture. Its runtime generation fences
  output/failure callbacks. `ProjectionConsentGate` holds only an in-memory,
  single-use request identity: notification Stop or a newer plain Start invalidates
  pending consent. Neither a grant nor a live object enters preferences/state bundles.
- Foreground promotion precedes projection acquisition, with mediaProjection
  requested only for capture. No overlapping projection replacement or token replay.
- Main-looper ownership serializes resource creation, small-grid sampling, render
  ticks, Stop, parameter edits and teardown. The capture grid's longest axis is
  64 pixels; frames are drained but RGB sampling is capped at 10 Hz. Smoothing
  ticks at about 30 Hz only while converging; unchanged final frames are suppressed
  by the existing complete-zone driver.
- Running requires a rendered sample plus accepted vendor Binder submission.
  Side-brightness edits cannot acknowledge the old output as Ambient readiness.
  This remains submission evidence, not physical LED telemetry.
- First output has a five-second deadline. Projection stop/revocation, source
  removal or frame processing errors end capture, clear LEDs and show a direct
  Start-to-reconnect message. Unchanged frames/black are valid; there is no
  change-detection timeout, retry loop, automatic restart or recording to storage.
- Activity recreation, Home, UI movement and sampling/brightness changes do not
  own/recreate capture. Stop invalidates listeners before releasing resources.
  Android 14+ content resize changes the existing virtual display/surface;
  Android 13 display changes use the top display's WindowContext metrics.

## Sampling and controls

Left/Right remain independent brightness tiles. Ambient replaces Speed with a
center **Sampling** tile. Tap cycles three choices, with position dots:

- **Edges:** derive each side's color from its outer eighth (12.5%), across the content height.
- **Screen halves:** derive each side's color from its corresponding image half.
- **Full screen:** derive one representative color from the whole picture for both sides.

Every choice is an active sampling mode, including Edges; none is styled as Off.

Brightness stays independent even when both sides share a full-screen color.
Switching uses held pixels immediately; the source stays top regardless of UI
placement. The temporary Whole screen Boolean migrates to Screen halves because
that was its actual sampling behavior. New settings store an explicit mode name.

RGBA unpacking respects row stride, pixel stride and crop. A one-grid-cell
perimeter guard excludes very thin system gesture/edge artifacts before region
selection. This slightly insets the sampled picture (about 1.6% horizontally at
64 pixels); edge-band width is about one eighth of the retained content. Color processing
preserves neutral hue, magenta and exact black. Only
matching completely black outer rows/columns are trimmed, bounded to one quarter
per edge. This conservative heuristic can mistake symmetric black content for
bars; irregular/gray bars are retained. It does not reconstruct protected content.

### Brighter, more identifiable color

The owner's first mixed-content check found the unweighted average too dim and
washed out. The inherited Bifrost `ScreenAnalyser.kt` confirms Custom Sampling
splits the picture into halves, then selects/weights its most colorful pixels
(default 30%); it is not just an arithmetic half-screen average. Its single-color
option instead combines the sampled picture for both sides. The old default 2×1
capture is not an actual edge-band algorithm.

`AmbientColorProfile` now applies the colorful-pixel idea within **each selected
region**, keeping the three spatial modes distinct. A bounded 1,024-bin histogram
selects the top 30% by saturation/chroma/value score, includes cutoff ties equally,
and computes a weighted representative RGB. This differs deliberately from the
old scan-order-sensitive top-N selection and hue-dependent luminance weighting.
A square-root value term prevents near-black colored noise outranking bright
neutral content. True-black pixels have zero weight; all-black returns exact zero.

After selection, HSV-equivalent saturation ×1.35 and value exponent 0.80 provide a
modest default lift. A uniform RGB 128 gray becomes about 147 gray; full black
stays black and full white stays white. Gray acquires no invented hue, hue wraps
need no angle conversion, and balanced opposite hues may still average neutral.
These are initial tuning choices, not calibration parity with Bifrost or HDR
color management. No extra slider/setup is added. Side brightness applies once,
after this processing, and stays the output ceiling.

The initial held target advances with elapsed-time exponential smoothing (110 ms time
constant), snaps within half a channel step, then stops scheduling. Brightness
is applied once by the driver after sampling/smoothing. No sound overlay yet.

The later [sound/latency increment](sound-latency-2026-10-21.md) adds sound and
reduces color latency: sampling at most every 40 ms, a 35 ms color time constant
and 16 ms active render ticks. The initial 10 Hz/110 ms figures above describe
the earlier baseline. A shared latest-frame writer limits actual vendor output
to 25 Hz, independently of capture or audio; held targets still settle and idle.

## Validation

Java 17, offline dependencies:

- `./scripts/dev.sh unit --offline`: **90 JVM tests pass**, including sampling
  geometry, three-mode semantics, padded/cropped RGBA, held-frame convergence,
  neutral/black preservation, highlight/HUD behavior and single-use consent fencing.
- Debug + Android test APK assembly and `:app:assembleRelease`: **pass**, including
  R8/resource shrinking and vital lint. A later debug-only fixture adds optional
  label-free output and dark-background/white-HUD content; debug assembly passes.
- Five focused Thor instrumentation tests: **pass** on the improved color build.
  Real animation timers + local recording Binder prove convergence, all four zones,
  live mode/brightness without capture replacement, Full screen shared color with
  independent levels, exact zero black, idle writes and stale callbacks after Stop.
  UI recreation preserves sampling; Edges is active. Service Stop rejects old
  consent. Existing plain-effect handoff/rapid Stop–Start cases still pass.
- `./scripts/dev.sh lint --offline` on the final color-profile build: **fails
  with the two known inherited errors**, AudioAnalyzer MissingPermission and the
  Quick Settings collapse call. No new capture/profile error; no broad baseline
  added. Release/vital lint passes independently.

Before color tuning, the live native top-source path reached Running and the
owner confirmed red-left / blue-right LEDs. Full screen then produced the same
purple on both sides, also owner-confirmed. One retained 64×36 virtual display
survived live edits, Home/reopen, ADB task movement top/back and a brief sleep/wake;
foreground promotion count remained one. Explicit Stop released projection.
One bounded consent-cancel sequence left the UI Stopped with no projection; a
fresh consent started a new session. An earlier attempted cancellation was
inconclusive and is not counted. No overlapping projection was introduced.

Those lifecycle checks precede the pure color-profile adjustment. Actual
Pathfinder while capturing, system revocation/reconnect, representative game,
individual-panel-off behavior, Android 14+, bottom capture, sound overlay and
long gameplay/performance acceptance remain open. UI movement by ADB is not
Pathfinder acceptance; an existing projection after wake is not proof of fresh
post-wake colors. Raw evidence/APKs/mapping remain ignored and local.

The color-profile build starts native Ambient on a label-free scene with a
colored strip, white HUD strip and dark background. The owner confirmed clear
warm orange on both physical LED sides, rather than washed-out gray/white.

The first black-fixture check still glowed: the actual screenshot contained a
small gray system gesture handle (828 nonblack physical pixels). Color selection
could amplify that isolated UI artifact. A one-cell sample perimeter guard and
a regression for a black picture with a gray outer border were then added;
90 JVM tests and debug/release assembly pass. The Android output regression
passes again after the guard. The owner then confirmed **fully dark LEDs** on the same black fixture while capture/Lighting
remained On. This resolves the observed system-edge glow in that controlled case.
The saved native Ambient screenshot shows controls, not measured LED telemetry. A final Edges screenshot confirms the
active styling. Explicit Stop then released projection, fixture activities were
closed, and the owner's resumed settings were restored byte-for-byte. Runelight
was left Stopped on the lower screen, with no active capture.

## Reproducibility

Exact local APK SHA-256 values for the color-profile check (unsigned release):

- Debug: `0e6ba266a61b3e0ea6b97e80531eae6cf8178dd6e64b19588a5551821761815f`
- Test: `459765809a12a6abe761e0b89b87ad692cf3d57cecbe87c42577d94ef89202dd`
- Release: `58850bc8466a056dd082dfe50563824c7538182abbe9c18a43ed449d7ee08efc`

The five-test run preceded the perimeter guard. After that narrow math change,
all 90 JVM tests, debug/release assembly and the Android Ambient output test
passed again. The final hash above identifies that follow-up APK. The test APK
is unchanged. The final full-lint run preceded the guard; the guard follow-up
has release/vital-lint coverage.
The release mapping is preserved beside those APKs under ignored
`local-test-results/2026-10-21-native-ambient/`. No Git commit, push or stash was made.
