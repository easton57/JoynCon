#!/usr/bin/env python3
"""Generate the Play Console graphics: assets/feature_graphic.png (1024x500) and
assets/icon_512.png (512x512 hi-res icon).

Usage: python3 scripts/make_play_graphics.py

Uses the same artwork and keying as scripts/make_icon.py.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "assets/icon_color.PNG"
CONTENT_FRACTION = 0.575  # as in make_icon.py
DEST = ROOT / "assets/feature_graphic.png"
ICON_DEST = ROOT / "assets/icon_512.png"

W, H = 1024, 500
ICON = 512
ICON_VIEWPORT = 72 / 108  # launchers show the middle 72dp of the 108dp adaptive-icon layers
SS = 3  # supersample, then downscale for smooth edges
BG = (0x1C, 0x1C, 0x1C)
BLUE = (0x4D, 0xB5, 0xD9)
RED = (0xFF, 0x6F, 0x69)
GREY = (0xB0, 0xB0, 0xB0)
FONT_BOLD = "/usr/share/fonts/rsms-inter-fonts/InterDisplay-Bold.ttf"
FONT_MEDIUM = "/usr/share/fonts/rsms-inter-fonts/InterDisplay-Medium.ttf"

TITLE = [("Joyn", BLUE), ("Con", RED)]
TAGLINE = "Two controllers. One gamepad."

EDGE, SOFT = 2, 60


def key_out_black(src):
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
    return out.crop(out.getchannel("A").getbbox())




def glow(img, center, radius, color, strength):
    layer = Image.new("RGBA", img.size, color + (0,))
    mask = Image.new("L", img.size, 0)
    cx, cy = center
    ImageDraw.Draw(mask).ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=strength)
    layer.putalpha(mask.filter(ImageFilter.GaussianBlur(radius * 0.6)))
    img.alpha_composite(layer)


w, h = W * SS, H * SS
img = Image.new("RGBA", (w, h), BG + (255,))
glow(img, (int(w * 0.14), int(h * 0.3)), int(h * 0.75), BLUE, 55)
glow(img, (int(w * 0.40), int(h * 0.85)), int(h * 0.7), RED, 50)

# Icon on the left, vertically centred.
source_art = key_out_black(Image.open(SRC).convert("RGBA"))
target_h = int(h * 0.76)
art = source_art.resize((round(source_art.width * target_h / source_art.height), target_h), Image.LANCZOS)
icon_cx = int(w * 0.25)
img.alpha_composite(art, (icon_cx - art.width // 2, (h - art.height) // 2))

# Title and tagline on the right.
draw = ImageDraw.Draw(img)
x0 = int(w * 0.49)
title = "".join(text for text, _ in TITLE)
title_size = 108 * SS
while ImageFont.truetype(FONT_BOLD, title_size).getlength(title) > w * 0.95 - x0:
    title_size -= SS
title_font = ImageFont.truetype(FONT_BOLD, title_size)
tag_font = ImageFont.truetype(FONT_MEDIUM, 32 * SS)
title_h = title_font.getbbox(title)[3]
tag_gap = 22 * SS
block_h = title_h + tag_gap + tag_font.getbbox(TAGLINE)[3]
y = (h - block_h) // 2 - 6 * SS
x = x0
for text, color in TITLE:
    draw.text((x, y), text, font=title_font, fill=color)
    x += draw.textlength(text, font=title_font)
draw.text((x0 + 4 * SS, y + title_h + tag_gap), TAGLINE, font=tag_font, fill=GREY)

img.convert("RGB").resize((W, H), Image.LANCZOS).save(DEST, optimize=True)
print(DEST.relative_to(ROOT), (W, H))

# Hi-res icon: the launcher icon as it appears on a home screen — the adaptive foreground on
# ic_launcher_background, cropped to the visible viewport. Play applies its own corner mask.
size = ICON * SS
img = Image.new("RGBA", (size, size), BG + (255,))
scale = size / ICON_VIEWPORT * CONTENT_FRACTION / max(source_art.size)
art = source_art.resize((round(source_art.width * scale), round(source_art.height * scale)), Image.LANCZOS)
img.alpha_composite(art, ((size - art.width) // 2, (size - art.height) // 2))
img.convert("RGB").resize((ICON, ICON), Image.LANCZOS).save(ICON_DEST, optimize=True)
print(ICON_DEST.relative_to(ROOT), (ICON, ICON))
