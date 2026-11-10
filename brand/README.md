# ZIO Logging logo: working folder

Design drafts for a ZIO Logging mark. Nothing here is served by the website and nothing here is final.

## Brief (as understood, 2026-11-10)

**Goal.** One mark for ZIO Logging that says something true about logging and reads at 16 px. End state: a chosen mark, a lockup, light and dark versions, a 16 px small-size cut and favicon, and a one-page brand sheet.

**Process.** 3 to 5 concepts in round 1, narrowed with Milad over rounds. Each round is saved here and committed. Draft only: no PR unless Milad asks.

**Binding family rules** (from the ZIO family rules draft, "Decided" table, 2026-11-10):

- The mark is one simple solid glyph that works at 16 px. A Z is allowed, not required.
- If the mark is a Z, it sits inline as the first letter of "ZIO" in the lockup.
- The mark has one accent element or none; the sheet says which.
- "Brand colour" = the colour that defines the brand; "accent" = the small highlight.
- Text pairs meet WCAG AA 4.5:1, light and dark, with computed ratios shown.
- The mark is never below 16 px; below 24 px a small-size cut is used.
- Clear space is one defined unit of the mark on every side; the sheet states the unit.
- Full light and dark palettes; either may be the default.
- One sans plus one mono, no third family.
- No glow, shadow, gradient or 3D on the mark or in imagery.
- Rounded tiles for app icons and avatars only.
- Social cards: 1200 x 630 shared plus 1280 x 640 GitHub, safe zone 7.5% of width, dark and light.

**Family context.** Prelude: solid Z with a terracotta base bar. HTTP: request/response arrows in mint. Blocks: five blocks cut from an ultramarine tile. ZIO Logging must look like family but not like a copy of any of these.

**Assumptions (mine, open to change).**

- Accent colour is amber, the colour logs use for WARN and the colour of old terminal phosphor. No other family member uses it.
- Neutrals are cool graphite, so the brand does not drift toward Prelude's warm browns.
- Fonts: Geist (sans) and Geist Mono. Both are open source on Google Fonts and differ from the sans fonts of the other three brands.

## Layout

- `tools/marks.py`: mark geometry for every concept, per round (`python3 tools/marks.py <round> <out-dir>`), writes the SVGs and a size preview page.
- `tools/wcag.py`: WCAG 2.x contrast calculator used for every ratio in this folder.
- `round-1/`: first concepts. See `round-1/NOTES.md`.
- `round-2/`: variants of Line Z. See `round-2/NOTES.md`.
- `round-3/`: wider exploration from 2A. See `round-3/NOTES.md`.
- `final/`: chosen mark (round 1, 03 Line Z), files, brand sheet. See `final/README.md`.
