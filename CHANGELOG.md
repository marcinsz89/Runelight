# Changelog

## 1.0.0 — 2026-10-21

First Runelight version, based on Bifrost with native controls for the AYN Thor.

- Lower-screen controls with dark/light appearance, independent side brightness,
  inline color editing and saved settings.
- Ambient from the top screen with Edges, Screen halves and Full screen sampling;
  colorful output, held-frame smoothing and exact black.
- Solid, Rainbow, Breathing and Pulse, with continuous effect handoff.
- Independent Off / Flow / Beat sound reaction on Ambient, Solid and Rainbow.
  Breathing/Pulse preserve the selected sound mode while pausing reaction.
- Three-dot sound indicator, Quick Settings shortcut, shortcut-placement menu,
  effect/sound notifications and a direct Stop action.
- Retired legacy screens, plugins/API, presets, schedules, wallpaper, extra effects
  and obsolete capture providers, with their resources, permissions and dependencies.
- Shared capture ownership and paced latest-frame output to prevent accumulating
  stale LED commands.

Version name is 1.0.0; Android version code increases to 17 for updates over the
existing internal installation. Local validation and known limitations are
recorded in [README](README.md) and the [device matrix](docs/device-test-matrix.md).
Full debug lint now passes without suppression. Bottom-source Ambient and broader
sleep/wake/OLED-cleanup/game acceptance remain open. Stereo sound reaction is
planned for a later version.
