# Port Royale 2D Tiny — working notes

Kotlin/libGDX (LWJGL3 desktop), Gradle multi-module: `core` (game logic) + `lwjgl3` (launcher).
Windows-first. Current version: see `gradle.properties` (`appVersion`).

## Экономия контекста

- Перед изучением кода (поиск, больше 2 файлов, «как это устроено») делегируй сабагенту `Explore`, а не читай файлы сам.
- Сборку и тесты запускай через сабагента `build-runner`. Полный лог Gradle сам не читай.
- Рутинные правки по точному плану (однотипные изменения, шаблонный код, переименования) отдавай `implementer`, потом проверь diff и собери через `build-runner`. Игровую логику и архитектуру делай сам.
- Сам открывай только те файлы, которые собираешься менять.
- Если задача разрослась (много шагов, большой контекст), предложи пользователю: `/handoff` → `/clear` → `/resume`.

## Build / run

```
export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
./gradlew.bat -q :core:compileKotlin :lwjgl3:compileKotlin   # compile check
./gradlew.bat -q :core:test                                   # unit tests (run after EVERY change)
./gradlew.bat -q :lwjgl3:run                                  # launch (run in background)
./gradlew.bat -q :lwjgl3:shadowJar                            # distributable: lwjgl3/build/libs/port-royale-2d-tiny-<version>.jar
./gradlew.bat -q :core:reference                              # regenerate Reference/ tables from the code
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

## Reference — look here before reading code or opening sprites

`Reference/` holds what the game *contains*: every hull with its stats and its appearance, the
ten goods, how a price is computed, standing, warehouses, loans, captains. **Check it first.**
Opening a PNG to recall what a Brigantine looks like, or grepping `ShipType.kt` for a price, is
exactly what it exists to replace — and an image read costs far more than a table lookup.

If it does not answer the question, read the code (and then consider whether the answer belonged
in `Reference/`). Its numeric tables are generated — see `Reference/README.md` and the release
workflow below; the prose around them is hand-written and safe to edit.

Not to be confused with `Project design/`, which is *why* and *what next*, not *what is*.

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

Three traps specific to this codebase, and how to sail the real map inside a test
instead of reasoning about movement: [docs/testing.md](docs/testing.md). Read it before
writing a test.

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

Any new or replaced picture goes through the `art` skill (`.claude/skills/art/SKILL.md`,
also `/art`), even a one-off. The pipeline, the measured chroma-key recipes, the
mipmap/bleed rule and the shot mode: [docs/artwork.md](docs/artwork.md).

## Release workflow

What to touch for a version bump, in what order, and why the tag is CI's to create and
not yours: [docs/release.md](docs/release.md). Only when the user explicitly asks to
release a version.

## Architecture gotchas worth knowing before touching rendering code

ShapeRenderer's leaking projection matrix, zoom-constant on-screen sizing, `UiScale`,
the one place the UI font is generated, and the two independent map scales:
[docs/rendering.md](docs/rendering.md). Read it before touching rendering code.
