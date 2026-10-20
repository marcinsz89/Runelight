# Proposed design — Quick tiles

Selected by the owner on 2026-10-20; then refined to give Runelight its own identity.

- [Interactive standalone mockup](quick-tiles.html) — open locally in a browser.
- [Editable source fragment](quick-tiles.fragment.html).
- [Saved preview image](quick-tiles.png).

![Runelight Quick tiles proposal](quick-tiles.png)

The files are design references. Values, readiness messages and LED colors are
illustrative; the mockup does not read audio, capture a display or control LEDs.
Its two preview rings represent desired colors, not hardware telemetry.

A first [native UI slice](../native-ui-increment.md) now implements the Quick
tiles direction. Its [Thor screenshot](native-quick-tiles.png) shows real native
controls. Ambient and sound remain unavailable in that internal build.
The native layout now supersedes the original mockup's redundant Current look
tile: sound sits beside Lighting, followed by the effect strip and full-width
brightness. The HTML/image above preserve the earlier proposal.

## Visual direction

- Dark graphite surfaces, restrained lavender accent, simple light variant.
- **ᛞ Dagaz** day/daylight motif as the mark; no lightning logo. The prototype
  uses a Unicode glyph; the Android asset should render consistently without
  relying on the device's installed rune font.
- Flatter cards, modest corners, sentence-case labels and a unified effect strip.
  ThorTune inspired the directness and large controls; the refined styling is
  specific to Runelight. No theme packs, artwork or decorative UI animation.
- Plain labels throughout: Lighting and Start/Stop. No "Call Heimdall" or
  mythology-based names that require learning what a control does.
- Edit colors inline, preserving unfinished values across recreation. Apply
  explicitly; keep Stop accessible while editing.

## Main screen

| Region | Purpose |
|---|---|
| Header | Rune/name and light/dark appearance action |
| Lighting tile | Large direct Start/Stop control; reflects acknowledged engine state |
| React to sound tile | Beside Lighting; separate on/off option; sensitivity shown only when enabled |
| Effect strip | Ambient, Solid, Rainbow, Breathing, Pulse; changes directly |
| Brightness card | Full width below the effect strip; numeric value and slider |
| Context card | Relevant controls for the chosen effect |
| Footer | Continuation/status information derived from actual runtime state |

The selected segment is the effect indicator; no separate Current look summary
is needed. Lighting and sound stay accessible above the scrolling controls.

Effect controls:

- Ambient: capture Top/Bottom, response; saturation under Settings initially.
- Solid: left/right colors; no animation speed control.
- Rainbow: speed; no unused manual-color picker in the Android UI.
- Breathing and Pulse: left/right colors and speed.

The prototype still uses a shared generic color/speed context for Rainbow. Correct
that conditional visibility during Android implementation; the table above is
the intended behavior. Users never need to name/save a preset to change a setting.

## Sound is an overlay

The selected look remains the source of color and animation. The sound envelope
modulates its intensity; global brightness remains the overall ceiling. Black
Ambient content stays at zero, with sound either on or off. A sound toggle must
not replace the effect or reset its colors/source.

One sensitivity slider is enough for the POC. Silence floor and response defaults
remain tuning decisions in the plan. Request playback capture permissions when
sound is enabled; never switch to a physical microphone as a fallback.

## Lower-screen and lifecycle behavior

Design for the Thor lower display first. Measure its actual density, insets and
available dp rather than converting the mockup's CSS pixels directly to Android
dimensions. Use at least 48dp effective touch targets, readable text, labeled
controls and room for system bars. Reflow/scroll at larger font sizes as needed.
Keep Start/Stop accessible while settings are open.
The saved mockup's settings overlay currently requires closing it to reach the
main power tile; the Android implementation should retain a Stop action there.

The UI must also fit the top display, survive movement/recreation, and preserve
draft colors/selected effect without restarting valid capture. The UI's display
does not select the Ambient source. Avoid a forced relaunch tug-of-war with
Pathfinder or the launcher; test direct launch and moving separately.

## Honest status and permissions

- Starting / Running / Stopped describe engine state, not merely service existence.
- Waiting for capture permission / Capture lost provide a user reconnect action.
- Sound unavailable does not falsely show active audio reaction; the selected
  base effect can continue under the proposed fallback.
- Hardware unavailable is explicit. A one-way vendor transaction does not prove
  the physical LEDs reached a measured color.
- Settings updates are live; no stop/delayed-start cycle for parameter changes.
- Standard effects request no screen/audio/Accessibility/Usage Access permission.
  Ambient and sound ask only for their applicable capabilities at first use.
- Closing the UI continues lighting; explicit Stop always wins. The footer's
  continuation claim is valid only once that behavior is implemented and tested.

## Prototype verification and export

The refined concept was checked at desktop and narrow widths, including touch
targets, effect selection, independent sound, brightness, Start/Stop, Settings
and light appearance. Native Android results and remaining display/lifecycle
limits are recorded in [the UI increment](../native-ui-increment.md).
The standalone export is saved with the repo and needs no server, network or app
account. It embeds Lucide 1.8.0 from the local runtime, preserves its license in
[THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt), and omits the export wrapper's
unused tooltip CDN scripts. Its interactions are verified with network blocked.
The fragment is retained for future edits; the full HTML is the openable version.
When regenerating the export, repeat the offline bundling check rather than
silently restoring external scripts. The HTML asset does not ship in the APK.
