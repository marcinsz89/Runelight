# Build and test Runelight

The active repository is the checkout containing this file and `gradlew`.
On the owner's machine this is `/Users/mszydlowski/Developer/priv/Runelight`.
The accidental nested checkout has been promoted here; open **this directory**
in Android Studio or Fork. See [the repository layout record](docs/repository-layout.md).

## Toolchain

| Tool | Pinned baseline |
|---|---|
| Build JDK | 17 (verified locally: Microsoft OpenJDK 17.0.16) |
| Gradle | 8.13, checked-in wrapper |
| Android Gradle Plugin | 8.13.2 |
| Kotlin plugin | 2.0.21 |
| SDK | compile/target 36, minimum 33 |
| Build Tools | 35.0.0, selected by the current AGP |
| JVM bytecode | Java/Kotlin 11; keep separate from the JDK requirement |

Install JDK 17 and Android Studio's SDK Platform 36, Build Tools 35.0.0, and
Platform-Tools. Accept SDK licenses locally through SDK Manager. Gradle downloads
dependencies on a first build; the doctor command does not install anything.
Use the wrapper; no global Gradle installation is required.

The helper discovers JDK 17 on macOS if `JAVA_HOME` is unset, and the standard
macOS/Linux SDK paths if `ANDROID_HOME` is unset. For an explicit installation:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"  # macOS
export ANDROID_HOME="$HOME/Library/Android/sdk"   # macOS default
./scripts/dev.sh doctor
```

On Linux, set `JAVA_HOME` to the installed JDK 17 directory and `ANDROID_HOME`
to the SDK directory (commonly `$HOME/Android/Sdk`). An explicitly selected
non-17 JDK is rejected rather than silently changed.

In Android Studio, select JDK 17 as the Gradle JDK and the local SDK. Machine
paths belong in environment variables or ignored `local.properties`, not Git.
The helper uses the SDK environment/default paths; set `ANDROID_HOME` explicitly
if Android Studio's `sdk.dir` points elsewhere.

## Everyday commands

Run from this checkout:

```sh
./scripts/dev.sh doctor
./scripts/dev.sh unit --tests 'com.moonbench.bifrost.tools.LedFrameStateTest'
./scripts/dev.sh unit
./scripts/dev.sh build
./scripts/dev.sh lint
./scripts/dev.sh release
```

`./scripts/dev.sh check` combines JVM tests, debug assembly and full debug lint.
The 1.0 cleanup passes this check without lint suppression. Additional
Gradle arguments, such as `--offline`, `--rerun-tasks` or `--stacktrace`, pass
through the helper. Offline mode requires previously cached dependencies.

The helper only selects the environment and invokes the checked-in wrapper.
It does not use GitHub, install/launch an app, modify device settings or configure
production signing. Gradle uses the standard debug keystore, creating it locally
if absent. In Codex, request normal sandbox escalation if shared Gradle
caches or dependency downloads are denied; do not change permissions globally.

| Output | Path |
|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Unsigned release APK, without local signing properties | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| JVM test report | `app/build/reports/tests/testDebugUnitTest/index.html` |
| Full lint report | `app/build/reports/lint-results-debug.html` |
| Release mapping | `app/build/outputs/mapping/release/mapping.txt` |

Release uses R8 and resource shrinking. `assembleRelease` also runs vital lint,
which covers a different subset from full debug lint. Keep the exact APK and its
mapping together in local evidence when diagnosing release traces.

Runelight starts its own display version at **1.0.0** (`1.0.0-debug` for debug).
Android `versionCode` is **17**, continuing above the inherited value 16 so this
build can update the existing Runelight installation. Keep future version codes
increasing independently of the public version name. The app IDs and signing
configuration are unchanged.

## Retained tests

The 1.0 cleanup has 62 JVM tests for sampling/color, audio envelopes, consent,
output frames/backpressure, runtime state, notifications and saved effect names.
Manifest contracts parse Gradle's merged debug/release manifest instead of source
strings; the test task tracks that XML as an input. Tests for retired features
were removed intentionally alongside their production code.

`app/src/androidTest/.../RunelightActivityTest.kt` covers effect/draft restoration,
independent levels, the mode-specific Speed tile, copy/Cancel/Apply, gestures and
real running-mode acknowledgement on the Thor. `AnimationOutputTest` runs the
real animation timers and driver against an in-memory Binder: it checks temporal
frames, side scaling, settled output and Stop without writing physical LEDs.
Merged-manifest checks verify packaging, not Android lifecycle behavior.

`LightingServiceLifecycleTest` exercises explicit Start/Stop, task removal,
durable intent, ignored legacy commands and destruction/recreation without
automatic resume. See the [current results](docs/service-state-2026-10-21.md).

Build and run the instrumentation suite on the explicitly selected Thor:

```sh
./scripts/dev.sh build :app:assembleDebugAndroidTest
"$ADB" -s "$THOR_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "$THOR_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$ADB" -s "$THOR_SERIAL" shell am instrument -w -r \
  -e class com.moonbench.bifrost.RunelightActivityTest \
  io.github.marcinsz89.runelight.debug.test/androidx.test.runner.AndroidJUnitRunner
```

These tests select an active display at runtime. Espresso 3.5 click injection on
the Thor targets display 0, so the lifecycle tests invoke real control callbacks
on the Activity thread. Separately verify touch input using explicit display IDs;
do not interpret callback tests as physical input coverage.

## Retained test and diagnostic tools

Keep behavior regressions under `src/test` and `src/androidTest`: they protect
capture/recorder ownership, Stop, four-zone output and the latency fixes. They do
not ship in the normal debug or release app. The separate test APK contains the
bounded `TonePlaybackFixtureActivity`, including a changing-picture/beat mode.
Capture capability tools under `src/debug` remain useful for bottom-source and
interruption acceptance. They have no native-controls route and are excluded from
release; release manifest, R8 mapping and DEX were checked on 2026-10-21.

`VendorOutputTimingProbeTest` is a manual hardware benchmark, skipped in ordinary
runs. Explicitly Stop lighting before running it; it temporarily writes modest
test colors and finishes with black. To repeat the paced-writer load check:

```sh
"$ADB" -s "$THOR_SERIAL" shell am instrument -w -r \
  -e runelightVendorTiming true \
  -e class com.moonbench.bifrost.VendorOutputTimingProbeTest#measurePacedProductionWriterUnderCombinedLoad \
  io.github.marcinsz89.runelight.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The aggregate result is the target app's `files/paced-output-timing.json` (read
with `run-as`). The separate ordered one-way benchmark records its temporary
marker paths in `files/vendor-output-timing.json`; collect those timestamps and
remove only the listed markers after completion. Keep all collected evidence,
APK copies and vendor binaries under ignored `local-test-results/`.

## Current validation and warnings

The 1.0 cleanup passes all 62 JVM tests, debug/release assembly, release R8/resource
shrinking, vital lint and full debug lint. The old analyzer's `MissingPermission`
error is removed with that component; no lint baseline or suppression was added.
The Android-test APK compiles, including a new idempotent old-alarm-token
retirement check. It has not been run on the Thor after this cleanup.

See [the cleanup report](docs/cleanup-1.0-2026-10-21.md) for exact commands,
APK inspection and remaining warnings. Earlier notification/menu and sound
checks are in the [shortcut](docs/shortcuts-background-2026-10-21.md) and
[Beat](docs/beat-mode-2026-10-21.md) reports. Historical baseline failures remain
recorded in [the baseline record](docs/baseline.md).

## Test layers during development

1. **JVM behavior tests:** sampling/color math, settling and output deduplication,
   per-zone frame construction, audio envelope, state transitions, explicit Stop
   and late-callback rejection. Use fake clocks/dispatchers/output sinks.
2. **Android framework tests:** Activity recreation, alert teardown, control state
   synchronization, runtime permissions and service lifecycle. Add focused
   instrumentation tests as these boundaries are changed. A JVM Android runtime
   dependency is an implementation choice to justify, not an assumed baseline.
3. **Android 13 Thor:** physical screens and LED zones, vendor binder behavior,
   source routing, game playback capture, Recents/cleaner and sleep/wake.
4. **Android 14+ compatibility:** consent consumption/revocation and foreground
   service transitions on a matching emulator/device. The currently installed
   AVD is `Medium_Phone_API_36.1`; there is no Android 13 AVD configured yet.

An emulator lacks the AYN vendor binder and the Thor's physical display routing.
It can test graceful hardware-unavailable behavior, not actual LED correctness.

## Device session

Enable Developer options / USB debugging and authorize the host on the Thor.
Connect one intended device. Start with read-only identification:

```sh
export ADB="$ANDROID_HOME/platform-tools/adb"
"$ADB" devices -l
export THOR_SERIAL='replace-with-the-device-serial'
"$ADB" -s "$THOR_SERIAL" shell getprop ro.build.version.release
"$ADB" -s "$THOR_SERIAL" shell getprop ro.build.display.id
"$ADB" -s "$THOR_SERIAL" shell wm size
"$ADB" -s "$THOR_SERIAL" shell wm density
"$ADB" -s "$THOR_SERIAL" shell dumpsys display
```

Always use the serial explicitly. Stop other LED controllers manually before
testing. Record display IDs from `dumpsys display`; do not hardcode bottom as 4.
Record whitelist/battery restrictions and Pathfinder state as test variables.

**Current package identity:** debug is `io.github.marcinsz89.runelight.debug`;
production is `io.github.marcinsz89.runelight`. The code namespace remains
`com.moonbench.bifrost`, and the new launcher is `RunelightActivity`.
The historical debug baseline `com.moonbench.bifrost.debug` may remain installed
alongside it; it is a separate app. Do not uninstall it or clear its data to
resolve a signing conflict. Current build outputs contain the new UI.

When intentionally testing Runelight, the explicit commands are:

```sh
"$ADB" -s "$THOR_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "$THOR_SERIAL" shell am start -n io.github.marcinsz89.runelight.debug/com.moonbench.bifrost.RunelightActivity
"$ADB" -s "$THOR_SERIAL" shell dumpsys activity services io.github.marcinsz89.runelight.debug
"$ADB" -s "$THOR_SERIAL" shell dumpsys media_projection
```

These examples install/launch only when deliberately invoked; no installation
is part of `dev.sh`. A debug APK is not a production update.

For a bounded reproduction, keep logs local and stop logcat with Ctrl-C after
the incident. Do not clear the crash buffer before collecting it:

```sh
mkdir -p local-test-results/session
"$ADB" -s "$THOR_SERIAL" logcat -b crash -d -v threadtime > local-test-results/session/crash.txt
"$ADB" -s "$THOR_SERIAL" logcat -v threadtime > local-test-results/session/logcat.txt
```

Reproduce each scenario independently using [the device test matrix](docs/device-test-matrix.md).
Mark unrun checks BLOCKED/NOT RUN, with the reason. Do not call a window-leak
allocation trace proof of a fatal crash or blame Pathfinder without comparison.

## Private repository and signing

Use only `marcinsz89` / `marcinsz89@gmail.com`. Fork is signed into the private
account according to the owner; `gh` has a separate work-account login. A private
username in a remote URL and `git user.email` do not prove token identity.
Verify the actual account before network operations; no GitHub access is needed
for cached local builds. Do not print credentials to diagnose authentication.

The repo already supports optional ignored `keystore.properties` and keystores.
They are not needed for debug or unsigned release validation. Do not generate,
copy or reuse a production key as part of setup. Keep GPL notices and source
attribution in derived builds.
