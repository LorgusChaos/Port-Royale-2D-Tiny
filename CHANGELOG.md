# Changelog

## [0.2.0] - 2026-08-02

### Added
- Port screen: a market (buy/sell 6 goods, prices differ per port) and a shipyard (3 ship tiers -
  Sloop, Brigantine, Galleon - with real speed/cargo/combat differences, purchasable with gold).
- Turn-based combat: random encounters while sailing, Fire Cannons / Attempt to Flee, with
  victory/defeat/flee outcomes affecting gold and cargo.
- Ship sprite art from Kenney's Pirate Pack (CC0) replacing the placeholder triangle; sprite
  scales and tints per ship tier.
- Camera zoom (mouse wheel), edge-of-screen panning while sailing (clamped to map bounds), and a
  minimap (top-right) showing the current view as a rectangle.
- Port name labels on the map.
- Visual feedback (a brief red X) when clicking land or an otherwise invalid destination, instead
  of silently doing nothing.

### Fixed
- The GL viewport rectangle (letterbox bars) wasn't being reliably re-applied every frame, which
  desynced rendering from mouse click coordinates - most noticeable the more the window's aspect
  ratio diverged from the map's. Now re-applied every frame, for both the main view and the
  port/combat UI screens.
- Port click-detection radius was too small to reliably hit the small port markers; increased,
  and the markers themselves are slightly larger.

## [0.1.0] - 2026-08-02

### Added
- Project scaffold: Gradle multi-module libGDX project (`core` + `lwjgl3`), Kotlin, JDK 17.
- World map built from real-world relief map data covering the Caribbean basin and surrounding
  coasts, used as the actual playing field.
- Water/land collision sampled directly from the map image's pixels, so the sailable area always
  matches what's drawn.
- Manual sailing (WASD/arrow keys) with coastline collision.
- Click-to-set-course autopilot with A* pathfinding around islands; any manual input cancels
  autopilot and returns direct control.
- Seven placeholder ports (Havana, Port Royal, Santo Domingo, San Juan, Cartagena, Nassau,
  Port-au-Prince), clickable to set a course.
