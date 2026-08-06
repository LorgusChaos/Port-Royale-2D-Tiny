# Port Royale 2D Tiny

A small 2D hybrid of *Port Royale* and *Space Trader* - Caribbean trade, piracy, and naval
combat, deliberately kept small in scope for a solo/hobby project.

> **Status: v0.4.5 - early prototype.** Sailing, trading with a living dynamic market, random sea
> events, ship upgrades, combat, historical factions with reputation, a living map of AI ships,
> warehouses/property, loans, autosave, audio, and a full main menu with save/load are all
> playable in a rough form; art and content breadth are still early. See [Roadmap](#roadmap).

Inspired by *Port Royale* and *Space Trader* - not affiliated with or endorsed by either
franchise's creators or publishers.

## What's here in v0.4.5

- A world map built from real-world relief map data (the Caribbean basin and surrounding
  coasts), used as the actual playing field rather than an abstract/schematic map, at twice
  the scale of earlier releases so a voyage between ports takes real time.
- Water/land collision sampled directly from the map image's own pixels, so the sailable area
  always matches what's drawn - no separately maintained coastline data to drift out of sync.
- Manual sailing (WASD/arrows), blocked by coastlines, plus click-to-set-course autopilot (A*
  pathfinding around islands) - any manual input immediately cancels autopilot and hands control
  back. Clicking an invalid (land) destination shows a brief marker instead of silently doing
  nothing.
- Mouse-wheel zoom, edge-of-screen panning and right-click-drag panning while sailing (both
  clamped to the map bounds), and a minimap showing the current view.
- Twelve labeled ports, each belonging to its real 17th/18th-century colonial owner - Spain,
  England, France, or the Netherlands. Arriving at one opens a city hub with icon buttons for:
  - a **market** - buy/sell ten goods with hand-drawn icons; a living supply level per port/good
    drifts over time and responds to the player's own trading, on top of the port's fixed
    historical bias and reputation with its owning faction. Goods notably cheap or scarce at a
    port show as small framed icons under its name on the map;
  - a **shipyard** - Ship Status (condition, upgrades, hull cleaning, repairs) is the primary
    screen; a "Buy a Ship" sub-screen offers six classes (Sloop/Cutter/Barque/Brigantine/Galleon/
    Man-of-War, each its own distinct hull livery) as a single row of cards;
  - a **Commandant's Office** - standing with each colonial power, plus a loan system: borrow one
    of three fixed sums with interest and a repayment term; missing the deadline doubles the debt
    and turns that faction hostile (a red-filling debt icon with a countdown sits above the
    minimap while a loan is outstanding);
  - a **warehouse** - buy one, expand it as an upgrade, and store goods and gold there via a
    Market-like transfer screen;
  - a **Tavern** - present in the menu, not yet functional.
  A **Property** menu (icon under the minimap) lists everything stored across every city with
  anything in it.
- A living map: trade and military ships patrol between their faction's own ports; pirates wander
  between any port at a random tier (1-5, hull size and reward scaled to match) and flee rather
  than engage a ship clearly stronger than them, and vice versa. Pirates and hostile-reputation
  military ships engage on approach; peaceful ships can be deliberately hunted down for loot.
  Ports are a safe harbor - no encounter ever triggers near one. Ships visibly dock - they
  disappear into a port on arrival (a slow purple glow marks where) and reappear only once you
  set off again.
- Random sea events: 30 invisible, one-time locations scattered across the water (respawning
  elsewhere after a delay once triggered) for classic encounters - a flavor-text bottle, a gold
  or goods chest, or a castaway who pays for a lift to a random port. Shown in a dismissible,
  centered notification; sailing and AI ships pause while one is open.
- Turn-based combat with animated ship sprites: cannonballs fly between ships, hits flash, shots
  can miss, maneuverability affects flee odds and evasion. Victory shows a dismissible reward
  notification (same style as sea events); defeat strips the player's cargo and gold and leaves
  them with a "Wreck" - the smallest, unarmed, slowest hull in the game, just enough to reach a
  port and immune to further NPC attacks - instead of a dead end.
- An in-game clock, running 10x faster than real time, denominating loan terms (see Commandant's
  Office above).
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

## Building a distributable build

```
./gradlew :lwjgl3:shadowJar        # Linux/macOS
.\gradlew.bat :lwjgl3:shadowJar    # Windows
```

Produces a single self-contained runnable JAR at `lwjgl3/build/libs/port-royale-2d-tiny-<version>.jar`
(all dependencies and platform natives bundled in). Anyone with a JDK 17+ can run it directly -
`java -jar port-royale-2d-tiny-<version>.jar` - no Gradle or source checkout needed.

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
- [ ] **M8b** - Android/macOS builds

M9 onward follow a design pass recorded under [`Project design/`](Project%20design/) - the
"why", not just the "what" (design pillars, progression ceilings, the fleet/threat/warehouse/
faction systems below, and a project-scope/hardware-requirements estimate). Order follows one
rule: cheapest first for quick wins, riskiest next while the codebase is still small.

- [ ] **M9** - flesh out the city hub's remaining sub-screens beyond their current stub/
      placeholder state (deeper Shipyard functionality, etc.) - Tavern specifically stays a
      stub until M10a, where it actually comes online
- [ ] **M9b** - warehouse speculation UX: a first-purchase tooltip explaining that supply/
      prices drift back over time, plus a "price above/below normal" indicator in the market
      screen reusing the map's existing surplus/shortage icon language - the underlying
      mechanic already exists (v0.4.1's living market), this just makes it legible
- [ ] **M10a** - Fleet MVP: hire a captain at the Tavern (fixed cost, scaling up per
      additional captain), assign a second ship a fixed port-to-port-to-good trade route,
      simulated once per in-game day, results reported through the existing toast
      notification; losses are real (no bailout) - the riskiest new architecture in the
      project, deliberately built while the codebase is still small
- [ ] **M10b** - unified threat model: extends the existing tier-based fight-or-flee with
      reputation as a second factor (how *persistently* a hostile ship pursues, not just
      whether it attacks) - the same module drives both the player's own encounters and
      captains' auto-resolved ones from M10a
- [ ] **M10c** - crew/boarding combat rework: headcount, losses during a fight, boarding,
      alternative ammunition types - *priority under review*; combat is meant to stay a
      support system rather than a deep one of its own, so this waits until M10a/M10b show
      whether combat actually feels shallow in practice
- [ ] **M11** - chase-mechanic polish, more ships on the map, a ship-stat balance pass (speed/
      cargo/damage/etc), an NPC-encounter popup (Fight / Sail On) instead of automatic
      engagement, and an "attack" distinction - a ship that was actively pursuing the player
      counts as an attack rather than a random encounter, removing the "Sail On" option in
      that case
- [ ] **M11b** - route safety: a simple track record per port pair ("N safe trips, M threat
      encounters"), shown when assigning a captain's route - deliberately not a numeric
      "danger score", to stay consistent with not handing the player a full picture upfront
- [ ] **M12** - expand the Commandant's Office beyond reputation + loans, including tying
      loans to fleet expansion (borrowing specifically to afford a second ship/captain sooner)
- [ ] **M13** - once every city-menu sub-screen is built out, restyle their UI to match the port
      hub's art (see M8c)
- [ ] **M14** - faction conflict: trading a rival faction's goods (bought from them) with
      their competitor costs reputation with the original faction, cumulatively - gives the
      "Influence" status ceiling a real trade-off (3 of 4 factions at max reputation, not 4 of 4)
- [ ] **M14b** - world expansion: grow the port roster beyond the current twelve - mostly
      content work (new entries in the already-parameterized port/market data), done after
      M14 so the new ports don't need the faction-conflict balance recalibrated twice
- [ ] **M15** - economy balance pass, now with real thresholds to calibrate against (status
      ceilings, captain hiring costs, the M14 reputation penalty) instead of placeholders

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
