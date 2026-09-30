#!/usr/bin/env python3
"""Renders Google Play store graphics from the launcher icon's geometry (res/drawable/ic_launcher_foreground.xml).

Writes docs/play-store/icon-512.png (app icon) and docs/play-store/feature-1024x500.png (feature graphic).
Needs Pillow (pip install pillow). Run from the repo root: python3 scripts/store-assets.py
"""
from PIL import Image, ImageDraw, ImageFont

RUST, CREAM, GOLD = "#8C3B2F", "#FBEFE2", "#F2B544"
SS = 4  # supersampling for smooth edges
FONT = "/System/Library/Fonts/Supplemental/Arial Rounded Bold.ttf"
FONT_TEXT = "/System/Library/Fonts/Supplemental/Arial.ttf"


def wallet(draw, x0, y0, scale):
    """Draws the wallet art; coordinates are the 108-unit adaptive-icon canvas."""
    def p(x, y):
        return x0 + x * scale, y0 + y * scale

    def circle(cx, cy, r, fill):
        draw.ellipse([*p(cx - r, cy - r), *p(cx + r, cy + r)], fill=fill)

    circle(58, 38, 9, GOLD)                                               # coin
    draw.rounded_rectangle([*p(30, 40), *p(78, 76)], radius=7 * scale, fill=CREAM)  # wallet
    draw.rectangle([*p(58, 50), *p(78, 66)], fill=RUST)                   # clasp flap
    circle(58, 58, 8, RUST)
    circle(61, 58, 3, CREAM)                                              # clasp button


def icon(path, size=512):
    img = Image.new("RGB", (size * SS, size * SS), RUST)
    # Show the adaptive icon's visible 72 units (18..90), like a launcher does; Play rounds the corners itself.
    scale = size * SS / 72
    wallet(ImageDraw.Draw(img), -18 * scale, -18 * scale, scale)
    img.resize((size, size), Image.LANCZOS).save(path)


def feature(path, w=1024, h=500):
    img = Image.new("RGB", (w * SS, h * SS), RUST)
    d = ImageDraw.Draw(img)
    scale = 330 * SS / 72
    wallet(d, 80 * SS - 18 * scale, (h * SS - 72 * scale) / 2 - 18 * scale, scale)
    title = ImageFont.truetype(FONT, 108 * SS)
    tagline = ImageFont.truetype(FONT_TEXT, 36 * SS)
    x = 470 * SS
    d.text((x, 150 * SS), "Bucklog", font=title, fill=CREAM)
    d.text((x, 290 * SS), "Shared expenses in seconds,", font=tagline, fill=CREAM)
    d.text((x, 340 * SS), "kept in your Google Sheet.", font=tagline, fill=GOLD)
    img.resize((w, h), Image.LANCZOS).save(path)


if __name__ == "__main__":
    icon("docs/play-store/icon-512.png")
    feature("docs/play-store/feature-1024x500.png")
    print("wrote docs/play-store/icon-512.png and docs/play-store/feature-1024x500.png")
