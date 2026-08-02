# Port Royale 2D Tiny

A small 2D hybrid of *Port Royale* and *Space Trader* - Caribbean trade, piracy, and naval
combat, deliberately kept small in scope for a solo/hobby project.

> **Status: v0.2 - early prototype.** Sailing, trading, ship upgrades, and basic combat are all
> playable in a rough form; art, content breadth, and economy balance are still early. See
> [Roadmap](#roadmap).

Inspired by *Port Royale* and *Space Trader* - not affiliated with or endorsed by either
franchise's creators or publishers.

## What's here in v0.2

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
- Seven labeled ports (Havana, Port Royal, Santo Domingo, San Juan, Cartagena, Nassau,
  Port-au-Prince). Arriving at one opens a port screen with:
  - a **market** - buy/sell six goods, prices differ by port, so there's an actual trade loop;
  - a **shipyard** - three ship tiers (Sloop/Brigantine/Galleon) with real speed/cargo/combat
    differences, purchasable with gold.
- Turn-based combat: random encounters while sailing, Fire Cannons or Attempt to Flee, with
  victory/defeat/flee outcomes.
- Ship sprite art from Kenney's Pirate Pack (CC0) instead of placeholder shapes; ports and other
  city art are still simple markers.

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

## Project structure

```
core/    platform-independent game logic (Kotlin)
  world/   map projection, water/land + pathfinding, port data
lwjgl3/  desktop (Windows/macOS/Linux) launcher
assets/  game assets (base map image, ship/tile sprites)
```

## Roadmap

Rough milestone plan, Windows first, with a runnable build checked at the end of each stage:

- [x] **M0** - project skeleton, a ship you can move on a test scene
- [x] **M1** - world map with real coastlines, manual + autopilot sailing, port markers
- [x] **M1b** - port UI and a basic per-port market (buy/sell, price differences across ports)
- [x] **M3** - turn-based ship combat, random encounters while sailing
- [ ] **M2** - ship sprite art done; port/city art still placeholder markers *(this release)*
- [ ] **M4** - ship types + shipyard done *(this release)*; more ports/goods and economy
      balancing still pending
- [ ] **M5** - polish, audio, packaging; Android/macOS builds

## Credits

- The base map is [*Middle America relief location
  map*](https://commons.wikimedia.org/wiki/File:Middle_America_relief_location_map.png) by Uwe
  Dedering, licensed [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/deed.en).
- Ship and tile sprites are from Kenney's [*Pirate
  Pack*](https://kenney.nl/assets/pirate-pack) ([CC0](https://creativecommons.org/publicdomain/zero/1.0/)).

None of the above are covered by this repo's code license - see [NOTICE](NOTICE) for details.

## License

Source code is licensed under the [Apache License 2.0](LICENSE). Third-party assets carry their
own licenses - see [NOTICE](NOTICE).
