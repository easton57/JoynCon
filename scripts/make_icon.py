#!/usr/bin/env python3
"""Generate adaptive launcher foreground and monochrome PNGs from a source icon.

Usage: python3 scripts/make_icon.py [assets/icon_color.PNG]

The source may be on a black or transparent background; black is keyed out.
"""
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "assets/icon_color.PNG"

# Foreground canvas is 108dp; artwork fits a box this fraction of it (inside the 66dp safe zone).
CONTENT_FRACTION = 0.575
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
EDGE = 2  # ignore stray pixels at the source's border
SOFT = 60  # channel value at which a pixel becomes fully opaque

src = Image.open(SRC).convert("RGBA")
w, h = src.size
px = src.load()
out = Image.new("RGBA", src.size, (0, 0, 0, 0))
opx = out.load()
for y in range(EDGE, h - EDGE):
    for x in range(EDGE, w - EDGE):
        r, g, b, a = px[x, y]
        m = max(r, g, b)
        alpha = min(255, m * 255 // SOFT) * a // 255
        if alpha:
            k = 255 / min(255, m * 255 // SOFT)
            opx[x, y] = (min(255, round(r * k)), min(255, round(g * k)), min(255, round(b * k)), alpha)

art = out.crop(out.getchannel("A").getbbox())
for name, size in DENSITIES.items():
    box = size * CONTENT_FRACTION
    scale = box / max(art.size)
    scaled = art.resize((round(art.width * scale), round(art.height * scale)), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(scaled, ((size - scaled.width) // 2, (size - scaled.height) // 2), scaled)
    dest = RES / f"mipmap-{name}/ic_launcher_foreground.png"
    canvas.save(dest, optimize=True)
    print(dest.relative_to(ROOT), canvas.size)

    # Themed icons (Android 13+) only use alpha; the system supplies the tint.
    mono = Image.new("RGBA", canvas.size, (255, 255, 255, 0))
    mono.putalpha(canvas.getchannel("A"))
    dest = RES / f"mipmap-{name}/ic_launcher_monochrome.png"
    mono.save(dest, optimize=True)
    print(dest.relative_to(ROOT), mono.size)
