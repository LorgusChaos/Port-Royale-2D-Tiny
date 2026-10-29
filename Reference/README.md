# Reference — what is in the game right now

Facts, not intentions: the hulls that exist, the goods that are traded, the numbers attached to
them. **Look here first.** Reading the source or opening sprites to answer "how fast is a
Brigantine" or "what does the Cutter look like" is the thing this folder exists to stop.

Three neighbours, and the difference matters:

- **`Reference/` (here)** — *what* and *how much*. Current state.
- **`Project design/`** — *why* and *what next*. Rationale, rejected alternatives, milestones.
- **`README.md`, `CHANGELOG.md`** — what the game can do, and how it got there.

## Contents

- **[ships.md](ships.md)** — the five purchasable classes, the longboat, upgrades and wear, and
  what every hull looks like (including faction and pirate colours).
- **[goods-and-economy.md](goods-and-economy.md)** — the ten goods, how a price is actually
  computed, standing with the four powers, warehouses, loans, captains.

## How this stays true

Every number here is **generated from the code it describes**, by
`core/src/test/kotlin/com/portroyale/tiny/docs/ReferenceDocsTest.kt`:

```
./gradlew.bat :core:reference
```

It rewrites only the regions between `<!-- generated:key -->` and `<!-- /generated:key -->`.
Everything around them is hand-written and is never touched — appearance, intent, the reason a
number is shaped the way it is. If a marker goes missing the task fails rather than writing the
table somewhere else, because a silently misplaced table is worse than no table.

The task is tagged `reference` and excluded from `:core:test`, so an ordinary test run never
rewrites files.

**When to run it:** as part of cutting a version bump, before touching `CHANGELOG.md` — see the
release workflow in `CLAUDE.md`. Deliberately not a failing test on every build: that would put
a regeneration step in the middle of every balance tweak, and the cost of this folder being a
day stale is close to nothing.
