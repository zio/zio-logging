"""Mark geometry for ZIO Logging. Writes SVGs and a size-preview page.

Master marks sit on a 24-unit grid (crisp at 24, 48, 96 px).
Small-size cuts sit on a 16-unit grid (crisp at 16 and 32 px), per family rule Q12.
Usage: python3 marks.py <round> <out-dir>
"""
import os
import sys

PALETTE = {
    "light": {"ink": "#15171C", "accent": "#985A00", "ground": "#FFFFFF"},
    "dark": {"ink": "#ECEDEF", "accent": "#F5A524", "ground": "#111318"},
}


def z(a, b, h, t, d):
    """Solid Z in box x a..b, y 0..h, bar thickness t, diagonal width d at the bars."""
    pts = [(a, 0), (b, 0), (b, t), (a + d, h - t), (b, h - t), (b, h),
           (a, h), (a, h - t), (b - d, t), (a, t)]
    return " ".join(f"{x},{y}" for x, y in pts)


def rect(x0, y0, x1, y1, role):
    return ("rect", role, (x0, y0, x1 - x0, y1 - y0))


def poly(points, role):
    return ("poly", role, points)


def pts(*xy):
    """Polygon points from (x, y) pairs, rounded to 2 decimals."""
    return " ".join(f"{round(x, 2):g},{round(y, 2):g}" for x, y in xy)


def band_slice(y0, y1, x_left, d, role):
    """One horizontal slice (y0..y1) of a diagonal band whose left edge is x_left(y), width d."""
    return poly(pts((x_left(y0), y0), (x_left(y0) + d, y0),
                    (x_left(y1) + d, y1), (x_left(y1), y1)), role)


# Each concept: name -> {"master": (size, shapes), "small": (size, shapes)}
ROUND1 = {
    # 1. Record: a level stripe plus three message lines. Not a Z; sits beside the name.
    "record": {
        "master": (24, [
            rect(0, 0, 4, 24, "accent"),
            rect(7, 0, 24, 6, "ink"),
            rect(7, 9, 19, 15, "ink"),
            rect(7, 18, 22, 24, "ink"),
        ]),
        "small": (16, [
            rect(0, 0, 3, 16, "accent"),
            rect(5, 0, 16, 4, "ink"),
            rect(5, 6, 12, 10, "ink"),
            rect(5, 12, 14, 16, "ink"),
        ]),
    },
    # 2. Tagged Z: the level stripe in front of a solid Z. The name becomes a log record.
    "tagged-z": {
        "master": (24, [
            rect(0, 0, 4, 24, "accent"),
            poly(z(7, 24, 24, 5, 7), "ink"),
        ]),
        "small": (16, [
            rect(0, 0, 3, 16, "accent"),
            poly(z(5, 16, 16, 3, 4), "ink"),
        ]),
    },
    # 3. Line Z: a Z set as log lines; the accent line is the one you grep for.
    "line-z": {
        "master": (24, [
            rect(0, 0, 24, 4, "ink"),
            rect(14, 5, 24, 9, "ink"),
            rect(7, 10, 17, 14, "accent"),
            rect(0, 15, 10, 19, "ink"),
            rect(0, 20, 24, 24, "ink"),
        ]),
        "small": (16, [
            rect(0, 0, 16, 3, "ink"),
            rect(8, 4, 16, 7, "accent"),
            rect(0, 8, 8, 11, "ink"),
            rect(0, 12, 16, 15, "ink"),
        ]),
    },
    # 4. Tail: a solid Z followed by a block cursor, the log still being written (tail -f).
    "tail": {
        "master": (24, [
            poly(z(0, 16, 24, 5, 7), "ink"),
            rect(19, 10, 24, 24, "accent"),
        ]),
        "small": (16, [
            poly(z(0, 11, 16, 3, 4), "ink"),
            rect(13, 6, 16, 16, "accent"),
        ]),
    },
}


# Round 2: variants of Line Z (Milad: "the 3rd one was not bad").
# Small cuts use rows 3,2,2,2,3 px with 1 px gaps, so all five lines survive at 16 px.
ROUND2 = {
    # 2A. Lines: round-1 Line Z with a five-line small cut (round 1's four-line cut lost the Z).
    "lines": {
        "master": (24, [
            rect(0, 0, 24, 4, "ink"),
            rect(14, 5, 24, 9, "ink"),
            rect(7, 10, 17, 14, "accent"),
            rect(0, 15, 10, 19, "ink"),
            rect(0, 20, 24, 24, "ink"),
        ]),
        "small": (16, [
            rect(0, 0, 16, 3, "ink"),
            rect(9, 4, 16, 6, "ink"),
            rect(5, 7, 11, 9, "accent"),
            rect(0, 10, 7, 12, "ink"),
            rect(0, 13, 16, 16, "ink"),
        ]),
    },
    # 2B. Sliced: a true Z outline cut into five log lines; diagonal slices keep the slant.
    "sliced": {
        "master": (24, [
            rect(0, 0, 24, 4, "ink"),
            band_slice(5, 9, lambda y: 15 * (20 - y) / 16, 9, "ink"),
            band_slice(10, 14, lambda y: 15 * (20 - y) / 16, 9, "accent"),
            band_slice(15, 19, lambda y: 15 * (20 - y) / 16, 9, "ink"),
            rect(0, 20, 24, 24, "ink"),
        ]),
        "small": (16, [
            rect(0, 0, 16, 3, "ink"),
            band_slice(4, 6, lambda y: 13 - y, 6, "ink"),
            band_slice(7, 9, lambda y: 13 - y, 6, "accent"),
            band_slice(10, 12, lambda y: 13 - y, 6, "ink"),
            rect(0, 13, 16, 16, "ink"),
        ]),
    },
    # 2C. Return: two log lines and the carriage return between them; the return is amber.
    "return": {
        "master": (24, [
            rect(0, 0, 24, 5, "ink"),
            poly(pts((15, 7), (24, 7), (9, 17), (0, 17)), "accent"),
            rect(0, 19, 24, 24, "ink"),
        ]),
        "small": (16, [
            rect(0, 0, 16, 3, "ink"),
            poly(pts((10, 5), (16, 5), (6, 11), (0, 11)), "accent"),
            rect(0, 13, 16, 16, "ink"),
        ]),
    },
    # 2D. Tagged lines: round-2's level stripe in front of an all-ink Line Z.
    "tagged-lines": {
        "master": (24, [
            rect(0, 0, 4, 24, "accent"),
            rect(7, 0, 24, 4, "ink"),
            rect(17, 5, 24, 9, "ink"),
            rect(12, 10, 19, 14, "ink"),
            rect(7, 15, 14, 19, "ink"),
            rect(7, 20, 24, 24, "ink"),
        ]),
        "small": (16, [
            rect(0, 0, 3, 16, "accent"),
            rect(5, 0, 16, 3, "ink"),
            rect(11, 4, 16, 6, "ink"),
            rect(8, 7, 13, 9, "ink"),
            rect(5, 10, 10, 12, "ink"),
            rect(5, 13, 16, 16, "ink"),
        ]),
    },
}

ROUNDS = {"1": ROUND1, "2": ROUND2}


def svg(size, shapes, colors, label, px=None, ground=False):
    out = []
    px = px or size
    out.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" '
               f'width="{px}" height="{px}" role="img" aria-label="{label}" shape-rendering="crispEdges">')
    if ground:
        out.append(f'<rect width="{size}" height="{size}" fill="{colors["ground"]}"/>')
    for kind, role, g in shapes:
        fill = colors[role]
        if kind == "rect":
            x, y, w, h = g
            out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill}"/>')
        else:
            out.append(f'<polygon points="{g}" fill="{fill}" shape-rendering="geometricPrecision"/>')
    out.append("</svg>")
    return "".join(out)


def main(rnd, out_dir):
    concepts = ROUNDS[rnd]
    os.makedirs(out_dir, exist_ok=True)
    for name, cuts in concepts.items():
        for cut, (size, shapes) in cuts.items():
            for mode, colors in PALETTE.items():
                path = os.path.join(out_dir, f"{name}-{cut}-{mode}.svg")
                with open(path, "w") as f:
                    f.write(svg(size, shapes, colors, f"ZIO Logging mark, {name}") + "\n")

    # Preview page: true-size marks, plus an 8x pixel zoom of what the browser actually drew.
    rows = []
    for name, cuts in concepts.items():
        for mode, colors in PALETTE.items():
            cells = []
            for px in (16, 24, 32, 96):
                cut = "small" if px < 24 else "master"
                size, shapes = cuts[cut]
                cells.append(f'<div class="cell"><img data-zoom="{px}" src="{name}-{cut}-{mode}.svg" '
                             f'width="{px}" height="{px}"><span>{px}</span></div>')
            rows.append(f'<div class="row" style="background:{colors["ground"]};color:{colors["ink"]}">'
                        f'<b>{name} / {mode}</b>{"".join(cells)}<canvas data-for="{name}-{mode}"></canvas></div>')
    html = """<!doctype html><meta charset=utf-8><style>
body{margin:0;font:12px monospace}.row{display:flex;align-items:center;gap:24px;padding:12px 16px}
.row b{width:140px}.cell{display:flex;flex-direction:column;align-items:center;gap:4px}
canvas{image-rendering:pixelated}</style>""" + "".join(rows) + """
<script>
// Zoom: draw each 16 and 24 px raster 8x with no smoothing, to judge pixels.
window.onload = () => document.querySelectorAll('.row').forEach(row => {
  const imgs = [...row.querySelectorAll('img')].filter(i => +i.dataset.zoom <= 24);
  const c = row.querySelector('canvas'); const z = 8;
  c.width = (16 + 24 + 8) * z; c.height = 24 * z;
  const g = c.getContext('2d'); g.imageSmoothingEnabled = false;
  let x = 0;
  imgs.forEach(img => {
    const px = +img.dataset.zoom; const t = document.createElement('canvas');
    t.width = px; t.height = px; const tg = t.getContext('2d');
    tg.fillStyle = getComputedStyle(row).backgroundColor; tg.fillRect(0, 0, px, px);
    tg.drawImage(img, 0, 0, px, px);
    g.drawImage(t, x, 0, px * z, px * z); x += (px + 8) * z;
  });
});
</script>"""
    with open(os.path.join(out_dir, "preview.html"), "w") as f:
        f.write(html)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
