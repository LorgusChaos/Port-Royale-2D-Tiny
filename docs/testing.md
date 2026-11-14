# Writing tests in this project

<!-- Split out of CLAUDE.md to keep it short: these notes are only needed for one kind
     of work, and loading them into every chat costs context for nothing. -->

## Writing tests here: three traps

- **`MarketState`, `GameClock` and `GameSpeed` are singletons with process-wide mutable state.**
  Any test class that touches a price, a date or the speed multiplier needs a `@BeforeTest` that
  calls `.reset()`, or trades from one test leak into another's prices and the failure looks like
  a logic bug in whichever test happened to run second.
- **Every roll goes through `GameRandom`, so wrap the test in `GameRandom.withSeed(n) { … }`.**
  Combat (`CombatState`, `FleetCombatResolver`), the AI, raids and sea events all draw from it, and
  `RandomnessSourceTest` fails the build if anything in `core/src/main` takes a chance elsewhere.
  A seed makes an outcome exact and repeatable, so assert on it directly instead of sampling. Still
  prefer rigging the opponent when the test is about a rule rather than a number - a 0-damage enemy
  can never win, a 100000-hull enemy can never lose, a captain starting below its caution threshold
  can never fire - because a seed pins one outcome while a rig pins them all. The older
  sample-sized tests in `CombatStateTest` and `FleetCombatResolverTest` predate this; move them
  onto a seed when you next touch them.
- **`Captain.rollCandidate()` randomizes `tradeSense`**, which moves every price that captain
  pays or receives. Build captains by hand (`Captain(name, tradeSense = 0f, caution = …)`) in any
  test that asserts on gold or cargo.

`Pixmap` (and so `WorldMap`) needs libGDX natives but no GL context — `GdxNativesLoader.load()`
in a `@BeforeTest` is enough, hence `gdx-platform:natives-desktop` as a `testRuntimeOnly` dep.
`PortReachabilityTest` loads the *real* map PNG this way (plain `File.readBytes()` →
`Pixmap(bytes, …)`, since `Gdx.files` needs a backend).

## Sailing the real map in a test — do this instead of reasoning about movement

`Pilot` (`core/.../world/Pilot.kt`) holds the one piece of seamanship every hull shares: follow a
route, don't run aground. It needs nothing of a ship but `position`/`headingDeg`/`speed`/
`turnRate`/`path`/`pathIndex` (the `Pilot.Hull` interface — `AiShip` satisfies it as it stands),
and takes `isWater` as a lambda. So `PilotTest` sails real hulls across the real Caribbean with
no window, at several tick lengths, and asserts they arrive.

**Movement bugs are geometry, and guessing at them from a session log wastes hours.** Three real
faults were found this way, each invisible to the old constant-vs-constant tests:

- the arrival radius (16) was *smaller than every hull's turning circle* (21–27), so a waypoint
  off the bow could not be reached at all and ships orbited it at full speed — displacing nothing,
  which is what the watchdog was reporting;
- movement tested only the far end of a step, so a hull slipped diagonally between two corners of
  land — the one move A* explicitly refuses to plan;
- a tick is not a fixed length (real frame time × game speed), so at 10 fps on 20x a passage took
  1.8× longer than at 60 fps. `Pilot` therefore sails a tick in slices of at most half a cell and
  re-checks the waypoint each slice, which makes a long tick equal several short ones.

Write the failing voyage first. If a movement test passes before the fix, the diagnosis is wrong —
that happened here, and it stopped a plausible-but-wrong fix from shipping.
