# 1.0 cleanup — 2026-10-21

Scope: remove the inherited feature graph after the native UI, Ambient and sound
paths have replaced it. Stereo sound is deferred to the next version. This pass
does not change the layout, sampling/envelope math or paced vendor output timing.

## Ownership and removal boundary

- State stays in native saved controls, `LightingIntentStore` and service-owned
  `LightingRuntime`. Removing old UI/policy code must not erase preferences/files.
- Feedback remains observed lighting/capture/output state, direct Stop and recovery
  notifications. Parsed merged-manifest tests and actual APK inspection check the
  component/permission boundary; existing behavior tests check the retained engine.
- Blast radius includes old activities/dialogs, presets/import/export, external
  API/plugins/profiles, schedules/boot, wallpaper, battery/temperature/extra effects,
  old Accessibility/screen/audio analyzers and their resources/dependencies/tests.
- Timing stays in existing capture/audio/driver owners. No old startup hook,
  receiver or delayed controller remains. A cancellation-only migration matches
  the earlier schedule PendingIntent by its original identity and invalidates it;
  it never creates or schedules work. Quick Settings keeps its registered class
  name so existing tiles survive an update.

All removed components are retired, rather than hidden or frozen. No useful
reference warrants shipping or copying the unsafe old analyzers; source history
and the independent debug capture probes preserve the investigation material.
GPL/upstream attribution and third-party font/color-picker notices remain.

## Changes

Removed 61 production source files, 78 obsolete resource files and 11 obsolete
unit-test files (about 18,000 lines overall). The native source set retains 31
Kotlin files. Added merged-manifest contracts, a regression rejecting retired
saved effect names and an Android check for idempotent alarm-token retirement.
The remaining native `sound unavailable in this build` string was also removed.

Compose Material3 and its unused graph are gone. The native XML Material library
and color picker remain. Core 1.13.1, Activity 1.8.2 and LiveData 2.9.0 are declared
explicitly; focused constraints preserve the earlier shared versions of Collection,
Emoji2, ConstraintLayout, annotations and coroutines. An offline Android-variant
dependency comparison against the earlier Compose graph found **zero changed
versions among retained runtime modules**. This is dependency removal, not a
library migration.

## Verification

Local Java 17 checks:

- Scoped effect-name and merged-manifest regressions: 7 tests pass.
- `./scripts/dev.sh check --offline :app:assembleDebugAndroidTest :app:assembleRelease`:
  62 JVM tests pass; debug/test APKs and release R8/resource shrinking/vital lint pass.
- Full debug lint: **0 errors, 19 warnings**, no suppression/baseline. Remaining
  warnings concern vendor reflection, tool/dependency suggestions, existing layout
  weights, Kotlin/style suggestions and text formatting. The obsolete analyzer's
  permission error and all unused-resource findings are gone.
- Parsed actual debug/release APK DEX class definitions, manifests, permissions,
  resources and release R8 mapping: all 61 retired production types and their
  nested classes are absent. No Compose classes or retired layout/provider XMLs;
  no network, wallpaper, boot, usage-access or wake-lock permissions. Font and
  color-picker license assets are present in both APKs. Debug capture probes are
  present only in debug; the test tone Activity ships only in the test APK.
- `git diff --check`: passes.

A clean rebuild also passes with Java 17 and the wrapper:

```sh
./gradlew :app:clean :app:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease \
  :app:testReleaseUnitTest --tests com.moonbench.bifrost.ManifestContractTest --offline
```

This reruns all 62 debug JVM tests plus the three release manifest contracts.
There are no test failures/skips. A final check after moving constraints into the
version catalog also confirms zero retained dependency version changes. Commands,
local audit scripts/results and APK/manifest diagnostics are recorded under
ignored `local-test-results/2026-10-21-cleanup/`.

APK sizes compared with the saved pre-cleanup 1.0 builds: debug 31.66 → 13.67 MiB; release 2.87 → 2.37 MiB.

The cleanup validation did not interrupt the owner's earlier device session.
On the owner's subsequent request, `./scripts/dev.sh build --offline` passed,
the debug APK was installed with `adb install -r` (no app data clear), and native
controls were launched on the active lower display. The installed package reports
**1.0.0-debug / version code 17**. The owner subsequently approved amending the
cleanup into the 1.0 commit. No individual manual scenario results were supplied,
so this approval does not establish full device-matrix coverage. The new Android
alarm migration check is compiled, **not run**. Long-game, broader wake/lock/
OLED-cleanup and process-loss behavior remain device acceptance work.
Stereo remains deferred; nothing in this pass adds it.
