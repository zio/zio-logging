# Round 1: four concepts

Date: 2026-11-10. Canvas: https://claude.ai/artifact/19oeK6TcKiVqCShfT255wK

Masters are drawn on a 24-unit grid; small-size cuts (used below 24 px) on a 16-unit grid. Files: `marks/<concept>-<master|small>-<light|dark>.svg`. `marks/preview.html` shows each mark at 16, 24, 32 and 96 px on light and dark, with an 8x pixel zoom of the 16 and 24 px rasters.

## Concepts

| # | Name | Idea | Z? | Accent element | Lockup |
| --- | --- | --- | --- | --- | --- |
| 1 | Record | A level stripe plus three message lines: one log record as a log viewer draws it. | No | The level stripe | Mark beside "ZIO Logging" |
| 2 | Tagged Z | The level stripe in front of a solid Z. In the lockup the stripe stands in front of "ZIO Logging", so the name itself reads as a log record. | Yes | The level stripe | Stripe, then inline Z of "ZIO" |
| 3 | Line Z | A Z set as five log lines; the middle line, the one you grep for, is amber. | Yes | The middle line | Inline Z of "ZIO" |
| 4 | Tail | A solid Z followed by a block cursor: the log is still being written (`tail -f`). | Yes | The cursor | Inline Z; cursor moves to the end of the name |

## What I saw at 16, 24, 32 and 96 px (light and dark)

- **Record**: holds at 16 px; three lines stay apart. Risk: at small sizes it looks like the generic "blockquote" or "align left" icon.
- **Tagged Z**: the clearest at 16 px. Stripe and Z stay apart (2 px gap); the Z diagonal stays solid.
- **Line Z**: fails at 16 px. The small cut needs 1 px gaps and reads as stripes, not a Z. It is also the closest to Blocks (pieces cut to form a Z).
- **Tail**: reads at 16 px as "Z" plus a cursor. Risk: a solid Z with one amber part is the same family move as Prelude's base bar, although the shape differs.

## Recommendation

**Tagged Z (2).** It is the only concept where the logging idea also shapes the lockup: the amber stripe is the level marker every log viewer draws, and placing it before "ZIO Logging" turns the name into a log record. It is the most legible at 16 px. It shares the Z with Prelude and Blocks, but its accent is a separate bar in front, not a part of the Z (Prelude) and not a cut (Blocks).

Clear-space unit if chosen: one Z bar thickness (5 of 24 units, about 21% of mark height) on every side.

## Palette (draft)

| Role | Light | Dark |
| --- | --- | --- |
| Page (ground) | White `#FFFFFF` | Terminal `#111318` |
| Panel | Ledger `#F5F4F0` | Shell `#1A1D24` |
| Ink (text, mark body) | Console `#15171C` | Phosphor `#ECEDEF` |
| Secondary text | Slate `#5B606B` | Fog `#9AA0AB` |
| Accent (mark accent, links, keywords) | Deep amber `#985A00` | Amber `#F5A524` |

Brand colour: Console / Phosphor (the mark body). Accent: amber, the one highlight in the mark.

## Contrast (computed with `tools/wcag.py`, WCAG 2.x)

| Pair | Ratio | 4.5:1 text |
| --- | --- | --- |
| Console on White | 17.93 | Pass |
| Console on Ledger | 16.29 | Pass |
| Slate on White | 6.31 | Pass |
| Slate on Ledger | 5.73 | Pass |
| Deep amber on White | 5.53 | Pass |
| Deep amber on Ledger | 5.02 | Pass |
| White on Deep amber (button) | 5.53 | Pass |
| Phosphor on Terminal | 15.86 | Pass |
| Phosphor on Shell | 14.40 | Pass |
| Fog on Terminal | 7.07 | Pass |
| Fog on Shell | 6.42 | Pass |
| Amber on Terminal | 9.10 | Pass |
| Amber on Shell | 8.26 | Pass |
| Terminal on Amber (button) | 9.10 | Pass |
| Amber on White (misuse) | 2.04 | Fail: never use bright amber on light |

The first deep amber I tried, `#A86400`, gave 4.68 on White but 4.25 on Ledger, so I darkened it to `#985A00`.

## Questions for Milad

1. Which concept (or which two) go to round 2?
2. Amber accent and graphite neutrals: keep, or try another hue?
3. Geist + Geist Mono: keep, or prefer another pair?
