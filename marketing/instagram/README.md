# DoyaLabs — Instagram launch carousel

Five slides, each in two sizes:

| Slide | Square (1080×1080) | Portrait (1080×1350) |
|---|---|---|
| 01 Cover — "Introducing DoyaLabs" | `square/01-cover.png` | `portrait/01-cover.png` |
| 02 What we make | `square/02-what-we-make.png` | `portrait/02-what-we-make.png` |
| 03 Lyro, our first release | `square/03-lyro.png` | `portrait/03-lyro.png` |
| 04 Inside Lyro (real app screenshots) | `square/04-inside-lyro.png` | `portrait/04-inside-lyro.png` |
| 05 More soon / follow | `square/05-follow.png` | `portrait/05-follow.png` |

Post either all-square or all-portrait; Instagram crops a carousel to the first slide's ratio.

## Editing

- Copy lives in each `NN-*.html`. Shared look (colours from `lib/core/theme/nothing_colors.dart`,
  fonts from `assets/fonts/`) lives in `slides.css`.
- Open a slide in Chrome to preview it; add `#portrait` to the URL for the 4:5 layout.
- Re-render all PNGs: `bash marketing/instagram/render.sh` (uses Chrome or Edge headless;
  set `BROWSER=/path/to/chrome` to override).

App screenshots in `screenshots/` were taken on a Pixel 6 Pro with the status bar cropped off.

All copy, including the `@doyalabs` handle, is placeholder.
