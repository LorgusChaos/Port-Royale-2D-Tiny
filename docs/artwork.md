# Artwork pipeline

<!-- Split out of CLAUDE.md to keep it short: these notes are only needed for one kind
     of work, and loading them into every chat costs context for nothing. -->

Any new or replaced picture goes through the `art` skill (`.claude/skills/art/SKILL.md`, also
`/art`), even a one-off. In short:

- Sources and mockups in `art-src/`, never in `assets/` (that folder ships inside the jar).
- Sprites are produced by `tools/art/art.py` (`slice` a sheet, `bleed` a single cut-out,
  `uncheck` a generator's checkerboard baked into one, `key` a flat background colour out,
  `preview` edges) — never cropped or cleaned by hand. Python deps: `tools/art/requirements.txt`.
- `key --shade N` also clears pixels that are a *darkened* version of the key colour, matched by
  hue rather than distance. Generators paint an object's contact shadow in the background's own
  colour — magenta at (121,4,121) — which by distance sits exactly where the real art begins, so
  no threshold separates them. 24 is the working value; at 40+ it starts eating dark wood.
- HUD elements are cut with
  `art.py key <file> --colour FF00FF --tolerance 100 --soft 30 --spill 4 --shade 24`. The
  tolerance of 100 comes from the measured histogram: background sits under 20, the painted
  magenta glow trails off to ~120, real art starts past 130.
- `key` is global by default (it reaches holes a flood fill cannot, which is why props are
  generated on magenta). Add `--flood` when the key colour also occurs *inside* the art — the four
  port frames were painted on black and have black shadows, and keying those punched holes
  through the picture. With `--flood` only background the border can reach is cut.
- The port-window frames (`assets/sprites/ui/port_*_frame.png`) are cut this way:
  `art.py key <file> --colour 000000 --tolerance 12 --soft 24 --spill 0 --flood`. The tolerance is
  narrow on purpose: the real background is within 5 of black, painted shadow is 20–60, and at
  the default 60 the flood walks through the shadows and shreds the stalls.
- Painted art is drawn with mipmaps + Linear filtering, which only looks right if the RGB under
  transparent pixels matches the edge (`bleed`); otherwise every sprite gets a white or black rim.
  Ships load through `ui/ShipArt.kt`, icons through `IconFactory.buildArtIcon` — never a bare
  `Texture(Gdx.files.internal(...))`, which gets Nearest and no mipmaps.
- `AssetIntegrityTest` (part of `:core:test`) fails on missing paths, opaque backgrounds, unbled
  edges, a painted transparency checkerboard left in a sprite, and source files in `assets/`.
- Shot mode, for looking at a screen without a person at the keyboard:
  ```
  ./gradlew.bat -q :lwjgl3:run --args="--shot=market --shot-port=Havana --shot-seed=1"
  ```
  Screens: menu, sea, hub, market, shipyard, status, fortress, warehouse, tavern, docks. Also
  `--shot-out=<png>` (default `lwjgl3/build/shots/<screen>.png`, relative to `lwjgl3/`),
  `--shot-frames=<n>` (30), `--shot-size=<w>x<h>` (1280x720). It prints `SHOT <path>` and exits;
  it always starts a new game (never a save) and writes no session log. Parsing lives in
  `diagnostics/ShotRequest.kt`, staging in `PortRoyaleGame.stageShot`.
