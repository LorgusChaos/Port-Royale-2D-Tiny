# Port Royale 2D Tiny

A small 2D hybrid of *Port Royale* and *Space Trader* - Caribbean trade, piracy, and naval
combat, deliberately kept small in scope for a solo/hobby project.

> **Status: v0.3 - early prototype.** Sailing, trading, ship upgrades, combat, audio, and a full
> main menu with save/load are all playable in a rough form; art, content breadth, and economy
> balance are still early. See [Roadmap](#roadmap).

Inspired by *Port Royale* and *Space Trader* - not affiliated with or endorsed by either
franchise's creators or publishers.

## What's here in v0.3

- A world map built from real-world relief map data (the Caribbean basin and surrounding
  coasts), used as the actual playing field rather than an abstract/schematic map.
- Water/land collision sampled directly from the map image's own pixels, so the sailable area
  always matches what's drawn - no separately maintained coastline data to drift out of sync.
- Manual sailing (WASD/arrows), blocked by coastlines, plus click-to-set-course autopilot (A*
  pathfinding around islands) - any manual input immediately cancels autopilot and hands control
  back. Clicking an invalid (land) destination shows a brief marker instead of silently doing
  nothing.
- Mouse-wheel zoom, edge-of-screen panning while sailing (clamped to the map bounds), and a
  minimap showing the current view.
- Twelve labeled ports. Arriving at one opens a city hub with icon buttons for:
  - a **market** - buy/sell ten goods with hand-drawn icons, prices differ by port with
    specialties loosely flavored by real Caribbean-era trade;
  - a **shipyard** - three ship tiers (Sloop/Brigantine/Galleon) with real speed/cargo/combat
    differences and sprite thumbnails, purchasable with gold;
  - a **Commandant's Office** - currently a stub, reserved for future quests.
- Turn-based combat with animated ship sprites: cannonballs fly between ships, hits flash, shots
  can miss, and victory/defeat/flee outcomes affect gold and cargo.
- Music and sound: a rotating sailing playlist, a combat track, a port-arrival cue, and SFX for
  cannon fire/hits/coins/clicks/menu transitions - all controllable from Settings (volume slider
  + mute).
- A main menu: **New Game** (name your captain), **Load**, **Save**, and **Settings** (audio,
  window resolution). `Esc` pauses and reopens the same menu mid-game, resuming exactly where
  play left off. Saves and settings persist as JSON files under the user's home directory.
- Ship sprite art from Kenney's Pirate Pack (CC0) instead of placeholder shapes; ports and other
  city art are still simple markers, alongside the new hand-drawn UI icons.

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
| `Esc` | Open the main menu; pauses the game and resumes exactly where you left off |

## Project structure

```
core/    platform-independent game logic (Kotlin)
  world/   map projection, water/land + pathfinding, port data
  audio/   music/SFX playback
  save/    JSON save-game and settings persistence
lwjgl3/  desktop (Windows/macOS/Linux) launcher
assets/  game assets (base map image, ship/tile sprites, music/SFX)
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
- [ ] **M5** - audio and main menu/save-load done *(this release)*; packaging and Android/macOS
      builds still pending

## Credits

- The base map is [*Middle America relief location
  map*](https://commons.wikimedia.org/wiki/File:Middle_America_relief_location_map.png) by Uwe
  Dedering, licensed [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/deed.en).
- Ship and tile sprites are from Kenney's [*Pirate
  Pack*](https://kenney.nl/assets/pirate-pack) ([CC0](https://creativecommons.org/publicdomain/zero/1.0/)).
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
