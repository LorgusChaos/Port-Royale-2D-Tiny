"""
Art pipeline for Port Royale 2D Tiny: from a generated sheet to sprites the game can load.

Every sprite that ends up in assets/sprites goes through this script, never through hand-done
cropping. The reason is the same one CLAUDE.md gives for sailing the real map in a test: done by
eye, the same three mistakes come back every time - a background that was painted rather than
transparent (image generators love a fake checkerboard), crops that differ by a few pixels from
one tier to the next, and the colour hiding under transparent pixels.

That last one is invisible until it isn't. A cut-out sprite's transparent pixels still carry RGB,
usually whatever the background was (white, here). Under Nearest filtering nobody sees it; under
Linear filtering or mipmaps the GPU blends those pixels into the edge and every ship grows a white
halo. `bleed` paints each transparent pixel with the colour of the nearest opaque one, so blending
at the edge blends with the sprite itself. AssetIntegrityTest checks the result.

Usage (from the repo root; needs: pip install -r tools/art/requirements.txt):

  python tools/art/art.py slice art-src/ships/ship_sheet_v2_source.png assets/sprites/ships \
      --cols white,cream,red,blue,green,black --rows 1,2,3,4,5 --name "ship2_{col}_{row}"

  python tools/art/art.py bleed assets/sprites/ships/ship2_*.png assets/sprites/icons/*.png

  python tools/art/art.py uncheck assets/sprites/icons/port_town.png     # checkerboard crumbs

  python tools/art/art.py key art-src/ui/market_native_wide.png          # magenta -> transparent

  python tools/art/art.py preview assets/sprites/ships/ship2_red_3.png   # sprite on dark + light

`slice` refuses to write anything if it does not find exactly len(cols) * len(rows) sprites, and
says how many it did find: a wrong count means the background removal or the grouping went wrong,
and a half-written set of sprites is worse than none.
"""

import argparse
import glob
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

# --- background removal -------------------------------------------------------------------------


def background_mask(rgb: np.ndarray, min_brightness: int, max_saturation: int) -> np.ndarray:
    """Pixels that look like sheet background AND are connected to the sheet border.

    "Looks like background" = light and grey: that covers plain white and the two light greys of a
    painted transparency checkerboard alike. Connected-to-border is what keeps a white sail from
    being eaten: a sail is enclosed by the hull's dark outline, so the flood never reaches it.
    """
    r, g, b = (rgb[..., i].astype(int) for i in range(3))
    brightness = (r + g + b) // 3
    saturation = np.max(rgb, axis=2).astype(int) - np.min(rgb, axis=2).astype(int)
    candidate = (brightness >= min_brightness) & (saturation <= max_saturation)
    labels, _ = ndimage.label(candidate)
    border = np.unique(np.concatenate([labels[0, :], labels[-1, :], labels[:, 0], labels[:, -1]]))
    border = border[border != 0]
    return np.isin(labels, border)


def chroma_mask(rgb: np.ndarray, colour, tolerance: int) -> np.ndarray:
    """Pixels within `tolerance` of a flat key colour, wherever they are in the picture.

    Unlike background_mask this does not care about being connected to the border, which is the
    whole point: the holes a flood fill cannot reach - between a basket's handle and the fruit, the
    pans of a scale, the gaps in a bunch of peppers - came back opaque white and read as cut-out
    paper in the game. Generating on magenta instead of white makes those holes unambiguous, since
    no part of this game's art is magenta.
    """
    return np.abs(rgb.astype(int) - np.array(colour, dtype=int)).max(axis=2) <= tolerance


def to_rgba(path: Path, min_brightness: int, max_saturation: int) -> np.ndarray:
    im = Image.open(path).convert("RGBA")
    a = np.array(im)
    if (a[..., 3] < 255).mean() > 0.05:
        # Already has real transparency - trust it rather than guessing a background.
        return a
    bg = background_mask(a[..., :3], min_brightness, max_saturation)
    a[..., 3] = np.where(bg, 0, 255).astype(np.uint8)
    return a


# --- edge colour --------------------------------------------------------------------------------


def bleed(a: np.ndarray, alpha_threshold: int = 8) -> np.ndarray:
    """Give every (nearly) transparent pixel the RGB of the nearest opaque pixel. Alpha untouched."""
    alpha = a[..., 3]
    opaque = alpha > alpha_threshold
    if opaque.all() or not opaque.any():
        return a
    _, (iy, ix) = ndimage.distance_transform_edt(~opaque, return_indices=True)
    out = a.copy()
    clear = ~opaque
    out[..., :3][clear] = a[iy[clear], ix[clear], :3]
    return out


# --- checkerboard left inside a cut-out -----------------------------------------------------------


def checker_tones(a: np.ndarray, max_saturation: int, min_share: float = 0.10, min_contrast: int = 25):
    """The two grey tones of a painted transparency checkerboard, or None if there is no such pair.

    Taken from the opaque pixels that sit against the transparent field, because that is where the
    leftovers are: a generator's checkerboard survives background removal only where the removal
    stopped, i.e. hugging the sprite. Quantised to 12 levels so the JPEG-ish noise inside one
    square still counts as one tone.

    Two tones of nearly the same brightness are a sprite's own dark outline, not a checkerboard -
    every clean sprite in assets/ looks like that - so a real brightness gap is required.
    """
    rgb = a[..., :3].astype(int)
    opaque = a[..., 3] > 8
    rim = ndimage.binary_dilation(~opaque, np.ones((5, 5), bool)) & opaque
    saturation = rgb.max(axis=2) - rgb.min(axis=2)
    candidates = rgb[rim & (saturation <= max_saturation)]
    if len(candidates) < 200:
        return None
    quantised, counts = np.unique((candidates // 12) * 12, axis=0, return_counts=True)
    top = [i for i in np.argsort(counts)[::-1][:2] if counts[i] >= min_share * len(candidates)]
    if len(top) < 2:
        return None
    dark, light = quantised[top[0]] + 6, quantised[top[1]] + 6
    if abs(dark.mean() - light.mean()) < min_contrast:
        return None
    return dark, light


def checker_block(a: np.ndarray, tones, tolerance: int, min_gap: float = 0.25):
    """The checkerboard's square size, or None if those two tones are not laid out as a grid.

    A checkerboard is the one pattern where a pixel resembles its neighbour one square away least
    and its neighbour two squares away most. Correlating the two-tone map against itself at every
    lag therefore dips at the square size and peaks at twice it; art without a grid in it has no
    such dip. This is the check that keeps `uncheck` from eating the grey stonework of a sprite
    that never had a checkerboard on it.
    """
    rgb = a[..., :3].astype(int)
    opaque = a[..., 3] > 8
    signed = np.zeros(opaque.shape, np.int8)
    signed[(np.abs(rgb - tones[0]).max(axis=2) <= tolerance) & opaque] = 1
    signed[(np.abs(rgb - tones[1]).max(axis=2) <= tolerance) & opaque] = -1
    correlation = {}
    for lag in range(2, 25):
        across = signed[:, :-lag] * signed[:, lag:]
        down = signed[:-lag, :] * signed[lag:, :]
        pairs = int((across != 0).sum() + (down != 0).sum())
        if pairs >= 200:
            correlation[lag] = float(across.sum() + down.sum()) / pairs
    lags = [d for d in correlation if 2 <= d <= 12 and 2 * d in correlation]
    if not lags:
        return None
    block = min(lags, key=lambda d: correlation[d])
    return block if correlation[2 * block] - correlation[block] >= min_gap else None


def uncheck(a: np.ndarray, tolerance: int, max_saturation: int, close_px: int = 9, grow_px: int = 2):
    """Clear the checkerboard squares an image generator painted around (not under) the sprite.

    `bleed` cannot help here: these squares are opaque pixels, part of the picture as far as the
    GPU is concerned, and they show on the map as grey crumbs floating beside a town. They cannot
    be keyed out by colour alone either - the sprite's own shadows share those greys - so the
    sprite's body is found first (the largest run of pixels that are NOT the checkerboard's tones,
    closed into a solid silhouette), and only what lies outside it is cleared.

    Returns (image, pixels cleared, square size); (a, 0, None) when there is no checkerboard.
    """
    opaque = a[..., 3] > 8
    tones = checker_tones(a, max_saturation) if opaque.any() else None
    if tones is None:
        return a, 0, None
    block = checker_block(a, tones, tolerance)
    if block is None:
        return a, 0, None
    rgb = a[..., :3].astype(int)
    checker = np.zeros(opaque.shape, bool)
    for tone in tones:
        checker |= np.abs(rgb - tone).max(axis=2) <= tolerance
    body = opaque & ~checker
    labels, found = ndimage.label(body, np.ones((3, 3), bool))
    if found == 0:
        return a, 0, None
    sizes = ndimage.sum(body, labels, range(1, found + 1))
    core = labels == (int(np.argmax(sizes)) + 1)
    silhouette = ndimage.binary_fill_holes(ndimage.binary_closing(core, np.ones((close_px, close_px), bool)))
    keep = opaque & silhouette
    # The sprite's own outline is dark and unsaturated too, so it can read as checkerboard; let
    # the kept body reclaim its immediate neighbours, but never a pixel of the checkerboard itself.
    for _ in range(grow_px):
        keep |= ndimage.binary_dilation(keep, np.ones((3, 3), bool)) & opaque & ~checker
    cleared = opaque & ~keep
    out = a.copy()
    out[..., 3][cleared] = 0
    return out, int(cleared.sum()), block


# --- slicing ------------------------------------------------------------------------------------


def find_sprites(alpha: np.ndarray, join_px: int, min_area_fraction: float):
    """Bounding boxes of the sprites on a sheet, in reading order (rows top to bottom, then x).

    Parts of one sprite that do not touch (a pennant above the mast, a loose rope) are joined by
    dilating the mask by join_px before labelling; the boxes are then taken from the undilated
    mask so the crop stays tight.
    """
    solid = alpha > 0
    grouped = ndimage.binary_dilation(solid, iterations=join_px) if join_px > 0 else solid
    labels, n = ndimage.label(grouped)
    min_area = solid.size * min_area_fraction
    boxes = []
    for i, sl in enumerate(ndimage.find_objects(labels), start=1):
        if sl is None:
            continue
        part = solid[sl] & (labels[sl] == i)
        if part.sum() < min_area:
            continue
        ys, xs = np.nonzero(part)
        boxes.append((sl[0].start + ys.min(), sl[1].start + xs.min(), sl[0].start + ys.max() + 1, sl[1].start + xs.max() + 1))
    if not boxes:
        return []
    # Group into rows by vertical centre: a new row starts when a centre is further below the
    # current row's first centre than half the tallest sprite seen so far.
    boxes.sort(key=lambda b: (b[0] + b[2]) / 2)
    rows, current = [], [boxes[0]]
    tallest = boxes[0][2] - boxes[0][0]
    for b in boxes[1:]:
        tallest = max(tallest, b[2] - b[0])
        row_centre = (current[0][0] + current[0][2]) / 2
        if (b[0] + b[2]) / 2 - row_centre > tallest / 2:
            rows.append(current)
            current = [b]
        else:
            current.append(b)
    rows.append(current)
    return [sorted(r, key=lambda b: b[1]) for r in rows]


def cmd_slice(args):
    sheet = to_rgba(Path(args.sheet), args.min_brightness, args.max_saturation)
    rows = find_sprites(sheet[..., 3], args.join, args.min_area)
    cols_names = args.cols.split(",")
    row_names = args.rows.split(",")
    # Sheets are laid out either one row per --rows value (a row = one tier, columns = colours)
    # or transposed; take whichever matches what was found.
    shape = [len(r) for r in rows]
    if shape == [len(cols_names)] * len(row_names):
        grid = rows
    elif shape == [len(row_names)] * len(cols_names):
        grid = [list(t) for t in zip(*rows)]
    else:
        sys.exit(
            f"slice: expected {len(row_names)} rows x {len(cols_names)} sprites, found rows of {shape}. "
            "Nothing written. Try --join (sprite falls apart) / --min-area (specks) / "
            "--min-brightness, --max-saturation (background not removed)."
        )
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    pad = args.pad
    h, w = sheet.shape[:2]
    # With --equal every sprite is cut to the same box, centred on its own middle, instead of
    # trimmed to its own ink. For the frames of one thing - a button drawn normal and pressed,
    # where the pressed one is painted slightly smaller - trimming each to its own size makes the
    # button change size and jump under the cursor. The size difference has to survive INSIDE a
    # constant box, which is exactly what this does.
    box = None
    if args.equal:
        box = (max(y1 - y0 for row in grid for (y0, _, y1, _) in row) + pad * 2,
               max(x1 - x0 for row in grid for (_, x0, _, x1) in row) + pad * 2)
    for r, row in enumerate(grid):
        for c, (y0, x0, y1, x1) in enumerate(row):
            if box is not None:
                cy, cx = (y0 + y1) // 2, (x0 + x1) // 2
                y0, x0 = cy - box[0] // 2, cx - box[1] // 2
                y1, x1 = y0 + box[0], x0 + box[1]
                sprite = np.zeros((box[0], box[1], 4), np.uint8)
                sy0, sx0 = max(0, -y0), max(0, -x0)
                y0, x0 = max(0, y0), max(0, x0)
                y1, x1 = min(h, y1), min(w, x1)
                sprite[sy0:sy0 + (y1 - y0), sx0:sx0 + (x1 - x0)] = sheet[y0:y1, x0:x1]
                sprite = bleed(sprite)
                name = args.name.format(col=cols_names[c], row=row_names[r]) + ".png"
                Image.fromarray(sprite, "RGBA").save(out_dir / name, optimize=True)
                print(f"{out_dir / name}  {box[1]}x{box[0]}")
                continue
            y0, x0, y1, x1 = max(0, y0 - pad), max(0, x0 - pad), min(h, y1 + pad), min(w, x1 + pad)
            sprite = bleed(sheet[y0:y1, x0:x1])
            name = args.name.format(col=cols_names[c], row=row_names[r]) + ".png"
            Image.fromarray(sprite, "RGBA").save(out_dir / name, optimize=True)
            print(f"{out_dir / name}  {x1 - x0}x{y1 - y0}")


def cmd_bleed(args):
    paths = [p for pattern in args.files for p in (glob.glob(pattern) or [pattern])]
    for p in paths:
        im = Image.open(p)
        if im.mode != "RGBA":
            print(f"skip {p}: no alpha channel ({im.mode})")
            continue
        Image.fromarray(bleed(np.array(im)), "RGBA").save(p, optimize=True)
        print(f"bled {p}")


def cmd_uncheck(args):
    paths = [p for pattern in args.files for p in (glob.glob(pattern) or [pattern])]
    for p in paths:
        im = Image.open(p)
        if im.mode != "RGBA":
            print(f"skip {p}: no alpha channel ({im.mode})")
            continue
        a = np.array(im)
        cleaned, cleared, block = uncheck(a, args.tolerance, args.max_saturation)
        if cleared == 0:
            print(f"clean {p}: no checkerboard found")
            continue
        share = cleared * 100.0 / max(int((a[..., 3] > 8).sum()), 1)
        if share > args.max_share:
            print(
                f"REFUSED {p}: would clear {cleared} px ({share:.1f}% of the sprite) - that is more "
                f"than --max-share={args.max_share}%. Check the sprite by eye before forcing it."
            )
            continue
        Image.fromarray(bleed(cleaned), "RGBA").save(p, optimize=True)
        print(f"uncheckered {p}: {block}px squares, cleared {cleared} px ({share:.1f}%), then bled")


def cmd_key(args):
    """Key a flat background colour out of generated art, clean its spill, and bleed, in place.

    Three steps, because a hard threshold alone leaves a coloured rim: the pixels where the art
    was blended against the key colour by the renderer are neither art nor background. They get a
    partial alpha instead (the ramp between --tolerance and --soft), and then the key colour is
    subtracted from what remains of them - without that, a magenta fringe outlines every sprite,
    which is the same halo the bleed step exists to prevent, arriving from the other direction.
    """
    colour = tuple(int(args.colour[i:i + 2], 16) for i in (0, 2, 4))
    paths = [p for pattern in args.files for p in (glob.glob(pattern) or [pattern])]
    for path in paths:
        a = np.array(Image.open(path).convert("RGBA"))
        rgb = a[..., :3].astype(int)
        distance = np.abs(rgb - np.array(colour, dtype=int)).max(axis=2)
        inner, outer = args.tolerance, args.tolerance + max(args.soft, 1)
        ramp = np.clip((distance - inner) / float(outer - inner), 0.0, 1.0)
        keyed = distance <= inner
        if args.shade:
            # A generator asked to paint on a flat colour often paints the object's shadow as a
            # DARKENED version of that colour - magenta at (121,4,121) rather than (255,0,255).
            # By distance that sits exactly where the real art begins, so no threshold separates
            # them; by hue it is unmistakable. Normalising each pixel by its brightest channel
            # throws brightness away and leaves the hue, which is what actually identifies the
            # background here.
            brightest = rgb.max(axis=2)
            lit = brightest >= 40                      # near-black normalises to noise
            scale = np.where(brightest == 0, 1, brightest)[..., None]
            key_scale = max(colour)
            normalised = rgb * (255.0 / scale)
            key_normalised = np.array(colour, dtype=float) * (255.0 / key_scale)
            same_hue = np.abs(normalised - key_normalised).max(axis=2) <= args.shade
            shaded = same_hue & lit
            keyed |= shaded
            # Alpha comes from the distance ramp, not from `keyed` - which is why this has to
            # clear the ramp too. (It did not, at first, and --shade silently did nothing.)
            ramp = np.where(shaded, 0.0, ramp)
        if args.flood:
            # Only the background that the border can reach. A flat magenta field is unambiguous
            # anywhere in the picture, so `key` normally ignores where a pixel sits - but a frame
            # generated on black shares its key colour with its own painted shadows, and keying
            # those punches holes through the art. Flooding from the border keeps the outside
            # outside; the ramp below still softens whatever edge the flood stopped at.
            labels, _ = ndimage.label(keyed)
            border = np.unique(np.concatenate([labels[0, :], labels[-1, :], labels[:, 0], labels[:, -1]]))
            border = border[border != 0]
            keyed = np.isin(labels, border)
            # The distance ramp only means anything beside the cut. Everywhere else the art may
            # legitimately be as dark as the key colour, and letting the ramp reach it would turn
            # every painted shadow half-transparent.
            edge = ndimage.binary_dilation(keyed, np.ones((3, 3), bool),
                                           iterations=max(args.soft // 8, 2)) & ~keyed
            ramp = np.where(keyed, 0.0, np.where(edge, ramp, 1.0))
        if not keyed.any():
            print(f"skip {path}: nothing within {args.tolerance} of #{args.colour}")
            continue
        # Spill is only ever a border effect; limiting the fix to a band around the keyed area
        # keeps it off the art's own reds and pinks.
        band = ndimage.binary_dilation(keyed, np.ones((3, 3), bool), iterations=args.spill) & ~keyed
        if band.any() and args.spill:
            r, g, b = (rgb[..., i].astype(float) for i in range(3))
            excess = np.clip((r + b) / 2.0 - g, 0, None)
            r = np.where(band, r - excess, r)
            b = np.where(band, b - excess, b)
            rgb = np.clip(np.dstack([r, g, b]), 0, 255)
            a[..., :3] = rgb.astype(np.uint8)
        a[..., 3] = (a[..., 3] * ramp).astype(np.uint8)
        Image.fromarray(bleed(a), "RGBA").save(path, optimize=True)
        print(f"keyed {path}: {keyed.sum()} px out, {int(((ramp > 0) & (ramp < 1)).sum())} px on the soft edge, spill cleaned, bled")


def cmd_preview(args):
    """Sprite on a dark and a light background, scaled down with smooth filtering the way the
    game draws it - a halo or a ragged edge shows up here before it shows up in the game.

    Each channel is resized on its own, without premultiplying by alpha, because that is what
    the GPU does with a straight-alpha PNG under Linear/mipmap filtering. Pillow's RGBA resize
    premultiplies, which would hide exactly the halo this preview exists to show."""
    src = Image.open(args.file).convert("RGBA")
    scale = args.scale
    size = (max(1, round(src.width * scale)), max(1, round(src.height * scale)))
    small = Image.merge("RGBA", [ch.resize(size, Image.BOX) for ch in src.split()])
    tiles = []
    for colour in ((24, 40, 64, 255), (225, 205, 160, 255)):
        bg = Image.new("RGBA", (src.width + small.width + 30, src.height + 20), colour)
        bg.alpha_composite(src, (10, 10))
        bg.alpha_composite(small, (src.width + 20, 10))
        tiles.append(bg)
    sheet = Image.new("RGBA", (tiles[0].width, tiles[0].height * 2))
    sheet.paste(tiles[0], (0, 0))
    sheet.paste(tiles[1], (0, tiles[0].height))
    out = Path(args.out or (Path(args.file).stem + "_preview.png"))
    sheet.save(out)
    print(out)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("slice", help="cut a sheet into named, trimmed, edge-bled sprites")
    s.add_argument("sheet")
    s.add_argument("out")
    s.add_argument("--cols", required=True, help="comma list, left to right (e.g. colours)")
    s.add_argument("--rows", required=True, help="comma list, top to bottom (e.g. tiers)")
    s.add_argument("--name", required=True, help='file name template, e.g. "ship2_{col}_{row}"')
    s.add_argument("--pad", type=int, default=2, help="transparent margin around each sprite")
    s.add_argument("--equal", action="store_true",
                   help="cut every sprite to the same box, centred on its own middle, rather than "
                        "trimming each to its own ink. For the states of one button, which must "
                        "not change size between frames")
    s.add_argument("--join", type=int, default=3, help="px gap still treated as one sprite")
    s.add_argument("--min-area", type=float, default=0.0005, help="ignore specks below this fraction of the sheet")
    s.add_argument("--min-brightness", type=int, default=200)
    s.add_argument("--max-saturation", type=int, default=24)
    s.set_defaults(fn=cmd_slice)

    b = sub.add_parser("bleed", help="fix edge colour of existing RGBA sprites in place")
    b.add_argument("files", nargs="+")
    b.set_defaults(fn=cmd_bleed)

    u = sub.add_parser("uncheck", help="clear checkerboard squares baked into a cut-out, then bleed")
    u.add_argument("files", nargs="+")
    u.add_argument("--tolerance", type=int, default=22, help="how far a pixel may sit from a checker tone")
    u.add_argument("--max-saturation", type=int, default=24)
    u.add_argument("--max-share", type=float, default=25.0, help="refuse if it would clear more than this %% of the sprite")
    u.set_defaults(fn=cmd_uncheck)

    k = sub.add_parser("key", help="make a flat background colour transparent (holes included), then bleed")
    k.add_argument("files", nargs="+")
    k.add_argument("--colour", default="FF00FF", help="hex, default magenta")
    k.add_argument("--tolerance", type=int, default=60, help="distance from the key colour that counts as background")
    k.add_argument("--soft", type=int, default=40, help="width of the partial-alpha ramp past the tolerance")
    k.add_argument("--spill", type=int, default=3, help="px of edge to clean the key colour out of (0 to skip)")
    k.add_argument("--shade", type=int, default=0, metavar="TOLERANCE",
                   help="also key pixels that are a DARKENED version of the key colour, matched by "
                        "hue instead of distance - the shadow a generator paints under an object "
                        "in the background's own colour. 24 is a good value; 0 (default) is off")
    k.add_argument("--flood", action="store_true",
                   help="key only background connected to the border. Needed when the key colour "
                        "also occurs inside the art - a frame painted on black has black shadows")
    k.set_defaults(fn=cmd_key)

    v = sub.add_parser("preview", help="render a sprite on dark/light backgrounds to eyeball edges")
    v.add_argument("file")
    v.add_argument("--scale", type=float, default=0.25)
    v.add_argument("--out")
    v.set_defaults(fn=cmd_preview)

    args = p.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
