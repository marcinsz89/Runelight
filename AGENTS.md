# Runelight development guidance

## Project and scope

- Personal AYN Thor Max LED utility; the owner's device runs Android 13.
- The sole active checkout is `/Users/mszydlowski/Developer/priv/Runelight`.
  Do not create another nested `Runelight/` checkout. Earlier review material
  and disabled Git metadata are preserved under ignored `local-test-results/`;
  they are historical evidence, not working repositories.
- Read [the POC plan](docs/poc-plan.md), [design](docs/design/README.md), and
  relevant findings in [the review](docs/reviews/2026-10-20-inherited-code.md)
  before implementation. The original handoff is historical, not final scope.
- POC direction: Ambient, Solid, Rainbow, Breathing and Pulse; independent sound
  reaction; direct controls, dark/light appearance and saved last-used settings.
  Design for the lower screen first and support the top screen and UI movement.
- Use the selected Quick tiles design and Dagaz mark, with Runelight's own
  styling. No preset requirement to change a look.
- Use plain product labels: Lighting, Start/Stop, effect, brightness and sound.
  Do not expose inherited names such as "Call Heimdall" or "Ragnarok" in the POC.
- The native entry point is `RunelightActivity`; application ID is
  `io.github.marcinsz89.runelight` (debug suffix `.debug`). The code namespace
  remains `com.moonbench.bifrost`. The inherited MainActivity is internal legacy
  code, not the new launcher. Do not route new controls back through it.
- Keep schedules, plugins/external API, app profiles, wallpapers, artwork,
  preset libraries/import-export, boot start and battery/temperature modes out of
  the POC. Retire their dependencies and manifest entries deliberately.
- For capture/service changes, consult the [reference comparison](docs/reviews/2026-10-20-capture-service-references.md).
  Adapt bounded fixes, not entire comparator services. Basic effects need no
  special-settings checklist; request capture/audio capabilities in context and
  suggest battery exemptions only after demonstrated device interference.

## Private identity and source handling

- Only GitHub `marcinsz89` / author email `marcinsz89@gmail.com` may be used for
  this project. Verify the identity used before fetch/pull/push or GitHub API
  work. Fork login and GitHub CLI login are independent; don't use a work-account
  token as a fallback. Local work can continue if authentication is unavailable.
- Do not change global Git/account settings. Do not commit, push, publish,
  change repository visibility or deploy unless the user asks.
- Never commit tokens, signing keys, credentials or raw device logs. Machine SDK
  settings belong in environment variables/ignored local properties, not build
  configuration. Preserve GPLv3 and upstream attribution in derivative code.
- No source, APK, diagnostic or design uploads to external services by default.

## Build and verification

- Use **JDK 17**, the checked-in Gradle wrapper and pinned dependencies. Java/Kotlin
  bytecode target 11 is intentional and separate from the build JDK.
- `./scripts/dev.sh doctor` checks the local Java/SDK setup without downloads.
- `./scripts/dev.sh unit --tests 'package.TestClass'` runs a narrow JVM regression;
  `unit` runs all JVM tests; `build` produces debug; `lint` runs full debug lint;
  `check` combines those; `release` verifies R8/resource shrinking and vital lint.
- See [BUILDING.md](BUILDING.md) for setup, APK identities, reports and ADB steps.
- The inherited baseline has 60 passing JVM tests and known full-lint errors.
  Record those failures; do not add a broad lint baseline or disable lint to hide
  them. A passing release/vital-lint task is not a full-lint pass.
- Run narrow meaningful tests first. Add behavior regressions for fixes; avoid
  tests that merely assert source strings. Use pure JVM tests for math/state,
  Android lifecycle tests for Activity/service behavior and a real Thor for LEDs,
  physical displays, playback capture, OEM cleanup, sleep/wake and performance.
- Report commands, actual results and unrun/blocked tests. Emulator success does
  not establish Thor reliability. Redact logs; preserve exact APK/version/mapping
  when diagnosing obfuscated traces. Keep local evidence in ignored locations.

## Engine and lifecycle invariants

- The service owns active lighting state. The UI sends explicit commands and
  observes state; programmatic control synchronization must not issue commands.
- Explicit Stop wins and survives UI pause/recreation. Closing a task is not Stop.
  Background continuation must not depend on boot start or the external API.
- Separate saved configuration/wanted-running intent from runtime resources.
  Never persist capture grants, external leases or live recorder/display objects.
- One active effect owns output; one serialized driver applies complete per-zone
  frames. Preserve both sticks, both zones and distinct colors when batching.
  Static output becomes idle after settling; suppress unchanged final frames.
- Keep the latest sampled target and let smoothing converge even if new input
  frames are identical. True black stays zero through brightness/audio processing.
- Sound modulates the selected effect's intensity; it is not a separate effect
  family. Request playback permissions only when needed. Failed reads must end
  cleanly; release/unblock before joining; no urgent-priority retry spin.
- Capture backend/source selection is independent of the UI's display. Use a
  source the backend actually supports; expose unavailable combinations clearly.
- Own capture/recorder lifetimes explicitly, reject stale callbacks and propagate
  stopped/failed sessions to visible state. No silent Accessibility fallback or
  infinite retry loop. Respect Android 14+ single-use projection consent without
  misattributing that restriction to the owner's Android 13 launch incident.
- Dialogs follow the host lifecycle. Teardown is not a user acknowledgement.
  Activity moves/recreation must preserve settings and must not restart capture.
  The new color editor stays inline; preserve drafts through recreation and apply
  only on an explicit Apply action. Prefer avoiding additional windows.
- Treat missing vendor binder/output capability as a real failure; a service
  object alone is not evidence that capture or LEDs are operating.

## Change discipline

- Inspect -> scope -> implement -> verify -> report. Keep increments small and
  reversible. For lifecycle/concurrency/removal work, map state, feedback, blast
  radius and timing before editing. Use bounded retries and explicit cleanup.
- Reuse the established vendor protocol/effects where sound; avoid a wholesale
  rewrite, new UI framework or dependency upgrades as incidental cleanup.
- Before deleting a subsystem, trace shared types, startup paths, receivers,
  permissions and tests. Update tests intentionally as scope is retired. If legacy
  theme transfer remains, keep its settings independent of full backups.
- After the UI settles, run the explicit cleanup pass in the POC plan. Remove
  retired features completely. Freeze only useful reference components outside
  every compiled source/resource set, with no manifest entry, startup hook,
  dependency or runtime route. A hidden control or disabled Boolean is not frozen.
  Record the reason, dependencies and conditions for restoring frozen material;
  verify it does not ship in debug or release APKs.
- Preserve user changes and divergent upstream material. Keep fork identifiers
  and design changes out of unrelated upstream contributions.
- Persist repeated lessons as focused regressions or tooling; keep this file
  short. Use subagents only when explicitly requested by the user or instructions.
