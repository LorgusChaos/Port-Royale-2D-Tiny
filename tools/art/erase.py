"""
Take an object out of a picture by cloning the surface it sits on, with no model involved.

Asking the model to remove something goes wrong in two ways we hit for real. Repainting a
rectangle leaves a patch that is lighter than its surroundings and cuts whatever crosses its
border in half - a rope that now ends in mid-air. Repainting the whole canvas at a strength that
would remove anything restyles the entire picture, and at a strength that preserves it, removes
nothing: img2img keeps structure, and a hanging basket is structure.

So: find the objects against the flat surface they hang over, and fill their pixels from the same
surface further down the same image. The fill is the picture's own paper, so the tone matches by
construction, and the mask follows the objects (grown upward to the beam they hang from) rather
than a blind rectangle, so nothing is left severed.

    python tools/art/erase.py art-src/ui/market.png --out art-src/ui/market_clear.png \
        --region 205,182,1125,480 --keep-outside 300,1050 --from-below 260

--region is where to look (y down). --keep-outside x0,x1 spares anything centred left of x0 or
right of x1 - the props hanging against the side posts are wanted; the ones over the middle of
the panel are the ones that would sit under the interface's own buttons.
"""

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage


def surface_colour(image: np.ndarray, region) -> np.ndarray:
    """The flat surface's own colour, taken well below the objects so none of them skew it."""
    x0, y0, x1, y1 = region
    below = image[y1:min(y1 + 240, image.shape[0]), x0:x1]
    return np.median(below.reshape(-1, 3), axis=0)


def find_objects(image: np.ndarray, region, threshold: int, min_area: int):
    x0, y0, x1, y1 = region
    colour = surface_colour(image, region)
    band = image[y0:y1, x0:x1]
    mask = np.abs(band - colour).max(axis=2) > threshold
    mask = ndimage.binary_opening(mask, np.ones((3, 3), bool))
    mask = ndimage.binary_closing(mask, np.ones((7, 7), bool))
    labels, count = ndimage.label(mask, np.ones((3, 3), bool))
    found = []
    for index in range(1, count + 1):
        blob = labels == index
        if blob.sum() < min_area:
            continue
        ys, xs = np.where(blob)
        found.append({
            "mask": blob,
            "x0": int(xs.min()) + x0, "x1": int(xs.max()) + x0,
            "y0": int(ys.min()) + y0, "y1": int(ys.max()) + y0,
            "area": int(blob.sum()),
        })
    return found, colour


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("image")
    p.add_argument("--out", required=True)
    p.add_argument("--region", metavar="X0,Y0,X1,Y1", help="where to look for objects")
    p.add_argument("--rect", action="append", default=[], metavar="X0,Y0,X1,Y1",
                   help="erase this whole rectangle, no detection. For what detection cannot see: "
                        "the soft shadow an object cast, which stays behind as a pale ghost")
    p.add_argument("--from-mirror", action="store_true",
                   help="clone from the picture mirrored left-to-right. For an edge or a post that "
                        "has no clean twin above, below or beside it, but does have one on the "
                        "other side of a symmetrical frame")
    p.add_argument("--from-side", type=int, metavar="DX",
                   help="clone from DX px to the side instead of from below - near an edge the "
                        "surface below is a different thing (a beam), and cloning it paints over that edge")
    p.add_argument("--keep-outside", metavar="X0,X1", help="spare objects centred outside this x range")
    p.add_argument("--from-below", type=int, default=260, help="px below the object to clone the surface from")
    p.add_argument("--threshold", type=int, default=45, help="colour distance that counts as 'not the surface'")
    p.add_argument("--min-area", type=int, default=200)
    p.add_argument("--grow", type=int, default=4, help="px to widen each object's mask by")
    p.add_argument("--feather", type=int, default=3)
    p.add_argument("--up-to", type=int, help="extend each mask up to this y, to take its cord/hook with it")
    args = p.parse_args()

    if not args.region and not args.rect:
        p.error("give --region to find objects, or --rect to erase a stated area")
    source = Image.open(args.image).convert("RGBA")
    rgb = np.array(source.convert("RGB")).astype(int)
    erase = np.zeros(rgb.shape[:2], bool)

    for spec in args.rect:
        x0, y0, x1, y1 = (int(n) for n in spec.split(","))
        erase[y0:y1, x0:x1] = True
        print(f"  erase  rectangle x {x0}..{x1}  y {y0}..{y1}")

    objects = []
    if args.region:
        region = tuple(int(n) for n in args.region.split(","))
        objects, _ = find_objects(rgb, region, args.threshold, args.min_area)
        if not objects and not args.rect:
            p.error("no objects found - raise --threshold or widen --region")

    keep_x = tuple(int(n) for n in args.keep_outside.split(",")) if args.keep_outside else None
    for obj in objects:
        centre = (obj["x0"] + obj["x1"]) // 2
        if keep_x and not (keep_x[0] <= centre <= keep_x[1]):
            print(f"  keep   x {obj['x0']}..{obj['x1']}  y {obj['y0']}..{obj['y1']}")
            continue
        print(f"  erase  x {obj['x0']}..{obj['x1']}  y {obj['y0']}..{obj['y1']}  ({obj['area']} px)")
        full = np.zeros(rgb.shape[:2], bool)
        full[region[1]:region[3], region[0]:region[2]] = obj["mask"]
        if args.up_to is not None:
            # Everything above the object in its own columns goes too: the cord it hangs by,
            # the hook, the knot. Leaving those is what makes a removal look like a mistake.
            columns = full.any(axis=0)
            top = np.where(full.any(axis=1))[0].min()
            full[args.up_to:top, :] |= columns[None, :]
        erase |= full

    # Guarded on purpose: scipy reads iterations=0 as "dilate until nothing changes", which grows
    # the mask over the whole picture and silently replaces it with a shifted copy of itself.
    if args.grow > 0:
        erase = ndimage.binary_dilation(erase, np.ones((3, 3), bool), iterations=args.grow)
    if args.from_mirror:
        donor = rgb[:, ::-1]
        where = "the mirrored picture"
    elif args.from_side:
        offset = args.from_side
        donor = np.roll(rgb, -offset, axis=1)
        where = f"{abs(offset)}px to the {'right' if offset > 0 else 'left'}"
    else:
        offset = args.from_below
        donor = np.roll(rgb, -offset, axis=0)
        where = f"{offset}px below"
    alpha = np.array(Image.fromarray((erase * 255).astype(np.uint8)).filter(
        ImageFilter.GaussianBlur(args.feather)), dtype=float)[..., None] / 255.0
    merged = rgb * (1 - alpha) + donor * alpha

    out = np.dstack([merged.astype(np.uint8), np.array(source)[..., 3]])
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(out, "RGBA").save(args.out)
    print(f"{args.out}  ({int(erase.sum())} px filled from {where})")


if __name__ == "__main__":
    main()
