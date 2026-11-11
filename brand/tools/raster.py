"""PNG and ICO fallbacks for the favicon and app icons, drawn on whole pixels.

Usage (from brand/):
  uv run --with pillow python tools/raster.py final

Every mark is drawn at a size where its lines fall on whole pixels: the 16-unit small cut at 16 or 32 px,
the 24-unit master at a multiple of 24. Fallback PNGs cannot follow the browser's colour scheme like
favicon.svg. The 16 px favicon is the small cut in light-mode colours on transparency (what favicon.svg
shows in a light browser); a full-bleed tile would lose the full-width top and bottom lines against a
light tab bar. From 32 px the mark sits on the Terminal ground with padding and reads on any bar.
"""
import io
import os
import struct
import sys

from PIL import Image, ImageDraw

from marks import PALETTE, ROUND2

MARK = ROUND2["lines"]
DARK = PALETTE["dark"]
TERMINAL = DARK["ground"]


def draw_mark(img, cut, px, off_x, off_y, colors=DARK):
    """Draw the mark `px` pixels tall at (off_x, off_y); px must be a whole multiple of the cut's grid."""
    size, shapes = MARK[cut]
    k, rem = divmod(px, size)
    assert rem == 0, f"{px} px is not a whole multiple of the {size}-unit {cut} grid"
    d = ImageDraw.Draw(img)
    for _, role, (x, y, w, h) in shapes:
        d.rectangle([off_x + x * k, off_y + y * k, off_x + (x + w) * k - 1, off_y + (y + h) * k - 1],
                    fill=colors[role])


def tile(size, radius):
    """Terminal square, optionally with anti-aliased rounded corners (drawn at 4x, then reduced)."""
    if not radius:
        return Image.new("RGBA", (size, size), TERMINAL)
    big = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(big).rounded_rectangle([0, 0, size * 4 - 1, size * 4 - 1], radius=radius * 4, fill=255)
    img = Image.new("RGBA", (size, size), TERMINAL)
    img.putalpha(big.resize((size, size), Image.LANCZOS))
    return img


def icon(size, mark_px, radius=0, cut="master"):
    img = tile(size, radius)
    off = (size - mark_px) // 2
    draw_mark(img, cut, mark_px, off, off)
    return img


def ico(images):
    """ICO container holding PNG-compressed images (supported by every current browser and Windows)."""
    blobs = []
    for im in images:
        buf = io.BytesIO()
        im.save(buf, "PNG")
        blobs.append((im.size[0], buf.getvalue()))
    out = struct.pack("<HHH", 0, 1, len(blobs))
    offset = 6 + 16 * len(blobs)
    for size, data in blobs:
        out += struct.pack("<BBBBHHII", size % 256, size % 256, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
    return out + b"".join(data for _, data in blobs)


def main(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    fav16 = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw_mark(fav16, "small", 16, 0, 0, PALETTE["light"])   # = favicon.svg in a light browser
    fav32 = icon(32, 24)                  # master at 1 px per unit, 4 px of Terminal on every side
    files = {
        "favicon-16.png": fav16,
        "favicon-32.png": fav32,
        # iOS rounds the corners itself: square ground. 96 px = nearest whole-pixel size to 56% of 180.
        "apple-touch-icon.png": icon(180, 96),
        # Web app manifest icons. Rounded tile, radius 22% (family rule: rounded tiles for app icons only).
        "icon-192.png": icon(192, 96, radius=42),
        "icon-512.png": icon(512, 288, radius=112),
        # Maskable: square, mark inside Android's safe circle (see README).
        "icon-maskable-512.png": icon(512, 288),
    }
    for name, img in files.items():
        img.save(os.path.join(out_dir, name), optimize=True)
        print("wrote", name, img.size)
    with open(os.path.join(out_dir, "favicon.ico"), "wb") as f:
        f.write(ico([fav16, fav32]))
    print("wrote favicon.ico (16, 32)")


if __name__ == "__main__":
    main(sys.argv[1])
