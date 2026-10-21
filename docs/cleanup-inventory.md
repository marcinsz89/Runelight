# 1.0 keep/remove inventory

Updated 2026-10-21. The final source/resource cleanup is implemented; validation
and limits are recorded in [the cleanup report](cleanup-1.0-2026-10-21.md).
Paths below are relative to `app/src/main/java/com/moonbench/bifrost/`.

## Keep

| Component | Ownership / dependencies | Verification |
|---|---|---|
| `RunelightActivity`, `ui/*`, native resources and fonts | Lower-screen controls, saved configuration, inline editor; AppCompat, XML Material components, ColorPickerView | Native UI regressions; physical lower/top acceptance |
| `ambient/*`, `audio/*` | Service-owned top capture and playback overlay; shared projection and explicit cleanup | Sampling/envelope/consent JVM tests; Android recorder/capture tests; Thor color/audio checks |
| `animations/{LedAnimation,LedAnimationType,StaticAnimation,RainbowAnimation,BreathAnimation,PulseAnimation}` | Five supported effects including native Ambient | Stored-name compatibility, Android output/handoff regressions |
| `services/{LEDService,LightingRuntime,LightingIntentStore,LightingNotice,LightingNotifications,BifrostTileService}` | Active resource/state ownership, separate wanted intent, direct Stop and existing Quick Settings identity | State/notice tests, service lifecycle, parsed merged-manifest contract |
| `tools/{LedController,LedFrameState,LatestValueWriter,LedOutputDispatcher,DeviceInfo,CrashReporter}` | Serialized complete frames, bounded latest-frame queue, local diagnostics | Frame/queue tests; Android backpressure and opt-in vendor timing probe |
| `BifrostApp` | Local crash handler and cancellation-only migration for earlier schedule tokens | Android migration regression; no receiver or new alarm |
| Debug capture probes, JVM/Android behavior tests and test-APK tone fixture | Independent diagnostics; no route from normal UI | Compile debug/test APKs; prove debug fixtures absent from release |
| GPL/upstream attribution and shipped font/color-picker notices | Derivative licensing | Source and APK notice inspection |

## Removed

- Old main/onboarding screens, dialogs, preset artwork and import/export/backup UI.
- Profiles, charging/low-battery/adaptive policies, external API/leases/plugins,
  plugin store and package queries.
- Schedules, boot/package/time receivers and startup controllers. The one-purpose
  alarm cancellation migration preserves the old PendingIntent identity without
  depending on the removed receiver or deleting preferences/files.
- Wallpaper service and settings; obsolete production Accessibility provider,
  screen/audio analyzers and unsafe performance profiles.
- Battery/CPU/audio-only and other effects outside the five-effect scope, their
  enum entries, unused service constants and effect hooks.
- Associated layouts/icons/strings/resources, manifest entries and obsolete unit
  tests. The remaining manifest test parses Gradle's actual merged debug XML.
- Network/wallpaper permissions, Compose Material3, the unused direct
  ConstraintLayout dependency and plugin-only JVM JSON parser. Material's
  transitive ConstraintLayout remains. Shared AndroidX/coroutine versions are
  preserved explicitly where removing Compose would otherwise downgrade them.

No preference files, reports, user-created files or saved controls are deleted.
The Quick Settings service keeps its existing component name. The code namespace
and application IDs remain unchanged.

## Freeze

None. No retired component has a concrete restoration need; Git history contains
its source. The independently maintained debug probes cover capture experiments.
There is no frozen source hidden inside a compiled source/resource set.

Stereo playback reaction is deferred to the next version. Bottom-source capture,
long game sessions and broader wake/lock/OLED behavior remain device acceptance
work; source cleanup does not establish those results.
