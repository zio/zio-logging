# ZIO Logging brand: final draft (v0.1)

Date: 2026-11-10, approved by Milad 2026-11-11. Status: approved; not merged, not served by the website yet.
Canvas (brand sheet and social cards): https://claude.ai/artifact/19oeK6TcKiVqCShfT255wK, boards under "Final".

## Decision

Milad picked round 1's **03 · Line Z** (2026-11-10). The master is unchanged from round 1. The small cut is round 2's five-line cut, because round 1's four-line cut lost the Z at 16 px.

## Mark

- A Z set as five log lines on a 24-unit square: lines 4 units tall, 1 unit apart. Top and bottom lines are full width; the three middle lines are 10 units long and step 7 units from right to left.
- **Accent element: the middle line**, amber in every colour version. All other lines take the ink colour. One-colour versions keep the line, in ink.
- **Small cut** (16 to 23 px): 16-unit grid, lines 3, 2, 2, 2, 3 px with 1 px gaps. The master is used from 24 px. The mark alone is never below 16 px.
- **Clear space: one line height** (4/24 of the mark height) on every side.

## Lockup

- The mark is the Z of "ZIO" (family rule Q1), set inline: mark height = cap height of Geist SemiBold; space to "I" = 1/12 of the mark height; tracking -2%.
- Horizontal is the default; stacked ("ZIO" over "Logging") for square spaces.
- Smallest lockup: mark 16 px tall. Never set the mark beside a full "ZIO Logging".

## Files in this folder

All generated from the geometry in `tools/marks.py`; run from `brand/`. The Geist fonts come from the `geist` npm package (OFL, version 1.9.2 used) and are not committed.

```sh
python3 tools/final.py final                                                   # mark SVGs, favicon.svg, icon SVGs, tokens.css, design-system/styles.css
uv run --with fonttools --with uharfbuzz python tools/lockup.py <geist>/dist/fonts/geist-sans/Geist-SemiBold.ttf final
python3 tools/cards.py <geist>/dist/fonts final/social                        # needs chromium
uv run --with pillow python tools/raster.py final                              # PNG and ICO fallbacks
```

| File | Use |
| --- | --- |
| `zio-logging-mark-light.svg`, `-dark.svg` | Master mark, 24 px and up |
| `zio-logging-mark-small-light.svg`, `-dark.svg` | Small cut, 16 to 23 px |
| `zio-logging-lockup-light.svg`, `-dark.svg` | Horizontal lockup, text outlined; mark 24 px and up |
| `zio-logging-lockup-small-light.svg`, `-dark.svg` | Horizontal lockup with the small cut; mark 16 to 23 px |
| `zio-logging-lockup-stacked-light.svg`, `-dark.svg` | Stacked lockup ("ZIO" over "Logging") for square spaces |
| `favicon.svg` | Small cut; colours follow the browser's colour scheme. The main favicon |
| `favicon-mono.svg` | Small cut in `currentColor` |
| `favicon.ico` | 16 and 32 px, for browsers without SVG favicons |
| `favicon-16.png` | Small cut, light-mode colours, transparent. Reads on light tab bars, weakly on dark ones: a tile would lose the full-width lines on light bars, and the mark is never below 16 px |
| `favicon-32.png` | Master at 24 px on a 32 px Terminal square; reads on any tab bar |
| `apple-touch-icon.png` | 180 px square Terminal (iOS rounds it), mark 96 px |
| `icon-192.png`, `icon-512.png` | Web app manifest icons: rounded tile (radius 22%), mark 96 px and 288 px |
| `icon-tile-512.svg` | App icon and avatar: rounded Terminal tile (radius 22%), mark at 56% |
| `icon-maskable-512.svg`, `icon-maskable-512.png` | Maskable icon: square, mark at 56% |
| `social/zio-logging-og-{light,dark}.png` | 1200 x 630 social cards (light is the fallback) |
| `social/zio-logging-github-{light,dark}.png` | 1280 x 640 GitHub social preview |
| `tokens.css` | Colour tokens, light and dark |

Marks in raster files are drawn at whole-pixel sizes: the small cut at 16 or 32 px, the master at a multiple of 24. That is why the 180 and 192 px icons use a 96 px mark (53% and 50%), the whole-pixel size nearest the 56% of the 512 px icons.

The maskable mark is 56%, below the family's 60 to 62%: the full-width top and bottom lines put ink in the corners, and a square mark only fits Android's safe circle (80% of 512) up to 289 px.

## Colour

| Role | Light | Dark |
| --- | --- | --- |
| Page | White `#FFFFFF` | Terminal `#111318` |
| Panel | Ledger `#F5F4F0` | Shell `#1A1D24` |
| Text, mark lines (brand colour) | Console `#15171C` | Phosphor `#ECEDEF` |
| Secondary text | Slate `#5B606B` | Fog `#9AA0AB` |
| Accent | Deep amber `#985A00` | Amber `#F5A524` |
| Social-card ghost mark | `#EEEEEA` (1.16:1 on White) | `#20232B` (1.18:1 on Terminal) |

Contrast, computed with `tools/wcag.py` (WCAG 2.x relative luminance):

| Pair | Ratio | 4.5:1 text |
| --- | --- | --- |
| Console on White | 17.93 | Pass |
| Console on Ledger | 16.29 | Pass |
| Console on ghost `#EEEEEA` | 15.41 | Pass |
| Slate on White | 6.31 | Pass |
| Slate on Ledger | 5.73 | Pass |
| Slate on ghost `#EEEEEA` | 5.42 | Pass |
| Deep amber on White | 5.53 | Pass |
| Deep amber on Ledger | 5.02 | Pass |
| Deep amber on ghost `#EEEEEA` | 4.75 | Pass |
| White on Deep amber (button) | 5.53 | Pass |
| Phosphor on Terminal | 15.86 | Pass |
| Phosphor on Shell | 14.40 | Pass |
| Phosphor on ghost `#20232B` | 13.41 | Pass |
| Fog on Terminal | 7.07 | Pass |
| Fog on Shell | 6.42 | Pass |
| Fog on ghost `#20232B` | 5.98 | Pass |
| Amber on Terminal | 9.10 | Pass |
| Amber on Shell | 8.26 | Pass |
| Amber on ghost `#20232B` | 7.70 | Pass |
| Terminal on Amber (button) | 9.10 | Pass |
| Amber on White (misuse) | 2.04 | Fail: never bright amber on a light ground |

## Type

Geist (400, 600) for headings, body and the wordmark; Geist Mono (400, 500) for code, log output and labels. No third family. Sentence case; caps only for mono labels (12 to 13 px, 500, +8%). Headings -2% at 48 px and up, -1% at 32 px, 0 at 24 px and below. Body 16 px / 1.55, at most 72 characters a line.

## Social cards

1200 x 630 (X, Slack, Discord, Facebook, LinkedIn) and 1280 x 640 (GitHub), each light and dark; the light card is the fallback. Safe zone 7.5% of width (90 px / 96 px). The mark bleeds off the right edge as a tone-on-tone ghost. Text: the README tagline and `ZIO.logInfo("order placed")`.

## Don't

Close the gaps; colour any line but the middle one; stretch, squash or rotate; use bright amber on light grounds; set the mark beside a full "ZIO Logging"; add gradients, glows, shadows, strokes or 3D; place the mark on a busy photo or pattern.

## Done after approval (2026-11-11)

1. Cap height checked against Geist SemiBold 1.900: `OS/2.sCapHeight` = 710 of 1000 units, and the "I" glyph is 710 tall. The 0.71 em used for the inline Z is exact.
2. Lockups outlined (`tools/lockup.py`), shaped with HarfBuzz (kerning on, tracking -2%). Overlaid on live Geist text in Chromium, they match to within anti-aliasing.
3. Social cards exported from the canvas boards (`tools/cards.py`) with local Geist files.
4. PNG and ICO fallbacks made (`tools/raster.py`).
