#!/usr/bin/env python3
"""Builds the mod icons for tudursvehiclemod and the motorcycle addon.

Both are drawn from one script so they share a frame, palette, and light
direction - the addon should read as "the same family, but a bike" at thumbnail
size, which is the only size a mod list ever shows.

Palette comes from the mod's own existing item textures (see
assets/tudursvehiclemod/textures/item/*.png): slate-blue bodywork with a gold
accent, the same gold the tier-5 border already uses.

Output is 128x128 PNG, the size Fabric's own mod menu renders at.
"""
import math
import pathlib

from PIL import Image, ImageDraw

SIZE = 128

# --- palette (sampled from the mod's own existing textures) -----------------
BG_TOP = (58, 68, 88)
BG_BOTTOM = (26, 31, 42)
BODY = (120, 120, 135)
BODY_LIGHT = (176, 180, 196)
BODY_DARK = (74, 76, 88)
GOLD = (255, 190, 40)
GOLD_DARK = (190, 132, 20)
GLASS = (128, 196, 230)
TIRE = (24, 24, 28)
OUTLINE = (16, 18, 24)


def base_canvas():
    """Rounded-square background with a vertical gradient and a gold rim."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    grad = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 255))
    gd = ImageDraw.Draw(grad)
    for y in range(SIZE):
        t = y / (SIZE - 1)
        gd.line(
            [(0, y), (SIZE, y)],
            fill=tuple(round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM)) + (255,),
        )

    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=22, fill=255)
    img.paste(grad, (0, 0), mask)

    d = ImageDraw.Draw(img)
    d.rounded_rectangle([2, 2, SIZE - 3, SIZE - 3], radius=20, outline=GOLD_DARK, width=3)
    d.rounded_rectangle([4, 4, SIZE - 5, SIZE - 5], radius=18, outline=GOLD, width=2)
    return img


def wheel(d, cx, cy, r, rim=True):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=TIRE, outline=OUTLINE, width=2)
    if rim:
        d.ellipse([cx - r * 0.45, cy - r * 0.45, cx + r * 0.45, cy + r * 0.45],
                  fill=BODY_LIGHT, outline=OUTLINE, width=1)


def build_base_icon(out_path):
    """A generic vehicle silhouette in three-quarter view - deliberately not any
    one real vehicle, since this mod is a framework rather than a vehicle pack."""
    img = base_canvas()
    d = ImageDraw.Draw(img)

    # Ground shadow.
    d.ellipse([20, 94, 108, 108], fill=(0, 0, 0, 70))

    # Lower hull.
    d.polygon([(22, 88), (106, 88), (100, 66), (28, 66)], fill=BODY_DARK, outline=OUTLINE)
    # Main body.
    d.polygon([(26, 70), (102, 70), (96, 50), (32, 50)], fill=BODY, outline=OUTLINE)
    # Upper deck / cabin, offset to read as three-quarter view.
    d.polygon([(44, 52), (88, 52), (80, 32), (52, 32)], fill=BODY_LIGHT, outline=OUTLINE)
    # Windows.
    d.polygon([(50, 50), (63, 50), (63, 36), (55, 36)], fill=GLASS, outline=OUTLINE)
    d.polygon([(67, 50), (81, 50), (77, 36), (67, 36)], fill=GLASS, outline=OUTLINE)

    # Gold accent stripe - ties the icon to the tier borders.
    d.polygon([(28, 64), (100, 64), (100, 60), (28, 60)], fill=GOLD)

    wheel(d, 40, 88, 13)
    wheel(d, 88, 88, 13)

    img.save(out_path)
    print(f"wrote {out_path}")


def build_addon_icon(out_path):
    """The same frame and palette, but a two-wheeler leaning into a turn - the
    lean being exactly what the addon adds on top of the base mod."""
    img = base_canvas()
    d = ImageDraw.Draw(img)

    # Ground shadow first, so the bike sits on it.
    d.ellipse([22, 96, 106, 108], fill=(0, 0, 0, 70))

    front_x, rear_x, axle_y, r = 90, 42, 86, 15

    # The lean is drawn directly rather than by rotating the finished bike:
    # both wheels stay planted on the ground line while everything above them
    # shifts sideways, which is what a bike leaning into a corner actually does.
    # (Rotating the whole layer instead moves the bike's own position too, and
    # no single pivot keeps both wheels down.)
    LEAN = 0.34  # horizontal shift per unit of height above the axle line

    def lean_pt(x, y):
        return (x + (axle_y - y) * LEAN, y)

    wheel(d, front_x, axle_y, r)
    wheel(d, rear_x, axle_y, r)

    # Frame: rear axle -> seat -> tank -> steering head, all leaned.
    d.polygon([lean_pt(rear_x, axle_y), lean_pt(50, 62), lean_pt(76, 58),
               lean_pt(front_x - 4, 70)], fill=BODY, outline=OUTLINE)
    # Tank / seat hump.
    d.polygon([lean_pt(54, 62), lean_pt(78, 57), lean_pt(76, 48), lean_pt(58, 50)],
              fill=BODY_LIGHT, outline=OUTLINE)
    # Fork down to the front axle.
    d.line([lean_pt(84, 54), lean_pt(front_x, axle_y - 4)], fill=BODY_DARK, width=6)
    d.line([lean_pt(84, 54), lean_pt(front_x, axle_y - 4)], fill=OUTLINE, width=2)
    # Handlebar.
    d.line([lean_pt(78, 50), lean_pt(94, 46)], fill=OUTLINE, width=5)
    d.line([lean_pt(79, 50), lean_pt(93, 46)], fill=BODY_LIGHT, width=3)
    # Gold accent on the tank.
    d.polygon([lean_pt(58, 59), lean_pt(76, 55), lean_pt(76, 52), lean_pt(59, 55)],
              fill=GOLD)

    # Motion arc, hinting at the cornering the lean comes from.
    for i, alpha in enumerate((150, 110, 70)):
        off = i * 7
        d.arc([14 - off, 46, 58 - off, 106], start=120, end=205,
              fill=GOLD[:3] + (alpha,), width=3)

    img.save(out_path)
    print(f"wrote {out_path}")


if __name__ == "__main__":
    base = pathlib.Path("/home/claude/vehiclemod_work/vehiclemod/src/main/resources/assets/tudursvehiclemod")
    addon = pathlib.Path("/home/claude/addon/motorcycleaddon/src/main/resources/assets/motorcycleaddon")
    base.mkdir(parents=True, exist_ok=True)
    addon.mkdir(parents=True, exist_ok=True)
    build_base_icon(base / "icon.png")
    build_addon_icon(addon / "icon.png")
