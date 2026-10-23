# Port Royale 2D Tiny — working notes

Kotlin/libGDX (LWJGL3 desktop), Gradle multi-module: `core` (game logic) + `lwjgl3` (launcher).
Windows-first. Current version: see `gradle.properties` (`appVersion`).

## Build / run

```
export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
./gradlew.bat -q :core:compileKotlin :lwjgl3:compileKotlin   # compile check
./gradlew.bat -q :core:test                                   # unit tests (run after EVERY change)
./gradlew.bat -q :lwjgl3:run                                  # launch (run in background)
./gradlew.bat -q :lwjgl3:shadowJar                            # distributable: lwjgl3/build/libs/port-royale-2d-tiny-<version>.jar
```

`-q` suppresses the per-task log lines and the "Deprecated Gradle features" boilerplate that
otherwise prints on every invocation — only real output (errors, stack traces) shows. `JAVA_HOME`
must be exported per Bash call; it doesn't persist between separate tool invocations.

If a `NoClassDefFoundError` for a class that clearly exists shows up right after editing multiple
files, it's a stale Gradle daemon cache, not a real bug — `./gradlew.bat clean` before rebuilding
fixes it. Don't waste time debugging the "missing" class itself.

**The other cause of the same error: compiling while the game is running.** `:lwjgl3:run` loads
classes lazily from `core/build/classes`, so recompiling under a live JVM replaces that directory
underneath it and the next not-yet-loaded class fails to resolve — typically a `…UIKt` file class
holding a screen's top-level constants, and typically at the moment the player first opens that
screen. It looks exactly like a broken build and isn't one. **Don't run a compile or test task
while a play session is open**; wait for the window to close. (Cost of learning this the hard way:
one crash report and a lost session.)

## Before starting a roadmap milestone

Never start coding an M-numbered milestone from design prose alone — first produce the decision
sheet ("лист решений"): one page, six sections (правила / числа / краевые случаи / место в UI /
не делать в этой вехе / готово когда), numbered sequentially, every item a *decided proposal*
with its rejected alternative and a `[форма]`/`[константа]` tag by cost of being wrong.
Proposals, never questions; one message, then wait for a single reply with the user's edits.
Full format and procedure: the `veha` skill (`.claude/skills/veha/SKILL.md`), also runnable as
`/veha <milestone>`. Applies whenever the user says they're starting a milestone, even if they
don't invoke the skill by name.

Rationale: a paragraph of design prose hides ~7 undecided details. The ones about the *shape* of
a rule cost a rewrite when guessed wrong; the ones about numbers cost one line at M15. The sheet
splits them so review attention goes where it's expensive.

## Testing discipline

0. **Run `:core:test` after every change, without being asked.** ~458 JUnit 5 tests live in
   `core/src/test/kotlin`, mirroring the main package layout. They cover the pure logic only —
   economy (Market/MarketState/Player/Warehouse/Loan), ships (condition, upgrades, effective
   stats), combat (CombatState, FleetCombatResolver), fleet (Captain, FleetSimulation), world
   (GameClock, GameSpeed, MapProjection, Ports, AiShipFactory, WorldMap pathfinding), and
   SaveManager's `resolve*` save-compat functions. The Scene2D/rendering layer is deliberately
   NOT covered — it needs a live GL context, and is verified by actually running the game.
   Failures print with `exceptionFormat "full"`; per-test detail also lands in
   `core/build/test-results/test/*.xml`. Gradle caches a green run, so use `--rerun-tasks` to
   force a re-execution.
1. Compile check (`compileKotlin` on both modules).
2. Launch `:lwjgl3:run` in the background, redirecting output to a log file.
3. Grep-filter the log for `FAILED|Exception|Error|error:` (e.g. via Monitor) rather than reading
   the full Gradle output.
4. **A clean exit (code 0) is never treated as user confirmation that a change looks/works
   right** — it only means the process didn't crash. Always wait for the user to actually look at
   the running game (screenshot or description) before calling a visual/UX change done.
   Before asking, look yourself: `:lwjgl3:run --args="--shot=<screen>"` opens that screen in a
   fresh game, saves a PNG and exits (see "Artwork" below) — then Read the PNG. That catches the
   obvious misses so the user's look is spent on judgement, not on "it isn't there at all".
5. For interactive/visual bugs, don't ask multiple-choice diagnostic questions — launch the game
   and have the user reproduce it live. Abstract questions about a UI bug are usually harder for
   the user to answer than just showing you.
6. Batch several related small tweaks (e.g. "also change X, Y, Z") into one compile+launch+review
   cycle where feasible, instead of one full rebuild-and-relaunch round trip per tiny change.
7. New game logic ships with tests in the same change. New *rendering* code does not — see 0.

### Writing tests here: three traps

- **`MarketState`, `GameClock` and `GameSpeed` are singletons with process-wide mutable state.**
  Any test class that touches a price, a date or the speed multiplier needs a `@BeforeTest` that
  calls `.reset()`, or trades from one test leak into another's prices and the failure looks like
  a logic bug in whichever test happened to run second.
- **Combat rolls the shared `kotlin.random.Random`, which can't be seeded.** Don't assert on a
  single roll. Either rig the opponent so only one outcome is reachable (a 0-damage enemy can
  never win; a 100000-hull enemy can never lose; a captain starting below its caution threshold
  can never fire, since hull only falls), or size the sample so the assertion sits ~8 standard
  errors from the boundary. `CombatStateTest` and `FleetCombatResolverTest` show both.
- **`Captain.rollCandidate()` randomizes `tradeSense`**, which moves every price that captain
  pays or receives. Build captains by hand (`Captain(name, tradeSense = 0f, caution = …)`) in any
  test that asserts on gold or cargo.

`Pixmap` (and so `WorldMap`) needs libGDX natives but no GL context — `GdxNativesLoader.load()`
in a `@BeforeTest` is enough, hence `gdx-platform:natives-desktop` as a `testRuntimeOnly` dep.
`PortReachabilityTest` loads the *real* map PNG this way (plain `File.readBytes()` →
`Pixmap(bytes, …)`, since `Gdx.files` needs a backend).

### Sailing the real map in a test — do this instead of reasoning about movement

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

## Diagnostics — use these before reading code to guess

Everything the running game records lands in one place: `~/PortRoyaleTiny/diagnostics/` (session
logs, crash reports, screenshots). **Ask the user for that folder before theorising about a bug.**

- `GameLog` — decisions and outcomes only, never per-frame. Adding chatty logging is how this
  stops being useful; if it fires every frame it does not belong here.
- `WorldWatchdog` — invariant sweeps every 5s (aground, not moving, non-finite position, phantom
  cargo). Reports each (subject, problem) **once**. New invariants go in `runWatchdog`.
- `GameRandom` — the single source of randomness. **Never introduce `kotlin.random.Random` or
  `MathUtils.random` into game logic**; it makes outcomes unreproducible and forces tests into
  statistical margins. `GameRandom.withSeed(n) { … }` gives exact, repeatable outcomes in tests.
- `F3` in game shows the seed and the nearest AI ship's real state; `F12` grabs a screenshot.

```
./gradlew.bat :core:simulate    # headless economy, ~30 simulated hours in a second, CSV out
```

`BalanceSimulation` is tagged `simulation` and excluded from `:core:test` deliberately — it
asserts nothing, because what the right numbers are is what M15 has to decide. Its axis is **real
hours of play, not game days**: the clock runs 10x, so one game day is 2.4 hours at the keyboard,
and plotting game days puts every threshold out by a factor of 144.

## Artwork

Any new or replaced picture goes through the `art` skill (`.claude/skills/art/SKILL.md`, also
`/art`), even a one-off. In short:

- Sources and mockups in `art-src/`, never in `assets/` (that folder ships inside the jar).
- Sprites are produced by `tools/art/art.py` (`slice` a sheet, `bleed` a single cut-out,
  `preview` edges) — never cropped or cleaned by hand. Python deps: `tools/art/requirements.txt`.
- Painted art is drawn with mipmaps + Linear filtering, which only looks right if the RGB under
  transparent pixels matches the edge (`bleed`); otherwise every sprite gets a white or black rim.
  Ships load through `ui/ShipArt.kt`, icons through `IconFactory.buildArtIcon` — never a bare
  `Texture(Gdx.files.internal(...))`, which gets Nearest and no mipmaps.
- `AssetIntegrityTest` (part of `:core:test`) fails on missing paths, opaque backgrounds, unbled
  edges and source files left in `assets/`.
- Shot mode, for looking at a screen without a person at the keyboard:
  ```
  ./gradlew.bat -q :lwjgl3:run --args="--shot=market --shot-port=Havana --shot-seed=1"
  ```
  Screens: menu, sea, hub, market, shipyard, status, fortress, warehouse, tavern, docks. Also
  `--shot-out=<png>` (default `lwjgl3/build/shots/<screen>.png`, relative to `lwjgl3/`),
  `--shot-frames=<n>` (30), `--shot-size=<w>x<h>` (1280x720). It prints `SHOT <path>` and exits;
  it always starts a new game (never a save) and writes no session log. Parsing lives in
  `diagnostics/ShotRequest.kt`, staging in `PortRoyaleGame.stageShot`.

## Release workflow

When cutting a version bump (`vX.Y` or `vX.Y.Z`), touch exactly these:
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

## Architecture gotchas worth knowing before touching rendering code

- **ShapeRenderer's `projectionMatrix` is mutable, persistent state.** Any `shapeRenderer.begin()`
  must have `.projectionMatrix` explicitly (re)set immediately before it, every time — it silently
  leaks whatever the last draw call set (e.g. the minimap camera) into unrelated draw calls
  otherwise. This has regressed more than once; don't remove that line during refactors.
- **Constant on-screen size regardless of camera zoom**: `worldUnitsPerScreenPixel()` in
  `PortRoyaleGame.kt` (`camera.viewportWidth * camera.zoom / viewport.screenWidth`) converts a
  target screen-pixel size to the world-space size needed to render at that size at any zoom.
  Multiplying a *world-unit* constant by `camera.zoom` directly does NOT give a sane result -  it
  only keeps the size *consistent*, not *reasonably sized*, since the result depends on whatever
  zoom level the base constant happened to be eyeballed at.
- **`UiScale`** (`core/.../ui/UiScale.kt`) drives all Scene2D screens' text/button size, scaled to
  the actual window width (clamped) so the default 1280x720 launch still fits. Every screen's
  `resize()` must call `UiScale.applyResize(stage, width, height)`, not
  `stage.viewport.update(...)` directly.
- **`UiSkinFactory.generateFont()`** is the one place the UI font (Cormorant Garamond, a variable
  TTF) is generated — it fakes bold via a thin FreeType border (the font has no real bold
  instance), and generates independent `BitmapFont` objects for anything outside the shared Skin
  (e.g. map-space port labels), since `BitmapFontData.scale` is global mutable state shared by
  every screen using the same font object.
- `MapProjection.MAP_WIDTH/MAP_HEIGHT` and the pathfinding grid's `cellSize` (10f, in `WorldMap`)
  are independent scales — arrive-thresholds and pathfinding constants are tied to the grid, not
  to overall map size, and must NOT be scaled when the map's world-unit size changes.
