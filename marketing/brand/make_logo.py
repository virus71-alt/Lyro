"""Generates the DoyaLabs logo files as SVG.

Two concepts:
  pixel   - a dot-matrix "D" with a red signal dot in its counter; the wordmark is
            DOYALABS in the same dot grid, so mark and name are one system.
  signal  - a solid geometric "D" whose bowl holds a red dot, with a Geist wordmark.

Run:  python marketing/brand/make_logo.py   (needs fonttools for the Geist wordmark)
Then: bash marketing/brand/render.sh        (PNG exports)
"""
from pathlib import Path

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.ttLib import TTFont

HERE = Path(__file__).resolve().parent
FONTS = HERE.parents[1] / "assets" / "fonts"
OUT = HERE / "svg"

INK = "#0A0A0A"
WHITE = "#FFFFFF"
RED = "#D71921"
GREY_DARK = "#8C8C8C"   # "LABS" on dark backgrounds
GREY_LIGHT = "#5A5A5A"  # "LABS" on light backgrounds

# 5x7 dot-matrix glyphs. "R" marks the red signal dot.
GLYPHS = {
    "D": ["11110", "10001", "10001", "10R01", "10001", "10001", "11110"],
    "d": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
}
DOT = 0.84  # dot diameter as a share of the grid pitch


def svg(w, h, body, bg=None):
    rect = f'<rect width="{w}" height="{h}" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" '
            f'width="{w}" height="{h}">{rect}{body}</svg>\n')


def dots(glyph, x, y, pitch, ink, red=RED):
    out = []
    r = pitch * DOT / 2
    for row, line in enumerate(GLYPHS[glyph]):
        for col, c in enumerate(line):
            if c == "0":
                continue
            fill = red if c == "R" else ink
            out.append(f'<circle cx="{x + col * pitch + pitch / 2:.2f}" '
                       f'cy="{y + row * pitch + pitch / 2:.2f}" r="{r:.2f}" fill="{fill}"/>')
    return "".join(out)


def dot_word(word, x, y, pitch, colors, gap=1):
    """Dot-matrix word; colors is one fill per letter."""
    out = []
    for ch, ink in zip(word, colors):
        out.append(dots(ch, x, y, pitch, ink))
        x += (5 + gap) * pitch
    return "".join(out), x - gap * pitch


def geist_word(text, x, baseline, size, fill, font_file="Geist-SemiBold.ttf", tracking=0.0):
    """Outlines text in Geist so the SVG needs no font installed."""
    font = TTFont(FONTS / font_file)
    gs = font.getGlyphSet()
    cmap = font.getBestCmap()
    scale = size / font["head"].unitsPerEm
    out = []
    for ch in text:
        name = cmap[ord(ch)]
        pen = SVGPathPen(gs)
        gs[name].draw(pen)
        d = pen.getCommands()
        if d:
            out.append(f'<path transform="translate({x:.2f} {baseline:.2f}) scale({scale:.5f} {-scale:.5f})" '
                       f'd="{d}" fill="{fill}"/>')
        x += gs[name].width * scale + tracking * size
    return "".join(out), x


# ---------- Concept 1: Pixel D ----------

def pixel_mark(ink, bg=None, size=700):
    pitch = size / 7
    body = dots("D", (size - 5 * pitch) / 2, 0, pitch, ink)
    return svg(size, size, body, bg)


def pixel_icon(size=1024):
    pitch = size * 0.1
    w, h = 5 * pitch, 7 * pitch
    body = (f'<rect width="{size}" height="{size}" rx="{size * 0.225}" fill="{INK}"/>'
            + dots("D", (size - w) / 2, (size - h) / 2, pitch, WHITE))
    return svg(size, size, body)


def pixel_lockup(dark):
    pitch = 40
    ink = WHITE if dark else INK
    grey = GREY_DARK if dark else GREY_LIGHT
    pad = 60
    word, end = dot_word("DOYALABS", pad, pad, pitch, [ink] * 4 + [grey] * 4)
    w, h = end + pad, 7 * pitch + 2 * pad
    return svg(int(w), int(h), word, INK if dark else None)


# ---------- Concept 2: Signal D ----------

def signal_d(x, y, s, ink):
    """Geometric D in a box of side s at (x, y): a half-disc bowl on a straight stem,
    a round counter cut clean through, and a red signal dot in the counter."""
    r = s / 2
    cx, cy = x + s - r, y + r
    hr = s * 0.22
    hx = cx - s * 0.02
    d = (f"M{x},{y} H{cx} A{r},{r} 0 0 1 {cx},{y + s} H{x} Z "
         f"M{hx - hr:.2f},{cy} A{hr:.2f},{hr:.2f} 0 1 0 {hx + hr:.2f},{cy} "
         f"A{hr:.2f},{hr:.2f} 0 1 0 {hx - hr:.2f},{cy} Z")
    return (f'<path d="{d}" fill="{ink}" fill-rule="evenodd"/>'
            f'<circle cx="{hx:.2f}" cy="{cy}" r="{s * 0.11:.2f}" fill="{RED}"/>')


def signal_mark(ink, bg=None, size=700):
    return svg(size, size, signal_d(size * 0.1, size * 0.1, size * 0.8, ink), bg)


def signal_icon(size=1024):
    body = (f'<rect width="{size}" height="{size}" rx="{size * 0.225}" fill="{INK}"/>'
            + signal_d(size * 0.25, size * 0.25, size * 0.5, WHITE))
    return svg(size, size, body)


def signal_lockup(dark):
    ink = WHITE if dark else INK
    grey = GREY_DARK if dark else GREY_LIGHT
    bg = INK if dark else "#F2F2F2"
    pad, s = 60, 200
    mark = signal_d(pad, pad, s, ink)
    size = 168
    base = pad + s / 2 + size * 0.36
    a, x = geist_word("doya", pad + s + 56, base, size, ink, tracking=-0.035)
    b, x = geist_word("labs", x + 2, base, size, grey, "Geist-Regular.ttf", tracking=-0.035)
    w, h = x + pad, s + 2 * pad
    return svg(int(w), int(h), mark + a + b, bg if dark else None)


def main():
    OUT.mkdir(exist_ok=True)
    files = {
        "pixel-mark.svg": pixel_mark(INK),
        "pixel-mark-white.svg": pixel_mark(WHITE),
        "pixel-icon.svg": pixel_icon(),
        "pixel-wordmark.svg": pixel_lockup(dark=False),
        "pixel-wordmark-dark.svg": pixel_lockup(dark=True),
        "signal-mark.svg": signal_mark(INK),
        "signal-mark-white.svg": signal_mark(WHITE),
        "signal-icon.svg": signal_icon(),
        "signal-lockup.svg": signal_lockup(dark=False),
        "signal-lockup-dark.svg": signal_lockup(dark=True),
    }
    for name, text in files.items():
        (OUT / name).write_text(text, encoding="utf-8")
        print(" ", OUT.relative_to(HERE.parents[1]) / name)


if __name__ == "__main__":
    main()
