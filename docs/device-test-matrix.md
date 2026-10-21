# Thor POC test matrix

Initial status: **NOT RUN**. No Thor was connected during setup on 2026-10-20.
A subsequent read-only ADB check confirmed Android 13 / API 33, firmware
`Thor_V1.0.0.377_20260206_165408_user`, displays 0 and 4, and density 369 dpi.
See the [device discovery record](baseline.md) for dimensions and installed app
versions. Per-app insets, Pathfinder enabled state and all functional scenarios
still need testing. This matrix defines future results, not passes.

**Latest baseline run:** the inherited debug APK is now installed. Top-screen
UI smoke checks passed, but screen routing/movement failed. See the
[device session report](device-session-2026-10-20.md), including R14 and the
limitations of the live session. Other functional scenarios remain NOT RUN.

The later [native UI increment](native-ui-increment.md) has separate scoped
results: lower launch, live controls, repeated Android task moves, inline color
draft restoration, Home/reopen and immediate Stop passed. The table below keeps
the inherited-baseline failures visible. It does not mean every later APK fails
those checks, nor does the UI smoke run fully pass the broader scenarios.

For each run, keep a local record with: commit and dirty-state description, APK
SHA-256/package/version/signing type, firmware, serial alias, source/UI display IDs,
capture backend, sound state, whitelist/battery restrictions, Pathfinder state,
steps, expected/actual result, duration and redacted evidence paths.

The later [Ambient capability probe](ambient-probe-2026-10-20.md) records isolated
capture results and a framework watchdog incident. It emits no LED output, so
its source/color/Stop passes do not satisfy the full end-to-end scenarios below.
Controlled playback signal, bottom screenshots + audio, sound-off continuity,
probe Home/ADB display movement and consent denial/recovery now have scoped
passes. Actual LED Ambient/audio and long-run performance remain unproven;
the broader table still requires end-to-end implementation.

The [ownership/handoff increment](service-ownership-2026-10-21.md) adds scoped
passes for closed-driver fencing, Solid timer idle, rapid Stop/Start, UI task
removal and plain-effect handoff. Lower-screen Home/sleep/wake unexpectedly
restores the previous app in matched Runelight, Bifrost debug and Settings tests;
this remains open and is not a passed wake scenario. The owner subsequently
reported the same visibility changes across other apps, varying with the app
history before Home/lock. See the report for the scoped evidence and remaining
cause investigation.

The [service-state increment](service-state-2026-10-21.md) adds awake Thor passes
for wanted-intent/explicit Stop, ignored legacy commands, destruction/recreation
without automatic resume and observed output acceptance. These scoped results do
not turn the historical baseline table below into a full product acceptance pass.

**Owner check — 2026-10-21:** the owner confirms that the current Runelight UI
successfully swaps between screens using Pathfinder's shortcut. Record this as
an owner-reported PASS for basic shortcut movement on the current UI build
([typography/editor revision](ui-polish-2026-10-21.md)); no additional device run
was performed to collect this report. Editor drafts, active Ambient/audio capture
continuity, repeated moves and the Pathfinder enabled/disabled comparison remain
separate checks. Historical baseline results below are retained.

Use `PASS`, `FAIL`, `BLOCKED` or `NOT RUN`. Keep raw logs and identifying device
data under ignored `local-test-results/`; commit only deliberately redacted notes.
Keep the exact release mapping with the tested APK. Stop other LED controllers.

| ID | Scenario | Required observation | Latest result |
|---|---|---|---|
| D01 | First launch on lower display; repeat on top | Fits, controls usable, no leaked/invalid window | FAIL: requested lower launch redirected to top; lower fit unverified |
| D02 | Move UI both ways with settings/alerts open | No crash/leak; state retained; valid capture not restarted | FAIL: 0 → 4 closes UI; reverse/alerts pending |
| D03 | Repeat D01/D02 with Pathfinder disabled/enabled | Compare reproducible differences; identify exact versions | NOT RUN |
| D04 | Solid, four zones / distinct left-right RGB | Correct masks/colors, black truly off, hardware failure visible | NOT RUN |
| D05 | Solid then Home/Back/task swipe/cleaner, individually | Continues when permitted; actual state/whitelist outcome recorded | NOT RUN |
| D06 | Stop then immediately Home/move/sleep; repeat | Stops promptly and stays off | NOT RUN |
| D07 | Change parameters then immediately leave/move | No interrupted restart; last configuration retained | NOT RUN |
| D08 | Reopen/recreate UI while engine runs | Control synchronization issues no Start/Stop commands | NOT RUN |
| D09 | Sleep/wake and each display switched off separately | No crash or stale active capture; recover/reconsent honestly | NOT RUN |
| D10 | Top/bottom have distinct solid test colors, UI on either | Selected source actually drives LEDs; no UI/source coupling | NOT RUN |
| D11 | Red/green/blue/magenta/gray/black and split L/R content | Correct hue and mapping; physical zero for black | NOT RUN |
| D12 | Constant frame, adjust brightness/response | Settles to target even with unchanged frames | NOT RUN |
| D13 | Letterbox/pillarbox, custom+single sampling | Grid still used; useful content sampled correctly | NOT RUN |
| D14 | Stop/revoke capture, another recorder, permission denied | Clear state; output policy applied; fresh consent when needed | NOT RUN |
| D15 | Each of five effects, sound toggled on/off | Same effect/colors/source; optional intensity reaction only | NOT RUN |
| D16 | Silence, unsupported playback, recorder/read failure | No retry spin/leak/mic fallback; honest sound state | NOT RUN |
| D17 | Rapid effect/source/sound switches, then Stop | Old callbacks cannot relight or replace the new session | NOT RUN |
| D18 | App/process loss and explicit Force Stop, separately | Record policy; no false always-running/restart guarantee | NOT RUN |
| D19 | Two-hour Solid session | Stable, no continuing idle hardware-write loop | NOT RUN |
| D20 | Two-hour Ambient/game session; repeat with sound | Stable capture/output, bounded memory/work, frame-time evidence | NOT RUN |
| D21 | Light/dark, both displays, larger font, system bars | Readable, touch targets usable, no clipped core action | NOT RUN |
| D22 | Missing vendor binder / unavailable capture backend | Actionable state, no false success or infinite retries | NOT RUN |
| D23 | Fresh install, normal battery settings, deny notifications; enable features separately | Plain effects work without special settings; only applicable capture/audio prompts; no forced Accessibility onboarding | NOT RUN |
| D24 | Android 13 Active apps Stop, then reopen; compare OS reclaim and Settings Force Stop | User-requested stop distinguished from reclaim before recovery; record retained hardware output and explicit off behavior | NOT RUN |
| C01 | Android 14+ session consumption/revocation | No reused grants or second virtual display per consent | NOT RUN |

## Performance comparison

Use one fixed game/emulator scene, consistent display refresh/brightness, thermal
conditions, audio level and controller brightness. Compare controller stopped,
Solid, Ambient, Ambient+sound. Record warm-up and measurement durations.

Measure process CPU, memory, game frame times and observable frame pacing;
instrument capture/LED submission frequency in a bounded diagnostic build.
Do not infer writes/s from UI FPS or count one-way binder submission as a measured
kernel acknowledgement. Test any meaningful diagnostic overhead separately.

Acceptance requires no reproducible app crash, no growing resource usage, no
unbounded error loop and no controller-attributable gameplay regression in the
repeatable comparison. Record actual values and limitations before choosing a
final capture/output rate; the draft 20–30 Hz range is a starting experiment.
