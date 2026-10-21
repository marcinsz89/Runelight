# First native Quick tiles increment — 2026-10-20

Runelight now has a native Android home screen based on the selected design.
This is an internal UI increment, not the completed five-effect POC.

Later [ownership and mode-handoff work](service-ownership-2026-10-21.md) removes
the forced blank between plain effects and adds Stop/task-lifetime regressions.
That report also records the open lower-screen Home/sleep/wake investigation.
The subsequent [service-state report](service-state-2026-10-21.md) supersedes
this increment's pending service-state/idle/Stop work and records the current build.

![Native Runelight on the lower display](design/native-quick-tiles.png)

The screenshot above shows the compact layout refinement below. The first
layout's Current look tile has been removed.

## Implemented

- Plain Lighting / Start lighting / Stop lighting labels, Dagaz mark and
  Runelight app/notification identity. No "Call Heimdall".
- Solid, Rainbow, Breathing and Pulse through the inherited engine. Direct
  effect, brightness, relevant speed and left/right color controls.
- Light/dark appearance and remembered settings. An inline RGB editor preserves
  drafts through recreation and commits only on Apply. Stop stays accessible.
- No presets, app profiles, plugin prompts, wallpaper, schedules or onboarding
  wizard in the new UI. Ambient and independent sound are visibly unavailable
  pending their engine work; the full five-effect POC scope remains unchanged.
- Android owns the UI display; the new Activity does not relaunch/finish a moved
  task. Explicit Stop dispatches immediately, without Activity-owned debounce.
  Start allows background continuation independently of boot/API preferences.

`RunelightActivity` owns views, saved manual settings and unconfirmed drafts;
LEDService owns active effects/output. Recreation/control synchronization sends
no Start/Stop command. The service exposes its started effect and vendor binder
availability; absent hardware no longer silently starts a service.

Application ID is `io.github.marcinsz89.runelight` (debug suffix `.debug`), while
the code namespace stays `com.moonbench.bifrost`. MainActivity is internal legacy
code and no longer the launcher. Notification taps open the new screen. Deferred
components still exist and require deliberate removal; hiding them is not final
retirement. The inherited version string/counter remain 1.3.1-debug / 16 for now.

## Verification

Thor Android 13 / API 33, firmware `Thor_V1.0.0.377_20260206_165408_user`:

| Check | Actual result |
|---|---|
| Debug and Android test APK assembly | PASS |
| Release assembly, R8/resource shrinking, vital lint | PASS; unsigned APK |
| Existing JVM suite | PASS: 60 tests, no failures/errors/skips |
| Native instrumentation | PASS: 2 tests; effect restoration without Start and unapplied draft restoration |
| Full debug lint | FAIL: 3 inherited errors, 400 warnings; no diagnostics in the new UI files |
| Lower launch and Solid layout | PASS; core controls fit at current font/density |
| Light/dark toggle while stopped | PASS; no lighting Start |
| Start and four available effects | PASS engine/UI state; logs confirm selected effects start |
| Live brightness | PASS; 40% → 29%, retained through movement |
| Three round trips 4 → 0 → 4 while running | PASS; same task, effect/settings retained |
| Move with inline color editor open | PASS; editor survives, Cancel accessible |
| Home and reopen | PASS; service continues, UI reflects it |
| Stop then immediately move 4 → 0 → 4 | PASS; stays stopped, service absent afterward |
| Final bounded smoke logs | No fatal exception or leaked window; crash buffer empty |

Logs show five effect starts corresponding to initial Solid and four explicit
selection actions, then one cleanup at explicit Stop. Display moves and
Home/reopen added no effect starts. The final smoke harness completed
successfully; the app was left stopped on the lower screen.

Early tests encountered Espresso 3.5 injecting clicks into display 0, plus a
focus timeout when recreating a host with a separate picker window. Final
lifecycle tests discover an active display and invoke controls on their owning
thread. The independent ADB smoke run checks real touch input with explicit
display IDs. The inline editor removes the picker window lifecycle entirely;
both recreation tests then passed.

| First-increment artifact (before compact layout refinement) | SHA-256 |
|---|---|
| Debug | `78b55491d1daf887033728c2f72ca170d0536ff8292b37256dfadb47eccea642` |
| Unsigned release | `0d908a274c5da2516b3950f976787ef49a964354a14751f30b7ed0b6d80fbcdd` |

Local evidence: ignored `local-test-results/2026-10-20-quick-tiles/`. The saved
screenshot was inspected and contains only app UI. No source, APK or raw logs
were uploaded; no commit or push was made.

## Compact layout refinement — 2026-10-20

- Removed the redundant Current look tile and its label-update code/resources.
  The checked effect segment is now the sole selected-effect indicator.
- Moved React to sound beside Lighting. Both remain above the scrolling area,
  keeping Start/Stop accessible during inline color editing.
- Placed brightness below the effect strip as a full-width slider.
- Sound and Ambient remain unavailable pending their engine increments.

Validation of this revision:

- `./scripts/dev.sh build :app:assembleDebugAndroidTest --offline`: PASS under
  Java 17. Debug app and test APKs installed successfully on the Thor.
- `RunelightActivityTest`: PASS, 2 tests on the Thor. Effect selection stays
  checked through recreation without starting lighting; an unapplied color
  draft survives recreation. Assertions now observe the effect segment directly.
- Lower-screen layout: PASS at the current font/density, in light and dark
  appearance. The top cards share a measured height, sound is beside Lighting,
  and brightness spans the available width below the effect strip. Solid's
  controls fit without scrolling; animated effects' extra speed control scrolls.
- Real display-specific touch: PASS for Start, Pulse selection, brightness
  40% → 50% and Stop. Appearance changes while stopped did not start lighting.
- Android task move 4 → 0 → 4: PASS; same task retained Pulse and 50% brightness,
  stayed running until explicit Stop. Top rendering was captured; actual top
  touch and the owner's Pathfinder shortcut remain separate unrun checks.
- Bounded smoke logs contained no fatal exception or leaked window. The app was
  left stopped on the lower screen with Solid selected, 50% brightness and dark
  appearance. The saved screenshot was inspected and contains only app UI.

The smoke run completed after reconnecting USB and dismissing the Thor's system
screen-control overlay. These results cover the final aligned layout build;
earlier broad engine/device results above remain limited to the earlier build.

Local evidence: `local-test-results/2026-10-20-quick-tiles/layout-build.txt`,
`layout-instrumentation.txt`, `layout-smoke.txt` and `compact-layout/`. No commit
or push. Release assembly and the JVM suite were not rerun for this layout-only
refinement; their earlier results above are historical.

Tested debug APK SHA-256:
`65dfc4780a64f8f75ff7b3bdbfbc81e29b96883f7225e59760f7e832b3c0df19`.

## Latest refinement — fixed configuration, side levels and color wheel

The owner requested a fixed lower-screen layout, HomeKit-style color/level
controls, whole-tile sound toggle, stronger effect selection, copy from the other
side and live LED color preview. The current native screenshots in the
[design](design/README.md) supersede the earlier full-width slider layout above.

- Lighting uses a full-surface gradient only when acknowledged On. Sound has the
  same whole-tile shape and state treatment; its unavailable feature stays off.
  The effect strip has a stronger selected accent and contrasting bold text.
- Two rounded Left/Right tiles replace the global slider and outlined color
  buttons. Drag vertically for independent levels, tap to edit color on Solid,
  Breathing and Pulse. Rainbow owns its colors and retains both level controls.
- There is no ScrollView. Normal configuration and the inline editor each use
  the remaining screen area, retaining Lighting/Stop. Speed appears only for
  animated effects. Default-font lower-screen Solid and Pulse fit, in dark and
  light appearances; larger fonts remain unverified.
- Reuse ColorPickerView **2.3.0**, pinned to avoid an incidental Kotlin/AppCompat
  upgrade. The wheel has a Runelight selector and animated preview. Copy left/
  right copies color and brightness into the draft. Cancel discards it; Apply
  saves it. Draft color/level survive recreation. The library owns neither
  Runelight settings nor a dialog. Its Apache license ships in assets, with
  attribution in the repository's third-party notices.
- While lighting is already active, color drafts preview on the selected side.
  The owner confirmed the physical left LEDs changed toward cyan while the
  right stayed pink. Updates preserve the running basic animation instead of
  restarting it. Cancel/Back/UI closure restores saved values. Stop suppresses
  queued previews; a real picker drag after Stop left the LED service absent.
  Saved preferences matched the preserved pre-preview file byte for byte.

The driver now retains raw color/wire-field state per zone, serializes updates
and Binder submission, applies final independent side levels once, and suppresses
unchanged submitted frames. Static/Breathing/Pulse submit both sides together.
This corrects the last-side-only re-emission state needed for side brightness;
it does not finish the broader service cleanup or make Solid's CPU timer idle.

Ownership/risk boundary: native preferences own saved levels (seeded from the old
global level); the service/driver own active output. UI presentation changes send
no Start. Legacy starts without side extras default to 100% per side and retain
their existing global-brightness path. Parameter commands cannot modify a
stopping service; a stale update to an absent service is stopped. Legacy profiles,
overrides and restoration workflows remain outside the native POC acceptance.
No projection/backend behavior was changed in this UI increment.

Validation: 65 JVM tests passed, including five new output-state regressions;
six device tests passed, including independent level persistence, drag versus
tap, draft restoration, and copy/Cancel/Apply through recreation. Debug assembly
and release shrinking/vital lint passed. Full debug lint reports two inherited
errors: AudioAnalyzer runtime permission handling and the old Quick Settings
overload. The new probe permission guard removes its own new diagnostic; debug's
audio permission declaration accounts for the changed baseline count. No lint
baseline/suppression was added.

The owner's colors, levels, effect and appearance were backed up before tests
and restored. The app is left stopped on the lower screen. Raw local evidence:
`local-test-results/2026-10-20-ambient-probe/homekit-*`. No staging, commit or push.

Final debug APK SHA-256:
`f7ebf478f7ea61eec749d1797c8a37c19dfe1ddc37f9bc31562adad09db6847b`.
Final unsigned release APK SHA-256:
`e958df37286d1dc6d300950ba908ab77b45a19fa4f220140a9ad10f4b22a3270`.
The six-device-test rerun passed on that final debug build. Release packaging was
run separately after full debug lint's expected failure; assembly/vital lint
passed. Debug capture components do not ship in release.

### UI finalization and reported mode stall

The next owner refinement moves Speed into a third matching vertical control,
between Left and Right, for Rainbow/Breathing/Pulse. Solid keeps two wider tiles.
The horizontal speed row and normal Ambient rebuild footer are removed. Real
startup/hardware errors still appear when applicable. The final three-column
layout fits the lower screen at the current font size; the design screenshot
has been updated. Original colors/levels/speed were restored after testing;
Pulse is selected while stopped to display the center control.

The owner reported Rainbow, Breathing and Pulse staying solid, then confirmed
animations resumed during the live check. Current-process logs show the requested
animations starting; no concurrent AmbiLuma/Bifrost/DuoFrost services were active
when checked. The earlier stall was not reproduced or assigned a proven cause.
Do not record it as a fixed defect solely because subsequent checks pass.

Expanded device verification: **10 tests passed**. A real UI/service test switches
from running Solid through Rainbow, Breathing and Pulse and back, checking the
acknowledged effect and Stop. Two output tests run the actual animation timers
and driver through a recording Binder, proving temporal RGB changes before Stop,
four-zone frames, timer cancellation, independent levels and settled-output
suppression. Those tests do not write physical LEDs or replace calibrated output
validation. The center Speed control retains its value without changing the two
brightness levels or starting lighting. The production controller's default
vendor lookup is unchanged; an internal constructor accepts a Binder for tests.

Finalized debug APK SHA-256:
`60ac8eef4d229cb7f22db53d8e7a14dd752a484c71550121a3ff5983d7f9f1f4`.
Finalized release assembly/vital lint passed; SHA-256:
`d31f0021d935e55827c5b9113016ca4fbd63f949e7bb5ea282be81e9496cff39`. Debug probe/test components remain excluded and the picker license
is included.
Local evidence: `ui-finalization-*` and `modes-*` in the same ignored evidence
directory. Earlier 65 JVM results are unchanged; that suite was not rerun for
this layout/test-seam refinement.

## Limits and next work

Binder availability and a started animation do not establish measured physical
RGB accuracy, all-zone mapping or true black/off. Steady Solid's inherited write
timer, full capture/error feedback, durable running
intent and rapid stop/start cleanup still need their planned work. Apply-color
output accuracy beyond the owner's live-preview observation was not measured.
Pure regressions cover frame construction; Binder submission is not calibrated
physical color or a kernel acknowledgement.

New-build movement was tested through Android's task API. A fresh test with the
owner's actual Pathfinder shortcut and enabled/disabled comparison remains
pending. Top rendering was captured; top touch/visibility with its panel awake,
larger fonts, task swipe/OEM cleaner, sleep/wake, long sessions, performance,
Ambient/audio and Android 14+ are outside this smoke test.
