# Development baseline — 2026-10-20

Historical setup record. Current application changes and verification are tracked
in [the native UI increment](native-ui-increment.md); APK hashes below identify
the original unchanged Bifrost builds, not current Runelight outputs.

This record is from the **owner's Fork checkout**, not the earlier parent review
checkout. Source: Bifrost 1.3.1 commit
`1baddf1644ff0d7edd1bd0f4ba02f7eb6c8e3cfa`. Application code, dependencies,
package identity, permissions and Gradle build configuration remained unchanged.
Only development guidance/tooling, ignored QA storage and design documents/assets
were added or updated. Nothing was committed or pushed.

## Environment

- Microsoft OpenJDK **17.0.16**, selected by the helper on macOS.
- Gradle wrapper **8.13**, AGP **8.13.2**, Kotlin **2.0.21**.
- SDK Platform **36** and Build Tools **35.0.0** present; minimum API **33**.
- Existing Gradle dependency cache reused with normal sandbox escalation.
- No connected ADB device. Installed AVD: `Medium_Phone_API_36.1`.
  It was listed, not launched. No Android 13 AVD is currently configured.
- Private GitHub identity remains required. No GitHub network operation was used
  for these checks. Fork and `gh` authentication are separate.

## Executed results

| Command/check | Actual result |
|---|---|
| `./scripts/dev.sh doctor` | PASS: JDK 17, SDK 36, Build Tools 35.0.0 and ADB found |
| `bash -n scripts/dev.sh` | PASS |
| `shellcheck scripts/dev.sh` | PASS |
| Helper with explicitly selected JDK 22 | Correctly rejects it, exit 1 |
| Helper with unknown subcommand | Correctly reports usage, exit 2 |
| `./scripts/dev.sh check` | Exit 1 because full lint fails; took 38 seconds |
| JVM tests within `check` | **60 tests, 0 failures, 0 errors, 0 skipped**, across 12 test classes |
| Debug assembly within `check` | PASS: APK generated |
| Full debug lint within `check` | **FAIL: 3 errors, 401 warnings** |
| `./scripts/dev.sh release` | PASS, 24 seconds: R8, resource shrinking, vital lint, unsigned release APK |
| `adb devices -l` | PASS command, empty device list |
| Emulator profile listing | PASS command; no emulator launched |
| Saved design, network-blocked browser | PASS: 0 external requests, 0 script errors, primary interactions pass |

The full-lint errors reproduce the initial review:

1. `MissingPermission`: permission may be rejected / no explicit permission check
   or SecurityException handling, `tools/AudioAnalyzer.kt:55`.
2. `MissingPermission`: absent `android.permission.RECORD_AUDIO`, same location.
3. `StartActivityAndCollapseDeprecated`, `services/BifrostTileService.kt:87`.
   The call has an API guard and needs a targeted compatibility decision or
   removal alongside the deferred tile. This is not a reproduced Android 13 crash.

No lint suppression/baseline, permission change or dependency upgrade was made.
Deprecation warnings and an unstripped dependency native-library notice remain.
A successful release/vital-lint task does not negate the full-lint failure.

Generated reports and APK locations are in [BUILDING.md](../BUILDING.md).

## APK identity and hashes

These are baseline builds, not implemented Runelight POC builds:

| Artifact | Identity | SHA-256 |
|---|---|---|
| `app-debug.apk` | `com.moonbench.bifrost.debug`, standard debug signing | `c22225f36cb8f5bfe990a15e657dbce7a262e8f5354096a3048d0d9d914691cd` |
| `app-release-unsigned.apk` | `com.moonbench.bifrost`, unsigned | `585bae6cff4c5b9bf0219eea00b03cb81868de4d3f82600052e16bca814a2526` |

No APK was installed, no production key was configured and no Android app was
launched. Changing the package ID is a proposed first implementation increment.

## Design export

The chosen refined Quick tiles source, screenshot and standalone export are
saved under [docs/design](design/README.md). The export embeds the locally
available Lucide library and omits unused external tooltip scripts. Browser
verification checks effect selection, independent sound reaction, Stop, Settings,
light appearance and the Dagaz mark, with external requests blocked. **Passed:
zero external requests and zero script errors.**

## Still unverified

**Later device run:** the baseline debug APK was subsequently built, installed
and launched. A screen-move failure was reproduced and diagnosed; see the
[device session report](device-session-2026-10-20.md). The setup/discovery records
below describe their earlier stages, not the final installation state.

### Device discovery follow-up — 2026-10-20

The Thor was subsequently connected and authorized in ADB. Read-only queries
confirmed AYN Thor, Android **13 / API 33**, firmware
`Thor_V1.0.0.377_20260206_165408_user`.

| Display | Observed logical ID | Landscape pixels | Logical density |
|---|---|---|---|
| Main / Built-in Screen | 0 | 1920 × 1080 | 369 dpi |
| Lower / Screen-2 | 4 | 1240 × 1080 | 369 dpi |

At the sampled moment, Android reported lower-display app space of 1240 × 1025
pixels: approximately **538 × 444 dp** at the observed density, before any
additional app-specific insets. This is a measured layout constraint, not a
fixed viewport guarantee. Discover display IDs at runtime rather than hardcoding
the observed values.

Installed: Pathfinder **1.1.0** (version code 21), ThorTune **1.1.2** (10102).
The package listing found no installed package matching Bifrost, DuoFrost or
Runelight. Installation does not establish whether Pathfinder is enabled or
causes the reported issue. No APK was installed or launched, no device settings
or lighting were changed, and all functional device-test scenarios remain
**NOT RUN**.

- Physical LEDs, all four zones and actual black/off output.
- Top/bottom launch, source routing, UI movement and Pathfinder interaction.
- Permissions, playback capture, lost projection and recorder failure on Thor.
- Activity/task/process/OEM cleanup, explicit Stop races and sleep/wake.
- Android 14+ projection-consent behavior.
- Two-hour stability, CPU/memory/power, capture/output rates and game frame times.

The saved [device matrix](device-test-matrix.md) initially started NOT RUN
throughout; later session results are now recorded there.
The original review's isolated math/controller checks are historical evidence,
not new device tests or checks re-run by this setup task.
