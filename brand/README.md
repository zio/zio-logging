# ZIO Logging brand

The ZIO Logging logo, icons, social cards and colour tokens. Nothing here is served by the website yet.

- `final/`: the brand files and the full brand rules (construction, the logo with the name, clear space, colours with every contrast ratio, type, don'ts). Start with `final/README.md`.
- `final/canvas/`: sources of the brand sheet and social-card boards.
- `tools/`: the scripts that generate everything in `final/`. The commands are in `final/README.md`.
  - `marks.py`: the mark's geometry and the colour palette (it also keeps the shapes of the concepts explored before this mark was chosen).
  - `final.py`: mark SVGs, `favicon.svg`, icon SVGs, `tokens.css`.
  - `lockup.py`: the logo with the name, text converted to outlines.
  - `cards.py`: social-card PNGs from the canvas boards.
  - `raster.py`: PNG and ICO favicons and app icons.
  - `wcag.py`: the WCAG 2.x contrast calculator used for every ratio.

## Family rules this brand follows

From the ZIO family brand rules:

- The mark is one simple solid glyph that works at 16 px. A Z mark sits inline as the first letter of "ZIO" in the lockup.
- The mark has one accent element or none. Here it is the amber middle line.
- Text pairs meet WCAG AA 4.5:1, light and dark.
- The mark is never below 16 px; below 24 px a small-size version is used.
- Clear space is one defined unit of the mark on every side. Here it is one line of the mark.
- Full light and dark palettes. One sans plus one mono: Geist and Geist Mono.
- No glow, shadow, gradient or 3D on the mark or in imagery. Rounded tiles for app icons and avatars only.
- Social cards: 1200 x 630 plus 1280 x 640 for GitHub, safe zone 7.5% of width, dark and light.
