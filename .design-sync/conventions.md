# ZIO Logging brand (draft v0.1): conventions

**This design system has no React components.** It ships brand foundations only: colour tokens, fonts, and the ZIO Logging mark as CSS classes. `window.ZioLoggingBrand` is empty; build layouts with your own markup and style them with the tokens below.

## Setup

Link `styles.css` once. It loads Geist and Geist Mono from Google Fonts and defines every token. Light is the default. Dark follows `prefers-color-scheme`; force one with `data-theme="light"` or `data-theme="dark"` on `<html>` or any wrapper. Always set the page background yourself: `background: var(--zl-bg); color: var(--zl-text);`.

## Tokens (the only vocabulary; do not invent others)

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| `--zl-bg` | `#FFFFFF` | `#111318` | page |
| `--zl-surface` | `#F5F4F0` | `#1A1D24` | panels, code blocks |
| `--zl-text` | `#15171C` | `#ECEDEF` | text and mark lines (the brand colour) |
| `--zl-text-2` | `#5B606B` | `#9AA0AB` | secondary text |
| `--zl-accent` | `#985A00` | `#F5A524` | the one accent: links, string literals, a key highlight |
| `--zl-font-sans` | Geist | | headings, body, wordmark (weights 400, 600) |
| `--zl-font-mono` | Geist Mono | | code, log output, labels (weights 400, 500) |

All text pairs above pass WCAG AA (4.5:1) on `--zl-bg` and `--zl-surface`. Never use the bright amber `#F5A524` on a light background (2.04:1).

## The mark

A Z drawn as five log lines; the middle line is the accent. Use the classes, sized square:

- `.zl-mark`: 24 px and up (set `width` and `height` together).
- `.zl-mark-small`: 16 to 23 px. Never show the mark below 16 px.
- Clear space: one line height (1/6 of the mark's height) on every side.

The lockup puts the mark in place of the Z of "ZIO": mark height = cap height (0.71em of Geist 600), gap 1/12 of the mark, tracking -2%. Below about 34 px type, use `.zl-mark-small` instead.

```html
<div data-theme="dark" style="background: var(--zl-bg); color: var(--zl-text); font-family: var(--zl-font-sans); padding: 48px">
  <h1 style="margin: 0; font-size: 68px; font-weight: 600; letter-spacing: -0.02em; line-height: 1">
    <span class="zl-mark" style="width: .71em; height: .71em; margin-right: .06em" role="img" aria-label="Z"></span>IO Logging
  </h1>
  <p style="color: var(--zl-text-2); font-size: 20px; line-height: 1.55">Simple logging for ZIO apps.</p>
  <code style="font-family: var(--zl-font-mono); background: var(--zl-surface); padding: 2px 6px">ZIO.logInfo(<span style="color: var(--zl-accent)">"order placed"</span>)</code>
</div>
```

## Rules

- One accent element per view; everything else uses `--zl-text` / `--zl-text-2`.
- No gradients, glows, shadows, strokes or 3D on the mark or in imagery. Rounded corners only on app-icon and avatar tiles.
- Never stretch, recolour single lines, close the gaps, or set the mark beside the full "ZIO Logging" text.
- Type: sentence case; caps only for mono labels (12 to 13 px, weight 500, +8% tracking). Body 16 px / 1.55, at most 72 characters a line.

Full brand rules, contrast table and files: `guidelines/README.md`. Token source: `_ds_bundle.css` (imported by `styles.css`).
