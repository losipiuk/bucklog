#!/usr/bin/env python3
"""Renders Google Play store graphics from the launcher icon's geometry (res/drawable/ic_launcher_foreground.xml).

Writes docs/play-store/icon-512.png (app icon) and docs/play-store/feature-1024x500.png (feature graphic).
Needs Pillow (pip install pillow). Run from the repo root: python3 scripts/store-assets.py
"""
from PIL import Image, ImageDraw, ImageFont

CORAL, CREAM, INK = "#E99586", "#FFF8E8", "#4B2545"
SS = 4  # supersampling for smooth edges
FONT = "/System/Library/Fonts/Supplemental/Arial Rounded Bold.ttf"
FONT_TEXT = "/System/Library/Fonts/Supplemental/Arial.ttf"


def piggy(draw, x0, y0, scale):
    """Render the launcher's M/L/C/Z paths directly, avoiding duplicate geometry."""
    import re
    import xml.etree.ElementTree as ET
    from pathlib import Path

    source = Path(__file__).resolve().parents[1] / "androidApp/src/main/res/drawable/ic_launcher_foreground.xml"
    ns = "{http://schemas.android.com/apk/res/android}"
    for element in ET.parse(source).getroot():
        tokens = iter(re.findall(r"[MLCZ]|-?\d+(?:\.\d+)?", element.attrib[ns + "pathData"]))
        points = []
        current = (0, 0)
        for command in tokens:
            if command in ("M", "L"):
                current = (float(next(tokens)), float(next(tokens)))
                points.append(current)
            elif command == "C":
                p0 = current
                p1, p2, p3 = [(float(next(tokens)), float(next(tokens))) for _ in range(3)]
                for step in range(1, 33):
                    t = step / 32
                    points.append(tuple((1-t)**3*p0[i] + 3*(1-t)**2*t*p1[i] + 3*(1-t)*t*t*p2[i] + t**3*p3[i] for i in (0, 1)))
                current = p3
            elif command == "Z":
                draw.polygon([(x0+x*scale, y0+y*scale) for x, y in points], fill=element.attrib[ns + "fillColor"])
                points = []


def icon(path, size=512):
    img = Image.new("RGB", (size * SS, size * SS), CORAL)
    # Show the adaptive icon's visible 72 units (18..90), like a launcher does; Play rounds the corners itself.
    scale = size * SS / 72
    piggy(ImageDraw.Draw(img), -18 * scale, -18 * scale, scale)
    img.resize((size, size), Image.LANCZOS).save(path)


def feature(path, w=1024, h=500):
    img = Image.new("RGB", (w * SS, h * SS), CORAL)
    d = ImageDraw.Draw(img)
    scale = 330 * SS / 72
    piggy(d, 80 * SS - 18 * scale, (h * SS - 72 * scale) / 2 - 18 * scale, scale)
    title = ImageFont.truetype(FONT, 108 * SS)
    tagline = ImageFont.truetype(FONT_TEXT, 36 * SS)
    x = 470 * SS
    d.text((x, 150 * SS), "Bucklog", font=title, fill=INK)
    d.text((x, 290 * SS), "Shared expenses in seconds,", font=tagline, fill=INK)
    d.text((x, 340 * SS), "kept in your Google Sheet.", font=tagline, fill=INK)
    img.resize((w, h), Image.LANCZOS).save(path)


if __name__ == "__main__":
    icon("docs/play-store/icon-512.png")
    feature("docs/play-store/feature-1024x500.png")
    print("wrote docs/play-store/icon-512.png and docs/play-store/feature-1024x500.png")
