# Release workflow

<!-- Split out of CLAUDE.md to keep it short: these notes are only needed for one kind
     of work, and loading them into every chat costs context for nothing. -->

When cutting a version bump (`vX.Y` or `vX.Y.Z`), touch exactly these:
- `Reference/` — run `./gradlew.bat :core:reference` first, and commit it if the tables moved.
  This is the one place the reference is kept honest; nothing fails a build if it is stale.
- `CHANGELOG.md` — new version section (Added/Changed/Fixed)
- `README.md` — status line, feature list, roadmap checkbox, credits if assets changed
- `gradle.properties` — `appVersion`
- `lwjgl3/src/main/kotlin/com/portroyale/tiny/lwjgl3/Lwjgl3Launcher.kt` — window title

Then: compile check → `:core:test` → `git add` specific files (never `-A`; `core/bin/` and
`lwjgl3/bin/` are untracked IDE build output, not meant to be committed) → commit → push `main`.
Only do this when the user explicitly asks to release/push a version.

**Do not create the tag by hand.** CI (`.github/workflows/release.yml`) reacts to `appVersion`
changing on `main`: it reruns the tests, builds the Windows exe with `jpackage`, creates the
`vX.Y.Z` tag itself and publishes the release (portable zip + shadow jar), taking the release
notes from this version's `CHANGELOG.md` section. A hand-made tag is at best redundant and at
worst points at a different commit than the artifacts, which CI then refuses to publish.
