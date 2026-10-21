# POC cleanup inventory

Working inventory, started 2026-10-20 and updated 2026-10-21. These are removal
boundaries, not claims that the
cleanup has finished. Source roots below are relative to
`app/src/main/java/com/moonbench/bifrost/`. Preserve GPL attribution and existing
settings data; removing a runtime feature does not authorize deleting user files.

## Keep

| Component | Ownership / dependencies | Verification |
|---|---|---|
| `RunelightActivity`, `ui/ColorLevelTile`, native layout/colors/drawables | Current UI; `runelight_controls` saved configuration; ColorPickerView 2.3.0 and shipped license | Native UI tests, lower/top layout, draft and Stop behavior |
| `tools/LedController`, `LedFrameState` | Serialized vendor protocol and four-zone output; closed drivers reject late writers | Frame JVM tests, recording-Binder tests, actual Thor output |
| Static, Rainbow, Breath, Pulse animations | Retained plain effects; `LedAnimation`, Android Handler, driver | Output changes, independent levels, Stop cancellation, settled Solid idle |
| `LEDService`, `LightingRuntime`, `LightingIntentStore` | Retained effect/output ownership, observed runtime and separate durable intent; legacy policy engine removed | Rapid Stop/Start, task removal, output feedback, stale results, interruption/manual resume |
| `tools/CrashReporter`, basic app/theme/icon resources | Local diagnostics; no external reporting | Redaction/storage review and normal launch |
| Debug capture probe and separate test-APK tone source | Bounded investigation, no production route or LED output | Capability report; absent from release APK |

Keep reviewed capture/audio algorithms only after extracting them from legacy
callers into the new service-owned sessions. The existing `ScreenAnalyser`,
`AudioAnalyzer` and Ambient animations are not accepted wholesale: their lifecycle,
held-target smoothing and failure handling still need replacement or correction.

## Remove in dependency order

| Subsystem | Current callers / runtime hooks | Removal boundary and affected tests |
|---|---|---|
| Legacy entry UI/onboarding/dialogs | Manifest `MainActivity`, `StartupGuideActivity`; old tile and projection notification routes; `ServiceController`; preset/dialog/view helpers | Route retained entry points to native UI first. Remove old activities/layout/resources and Activity-owned delayed command controller with their last callers. Audit manifest activity tests. |
| Profiles and charging/low-battery/adaptive policies | `LEDService` battery receiver, brightness observer, legacy policy timer; `AppProfileManager`; old UI/startup/external/package-removal code; `PACKAGE_USAGE_STATS` | Remove policy ownership from service and all enabling routes together; plain effects must not depend on `bifrost_prefs`. Then remove manager, usage-access setup, observer/receiver and policy resources. |
| External API / leases / PIPBOY | Manifest exported `ExternalApiReceiver`, custom control permission; `external/*`; service external actions, snapshots, crossfade, lease timer and wake lock; plugin/profile users; `PackageRemovedReceiver` | Remove receiver/action/permission and engine branches with callers, then external types and contract tests. Remove `WAKE_LOCK` after confirming no retained capture need. |
| Plugin store / live policies | Manifest `PluginStoreActivity`; `plugins/*`; old UI, presets and service `LivePolicy`; network permission | Remove UI/repository/install/launch routes and live-policy engine dependencies together. Then remove `INTERNET`, unused package queries and plugin tests. No network path belongs in POC. |
| Schedules and boot startup | Manifest `BootReceiver`, `ScheduleReceiver`, `ScheduleActivity`; `schedule/*`; `HeimdallStartupManager`; package-replaced/boot/time-change actions; alarms | Remove schedule and boot hooks before deleting helpers. Retire persisted alarm registrations safely if any were created. Remove `RECEIVE_BOOT_COMPLETED`; update schedule tests intentionally. Do not implement an alternate restart watchdog. |
| Preset/artwork/archive workflows | `BifrostApp.onCreate` runs `PresetMigration`; `presets/*`, `BackupArchiveTransfer`, `ThemeArchiveTransfer`; old UI, profiles, schedules, plugins | Extract any retained plain configuration first. Remove app-start preset migration with its consumers, archives, image/media pickers and owned resources; retire preset migration/default tests with the feature. |
| Video wallpaper | Manifest `VideoLiveWallpaperService`, `res/xml/live_wallpaper`; `LiveWallpaperSettingsManager`, old UI and wallpaper permission | Remove service/settings/resources/callers and `SET_WALLPAPER`; update wallpaper manifest test. |
| Extra effects / performance tiers | Service factory, `LedAnimationType`, `PerformanceProfile`, old UI/presets/external validators | Remove Battery/CPU/PIPBOY/Chase/Strobe/Sparkle/Rave/Fade/standalone audio/AmbiAurora after their consumers. Retain only four plain effects plus the new Ambient pipeline and independent sound overlay. Update enum/legacy-default tests. |
| Old Accessibility provider | Manifest `BifrostAccessibilityService`, `res/xml/accessibility_service_config`; old analyzer/UI/plugin checks | Replace with the retained explicitly enabled bottom-capture provider only after its new ownership is tested. Keep top capture independent of this setup. Update accessibility manifest tests. |
| Old Quick Settings tile behavior | Manifest `BifrostTileService` label; service refresh calls; `HeimdallStartupManager`, `MainActivity` consent/start route | Retain the requested Notification Center shortcut in the plan; replace its old preset/startup route with current state/commands. Resolve the known tile lint error when this component is revised. |

The table records original dependencies and removal boundaries. The main manifest
still declares legacy activities, wallpaper and Accessibility; the boot, schedule,
external API and package-removal receivers have now been removed. Remaining legacy
sources still compile and must not be described as frozen. AndroidX/material dependencies
are shared with native controls and should not be deleted with old screens.
Check merged manifests, resources, DEX/R8 output and retained behavior after each
coherent deletion. No blanket lint suppression or dependency upgrade is needed.

## Freeze

None selected yet. If a concrete algorithm/reference is worth retaining after
replacement, move only the necessary source and attribution under
`reference/legacy/`, outside all Android source/resource sets. Record its original
dependencies, reason and restoration conditions there. A disabled setting or
unreachable screen does not establish that a component is frozen.

## Current ownership increment

The first bounded change removes delayed Stop cleanup and UI-task-dependent
continuation. A driver closes at teardown, so late writers cannot affect a newer
service session. Solid's frame timer sleeps after exact convergence and wakes on
edits. The [next state increment](service-state-2026-10-21.md) removes the service's
profile, charging, adaptive brightness, external lease/plugin and old projection
branches, adds durable wanted intent and an observed runtime model, removes four
receiver registrations and their obsolete permissions, and retires preset migration
at startup. The tile opens native controls instead of the old preset route.
Source/resource/dependency deletion and remaining legacy manifest components are
still pending. Nothing has been frozen; automatic recovery and the POC are not complete.
