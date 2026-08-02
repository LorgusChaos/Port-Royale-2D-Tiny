# Changelog

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
