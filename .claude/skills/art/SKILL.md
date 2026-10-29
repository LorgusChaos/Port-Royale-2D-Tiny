---
name: art
description: Add or replace game artwork (ship sprites, port icons, UI frames, any PNG under assets/sprites) through the project's art pipeline, and check it in the running game. Use whenever the user brings a new image or sheet, asks to generate art, or asks to put a picture into the game ("добавь картинку", "новые корабли", "/art <what>").
---

# Adding artwork

Goal: a picture goes from "a file the user generated" to "correct in the running game" without
anyone cropping by hand or guessing paths, and without the user being the first to look at it.

Why this exists: every failure so far came from doing this step by eye - a painted checkerboard
taken for transparency, crops that differ by a few pixels per tier, a path typo that only shows
when the screen opens, and white rims around sprites once they were smoothly filtered. Each of
those now has a tool or a test; this skill is the order to use them in.

## Where things live

- `art-src/<kind>/` - source sheets and mockups as generated. Never loaded by the game, never
  shipped (assets/ is the jar's resource root). Keep the originals here; re-slicing from them
  must always be possible.
- `assets/sprites/<kind>/` - finished sprites, produced only by `tools/art/art.py`.
- Ship sprites: bow points UP the image (ShipSpriteOrientationTest), one file per tier x colour,
  named `ship2_<colour>_<tier>.png`.

## Steps

1. **Get the source into `art-src/`.** If an image-generation MCP is configured, generate
   with the existing sheet of the same kind as a style reference (e.g.
   `art-src/ships/ship_sheet_v2_source.png`) and save straight into `art-src/`. Otherwise ask the
   user for the file. Ask for a plain white or real transparent background - not a checkerboard.
2. **Slice and clean it with the script**, never by hand:
   ```
   python tools/art/art.py slice art-src/<kind>/<sheet>.png assets/sprites/<kind> \
       --cols <left-to-right names> --rows <top-to-bottom names> --name "<template>"
   ```
   It removes the background, splits the sheet, trims, pads by 2px and bleeds edge colour. If it
   reports the wrong sprite count it writes nothing: adjust `--join`, `--min-area`,
   `--min-brightness`/`--max-saturation`, never cut the remainder manually. A single image that
   already has real transparency only needs `python tools/art/art.py bleed <file>`. If the
   generator painted its transparency checkerboard into the cut-out (grey squares floating
   beside the art - this shipped once, in port_town.png), `python tools/art/art.py uncheck
   <file>` clears them and bleeds; it refuses to touch a sprite with no checkerboard in it.
   A picture painted on one flat colour is cut with `python tools/art/art.py key <file> --colour
   <hex>`, which also reaches the holes a flood fill cannot - between a basket's handle and the
   fruit, the gaps in a bunch of peppers - and is why props are generated on magenta. Add
   `--flood` when the key colour also appears inside the art (a frame on black has black
   shadows): the cut is then limited to background the border can reach. Add `--shade 24` when
   the generator painted the object's shadow in a darkened version of the key colour - that
   shadow is indistinguishable from real art by distance and obvious by hue.

   Before choosing a tolerance, measure instead of guessing: histogram each pixel's distance from
   the key colour. The background clusters near 0, the real art starts past a clear valley, and
   the tolerance belongs in that valley. For the HUD elements it came out at 100, far above the
   40 that looked safe by eye and left a magenta halo behind.
   First run on a machine: `pip install -r tools/art/requirements.txt`.
3. **Eyeball edges before code:** `python tools/art/art.py preview <sprite>` renders it on dark
   and light backgrounds, downscaled the way the GPU does it. Look at the PNG (Read it). A halo or
   a bitten-off edge is fixed here, not in the game.
4. **Wire it in.** Ship artwork is loaded only through `ShipArt.texture(path)`; a painted icon
   is a new `by lazy { buildArtIcon("sprites/icons/<name>.png") }` entry in `IconFactory`. Do not add a new `Texture(Gdx.files.internal(...))` for
   painted art - it would get the Nearest default filter and no mipmaps.
5. **`:core:test`.** AssetIntegrityTest fails on a missing path, an opaque background, an unbled
   edge, a painted checkerboard baked into a sprite, or a source sheet left in assets/. Its message names the art.py command that fixes it.
6. **Look at it in the game yourself** with the shot mode (compile first; never while a play
   session is open - see CLAUDE.md):
   ```
   ./gradlew.bat -q :lwjgl3:run --args="--shot=<screen> --shot-seed=1"
   ```
   Screens: menu, sea, hub, market, shipyard, status, fortress, warehouse, tavern, docks
   (`--shot-port=<name>` for which port; default Port Royal). The output line `SHOT <path>` names
   the PNG (default `lwjgl3/build/shots/<screen>.png`); Read it. Check size relative to other
   things on screen, position, edges, and that nothing else on the screen changed.
7. **Then hand it to the user** with the screenshot, per CLAUDE.md testing rule 4 - your look is
   a filter before theirs, not a replacement for it.
8. If the art came from a new source, add it to `NOTICE` (third-party assets section).
