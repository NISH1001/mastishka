"""Render the macOS app icon: the ◎ concentric-circles motif (same as the menu bar
symbol) in mastishka mint over the night gradient, on a Big-Sur-style rounded square.

Writes macos/assets/AppIcon.icns (via an intermediate .iconset + iconutil).
Requires Pillow and macOS iconutil/sips. Run: python3 scripts/generate_mac_icon.py
"""

import os
import shutil
import subprocess

from PIL import Image, ImageDraw

SIZE = 1024
# Big Sur icon grid: the squircle is inset ~10% per side with ~22% corner radius.
INSET = 100
RADIUS = 185

BG_TOP = (0x17, 0x1C, 0x29)
BG_BOTTOM = (0x0A, 0x0D, 0x14)
MINT = (143, 211, 194)
GLOW = (94, 140, 127)


def main() -> None:
    here = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.normpath(os.path.join(here, "..", "macos", "assets"))
    os.makedirs(out_dir, exist_ok=True)

    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    # Night gradient, masked to the rounded square.
    gradient = Image.new("RGB", (1, SIZE))
    for y in range(SIZE):
        t = y / (SIZE - 1)
        gradient.putpixel((0, y), tuple(
            round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM)
        ))
    gradient = gradient.resize((SIZE, SIZE))
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (INSET, INSET, SIZE - INSET, SIZE - INSET), radius=RADIUS, fill=255
    )
    img.paste(gradient, (0, 0), mask)

    # Soft aura behind the circles, painted as concentric translucent rings.
    overlay = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    cx = cy = SIZE / 2

    def box(r: float) -> tuple[float, float, float, float]:
        return (cx - r, cy - r, cx + r, cy + r)

    for r in range(330, 150, -6):
        alpha = round(26 * (1 - (r - 150) / 180))
        draw.ellipse(box(r), fill=(*GLOW, alpha))

    # The ◎: outer ring + inner ring, mint.
    draw.ellipse(box(252), outline=(*MINT, 235), width=30)
    draw.ellipse(box(128), outline=(*MINT, 235), width=58)
    img = Image.alpha_composite(img, Image.composite(
        overlay, Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0)), mask
    ))

    # .iconset → .icns
    iconset = os.path.join(out_dir, "AppIcon.iconset")
    shutil.rmtree(iconset, ignore_errors=True)
    os.makedirs(iconset)
    master = os.path.join(iconset, "master.png")
    img.save(master)
    for size in (16, 32, 128, 256, 512):
        for scale, suffix in ((1, ""), (2, "@2x")):
            px = size * scale
            subprocess.run(
                ["sips", "-z", str(px), str(px), master, "--out",
                 os.path.join(iconset, f"icon_{size}x{size}{suffix}.png")],
                check=True, capture_output=True,
            )
    os.remove(master)
    icns = os.path.join(out_dir, "AppIcon.icns")
    subprocess.run(["iconutil", "-c", "icns", iconset, "-o", icns], check=True)
    shutil.rmtree(iconset)
    print(f"Wrote {icns} ({os.path.getsize(icns) / 1024:.0f} KB)")


if __name__ == "__main__":
    main()
