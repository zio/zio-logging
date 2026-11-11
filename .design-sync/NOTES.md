# design-sync notes: ZIO Logging brand

- **Moved (2026-11-11):** the Claude Design project `ZIO Logging Brand` moved to Claude and is now a Design System page, https://claude.ai/artifact/RvALFZV5MV2TGrvnaBgcbN. `/design-sync` can no longer send updates to it (`get_project` returns `moveState: moved`). It was rebuilt in place with the Artifact tool into the Design System format: `tokens.json` (colour tokens `zl-*`, light and dark; type styles; mark ratios), `tokens.css`, `components/bundle.css` (Google Fonts import and the `.zl-mark` classes only), the brand book `README.md`, `guidelines/README.md` (= `brand/final/README.md`), a cover, and the logo, icon and social files uploaded under Assets. To change it later, edit those files on the page or publish them with the Artifact tool; colour values come from `tools/marks.py` `PALETTE`.

- Tokens-only design system: no React components (zio-logging is a Scala library). The package is `brand/design-system/` (`@zio.dev/zio-logging-brand`, private, not published); `dist/index.js` is an empty module on purpose, so the converter takes the `[ZERO_MATCH] ... tokens-only DS` path.
- `brand/design-system/styles.css` is generated: run `python3 tools/final.py final` from `brand/` after any mark or colour change, then re-run the driver. It shares the mark geometry with `brand/final/*.svg` (`tools/marks.py`, `ROUND2["lines"]`).
- Guidelines: `guidelinesGlob` points at `brand/final/README.md`, which lands as `guidelines/README.md`.
- Fonts come from Google Fonts at runtime (`@import` at the top of styles.css); `runtimeFontPrefixes: ["Geist"]` keeps `[FONT_MISSING]` quiet. No font files ship.
- NixOS: playwright's downloaded chromium (cache `chromium-1243`, playwright 1.63.0) fails to launch ("Target page, context or browser has been closed"). Set `DS_CHROMIUM_PATH=$(readlink -f /nix/var/nix/profiles/default/bin/chromium)` before validate or the driver.
- `[DTS_REACT]` on build is expected (no components, nothing to type).
- The render check has 0 previews to check. The mark classes and the conventions snippet were checked by rendering the snippet against `ds-bundle/styles.css` in headless chromium, light and dark, mark at 16/20/24/48/96 px.

## Re-sync risks

- The brand is a draft (v0.1) pending Milad's review; any change to colours or the mark must go through `tools/final.py`, or styles.css and the SVGs drift apart.
- `.design-sync/conventions.md` hard-codes token values, the lockup ratios (cap height 0.71 em, assumed, not measured from the font file) and the 34 px small-cut threshold; re-check them when the brand changes.
- Google Fonts URL is network-fetched at render time; if it changes or is blocked, designs fall back to system-ui.
