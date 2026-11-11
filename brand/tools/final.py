"""Final ZIO Logging mark files: round 1's 03 Line Z master with round 2's five-line small cut.

Usage: python3 final.py <out-dir>
"""
import os
import sys

from marks import PALETTE, ROUND2, svg

MARK = ROUND2["lines"]  # master = round 1 "03 Line Z"; small = 2A's five-line 16 px cut
TERMINAL = PALETTE["dark"]["ground"]


def shapes_svg(shapes, colors, scale=1, dx=0, dy=0):
    out = []
    for kind, role, (x, y, w, h) in shapes:
        out.append(f'<rect x="{x * scale + dx:g}" y="{y * scale + dy:g}" width="{w * scale:g}" '
                   f'height="{h * scale:g}" fill="{colors[role]}"/>')
    return "".join(out)


def icon(size, mark_px, radius, colors):
    """Square icon: Terminal ground, mark centred at mark_px (a multiple of 24 keeps edges on whole pixels)."""
    _, shapes = MARK["master"]
    off = (size - mark_px) / 2
    tile = f'<rect width="{size}" height="{size}" rx="{radius}" fill="{TERMINAL}"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" width="{size}" height="{size}" '
            f'role="img" aria-label="ZIO Logging" shape-rendering="crispEdges">{tile}'
            f'{shapes_svg(shapes, colors, mark_px / 24, off, off)}</svg>')


def favicon_adaptive():
    """16-unit small cut; ink follows the browser's colour scheme so it shows on light and dark tab bars."""
    _, shapes = MARK["small"]
    light, dark = PALETTE["light"], PALETTE["dark"]
    body = "".join(
        f'<rect class="{role}" x="{x}" y="{y}" width="{w}" height="{h}"/>'
        for _, role, (x, y, w, h) in shapes)
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" role="img" aria-label="ZIO Logging" '
            'shape-rendering="crispEdges"><style>'
            f'.ink{{fill:{light["ink"]}}}.accent{{fill:{light["accent"]}}}'
            f'@media (prefers-color-scheme: dark){{.ink{{fill:{dark["ink"]}}}.accent{{fill:{dark["accent"]}}}}}'
            f'</style>{body}</svg>')


def favicon_mono():
    _, shapes = MARK["small"]
    body = "".join(f'<rect x="{x}" y="{y}" width="{w}" height="{h}"/>' for _, _, (x, y, w, h) in shapes)
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" role="img" aria-label="ZIO Logging" '
            f'shape-rendering="crispEdges" fill="currentColor">{body}</svg>')


TOKENS = """/* ZIO Logging colour tokens. Ratios: brand/final/README.md. */
:root {
  --zl-bg: #FFFFFF;        /* White: page */
  --zl-surface: #F5F4F0;   /* Ledger: panel */
  --zl-text: #15171C;      /* Console: text, mark lines (brand colour) */
  --zl-text-2: #5B606B;    /* Slate: secondary text */
  --zl-accent: #985A00;    /* Deep amber: mark accent line, links, keywords */
}
@media (prefers-color-scheme: dark) {
  :root {
    --zl-bg: #111318;      /* Terminal: page */
    --zl-surface: #1A1D24; /* Shell: panel */
    --zl-text: #ECEDEF;    /* Phosphor: text, mark lines (brand colour) */
    --zl-text-2: #9AA0AB;  /* Fog: secondary text */
    --zl-accent: #F5A524;  /* Amber: mark accent line, links, keywords */
  }
}
"""


def main(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    files = {}
    for mode, colors in PALETTE.items():
        size, shapes = MARK["master"]
        files[f"zio-logging-mark-{mode}.svg"] = svg(size, shapes, colors, "ZIO Logging")
        size, shapes = MARK["small"]
        files[f"zio-logging-mark-small-{mode}.svg"] = svg(size, shapes, colors, "ZIO Logging")
    files["favicon.svg"] = favicon_adaptive()
    files["favicon-mono.svg"] = favicon_mono()
    # App icon / avatar: rounded tile (family rule Q4: rounded tiles for app icons and avatars only).
    files["icon-tile-512.svg"] = icon(512, 288, 112, PALETTE["dark"])
    # Maskable: square, no rounding. The mark's corners are filled (full-width top and bottom lines), so a
    # square mark fits Android's safe circle (diameter 80% of 512) only up to 289 px: use 288 (56%), not 60-62%.
    files["icon-maskable-512.svg"] = icon(512, 288, 0, PALETTE["dark"])
    files["tokens.css"] = TOKENS
    for name, text in files.items():
        with open(os.path.join(out_dir, name), "w") as f:
            f.write(text if text.endswith("\n") else text + "\n")


if __name__ == "__main__":
    main(sys.argv[1])
