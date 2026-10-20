# Repository root normalization — 2026-10-20

The sole active checkout is now:

```text
/Users/mszydlowski/Developer/priv/Runelight
├── .git/                 # Owner's Fork checkout metadata
├── AGENTS.md
├── app/
├── docs/
├── gradle/
├── scripts/
└── local-test-results/   # Ignored evidence and historical backup
```

Open this root in Fork, Android Studio and terminals. The accidental nested
`Runelight/Runelight` checkout was promoted, including all its uncommitted native
UI work, documentation, design assets, tests, local evidence and Git metadata.
The wrapper directory was removed only after it was empty.

The old parent review checkout is preserved locally under:

```text
local-test-results/repository-normalization-2026-10-20/parent-review/
```

Its former `.git/` directory is named `git-metadata/`, making the archive a
non-active historical snapshot. This preserves its history, tracked files,
original handoff/review notes and generated files without creating a second
discoverable repository. Do not edit or build this archive. The canonical review
and handoff copies remain accessible under `docs/reviews/`.

## Integrity and recovery evidence

- Both original checkouts were on `main` at
  `1baddf1644ff0d7edd1bd0f4ba02f7eb6c8e3cfa`; neither had linked worktrees or a
  Git object-store alternate.
- Moves preserved files rather than overwriting or resetting either checkout.
  Failure rollback was journaled before promotion completed.
- SHA-256/content and file-permission checks passed for **4,437 active-checkout
  files** and **4,417 earlier-checkout files**, including Git metadata.
- HEAD and the complete uncommitted Git status matched immediately before and
  after promotion. No commit, reset, branch change or push was performed.
- The active personal remote remains
  `https://marcinsz89@github.com/marcinsz89/Runelight.git`; local author email
  remains `marcinsz89@gmail.com`. No account/global Git settings were changed.
- `migration-result.json`, checksum inventories, the move journal and script are
  under the same ignored evidence directory. Preserve this backup until the owner
  explicitly decides it is no longer needed.

Project-local Gradle/Kotlin state was also preserved under
`relocated-build-state/` and regenerated at the corrected root: the old cache
briefly recreated an empty old-path `.gradle` directory during the first build.
No build cache should be treated as source or committed; `.kotlin/` is ignored.
Gradle's shared dependency cache and Android SDK were left in place.

After normalization, Java/SDK doctor passed, debug assembly passed, and all
**60 JVM tests passed** (no failures/errors/skips). A subsequent normal cached
build also passed without recreating the nested directory. Exactly one `.git/`
directory remains in the project tree. Fork was opened at the corrected root.

Local logs: `post-move-build.txt`, `fresh-root-build.txt` and
`normal-root-build.txt`. No application behavior changed during normalization;
the previously completed Thor UI tests were not rerun for a folder-only move.

## Cleanup policy

After the native UI settles, perform the explicit keep/remove/freeze inventory in
[the POC plan](poc-plan.md). Delete fully retired features, including their
runtime hooks and dependencies. Keep useful reference components outside all
compiled source/resource sets, with no startup/manifest route and a documented
reason for retaining them. Verify frozen material is absent from debug and
release APKs. The local repository backup above is recovery evidence, not the
future home for supported product components.
