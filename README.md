# Runelight

A personal LED utility for the **AYN Thor Max**, built around reliable lighting
and simple controls that fit the lower screen. Runelight is a fork of
[Bifrost](https://github.com/Pollux-MoonBench/Bifrost).

**Work in progress:** the current internal build supports Solid, Rainbow,
Breathing and Pulse. Ambient and independent sound reaction are planned and
remain disabled in the normal interface.

![Runelight quick controls on the Thor lower screen](docs/design/native-quick-tiles.png)

## Current controls

- A full-tile Lighting button with explicit Start/Stop and observed service state.
- Direct effect selection with no forced off/on pause between the four working effects.
- Independent left/right brightness: drag either color tile vertically.
- Inline color wheel with live LED preview while lighting is running. Apply saves;
  Cancel, Back or leaving the UI restores the saved values.
- Copy the other side’s color and brightness into the editor before applying.
- A center Speed tile for animated effects, dark/light appearance and saved settings.
- A foreground notification with Stop, plus a Quick Settings shortcut to the controls.

The configuration fits the Thor’s lower screen without scrolling at the tested
font size. The UI also supports the top screen and Android display movement;
actual Pathfinder shortcut acceptance and larger-font checks remain pending.

Lighting continues when the UI is closed or its task is dismissed. Explicit Stop
ends the session. Reopening the app does not automatically restart lighting after
an interruption; it offers Start to resume manually.

Plain effects need no screen-capture consent, Accessibility, Usage Access or
battery-exemption setup. Notification permission is optional on Android 13, but
is needed to show the normal notification-drawer controls.

## Ambient and sound roadmap

The first Ambient slice will sample a game on the **top screen**, independently
of where the controls are displayed. It will include independent side brightness,
smoothing that converges on unchanged content, true black, and a center
**Whole screen** toggle to choose full-image versus edge sampling.

**React to sound** will be a separate intensity option layered over the selected
effect. It will use playback capture rather than a microphone fallback.

Debug-only probes have demonstrated top-screen MediaProjection, explicit
Accessibility sampling of both displays, and playback-capture combinations on
the Thor. These experiments do not establish production Ambient/audio reliability.
Bottom-source support will expose its actual permission/backend requirements.
See the [POC plan](docs/poc-plan.md) and [capture findings](docs/ambient-probe-2026-10-20.md).

Presets, app profiles, schedules, boot start, plugins/external APIs, wallpapers
and battery/temperature effects are outside the POC. Active legacy service policies
and startup/API receiver registrations have been removed; the remaining inherited
source and resources still need the [planned cleanup](docs/cleanup-inventory.md).

## Build and test

Use **JDK 17**, the checked-in Gradle wrapper and the pinned Android SDK/dependencies.
See [BUILDING.md](BUILDING.md) for setup, APK installation and device-test commands.

```sh
./scripts/dev.sh doctor
./scripts/dev.sh unit
./scripts/dev.sh build
./scripts/dev.sh lint
./scripts/dev.sh release
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
Runelight uses application ID `io.github.marcinsz89.runelight`; debug builds add
`.debug`. Bifrost releases and its Obtainium source install the upstream app.

Latest recorded validation on the owner’s **Thor Max / Android 13**:

- 69 JVM tests and 12 focused Thor instrumentation tests passed.
- Debug and release assembly, including release shrinking and vital lint, completed.
- Full debug lint still fails on two inherited errors in the old audio analyzer
  and the Android 13 Quick Settings collapse call.

This is a development build. The lower-screen Home/lock/unlock restoration issue
remains open; it also reproduced with Settings and the installed Bifrost debug
baseline. Quick Settings panel interaction, production capture/audio, long gameplay
sessions and Android 14+ acceptance remain unverified. See the
[current service results](docs/service-state-2026-10-21.md) and
[device test matrix](docs/device-test-matrix.md) for the precise scope and limits.

## Development notes

- [Project guidance](AGENTS.md)
- [POC scope and next steps](docs/poc-plan.md)
- [UI design and screenshots](docs/design/README.md)
- [Inherited-code review](docs/reviews/2026-10-20-inherited-code.md)
- [Capture/service reference comparison](docs/reviews/2026-10-20-capture-service-references.md)

## Attribution and license

Runelight derives from [Pollux-MoonBench/Bifrost](https://github.com/Pollux-MoonBench/Bifrost).
Thanks to the upstream authors and contributors, including
[KuriGohan-Kamehameha](https://github.com/KuriGohan-Kamehameha).
Upstream attribution and derivative source history are preserved.

Licensed under [GNU GPLv3](LICENSE). See [third-party notices](THIRD_PARTY_NOTICES.md)
for the color picker and its shipped license.
