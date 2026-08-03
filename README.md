# Port Royale 2D Tiny

A small 2D hybrid of *Port Royale* and *Space Trader* - Caribbean trade, piracy, and naval
combat, deliberately kept small in scope for a solo/hobby project.

> **Status: v0.4.1 - early prototype.** Sailing, trading with a living dynamic market, ship
> upgrades, combat, historical factions with reputation, a living map of AI ships, audio, and a
> full main menu with save/load are all playable in a rough form; art and content breadth are
> still early. See [Roadmap](#roadmap).

Inspired by *Port Royale* and *Space Trader* - not affiliated with or endorsed by either
franchise's creators or publishers.

## What's here in v0.4.1

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
  - a **shipyard** - three ship tiers (Sloop/Brigantine/Galleon) purchasable with gold, plus a
    **Ship Status** screen for upgrades (cannons/oars/sails), hull cleaning, and repairs;
  - a **Commandant's Office** - shows standing with each colonial power; still a stub beyond
    that, reserved for future quests.
- A living map: trade and military ships patrol between their faction's own ports, pirates
  wander between any port, all visible and faction-tinted. Pirates and hostile-reputation
  military ships engage on approach; peaceful ships can be deliberately hunted down for loot.
  Ports are a safe harbor - no encounter ever triggers near one. Ships visibly dock - they
  disappear into a port on arrival (a slow purple glow marks where) and reappear only once you
  set off again.
- Turn-based combat with animated ship sprites: cannonballs fly between ships, hits flash, shots
  can miss, maneuverability affects flee odds and evasion, and victory/defeat/flee outcomes
  affect gold, cargo, and reputation with the loser's faction.
- Music and sound: a rotating sailing playlist, a combat track, a port-arrival cue, and SFX for
  cannon fire/hits/coins/clicks/menu transitions - all controllable from Settings (volume slider
  + mute).
- A main menu: **New Game** (name your captain), **Load**, **Save**, and **Settings** (audio,
  fullscreen toggle, resolution). `Esc` pauses and reopens the same menu mid-game, resuming
  exactly where play left off. Saves and settings persist as JSON files under the user's home
  directory.
- Ship sprite art from Kenney's Pirate Pack (CC0); a period-style serif font (Cormorant Garamond)
  on a parchment-toned, bordered UI instead of placeholder styling; every hand-drawn icon (goods,
  ships, hub menu, port marker) gets an automatic outline for a more consistent look. The UI
  scales responsively to the window size, and map labels/icons hold a constant on-screen size
  regardless of zoom.

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
  world/     map projection, water/land + pathfinding, port data, roaming AI ships
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
      pass (font, scaling, zoom-constant labels) *(this release)*
- [ ] **M7** - packaging, Android/macOS builds

## Credits

- The base map is [*Middle America relief location
  map*](https://commons.wikimedia.org/wiki/File:Middle_America_relief_location_map.png) by Uwe
  Dedering, licensed [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/deed.en).
- Ship and tile sprites are from Kenney's [*Pirate
  Pack*](https://kenney.nl/assets/pirate-pack) ([CC0](https://creativecommons.org/publicdomain/zero/1.0/)).
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
