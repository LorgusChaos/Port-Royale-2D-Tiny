# Port Royale 2D Tiny

A small 2D hybrid of *Port Royale* and *Space Trader* - Caribbean trade, piracy, and naval
combat, deliberately kept small in scope for a solo/hobby project.

> **Status: v0.1 - early prototype.** Core sailing and navigation are playable; economy, ports,
> ships, and combat are not implemented yet. See [Roadmap](#roadmap).

Inspired by *Port Royale* and *Space Trader* - not affiliated with or endorsed by either
franchise's creators or publishers.

## What's here in v0.1

- A world map built from real-world relief map data (the Caribbean basin and surrounding
  coasts), used as the actual playing field rather than an abstract/schematic map.
- Water/land collision sampled directly from the map image's own pixels, so the sailable area
  always matches what's drawn - no separately maintained coastline data to drift out of sync.
- Manual sailing (WASD/arrows), blocked by coastlines.
- Click-to-set-course autopilot: click open water or a port to have the ship plot and follow a
  route (A* pathfinding around islands). Any manual input immediately cancels autopilot and
  hands control back.
- Seven placeholder ports (Havana, Port Royal, Santo Domingo, San Juan, Cartagena, Nassau,
  Port-au-Prince) you can click to sail to. No trading or city UI yet - that's next.

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

## Project structure

```
core/    platform-independent game logic (Kotlin)
  world/   map projection, water/land + pathfinding, port data
lwjgl3/  desktop (Windows/macOS/Linux) launcher
assets/  game assets (currently just the base map image)
```

## Roadmap

Rough milestone plan, Windows first, with a runnable build checked at the end of each stage:

- [x] **M0** - project skeleton, a ship you can move on a test scene
- [x] **M1** - world map with real coastlines, manual + autopilot sailing, port markers *(this
      release)*
- [ ] **M1b** - port UI and a basic per-port market (buy/sell, price differences across ports)
- [ ] **M2** - real ship/port art pass (currently placeholder shapes)
- [ ] **M3** - turn-based ship combat, random encounters while sailing
- [ ] **M4** - more ports/goods/ship types, economy balancing
- [ ] **M5** - polish, audio, packaging; Android/macOS builds

## Credits

The base map is [*Middle America relief location
map*](https://commons.wikimedia.org/wiki/File:Middle_America_relief_location_map.png) by Uwe
Dedering, licensed [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/deed.en). It is
**not** covered by this repo's code license - see [NOTICE](NOTICE) for details.

## License

Source code is licensed under the [Apache License 2.0](LICENSE). Third-party assets carry their
own licenses - see [NOTICE](NOTICE).
