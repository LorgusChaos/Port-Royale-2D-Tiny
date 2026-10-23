# Port Royale 2D Tiny

A small 2D hybrid of *Port Royale* and *Space Trader* - Caribbean trade, piracy, and naval
combat, deliberately kept small in scope for a solo/hobby project.

> **Status: v0.4.5.9 - early prototype.** Sailing, trading with a living dynamic market, random
> sea events, ship upgrades, combat, historical factions with reputation, a living map of AI
> ships that actually hunt down a target they've spotted, a small hired fleet of captains who
> fight/flee/evade for themselves, warehouses/property, loans, autosave, audio, and a full main
> menu with save/load are all playable in a rough form; art and content breadth are still early.
> See [Roadmap](#roadmap).

Inspired by *Port Royale* and *Space Trader* - not affiliated with or endorsed by either
franchise's creators or publishers.

## What's here in v0.4.5.9

- A hand-painted map of the Caribbean - jungle, mountain ridges, sandy shores and a turquoise
  shallow-water band along every coast - drawn over the real geography rather than an
  abstract/schematic map, at a scale where a voyage between ports takes real time.
- **The picture and the rules are two different files.** What a ship may sail on comes from a
  land/sea mask, not from the colours of the artwork, so the map can be painted however it likes
  and the sea stays exactly where it is meant to be.
- Water/land collision sampled directly from the map image's own pixels, so the sailable area
  always matches what's drawn - no separately maintained coastline data to drift out of sync.
- Manual sailing (WASD/arrows), blocked by coastlines, plus click-to-set-course autopilot (A*
  pathfinding around islands) - any manual input immediately cancels autopilot and hands control
  back. Clicking an invalid (land) destination shows a brief marker instead of silently doing
  nothing.
- **Courses are sailed as courses.** A route keeps a berth off the shore where the sea allows one
  and is laid as a handful of long legs rather than a point every few yards, because a ship turns
  inside a circle wider than that and cannot follow such a line at all - it would overshoot each
  mark in turn and crawl. A passage now takes within a few per cent of what the hull's own speed
  says it should, at any frame rate and any game speed.
- Mouse-wheel zoom, edge-of-screen panning and right-click-drag panning while sailing (both
  clamped to the map bounds), and a minimap showing the current view.
- Twenty-eight labeled ports, each belonging to its real 17th/18th-century colonial owner - Spain,
  England, France, or the Netherlands - from Veracruz and the logwood coast in the west out to
  Barbados and Dutch Guiana in the south-east. Arriving at one opens a city hub with icon buttons
  for:
  - a **market** - buy/sell ten goods with hand-drawn icons; a living supply level per port/good
    drifts over time and responds to the player's own trading, on top of the port's fixed
    historical bias and reputation with its owning faction. Goods notably cheap or scarce at a
    port show as small framed icons under its name on the map;
  - a **shipyard** - Ship Status (condition, upgrades, hull cleaning, repairs) is the primary
    screen; a "Buy a Ship" sub-screen offers six classes (Sloop/Cutter/Barque/Brigantine/Galleon/
    Man-of-War, each its own distinct hull livery) as a single row of cards;
  - a **Commandant's Office** - standing with each colonial power (see the reputation bars in the
    HUD, and the faction conflict below), plus a loan system: borrow one
    of three fixed sums with interest and a repayment term; missing the deadline doubles the debt
    and turns that faction hostile (a red-filling debt icon with a countdown sits above the
    minimap while a loan is outstanding);
  - a **warehouse** - buy one, expand it as an upgrade, and store goods and gold there via a
    Market-like transfer screen;
  - a **Tavern** - hire a captain (a choice of three, each with a name, portrait, and stats) to
    run an independent trade route for you, or re-crew one who's already on the payroll but
    between ships.
  A **Property** menu (icon under the minimap) lists everything stored across every city with
  anything in it, and a **Docks** screen (via the Shipyard) lists every ship you own that's
  currently in port, lets you rename them, and swap which one you're personally sailing.
- A small fleet beyond your own ship: buy additional hulls (named at purchase) and crew them
  with hired captains from the Tavern or Docks. A captain decides what to buy and sell on their
  own - never at a loss - the player only sets which two ports they run between. Their ship
  sails the same real, coastline-hugging routes as the player's own, visible on the map and
  clickable for a status popup (cargo, route, lifetime profit, redirect, recall); a HUD icon
  jumps the camera straight to any of them (zoomed in close). Every ship in the fleet has its
  own hull condition and fouling, exactly like your own active ship, and a captain pays for
  repair/cleaning out of the shared purse themselves when it's worth it.
- Danger for both you and your captains is a real thing happening on the map, not a hidden roll:
  a hostile ship that spots a target from a distance actually breaks off and sails after it -
  catching up means an attack with no warning, while a ship you simply crossed paths with instead
  offers a real choice ("Fight" or "Sail On"). A captain who sees trouble coming tries to dodge
  it first - running for whichever port is closer than the danger itself, or steering around it
  the same way ships already steer around land - and if caught anyway, fights for real (the same
  combat math as your own fights, just resolved instantly): win, flee, or lose the ship for good.
- You cannot be everyone's friend. Selling in a port raises your standing with the faction that
  owns it and lowers it with the other three, three times more gently - so every sale quietly
  takes a side, and an evenly spread trader earns nobody's friendship. The arithmetic means at
  most three of the four powers can be kept climbing at once; the fourth pays for it. Four
  reputation bars in the HUD (collapsible, notched at each tier boundary) show all of it moving
  in real time. Standing is not just a price modifier either: an unfriendly navy will pick a
  fight it thinks it wins, while a hostile one hunts you regardless. Your hired captains' sales
  count exactly like your own, so delegating trade never means dodging its consequences.
- A living map with regions rather than uniform traffic. Every ship lives around an anchor - its
  home port, or for pirates the lair - and picks where to go next weighted by distance from it, so
  Spanish colours dominate the Main, the Lesser Antilles are French, Dutch and English, and a
  crossing between them visibly changes company. It is a leaning, not a border: roughly one voyage
  in eight ignores the flag entirely, so anyone can turn up anywhere. Warships patrol out from
  their home port and back rather than criss-crossing the map like traders.
- **The Sea of Pirates**, the fogged and named stretch of the northern Gulf where no port stands.
  Pirates sortie from it to prowl the outskirts of a harbour, then sail home again. It sits there
  deliberately: a lair on a shipping crossroads is not a source of danger but a permanent blockade
  of the trade that passes through it, and that empty third of the map was doing nothing.
- **Standing rules of engagement.** Every ship in the world decides for itself whether to attack
  what it sees, by comparing real fighting power - the hull it has left times the damage it deals -
  and rolling against the odds that gives. Pirates prey on merchantmen and take on patrols they
  fancy their chances against; navies hunt pirates wherever they find them; merchantmen run for
  harbour. None of it depends on an event being active.
- **Pirates sail cruises**: out to a stretch of busy water, work it for a few minutes, then home to
  the lair. Only a pirate actually on station starts on shipping, which is what spreads them across
  the map instead of leaving them brawling outside their own front door.
- **Regional raids with a naval response.** A band gathers at the lair, sails out in line astern,
  and works a corridor between two ports. Each power musters its own squadron from its own harbour
  and reinforces after losses. Ships that go down leave a mark on the water until the raid ends.
- **Damage lasts.** A hull carries its wear from one fight into the next and only heals in home
  waters, so a squadron that has been fighting all afternoon is one pirates start fancying their
  chances against - and a warship can genuinely be sunk.
- **Ships answer their helm.** A course change takes time set by the class's manoeuvrability, and a
  ship loses way while coming round: a Cutter puts about in four seconds, a Galleon in nine.
- Pirates roll a random tier (1-5, hull size/speed and reward scaled to match) and flee rather than
  engage a ship clearly stronger than them, and vice versa - every AI ship's sailing speed comes
  from the same ship-type table the player and hired captains use, not a separate hand-picked
  number. Peaceful ships can be deliberately hunted down for loot. Ports are a safe harbor - no
  encounter ever triggers near one. Ships visibly dock - they disappear into a port on arrival (a
  slow purple glow marks where) and reappear only once you set off again. The world is already in
  motion when a game starts: ships begin partway through voyages, not all casting off at once.
- Random sea events: 30 invisible, one-time locations scattered across the water (respawning
  elsewhere after a delay once triggered) for classic encounters - a flavor-text bottle, a gold
  or goods chest, or a castaway who pays for a lift to a random port. Shown in a dismissible,
  centered notification; sailing and AI ships pause while one is open.
- Turn-based combat with animated ship sprites: cannonballs fly between ships, hits flash, shots
  can miss, maneuverability affects flee odds and evasion. Victory shows a dismissible reward
  notification (same style as sea events). Defeat is total and deliberately survivable: the
  victors take the ship, the cargo and every coin aboard and sail away, leaving the player adrift
  in the **ship's boat** - no hold, no guns, the slowest thing afloat, and never attacked by
  anyone, because sinking a man already in his boat is a dead end rather than danger. Make
  harbour, and buy a hull again. Warehouses and the rest of the fleet are untouched, so a
  merchant's standing capital survives what his own deck does not.
- An in-game clock, shown as a calendar date in the HUD alongside your gold balance, running 10x
  faster than real time by default - denominates loan terms and fleet voyage times. A HUD control
  (clock icon) cycles a 1x-20x speed multiplier over the whole simulated world (ship movement,
  the clock, market drift, hull fouling, sea events) independent of the UI itself, for skipping
  past the boring stretches of a voyage.
- Music and sound: a rotating sailing playlist, a combat track, a port-arrival cue, and SFX for
  cannon fire/hits/coins/clicks/menu transitions - all controllable from Settings (volume slider
  + mute).
- A main menu: **New Game** (name your captain), **Load**, **Save**, **Settings** (audio,
  fullscreen toggle, resolution), and **Exit**. `Esc` pauses and reopens the same menu mid-game,
  resuming exactly where play left off. Saves and settings persist as JSON files under the user's
  home directory; the game also autosaves every 10 minutes, and save/load lists show each save's
  date and time.
- New hand-painted ship and city-menu icon art (AI-generated): 5 ship tiers x 6 colors (white for
  the player, black for pirates, one color per colonial faction) and Market/Shipyard/Warehouse/
  Commandant/Tavern icons. The port hub itself is laid out directly over a matching AI-generated
  frame background (a wooden shopfront with a nameplate banner) instead of a generic panel - the
  port name and gold/cargo sit centered in the banner, and each destination's name is overlaid on
  its own icon's board rather than a separate label underneath. A period-style serif font
  (Cormorant Garamond) on a parchment-toned, bordered UI elsewhere; hand-drawn pictogram icons
  (goods, port marker) still get an automatic outline for a consistent look. The UI scales
  responsively to the window size, and map labels/icons hold a constant on-screen size regardless
  of zoom.

## Tech stack

- **Kotlin** on **[libGDX](https://libgdx.com/)** - a lightweight, free 2D-friendly framework
  rather than a full editor-based engine, targeting Windows first with Android/macOS ports
  planned.
- **Gradle** multi-module build: `core` (platform-independent game logic) + `lwjgl3` (desktop
  launcher).

## Running from source

Requires **JDK 17+**.

From the repository root:

```
./gradlew :lwjgl3:run        # Linux/macOS
.\gradlew.bat :lwjgl3:run    # Windows
```

First run downloads Gradle and dependencies, so it can take a minute.

### Tests

```
./gradlew :core:test        # Linux/macOS
.\gradlew.bat :core:test    # Windows
```

Covers the pure game logic - economy, ship condition and upgrades, combat maths, the fleet
simulation, save compatibility, and map pathfinding. The Scene2D/rendering layer is deliberately
left out: it needs a live GL context, and is checked by actually running the game. The same suite
runs on every push to `main` (see `.github/workflows/release.yml`).

### Diagnostics

Everything the game records about itself goes to **one folder**, next to the saves under your home
directory: `PortRoyaleTiny/diagnostics/`. Zip it and send it - that is the whole bug report.

| | |
|---|---|
| `session-*.log` | What the world decided and why: pursuits begun and abandoned, captains lost, standing crossing a tier, saves. Decisions only - never per-frame noise. Last five runs are kept. |
| `crash-*.txt` | Machine (including GPU and driver), the state of the world at that instant, the full exception chain, and the last events before it. |
| `screenshot-*.png` | `F12` at any time; also attempted automatically on a crash. |

In-game, **`F3`** toggles a developer overlay: frames per second, the session's random seed, and
the nearest AI ship's actual state (pursuing / patrolling / blocked, and for how long). Reporting
"the pirate is stuck, seed 8371, blocked=40s" is worth an hour of guessing from a description.

```
./gradlew.bat :core:simulate    # headless economy run, printed curves + CSV
```

Runs the trading economy with no window for thirty hours of simulated play in about a second, and
prints where wealth and reputation end up. It asserts nothing on purpose - deciding what the right
numbers *are* is what the balance pass is for - so it is excluded from the test suite and run by
hand after changing a constant.

## Building a distributable build

```
./gradlew :lwjgl3:shadowJar        # Linux/macOS
.\gradlew.bat :lwjgl3:shadowJar    # Windows
```

Produces a single self-contained runnable JAR at `lwjgl3/build/libs/port-royale-2d-tiny-<version>.jar`
(all dependencies and platform natives bundled in). Anyone with a JDK 17+ can run it directly -
`java -jar port-royale-2d-tiny-<version>.jar` - no Gradle or source checkout needed.

Released builds are produced by CI rather than by hand: bumping `appVersion` in
`gradle.properties` on `main` runs the tests, packages a Windows exe with a bundled JRE
(`jpackage`), tags the commit and publishes both the portable zip and the jar as a GitHub
Release.

## Controls

| Input | Action |
|---|---|
| `W` `A` `S` `D` / arrow keys | Sail manually |
| Left click on water or a port | Set an autopilot course |
| Any manual input during autopilot | Cancel autopilot, take back direct control |
| Mouse wheel | Zoom in/out |
| Cursor near a screen edge (while sailing) | Pan the camera |
| Hold right mouse button + drag (while sailing) | Pan the camera 1:1 with the cursor |
| Left click a peaceful ship | Chase it down (piracy) |
| `Esc` | Open the main menu; pauses the game and resumes exactly where you left off |

## Project structure

```
core/    platform-independent game logic (Kotlin)
  world/     map projection, water/land + pathfinding, port data, roaming AI ships, sea events
  factions/  colonial powers and reputation
  economy/   goods, player wallet/cargo, dynamic per-port market state
  audio/     music/SFX playback
  save/      JSON save-game and settings persistence
lwjgl3/  desktop (Windows/macOS/Linux) launcher
assets/  game assets (base map image, ship/tile sprites, music/SFX, font)
```

## Roadmap

Rough milestone plan, Windows first, with a runnable build checked at the end of each stage:

- [x] **M0** - project skeleton, a ship you can move on a test scene
- [x] **M1** - world map with real coastlines, manual + autopilot sailing, port markers
- [x] **M1b** - port UI and a basic per-port market (buy/sell, price differences across ports)
- [x] **M2** - ship sprite art, plus hand-drawn UI icons for goods/ships/menus; port/city art on
      the map itself is still simple markers
- [x] **M3** - turn-based ship combat, random encounters while sailing, now with sprites and
      animation
- [x] **M4** - ship types + shipyard, twelve ports and ten goods; economy balance still pending
- [x] **M5** - audio and main menu/save-load
- [x] **M6** - ship upgrades/condition, historical factions + reputation, a living map of
      roaming AI ships
- [x] **M6b** - a living per-port market economy, ships that visibly dock, and a UI/map polish
      pass (font, scaling, zoom-constant labels)
- [x] **M6c** - fixed ships rendering stern-first, six distinct purchasable ship classes instead
      of three
- [x] **M6d** - random sea events with a new dismissible notification UI, restructured shipyard
      navigation
- [x] **M7a** - packaging: a single distributable JAR (`:lwjgl3:shadowJar`)
- [x] **M8** - new ship/city-icon art; warehouses and a Property menu; loans with a debt-hostility
      consequence; autosave; a combat-result notification; a defeat rework (Wreck instead of a
      dead end); pirate tier variety and flee/engage behavior; an in-game clock
- [x] **M8c** - port hub redesign: a new AI-generated frame background replaces the generic panel,
      with the city name and gold/cargo centered in its nameplate, each destination's name
      overlaid on its own icon's board instead of a separate label, and a close (X) control
      replacing the old "Set Sail" text button *(this release)*
- **M8b** - Android/macOS builds - *moved to post-release* (decided 2026-10-22). Shipping on
      other platforms before the main project is released buys nothing. The *ability* to do it
      must not be lost, though: platform-specific code stays out of `core`, which is what the
      module split is for.

M9 onward follow a design pass recorded under [`Project design/`](Project%20design/) - the
"why", not just the "what" (design pillars, progression ceilings, the fleet/threat/warehouse/
faction systems below, and a project-scope/hardware-requirements estimate). Order follows one
rule: cheapest first for quick wins, riskiest next while the codebase is still small.

- [ ] **M9** - flesh out the city hub's remaining sub-screens beyond their current stub/
      placeholder state (deeper Shipyard functionality, etc.)
- [x] **M9b** - warehouse speculation UX: a first-purchase tooltip explaining that supply/
      prices drift back over time, plus a "price above/below normal" indicator in the market
      screen reusing the map's existing surplus/shortage icon language - the underlying
      mechanic already exists (v0.4.1's living market), this just makes it legible *(this
      release)*
- [x] **M10a** - Fleet: hire a captain at the Tavern (a choice of three, or re-crew an idle
      one for free), assign an owned ship a port-to-port route - the captain decides what to
      trade and never sells at a loss; a Docks screen manages the fleet roster, ships render
      and move on the map for real, and losses are real (no bailout) *(this release, and then
      some - grew well past the original MVP scope during implementation)*
- [ ] **M10b** - unified threat model, remaining piece: reputation already gates *whether* a
      faction's military is hostile at all, and the same hostility check now drives both the
      player's own encounters and a fleet captain's (done as part of M10a) - what's left is
      *persistence*, how doggedly a hostile ship keeps pursuing after a failed attempt, rather
      than every encounter being a fresh, memory-less check
- [ ] **M10c** - **the next stage** (re-scoped and promoted ahead of M9/M16, 2026-10-22):
      ship balance by class, and the combat depth that gives the classes something to differ
      about - **boarding**, and with it a **crew** that has a headcount and takes losses, and
      **guns as a countable thing**. The last one is the point: a hull's tonnage has to be
      *spent*, on a capacious hold or on being armed to the teeth, so choosing a ship becomes a
      trade-off rather than a ladder. This stage decides what buttons and readouts combat and the
      shipyard need, which is exactly why **M9 and M16 come after it** - restyling a screen before
      its mechanics have stopped moving means styling it twice.
      **Alternative ammunition types are cut from this stage** and moved to post-release: making
      shell types matter needs more combat depth than the game should carry yet, and the work to
      get there is out of proportion to what it adds.
- [x] **M11** - a hostile ship that spots a target now actually pursues it instead of engaging
      only on chance proximity; catching up is an attack with no warning, a passive encounter
      instead offers a real "Fight / Sail On" choice; fleet captains proactively dodge trouble
      (shelter in any port, or steer around a threat) and fight for real (not a coin flip) if
      caught anyway *(this release - "more ships on the map" and a ship-stat balance pass are
      pure numbers with no shape decision behind them, deferred to be eyeballed alongside M15's
      own balance pass rather than guessed at now)*
- [x] **M11c** - home waters and pirate lairs: AI ships used to pick their next port uniformly at
      random among their faction's, and since M14b a faction spans the whole map, so nothing had a
      territory. Each ship now has an *anchor* it lives around - its home port, or the lair for
      pirates - and weights its next destination by distance from that anchor rather than from
      wherever it happens to be, which is the distinction that turns a random walk into an orbit.
      A flat one-in-eight chance ignores the flag entirely, keeping a territory a bias rather than
      a fence. Warships patrol out and back; pirates sortie from the Sea of Pirates and return to
      it. The lair sits in the empty northern Gulf on purpose - a nest on a shipping crossroads is
      a permanent blockade of that trade, not a source of danger *(this release)*
- [x] **M11d** - regional events and a military response: a band of pirates gathers, sails out in
      line astern and works a corridor between two ports; each power musters its own squadron from
      its own harbour, reinforces after losses, and fights it out with the same combat maths the
      player's own battles use, so warships die too. Losses leave a mark on the water *(this
      release)*
- [x] **M11e** - the rules of engagement, and the reversal that came with them. This milestone was
      planned around "NPC versus NPC fighting happens only inside an active event", on the
      reasoning that fighting everywhere would grind the pirate population down. Built that way it
      gave the map two different physics: a pirate inside the red circle hunted merchantmen and an
      identical pirate fifty units outside it sailed politely past them. The rule was replaced with
      role-based engagement everywhere, all the time, and the population problem was solved where
      it actually lived - a pirate only starts on shipping while on station, and every chase has an
      end. Events concentrate trouble; they no longer own it *(this release)*
- [ ] **M11b** - route safety: a simple track record per port pair ("N safe trips, M threat
      encounters"), shown when assigning a captain's route - deliberately not a numeric
      "danger score", to stay consistent with not handing the player a full picture upfront.
      Moved after M11c/M11d: while danger is uniformly random, a track record would be a
      recording of noise
- [ ] **M12** - expand the Commandant's Office beyond reputation + loans, including tying
      loans to fleet expansion (borrowing specifically to afford a second ship/captain sooner)
- [x] **M14** - faction conflict: selling in a port raises standing with its faction and lowers
      it with the other three, three times more gently, so every sale takes a side. The share of
      revenue a faction needs before its standing climbs falls out of that ratio at 25%, which
      caps the number of powers you can keep friendly at three of four - the "Influence" ceiling,
      derived rather than declared. Navies now react in three steps instead of one (indifferent /
      picks fights it wins / hunts you), and four reputation bars sit in the HUD so a sale's
      trade-off is visible as it happens. Hired captains' sales count exactly like your own
      *(this release)*
- [x] **M14b** - world expansion: the port roster grew from twelve to twenty-eight, and the
      per-faction split from 7/3/1/1 to 9/8/6/5. Both mattered: M14's reputation runs off *share
      of sales revenue*, so a power holding one harbour could only be courted by pushing a quarter
      of all trade through it. The empty western third of the map is filled too - and not with
      Spain alone, though the region historically was Spanish: Roatan, the Mosquito Coast and Old
      Providence were real English footholds in Spain's own sea, which keeps the west interesting
      rather than merely populated *(this release)*
- [ ] **M15** - economy balance pass, now with real thresholds to calibrate against (status
      ceilings, captain hiring costs, the M14 reputation penalty) instead of placeholders
- [ ] **M16** - full UI/UX polish pass: every remaining menu, icon, and interactive element
      (main menu, settings, save/load, combat screen, HUD/minimap chrome, Property, and every
      city-hub sub-screen) restyled to the same standard as the port hub redesign (M8c) -
      deliberately last, once mechanics are settled and every button/icon a mechanic needs
      already exists, so nothing gets restyled twice (replaces the earlier, narrower M13, which
      only covered the city-hub sub-screens). M9's remaining content is the same work seen from
      the other side - its sub-screens are all functionally present, what is left of it is style -
      so both wait on M10c.

### Agreed, not yet scheduled

Decided in discussion and parked here so it does not live only in a conversation. Not a priority
order.

- **Sea events, reworked.** The thirty one-time encounters are thin and rough, and are meant to
  carry a real share of "the world is doing something" - a direction confirmed, with the shape of
  it still open.
- **A fight between two other ships, seen from the deck.** Close enough to watch two hulls at it,
  one of them breaking off damaged - and, later, perhaps a hand in it. Not a notification system:
  the player has no business being told about every sinking in the Caribbean. Note the real cost
  first - NPC against NPC is currently resolved in an instant, so this means giving those fights
  duration on the map before anything can be watched.
- **A pause before a lost ship is replaced.** A power losing a frigate and conjuring another the
  same instant is the one implausible thing left in the world's bookkeeping.
- **AI steering, refined.** Good enough to play on and not yet right: a hull can still pin itself
  against a headland and sit there (watched happening, 2026-10-23). The instrumentation for it is
  in place - the session log reports how far a ship actually sailed against how far it got, which
  separates a hull going in circles from one not being moved at all.
- **Post-release:** Android/macOS builds (M8b), and alternative ammunition types.

## Credits

- The base map is [*Middle America relief location
  map*](https://commons.wikimedia.org/wiki/File:Middle_America_relief_location_map.png) by Uwe
  Dedering, licensed [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/deed.en).
- Ship sprites (5 tiers x 6 colors), city-menu icons (Market/Shipyard/Warehouse/Commandant/
  Tavern), and the port hub's frame background art are AI-generated, with no identifiable human
  author/rights-holder to credit.
- Tile sprites (currently unused) and an earlier, now-replaced set of ship sprites are from
  Kenney's [*Pirate Pack*](https://kenney.nl/assets/pirate-pack)
  ([CC0](https://creativecommons.org/publicdomain/zero/1.0/)).
- The UI font is [*Cormorant Garamond*](https://fonts.google.com/specimen/Cormorant+Garamond)
  ([SIL OFL 1.1](https://openfontlicense.org/)).
- Background music is from the [*Ocean Music
  Pack*](https://opengameart.org/content/ocean-music-pack) by Leonardo Paz, licensed [CC BY 4.0 /
  3.0](https://creativecommons.org/licenses/by/4.0/) - credit required, given here.
- Sound effects are from Kenney's [*Impact Sounds*](https://kenney.nl/assets/impact-sounds),
  [*UI Audio*](https://kenney.nl/assets/ui-audio), and [*RPG
  Audio*](https://kenney.nl/assets/rpg-audio) packs, and rubberduck's [*25 CC0 bang/firework
  SFX*](https://opengameart.org/content/25-cc0-bang-firework-sfx) on OpenGameArt (all
  [CC0](https://creativecommons.org/publicdomain/zero/1.0/)).

None of the above are covered by this repo's code license - see [NOTICE](NOTICE) for details.

## License

Source code is licensed under the [Apache License 2.0](LICENSE). Third-party assets carry their
own licenses - see [NOTICE](NOTICE).
