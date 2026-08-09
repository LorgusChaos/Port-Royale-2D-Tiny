# Changelog

## [0.4.5.4] - 2026-08-09

### Fixed
- Upgrades bought for a ship (cannons, oars, sails) no longer vanish the moment you sail a
  different one. The player's hull condition and the ship's own condition were two separate
  objects: upgrades landed on one, the hull kept the other, and swapping ships at the docks reset
  the first to zero. They are now one object per hull, so upgrades, damage and fouling belong to
  the ship that earned them and stay there. A captain given one of your upgraded ships now
  actually sails it upgraded, instead of at stock speed and firepower.
- Swapping ships at the docks and back no longer repairs and scrubs a battered hull for free. The
  same reset that wiped upgrades also restored full hull and cleared fouling, which quietly
  undercut the shipyard's repair and cleaning fees entirely.
- Sinking a tougher opponent now pays better than sinking a weak one for hired captains too - a
  captain's prize was a flat random sum, so a tier-5 pirate was worth the same as a lightly armed
  trader. Both the player's own fights and a captain's now pay out the defeated ship's own worth.
- A victory no longer pays twice. The combat screen awarded a random salvage sum and the world
  then added the enemy's reward on top, so the amount that reached the purse never matched the
  amount the victory message quoted. There is now one payout, and the message states it.
- Hired captains sharp enough to read a market (`tradeSense`) now buy *cheaper* as well as sell
  dearer. The buying price used to move the wrong way, so a good captain overpaid on the way in
  and only came out ahead because the selling side made up for it.
- Boarding a smaller ship with a hold that will not fit is now refused, with the dock saying why,
  instead of leaving you carrying 160 units of cargo in a 50-unit hold.
- Storing gold is no longer offered in a port where you own no warehouse (the action silently did
  nothing).
- A saved captain carrying a good the game no longer recognises now has that hold emptied along
  with it. Left as-is, the captain could never sell (no good) and never restock (the hold read as
  full), and would sail their route forever without trading again.

### Changed
- Hired captains now trade at honest prices. A captain used to buy or sell an entire hold at the
  price on the board, then move the market by the full amount afterwards - so one captain could
  drain a port's stock to the floor and still pay the untouched price for all of it, something no
  player buying through the market screen could do. Each unit is now priced as it is traded.
- Following from that, captains size a load to what the route can actually absorb rather than
  always filling the hold, and decide whether to sell on what the sale would really fetch rather
  than on a single quoted price. A weaker captain now buys a smaller, sensible load instead of
  overbuying into cargo they cannot unload at a profit. Net effect: profit per leg is lower than
  before, and the gap between a sharp captain and a poor one is much wider - hiring a good one
  matters now.

### Internal
- Added a unit test suite (306 tests) for the pure game logic: economy, ship condition and
  upgrades, combat maths, fleet simulation, save compatibility, and map pathfinding. The
  Scene2D/rendering layer is deliberately not covered - it needs a live GL context and is checked
  by running the game.
- Added CI (`.github/workflows/release.yml`): tests run on every push to `main`, and bumping
  `appVersion` additionally builds the Windows exe, tags the commit and publishes a GitHub
  Release with the portable zip and the jar.
- Added a real `.gitignore` covering build output, packaged distributables, IDE files and logs.

## [0.4.5.3] - 2026-08-08

### Added
- Pursuit (M11): a hostile ship (pirate, or a faction's military once reputation with them has
  soured enough) that spots the player or a fleet captain's ship from a distance breaks off its
  patrol and actually sails after it, instead of danger only ever being a matter of who happens
  to sail past whom. Catching up ends in an attack with no warning; a ship that was never
  pursuing instead offers a real choice - a "Fight / Sail On" popup - the first time the two
  cross paths.
- Fleet captains now try to avoid trouble before it finds them: spotting a threat early, a
  captain either makes a run for whichever port (any of them, not just their own route's two) is
  closer than the danger itself, waits it out there, and resumes the same voyage once it's safe,
  or - if no port is close enough - steers around the threat the same way ships already steer
  around land, and carries on toward where they were going.
- A captain who's actually caught now fights for real - the exact same combat math as the
  player's own turn-based fights (miss chance, damage rolls, a chance to break off), just
  resolved instantly instead of one click at a time. Three outcomes instead of two: winning (the
  threat is sunk, prize money goes to the captain's lifetime profit) joins fleeing and losing.
- Every ship in the fleet now has its own hull condition and hull fouling, exactly like the
  player's own active ship - so a captain who's taken damage or gone too long between hull
  cleanings is honestly slower and weaker, not reset to pristine every leg. Captains look after
  this themselves: arriving at any port, they pay for a repair or a scrub from the shared purse
  when it's actually worth it, the same upkeep judgment call a player paying attention would make
  - never upgrades, those stay the player's own call.
- The HUD's fleet quick-jump camera zoom is now a closer 90% (was 30%), so the ship itself is the
  clear focus when you jump to it.

### Internal
- Combat's core formulas (miss chance, flee chance, damage rolls) moved out of the player-only
  `CombatState` into shared constants, so the player's fights and a captain's can never quietly
  drift apart.
- `WorldMap.findPath` can now be asked to temporarily treat a circle around a point as
  impassable, for a captain steering around a live threat - falls back to the direct route if
  avoiding leaves no path at all, rather than freezing the ship in place.
- Save games now carry each fleet ship's hull/fouling/upgrades and a sheltering captain's status,
  so neither is lost across a save/load round-trip.

## [0.4.5.2] - 2026-08-07

### Added
- Hired captains (M10a): the Tavern now actually works. Hiring offers a choice of three rolled
  candidates (name, portrait, trade sense, and caution), or lets you re-crew an already-hired
  captain who's currently idle for free; either way requires a free ship of your own docked at
  the current port. The player only sets the ship and the two ports on the route - what to
  actually carry is the captain's own call each leg: they buy whichever good looks most
  profitable by today's prices at both ends (the same market/reputation pricing the player
  themselves would get), and refuse to ever sell at a loss, holding cargo and trying the other
  end of the route instead if the price isn't there yet.
- Docks screen: lists every ship you own that's currently in port, lets you rename any of them,
  and swap which one you're personally sailing (you count as a captain too) or hand one to a
  hired captain.
- Buying a new ship at the Shipyard now adds it to your fleet instead of replacing the one
  you're sailing - you're prompted to name it at purchase.
- Fleet ships appear on the sailing map as real moving icons, following the same sailable A*
  route (around islands, hugging the coast) the player's own autopilot uses, at a leg duration
  derived from real distance and that ship's own speed - not a placeholder timer. Clicking one
  (or its quick-jump icon in the HUD - see below) opens a status popup: ship and cargo, route,
  lifetime profit, and a way to redirect the destination (only while the captain is inbound to
  home port, so a change can't retarget a leg already under way) or recall them.
- A captain's danger is now a real thing happening in the world, not a hidden dice roll: their
  ship can run into any of the same hostile AI ships the player can, using the same rules
  (pirates by relative strength, faction military by reputation). Escape chance depends on the
  captain's own ship speed against the threat's, plus their caution stat; failing to escape is
  a full, permanent loss of the ship, captain, and cargo - same as the player's own combat
  losses.
- A game-speed control in the HUD (clock icon, top-left): cycles 1x/2x/5x/10x/20x, speeding up
  or slowing down ship movement (the player's, the fleet's, and every AI ship's), the game
  clock, hull fouling, market drift, and sea-event timers together. Camera controls, UI
  animation, and the autosave interval deliberately stay real-time regardless of the setting.
- The HUD also now shows the in-game calendar date and gold balance (top-left), and a small
  portrait icon per active captain (top-right) that snaps the camera straight to that ship's
  current position at a close zoom.
- A warehouse's first-ever purchase shows a one-time popup tip explaining that supply/prices
  drift back over time (M9b); the market screen now shows a small framed icon on any good
  that's notably cheap or expensive at the current port, reusing the same surplus/shortage
  language already used on the map.

### Changed
- Every ship in the game - the player's, a hired captain's, and every roaming AI trader/
  warship/pirate - now draws its sailing speed from the same underlying ship-type table,
  instead of AI ships using a separate, hand-picked number disconnected from ship type. All
  base ship speeds were also reduced 10x, since honoring real distance made the old numbers
  cross the map implausibly fast.

### Fixed
- Toast notifications (sea events, combat results, loan-overdue, the new warehouse tip) could
  render underneath, and not receive clicks on, every port and combat sub-screen - they now
  always draw on top and share input with whatever screen is open.
- A fleet ship's rendered route could cut straight across land - pathfinding was using a port's
  raw map marker position instead of the water-adjacent point next to it (the same fix the
  player's own autopilot already relied on).
- The HUD's captain quick-jump icons could silently drop a click - the icon row was being
  rebuilt from scratch every single frame, which could tear down the exact button a click had
  just started on before its release landed. Now only rebuilt when the active-captain list
  actually changes.

### Internal
- Save games now carry the full fleet (every owned ship and hired captain, including a
  captain's current cargo/route/lifetime profit) so it survives a save/load round-trip.

## [0.4.5.1] - 2026-08-06

### Internal
- Added `Project design/`: working design docs (design pillars, progression ceilings, fleet/
  captains, a unified combat threat model, warehouse speculation, faction reputation conflict,
  route safety, world expansion, and a project-scope/hardware-requirements estimate) plus a
  roadmap-integration pass tying them to concrete milestones. Root `README.md`'s Roadmap section
  now reflects the full M9-M15b breakdown instead of the earlier flat M9-M15 stub list. No
  gameplay or code changes.

## [0.4.5] - 2026-08-04

### Changed
- Port hub redesign: the city menu now lays out directly over a new AI-generated frame background
  (a wooden shopfront with a nameplate banner) instead of a generic Scene2D panel. The port name
  and gold/cargo status are centered in the nameplate; each destination's name is overlaid on its
  own icon's board instead of a separate label underneath; a close (X) control in the frame
  replaces the old "Set Sail" text button. Overlay text uses a larger, bolder independent font so
  it stays legible at smaller window sizes, and the icon + its name now scale together on hover
  instead of the name visibly detaching from a growing icon.

### Fixed
- The frame's background-removal pass was treating some near-black decorative details (fine
  crack linework in the wood) as background, punching stray transparent holes straight through to
  the sea behind. Background removal now requires a solid, several-pixels-thick dark region
  (a morphological opening) before treating it as removable, so thin dark linework survives.

### Internal
- Roadmap expanded with the next several planned milestones (city sub-menu buildout, a crew/
  boarding combat rework, chase-mechanic and ship-balance polish, expanded Commandant/faction
  logic, economy balance) - no gameplay changes yet.

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
