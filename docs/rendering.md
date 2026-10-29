# Rendering gotchas

<!-- Split out of CLAUDE.md to keep it short: these notes are only needed for one kind
     of work, and loading them into every chat costs context for nothing. -->

- **ShapeRenderer's `projectionMatrix` is mutable, persistent state.** Any `shapeRenderer.begin()`
  must have `.projectionMatrix` explicitly (re)set immediately before it, every time — it silently
  leaks whatever the last draw call set (e.g. the minimap camera) into unrelated draw calls
  otherwise. This has regressed more than once; don't remove that line during refactors.
- **Constant on-screen size regardless of camera zoom**: `worldUnitsPerScreenPixel()` in
  `PortRoyaleGame.kt` (`camera.viewportWidth * camera.zoom / viewport.screenWidth`) converts a
  target screen-pixel size to the world-space size needed to render at that size at any zoom.
  Multiplying a *world-unit* constant by `camera.zoom` directly does NOT give a sane result -  it
  only keeps the size *consistent*, not *reasonably sized*, since the result depends on whatever
  zoom level the base constant happened to be eyeballed at.
- **`UiScale`** (`core/.../ui/UiScale.kt`) drives all Scene2D screens' text/button size, scaled to
  the actual window width (clamped) so the default 1280x720 launch still fits. Every screen's
  `resize()` must call `UiScale.applyResize(stage, width, height)`, not
  `stage.viewport.update(...)` directly.
- **`UiSkinFactory.generateFont()`** is the one place the UI font (Cormorant Garamond, a variable
  TTF) is generated — it fakes bold via a thin FreeType border (the font has no real bold
  instance), and generates independent `BitmapFont` objects for anything outside the shared Skin
  (e.g. map-space port labels), since `BitmapFontData.scale` is global mutable state shared by
  every screen using the same font object.
- `MapProjection.MAP_WIDTH/MAP_HEIGHT` and the pathfinding grid's `cellSize` (10f, in `WorldMap`)
  are independent scales — arrive-thresholds and pathfinding constants are tied to the grid, not
  to overall map size, and must NOT be scaled when the map's world-unit size changes.
