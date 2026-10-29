"""
Put cut-out props onto a piece of art at stated coordinates.

Why this exists rather than a graphics editor: a frame repainted whole by a model loses its edges
(a 1536x1024 canvas is a 192x128 latent - a basket's outline is two cells of it, and it comes back
soft). Props drawn one at a time at the model's own size keep their outlines, and the base art is
not touched at all. What is left is placement, and placement done by hand is exactly the step this
project does not allow: it cannot be repeated, and it drifts a few pixels every time.

So placement is numbers, in the base picture's own pixel space:

    python tools/art/compose.py assets/sprites/ui/port_hub_frame.png \
        --out art-src/ui/market_frame.png \
        --place art-src/props/basket_fruit.png:250,960,210 \
        --place art-src/props/scales.png:1180,300,160,top-center

Each --place is file:x,y,width[,anchor]. Width is the size the prop is drawn at, in base pixels;
height follows from its own aspect. The anchor says what (x,y) means - default bottom-center, so a
basket sits ON a beam at that point rather than hovering with its corner there.

Anchors: bottom-center (default), bottom-left, bottom-right, center, top-left, top-center.
Add :flip to mirror a prop horizontally (one awning, both ends of a beam).

The result still goes through the rest of the pipeline - `art.py bleed` before it ships, and
AssetIntegrityTest after.
"""

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ANCHORS = {
    "bottom-center": lambda w, h: (-w // 2, -h),
    "bottom-left": lambda w, h: (0, -h),
    "bottom-right": lambda w, h: (-w, -h),
    "center": lambda w, h: (-w // 2, -h // 2),
    "top-left": lambda w, h: (0, 0),
    "top-center": lambda w, h: (-w // 2, 0),
}


def tone_to_surroundings(prop: Image.Image, base: Image.Image, box, strength: float) -> Image.Image:
    """Pull a prop's colour towards the art it is being placed on.

    A prop drawn on its own white field comes back lit from nowhere, lighter and cooler than a
    frame painted in warm lamplight, and it reads as a sticker. Matching the channel means of the
    prop to the patch of art behind it fixes most of that in one multiply - it keeps the prop's
    own shading (it is a scale, not a repaint) while moving its overall cast into the scene.
    """
    if strength <= 0:
        return prop
    behind = np.asarray(base.crop(box).convert("RGB"), dtype=float)
    pixels = np.asarray(prop.convert("RGBA"), dtype=float)
    opaque = pixels[..., 3] > 32
    if not opaque.any():
        return prop
    prop_mean = pixels[..., :3][opaque].mean(axis=0)
    base_mean = behind.reshape(-1, 3).mean(axis=0)
    gain = np.clip((base_mean + 1e-6) / (prop_mean + 1e-6), 0.55, 1.6) ** strength
    pixels[..., :3] = np.clip(pixels[..., :3] * gain, 0, 255)
    return Image.fromarray(pixels.astype("uint8"), "RGBA")


def contact_shadow(size, strength: float) -> Image.Image:
    """A soft dark ellipse to sit under a prop, so it stands on the beam instead of hovering."""
    width, height = size
    shadow_h = max(6, round(height * 0.16))
    layer = Image.new("RGBA", (width, shadow_h * 3), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.ellipse((width * 0.06, shadow_h, width * 0.94, shadow_h * 2),
                 fill=(20, 12, 6, int(255 * strength)))
    return layer.filter(ImageFilter.GaussianBlur(shadow_h * 0.45))


def parse_placement(spec: str):
    """file:x,y,width[,anchor][,flip] -> (path, x, y, width, anchor, flip)"""
    path, _, rest = spec.rpartition(":")
    if not path:
        raise ValueError(f"expected file:x,y,width[,anchor] - got '{spec}'")
    parts = [p.strip() for p in rest.split(",")]
    if len(parts) < 3:
        raise ValueError(f"'{spec}' needs at least x,y,width")
    x, y, width = (int(p) for p in parts[:3])
    anchor = "bottom-center"
    flip = False
    for extra in parts[3:]:
        if extra == "flip":
            flip = True
        elif extra in ANCHORS:
            anchor = extra
        else:
            raise ValueError(f"unknown option '{extra}' in '{spec}'. Anchors: {', '.join(ANCHORS)}")
    return Path(path), x, y, width, anchor, flip


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("base", help="the art to put props on (RGBA; it is never modified in place)")
    p.add_argument("--out", required=True)
    p.add_argument("--place", action="append", required=True, metavar="FILE:X,Y,W[,ANCHOR]")
    p.add_argument("--tone", type=float, default=0.0, metavar="0..1",
                   help="pull each prop's colour towards the art behind it (0.5 is a good start)")
    p.add_argument("--shadow", type=float, default=0.0, metavar="0..1",
                   help="soft contact shadow under each prop, so it stands rather than hovers")
    args = p.parse_args()

    base = Image.open(args.base).convert("RGBA")
    for spec in args.place:
        try:
            path, x, y, width, anchor, flip = parse_placement(spec)
        except ValueError as e:
            p.error(str(e))
        if not path.is_file():
            p.error(f"no such prop: {path}")
        prop = Image.open(path).convert("RGBA")
        if flip:
            prop = prop.transpose(Image.FLIP_LEFT_RIGHT)
        height = max(1, round(prop.height * width / prop.width))
        prop = prop.resize((width, height), Image.LANCZOS)
        dx, dy = ANCHORS[anchor](width, height)
        left, top = x + dx, y + dy
        if args.tone:
            prop = tone_to_surroundings(prop, base, (left, top, left + width, top + height), args.tone)
        if args.shadow:
            shadow = contact_shadow((width, height), args.shadow)
            base.alpha_composite(shadow, (left, top + height - shadow.height * 2 // 3))
        base.alpha_composite(prop, (left, top))
        print(f"  {path.name}  {width}x{height} at {x + dx},{y + dy} ({anchor}{', flipped' if flip else ''})")

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    base.save(out)
    print(out)


if __name__ == "__main__":
    main()
