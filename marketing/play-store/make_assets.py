"""Builds the Play Store icon, feature graphic and framed phone screenshots.
Album covers are pixelated so the listing shows no third-party artwork."""
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SHOTS = 'marketing/instagram/screenshots/'
OUT = 'marketing/play-store/'
RED = (215, 25, 33)
GLYPHS = {
    'L': ["1....", "1....", "1....", "1....", "1....", "1....", "11111"],
    'Y': ["1...1", "1...1", ".1.1.", "..1..", "..1..", "..1..", "..1.."],
    'R': ["1111.", "1...1", "1...1", "1111.", "1.1..", "1..1.", "1...1"],
    'O': [".111.", "1...1", "1...1", "1...1", "1...1", "1...1", ".111."],
}
SS = 3  # supersampling for clean dots


def font(name, size):
    return ImageFont.truetype(f'assets/fonts/{name}', size)


def doto(size):
    f = font('Doto-ROND-wght.ttf', size)
    f.set_variation_by_axes([100, 850] if len(f.get_variation_axes()) > 1 else [850])
    return f


def dots(d, w, h, step=18, color=(30, 30, 30)):
    for y in range(step // 2, h, step):
        for x in range(step // 2, w, step):
            d.ellipse([x - 1.6, y - 1.6, x + 1.6, y + 1.6], fill=color)


def wordmark(d, x, y, cell):
    r = cell * .36
    for i, ch in enumerate("LYRO"):
        for ry, row in enumerate(GLYPHS[ch]):
            for rx, c in enumerate(row):
                cx, cy = x + (i * 6 + rx) * cell + cell / 2, y + ry * cell + cell / 2
                d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255) if c == '1' else (32, 32, 32))
    cx, cy, r = x + 24.5 * cell, y + 6.5 * cell, cell * .55
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=RED)


def pixelate(im, box, block=64):
    x0, y0, x1, y1 = box
    r = im.crop(box)
    w, h = r.size
    s = r.resize((max(1, w // block), max(1, h // block)), Image.BILINEAR)
    im.paste(s.resize((w, h), Image.NEAREST), box)


def pixel_circle(im, cx, cy, radius, block=40):
    box = (cx - radius, cy - radius, cx + radius, cy + radius)
    copy = im.copy()
    pixelate(copy, box, block)
    mask = Image.new('L', im.size, 0)
    ImageDraw.Draw(mask).ellipse(box, fill=255)
    im.paste(copy, (0, 0), mask)


def frame(src, title, sub, name):
    W, H = 1080, 1920
    c = Image.new('RGB', (W * SS, H * SS), (0, 0, 0))
    d = ImageDraw.Draw(c)
    dots(d, W * SS, H * SS, 18 * SS, (26, 26, 26))
    d.text((W * SS / 2, 112 * SS), title, font=font('Geist-SemiBold.ttf', 66 * SS), fill=(255, 255, 255), anchor='mm')
    d.text((W * SS / 2, 190 * SS), sub, font=font('Geist-Regular.ttf', 36 * SS), fill=(160, 160, 160), anchor='mm')
    c = c.resize((W, H), Image.LANCZOS)
    h = 1640
    w = round(src.width * h / src.height)
    s = src.resize((w, h), Image.LANCZOS)
    m = Image.new('L', (w * SS, h * SS), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w * SS - 1, h * SS - 1], 56 * SS, fill=255)
    m = m.resize((w, h), Image.LANCZOS)
    x, y = (W - w) // 2, 260
    b = Image.new('L', (W, H), 0)
    ImageDraw.Draw(b).rounded_rectangle([x - 2, y - 2, x + w + 1, y + h + 1], 58, fill=255)
    c.paste((58, 58, 58), (0, 0), b)
    c.paste(s, (x, y), m)
    c.save(f'{OUT}screenshots/{name}.png', optimize=True)


# ---------------------------------------------------------------- icon
icon = Image.open('assets/icon/icon.png').convert('RGBA')
bg = Image.new('RGBA', icon.size, icon.getpixel((512, 60))[:3] + (255,))
bg.alpha_composite(icon)
bg.convert('RGB').resize((512, 512), Image.LANCZOS).save(OUT + 'icon-512.png', optimize=True)

# ---------------------------------------------------------------- feature graphic
W, H = 1024, 500
g = Image.new('RGB', (W * SS, H * SS), (0, 0, 0))
d = ImageDraw.Draw(g)
dots(d, W * SS, H * SS, 16 * SS, (24, 24, 24))
wordmark(d, 70 * SS, 136 * SS, 17 * SS)
d.text((72 * SS, 300 * SS), 'Your music. Your device.', font=font('Geist-SemiBold.ttf', 40 * SS), fill=(255, 255, 255), anchor='lm')
d.text((72 * SS, 352 * SS), 'Offline player with a dot-matrix soul', font=font('Geist-Regular.ttf', 26 * SS), fill=(150, 150, 150), anchor='lm')
g = g.resize((W, H), Image.LANCZOS)
ic = icon.resize((270, 270), Image.LANCZOS)
g.paste(ic, (690, 115), ic)
g.save(OUT + 'feature-graphic-1024x500.png', optimize=True)

# ---------------------------------------------------------------- screenshots
player = Image.open(SHOTS + '01-player.png').convert('RGB')
pixel_circle(player, 548, 1420, 262)
frame(player, 'Your library, offline', 'No accounts. No ads. No tracking.', '1-player')

folders = Image.open(SHOTS + '04-folders.png').convert('RGB')
pixelate(folders, (150, 330, 372, 1350))
pixelate(folders, (40, 2480, 215, 2660))
frame(folders, 'Browse by folder', 'Songs, albums, artists and playlists', '2-folders')

tracks = Image.open(SHOTS + '05-folder-tracks.png').convert('RGB')
pixelate(tracks, (65, 190, 240, 2640))
pixelate(tracks, (40, 2680, 215, 2860))
frame(tracks, 'Plays your lossless files', 'FLAC, WAV, MP3, AAC and OPUS', '3-tracks')

frame(Image.open(SHOTS + '08-equalizer.png').convert('RGB'), 'Tuned to your earphones', 'Measured EQ curves, plus manual fine-tune', '4-equalizer')

# The access key and cover are already masked in this source file.
web = Image.open(OUT + 'source-web-stream.png').convert('RGB')
frame(web, 'Stream to your laptop', 'Over your own Wi-Fi, with the original files', '5-web-stream')
