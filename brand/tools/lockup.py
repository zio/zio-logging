"""Outlined ZIO Logging lockups: the mark as the Z of "ZIO", text converted to paths.

Usage (from brand/):
  uv run --with fonttools --with uharfbuzz python tools/lockup.py <Geist-SemiBold.ttf> .

The font is Geist SemiBold from the `geist` npm package (OFL); it is not committed.
Geometry matches the approved brand sheet:
  - mark height = cap height of Geist SemiBold (710/1000 em, read from the font);
  - space from the mark to the pen position of "I" = 1/12 of the mark height;
  - tracking -2% (added after every glyph, as CSS letter-spacing does), kerning on;
  - stacked: "Logging" on a second line, flush left with the mark, baselines 1.125 em apart.
Coordinates: the mark is 24 units tall (16 for the small cut), so the files stay crisp at 1x, 2x, 4x.
"""
import os
import sys

import uharfbuzz as hb
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

from marks import PALETTE, ROUND2

MARK = ROUND2["lines"]
TRACKING = -0.02      # em
LINE_GAP = 1.125      # baseline to baseline, em (sheet: 48 px type, line-height 1, 6 px gap)


class Font:
    def __init__(self, path):
        self.tt = TTFont(path)
        self.upm = self.tt["head"].unitsPerEm
        self.cap = self.tt["OS/2"].sCapHeight / self.upm
        self.glyphs = self.tt.getGlyphSet()
        self.order = self.tt.getGlyphOrder()
        blob = hb.Blob.from_file_path(path)
        self.hb = hb.Font(hb.Face(blob))

    def run(self, text, size, x, baseline, pen):
        """Draw shaped text into pen (y down); returns the pen x after the run."""
        buf = hb.Buffer()
        buf.add_str(text)
        buf.guess_segment_properties()
        hb.shape(self.hb, buf, {"kern": True, "liga": True})
        s = size / self.upm
        for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
            gx = x + pos.x_offset * s
            gy = baseline - pos.y_offset * s
            self.glyphs[self.order[info.codepoint]].draw(TransformPen(pen, (s, 0, 0, -s, gx, gy)))
            x += pos.x_advance * s + TRACKING * size
        return x


def fmt(v):
    return f"{round(v, 2):g}"


def mark_rects(cut, colors):
    _, shapes = MARK[cut]
    return "".join(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{colors[role]}"/>'
                   for _, role, (x, y, w, h) in shapes)


def lockup(font, layout, cut, colors):
    size = MARK[cut][0]                 # mark height in units
    type_size = size / font.cap          # font size whose cap height equals the mark
    svg_pen = SVGPathPen(font.glyphs, ntos=fmt)
    bounds = BoundsPen(font.glyphs)

    class Tee:                           # draw into both pens
        def __getattr__(self, name):
            def call(*a):
                getattr(svg_pen, name)(*a)
                getattr(bounds, name)(*a)
            return call

    tee = Tee()
    pen_x = size + size / 12             # mark, then 1/12 of its height, then the pen origin of "I"
    if layout == "stacked":
        font.run("IO", type_size, pen_x, size, tee)
        font.run("Logging", type_size, 0, size + LINE_GAP * type_size, tee)
    else:
        font.run("IO Logging", type_size, pen_x, size, tee)
    xmin, ymin, xmax, ymax = bounds.bounds
    xmin, ymin = min(0, xmin), min(0, ymin)
    xmax, ymax = max(size, xmax), max(size, ymax)
    rects = mark_rects(cut, colors)
    w, h = xmax - xmin, ymax - ymin
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{fmt(xmin)} {fmt(ymin)} {fmt(w)} {fmt(h)}" '
            f'width="{fmt(w)}" height="{fmt(h)}" role="img" aria-label="ZIO Logging">'
            f'<g shape-rendering="crispEdges">{rects}</g>'
            f'<path fill="{colors["ink"]}" d="{svg_pen.getCommands()}"/></svg>\n')


def main(font_path, out_dir):
    font = Font(font_path)
    print(f"cap height {font.cap:.3f} em ({font.tt['name'].getDebugName(5)})")
    for mode, colors in PALETTE.items():
        for layout, cut, name in (("horizontal", "master", "lockup"),
                                  ("horizontal", "small", "lockup-small"),
                                  ("stacked", "master", "lockup-stacked")):
            path = os.path.join(out_dir, f"zio-logging-{name}-{mode}.svg")
            with open(path, "w") as f:
                f.write(lockup(font, layout, cut, colors))
            print("wrote", path)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
