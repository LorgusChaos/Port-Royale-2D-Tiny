# Changelog

## [0.4.4] - 2026-08-04

### Added
- New ship sprite art (5 tiers x 6 colors, AI-generated) replacing Kenney's Pirate Pack sprites
  for every ship in the game: white sails for the player, black for pirates, and one distinct
  color per colonial faction (Spain/England/France/Netherlands).
- New hand-painted city-menu icon art (Market/Shipyard/Warehouse/Commandant), plus a Tavern
  building added to the port hub menu as a placeholder - no functionality behind it yet.
- Warehouses: buy one in a port, expand it as an upgrade, and store both goods and gold there via
  a Market-like transfer screen.
- A "Property" menu (icon under the minimap) listing everything the player has stored, by city -
  cities with nothing stored are omitted.
- An Exit button on the main menu.
- Autosave every 10 minutes (overwrites a single autosave file); the save/load lists now show the
  date and time of each save.
- A combat-result notification on victory showing the gold reward, reusing the same dismissible
  toast used for random sea events.
- A defeat rework: losing a fight now costs the player all cargo and gold aboard, leaving them
  with a "Wreck" - the smallest hull in the game, no sail, the lowest speed and cargo capacity of
  any ship, zero combat capability, and immunity from NPC attacks - just enough to limp to a port.
- An in-game clock running 10x faster than real time, currently used to denominate loan terms.
- A loan system at the Commandant's Office: three fixed sums with interest and a repayment term
  (roughly scaled to how long that sum would realistically take to earn back by trading). Missing
  the deadline doubles the debt and turns the lending faction's ships hostile toward the player.
  A debt icon above the minimap fills red as the deadline approaches, with a countdown beneath it.
- Pirate variety: pirates now spawn at a random tier (1-5, matching hull size/sprite) with stats
  and reward scaled accordingly, and a fight-or-flee AI - a pirate significantly weaker than the
  ship it meets flees instead of engaging, and vice versa.

### Fixed
- Clicking water near a port could be misread as clicking the port itself; the port click radius
  is now tighter.
- Ships rendered backwards after the new sprite set was installed (its bow points toward the top
  of the source image, opposite the old Kenney convention) - rotation fixed to match.

### Changed
- The autopilot route line is no longer drawn on the map.

## [0.4.3.1] - 2026-08-04

### Internal
- Added Gradle packaging: `./gradlew :lwjgl3:shadowJar` now produces a single self-contained
  runnable JAR (`lwjgl3/build/libs/port-royale-2d-tiny-<version>.jar`, all dependencies and
  LWJGL/GDX natives bundled in) for sharing a build without requiring Gradle or a source
  checkout - only a JDK 17+ to run it. No gameplay or code changes.

## [0.4.3] - 2026-08-04

### Added
- Random sea events: 30 invisible locations scattered across the water, each a one-time trigger
  (respawning elsewhere after 45-120s) for a classic genre encounter - a flavor-text bottle, a
  gold or goods chest, or a castaway who needs a lift to a random port for a reward. Results show
  in a new centered, dismissible notification (with a reward icon and a close button) - the
  game's first non-modal notification mechanism. Sailing, AI ships, and encounters all pause
  while a notification is open, so nothing can happen unseen behind it.
- Shipyard restructure: the primary shipyard screen is now Ship Status (condition/upgrades/
  maintenance) instead of the ship-buying list; buying a ship is now a "Buy a Ship" sub-screen,
  laid out as a single horizontal row of cards instead of a column, so it fits without scrolling.

### Fixed
- A click near a passing ship while plotting a course could be silently hijacked into chase mode
  (straight-line pursuit, no obstacle avoidance) - the hunt-trigger radius is now much smaller
  and separate from the port-click radius.

## [0.4.2] - 2026-08-03

### Fixed
- Ships (player and AI) rendered rotated 180° from their actual heading - the sprite art's bow
  points toward the bottom of the source image, not the top, so they always appeared to sail
  stern-first.

### Added
- Three new purchasable ship classes - Cutter (cheap, fast, low cargo - a genuine sidegrade
  rather than a strict upgrade), Barque, and Man-of-War - each with its own distinct hull
  livery, for six total ship tiers instead of three.

### Changed
- Brigantine and Galleon now use their own distinct ship liveries instead of reused/reskinned
  sprites (Brigantine no longer looks pre-damaged; Galleon no longer shares the Sloop's sprite).
  AI warships also got a cleaner, non-damaged-looking hull for the same reason.

## [0.4.1.1] - 2026-08-03

### Internal
- Added `CLAUDE.md`: build/run commands, the testing-verification discipline, the release-file
  checklist, and a handful of rendering-code gotchas (ShapeRenderer projection matrix reset, the
  zoom-compensation formula, UiScale, font generation) - no gameplay or code changes.

## [0.4.1] - 2026-08-03

### Added
- A living market: each port/good has a supply level that the player's own trades nudge (buying
  depletes it and raises the price, selling floods it and lowers the price) and that drifts back
  to neutral over about ten minutes on its own - a light illusion of a "background economy"
  without a full production/consumption simulation. Goods notably cheap or scarce at a port now
  show as small icons under its name on the map (green frame = surplus, red frame = shortage),
  dimming when the player's own ship sprite passes over them and showing a name tooltip on hover.
- Ships now visibly dock: arriving at a port makes the ship disappear from the map and the port
  glow with a slow purple pulse, so it's clear where the ship is at a glance; it reappears only
  once you actually set off toward a new destination.
- The market screen was restructured to a classic trading-game layout - a boxed buy price, the
  good's icon, and a boxed sell price in a row - instead of one combined sentence, and prices no
  longer carry a redundant currency letter.

### Changed
- Replaced the UI's pixel font with *Cormorant Garamond*, a smoother period-appropriate serif,
  and switched the panel background to a lighter parchment tone with black text to match.
- The UI now scales responsively to the actual window size (still fits at the default windowed
  launch size) instead of a single fixed multiplier that could overflow smaller windows.
- Map port-name labels and market-surplus/shortage icons now render at a constant on-screen size
  regardless of camera zoom, instead of shrinking to unreadable at full zoom-out.
- The minimap has a solid border frame, and its current-view rectangle is now red instead of
  gold, for better contrast against the border.

## [0.4.0] - 2026-08-03

### Added
- Ship upgrades and condition: cannons/extra oars/extra sails (3 levels each, persistent),
  hull fouling that slows the ship over time at sea (cleanable), and hull damage that now
  persists between fights instead of resetting (repairable) - all managed from a new Ship
  Status screen in the shipyard, alongside a full stat readout including a new maneuverability
  stat (improves flee chance and evasion in combat).
- Historical factions - Spain, England, France, the Netherlands - assigned to each port by its
  real 17th/18th-century colonial owner, and a reputation system (-100 to +100, five tiers)
  that shifts Market prices and determines whether a faction's warships are hostile on sight.
  The Commandant's Office now shows real content: standing with each power.
- Roaming AI ships, visible on the map and faction-tinted: trade and military ships patrol
  between their own faction's ports, pirates wander between any port. Pirates and
  hostile-reputation military ships engage the player on approach; peaceful ships can be
  deliberately hunted down (click to pursue) for gold at the cost of reputation. Replaces the
  old invisible random-encounter timer entirely. Ports (and the water right around them) are a
  safe harbor where no encounter can trigger.
- Visual pass: a real pixel font (Kenney Pixel, via FreeType) and bordered panel/button chrome
  replace the default placeholder look everywhere; every icon now gets an automatic outline for
  a more consistent "pixel art" read; the port marker on the map is a proper watchtower icon
  instead of a plain dot. The whole UI also renders 1.5x bigger.
- Settings: a Fullscreen toggle; Resolution now only applies while Fullscreen is on, so windowed
  mode is always freely resizable by dragging the window instead of being tied to a preset list.
- Right-click-drag camera panning (1:1 with the cursor), alongside the existing edge-of-screen
  panning.
- The world map is twice its previous size and sailing speeds are substantially slower across
  the board, so a voyage between ports takes real time instead of a few seconds.

### Fixed
- A port-icon rendering change had silently dropped a required projection-matrix reset, so the
  rejected-click marker and the autopilot route line were drawing in the minimap's coordinate
  space instead of the main view's - looked exactly like clicks landing in the wrong place.
- Clicking a new destination while chasing a ship didn't cancel the chase, leaving the ship
  "stuck" following the old target in a straight line with no obstacle avoidance instead of
  respecting the new course.
- Water/land collision sampling assumed world coordinates matched the map image's pixels 1:1;
  broke once the world was resized independent of the source image's resolution, now scales
  correctly. Single-port factions (France, the Netherlands) could also get stuck endlessly
  re-targeting their own only port instead of patrolling.

## [0.3.0] - 2026-08-03

### Added
- Background music: a rotating playlist of three sailing tracks, a dedicated combat track, and a
  port-arrival cue, switching automatically with what the player is doing (sailing/in port/
  fighting).
- Master volume slider and mute toggle (Settings) affecting both music and sound effects; new SFX
  for entering the market and the shipyard, on top of the existing cannon fire/hit/coins/click.
- Port screen redesign: arriving at a port now opens a city hub with icon buttons for **Market**,
  **Shipyard**, and a **Commandant's Office** (a stub reserved for future quests) instead of one
  combined panel. Goods have hand-drawn icons in the market; ships have sprite thumbnails in the
  shipyard.
- Main menu / title screen: **New Game** (captain name entry), **Load**, **Save**, and
  **Settings** (volume, mute, window resolution, and a "check for updates" placeholder). `Esc`
  opens the same menu as a pause screen during play and resumes exactly where play left off,
  including mid-port or mid-combat.
- Save/load: game state (captain name, gold, ship, cargo, position) persists to JSON files under
  the user's home directory, independent of wherever the game is installed/run from; settings
  persist the same way.
- Combat visuals: ship sprites for both sides, animated cannonball flights, a hit flash, a miss
  chance, wider ship spacing, and the combat screen is now centered instead of anchored top-left.
- Five more ports - Santiago de Cuba, Bridgetown, Willemstad, Veracruz, Portobelo (12 total) -
  and four more tradeable goods - Coffee, Cacao, Cloth, Silver (10 total) - with per-port price
  specialties reflecting real Caribbean-era trade patterns.

### Changed
- Player starting gold raised to make ship upgrades easier to reach for testing; economy balance
  is still a known open item.

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
