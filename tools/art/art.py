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
    for r, row in enumerate(grid):
        for c, (y0, x0, y1, x1) in enumerate(row):
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
    s.add_argument("--join", type=int, default=3, help="px gap still treated as one sprite")
    s.add_argument("--min-area", type=float, default=0.0005, help="ignore specks below this fraction of the sheet")
    s.add_argument("--min-brightness", type=int, default=200)
    s.add_argument("--max-saturation", type=int, default=24)
    s.set_defaults(fn=cmd_slice)

    b = sub.add_parser("bleed", help="fix edge colour of existing RGBA sprites in place")
    b.add_argument("files", nargs="+")
    b.set_defaults(fn=cmd_bleed)

    v = sub.add_parser("preview", help="render a sprite on dark/light backgrounds to eyeball edges")
    v.add_argument("file")
    v.add_argument("--scale", type=float, default=0.25)
    v.add_argument("--out")
    v.set_defaults(fn=cmd_preview)

    args = p.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
