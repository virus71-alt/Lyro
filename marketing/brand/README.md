# DoyaLabs logo

**Chosen: B · Signal D** (`svg/signal-*.svg`, `png/signal-*.png`). Concept A is kept for reference.

Two concepts, drawn from what the DoyaLabs apps already share: black and white with
one red accent (Bahi, NihongoDoya, Lyro), dot-matrix type (Lyro, GoFare) and bold,
simple geometry.

- **A · Pixel D**: a dot-matrix "D" with a red signal dot in its centre. The wordmark is
  DOYALABS in the same dot grid. Best at large sizes; the red dot gets lost below ~48 px.
- **B · Signal D**: a solid geometric "D" with a round red "lens" in its bowl, next to a
  `doya`**`labs`** wordmark in Geist. Reads well down to favicon size.

The red dot works like a record light (Lyro), the sun on Nepal's flag (Bahi) and Japan's
hinomaru (NihongoDoya), so the one mark fits every app.

## Files

- `svg/`: vector logos (text is outlined, so no fonts are needed)
- `png/`: transparent PNG exports; `*-icon.png` are 1024 px app-icon tiles
- `board.png`: both concepts side by side

## Editing

Shapes and colours live in `make_logo.py`. Run `bash marketing/brand/render.sh` to
regenerate the SVGs and PNGs (needs `pip install fonttools` and Chrome or Edge).
