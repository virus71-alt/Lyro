"""Renders the Lyro logo mark and every platform launcher icon from one source.

Mark: a precision-machined speaker aperture (brushed titanium ring around a
micro-perforated grille) with the signature red knob dot — the same object as
the 3D speaker on the Now Playing screen — on matte graphite.

usage: python tool/brand/render_logo.py <project-root> [--preview <dir>]
Requires numpy and Pillow.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

SS = 2  # supersampling factor
N = 1024 * SS  # master canvas


def grid(n):
    y, x = np.mgrid[0:n, 0:n].astype(np.float32)
    return x + 0.5, y + 0.5


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def over(dst, rgb, alpha):
    """Composite rgb (H,W,3 or 3) with alpha (H,W) over dst (H,W,4 premult-free)."""
    a = alpha[..., None]
    dst[..., :3] = dst[..., :3] * (1 - a) + np.asarray(rgb, np.float32) * a
    dst[..., 3:4] = dst[..., 3:4] * (1 - a) + a
    return dst


def hexrgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], np.float32)


def background(n=N):
    """Matte graphite with a soft top light and fine grain."""
    x, y = grid(n)
    cx, cy = n / 2, n * 0.30
    d = np.hypot((x - cx) / n, (y - cy) / n)
    top, mid, edge = hexrgb('#2A2B31'), hexrgb('#15161A'), hexrgb('#08080A')
    t1 = smoothstep(0.0, 0.45, d)[..., None]
    t2 = smoothstep(0.45, 0.85, d)[..., None]
    rgb = top * (1 - t1) + mid * t1
    rgb = rgb * (1 - t2) + edge * t2
    rng = np.random.default_rng(7)
    grain = rng.normal(0, 0.012, (n, n, 1)).astype(np.float32)
    rgb = np.clip(rgb + grain, 0, 1)
    out = np.zeros((n, n, 4), np.float32)
    out[..., :3] = rgb
    out[..., 3] = 1
    return out


def mark(n=N, scale=1.0, mono=False):
    """Transparent layer with the aperture + knob, mark diameter ~= 0.62*n*scale."""
    out = np.zeros((n, n, 4), np.float32)
    x, y = grid(n)
    c = n / 2
    dx, dy = x - c, y - c
    r = np.hypot(dx, dy)
    th = np.arctan2(dy, dx)
    px = n / 1024 / 1  # 1 design px at 1024
    R_out = 318 * px * scale
    R_in = 252 * px * scale
    aa = 1.2 * SS * (n / N)

    ring_a = smoothstep(R_out + aa, R_out - aa, r) * smoothstep(R_in - aa, R_in + aa, r)
    hole_a = smoothstep(R_in + aa, R_in - aa, r)

    if mono:
        out = over(out, (1, 1, 1), ring_a)
    else:
        # Soft drop shadow under the ring
        sh = smoothstep(R_out + 60 * px * scale, R_out - 10 * px * scale,
                        np.hypot(dx, dy - 22 * px * scale))
        out = over(out, (0, 0, 0), sh * 0.55)

        # Recess: deep, slightly lit from the top
        rr = np.clip(r / R_in, 0, 1)
        top_light = np.clip(-dy / R_in, -1, 1)
        rec = hexrgb('#050506') * (1 - rr[..., None] * 0.6) + hexrgb('#16171B') * (rr[..., None] * 0.6)
        rec = rec * (1 + 0.15 * top_light[..., None])
        out = over(out, rec, hole_a)

        # Micro-perforated grille: hex grid, fading towards the rim
        step = 30 * px * scale
        hole_r = 6.5 * px * scale
        row = np.round(y / (step * 0.866))
        gy = row * step * 0.866
        offs = (row % 2) * step * 0.5
        gx = np.round((x - offs) / step) * step + offs
        dd = np.hypot(x - gx, y - gy)
        dots = smoothstep(hole_r + aa, hole_r - aa, dd)
        lip = smoothstep(hole_r + aa, hole_r - aa, np.hypot(x - gx, y - gy - 1.6 * px * scale))
        inside = smoothstep(R_in - 14 * px * scale, R_in - 34 * px * scale, r) * hole_a
        out = over(out, (0.36, 0.37, 0.41), lip * inside * 0.55)
        out = over(out, (0.0, 0.0, 0.0), dots * inside * 0.9)

        # Inner shadow cast by the ring into the recess (top side)
        ish = smoothstep(R_in - 40 * px * scale, R_in, r) * smoothstep(-0.2, 1.0, -dy / R_in)
        out = over(out, (0, 0, 0), ish * hole_a * 0.7)

        # Brushed titanium ring: anisotropic sheen + bevel highlights
        sheen = 0.5 + 0.5 * np.cos(2 * (th + math.radians(55)))
        sheen2 = 0.5 + 0.5 * np.cos(6 * (th - 0.3))
        base = 0.20 + 0.42 * sheen ** 2 + 0.05 * sheen2
        rng = np.random.default_rng(3)
        brush = rng.normal(0, 1, 4096).astype(np.float32)
        idx = ((th + math.pi) / (2 * math.pi) * 4095).astype(np.int32)
        base = base + 0.025 * brush[idx]
        tint = np.stack([base * 0.98, base * 1.0, base * 1.06], -1)
        # bevel: bright outer-top edge, dark inner-bottom edge
        edge_out = smoothstep(R_out - 9 * px * scale, R_out - 1 * px * scale, r)
        edge_in = smoothstep(R_in + 9 * px * scale, R_in + 1 * px * scale, r)
        lit = np.clip(-(dx * 0.6 + dy) / r.clip(1), -1, 1)
        tint = tint + (edge_out * np.clip(lit, 0, 1) * 0.35)[..., None]
        tint = tint - (edge_in * np.clip(lit, 0, 1) * 0.12)[..., None]
        tint = tint + (edge_in * np.clip(-lit, 0, 1) * 0.22)[..., None]
        out = over(out, np.clip(tint, 0, 1), ring_a)

    # Red knob, sitting on the ring at the upper right like the speaker's
    ang = math.radians(-45)
    kr = 50 * px * scale
    kc = (c + math.cos(ang) * (R_out - 18 * px * scale), c + math.sin(ang) * (R_out - 18 * px * scale))
    kd = np.hypot(x - kc[0], y - kc[1])
    knob_a = smoothstep(kr + aa, kr - aa, kd)
    if mono:
        # cut a gap around the knob, then the knob itself
        gap = smoothstep(kr + 16 * px * scale + aa, kr + 16 * px * scale - aa, kd)
        out[..., 3] *= (1 - gap)
        out = over(out, (1, 1, 1), knob_a)
    else:
        kshadow = smoothstep(kr + 30 * px * scale, kr - 6 * px * scale,
                             np.hypot(x - kc[0], y - kc[1] - 14 * px * scale))
        out = over(out, (0, 0, 0), kshadow * 0.6)
        # Matte, precision-turned cap: soft top-left light, concentric
        # machining rings and a thin bright chamfer instead of a gloss blob.
        u = (x - kc[0]) / kr
        v = (y - kc[1]) / kr
        rho = np.hypot(u, v)
        red = hexrgb('#D9232B')
        shade = 1.06 - 0.30 * np.clip((u * 0.4 + v) * 0.7 + 0.2, 0, 1)
        knob = red * shade[..., None]
        turn = 0.5 + 0.5 * np.cos(rho * 60)
        knob = knob * (1 - 0.035 * turn[..., None])
        chamfer = smoothstep(0.80, 0.90, rho) * smoothstep(1.0, 0.92, rho)
        lit = np.clip(-(u * 0.6 + v), 0, 1)
        knob = knob + (chamfer * (0.10 + 0.28 * lit))[..., None]
        knob = knob * (1 - 0.30 * (smoothstep(0.93, 1.0, rho) * np.clip(u * 0.6 + v, 0, 1))[..., None])
        out = over(out, np.clip(knob, 0, 1), knob_a)
    return out


def to_image(arr, size):
    img = Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8), 'RGBA')
    return img.resize((size, size), Image.LANCZOS)


def squircle_mask(n, radius=0.225):
    x, y = grid(n)
    p = 5.0
    u = np.abs((x - n / 2) / (n / 2))
    v = np.abs((y - n / 2) / (n / 2))
    d = (u ** p + v ** p) ** (1 / p)
    return smoothstep(1.0, 1.0 - 2.0 / n, d)


def main():
    root = sys.argv[1]
    preview = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None

    bg = background()
    full = bg.copy()
    m = mark(scale=1.0)
    a = m[..., 3:4]
    full[..., :3] = full[..., :3] * (1 - a) + m[..., :3] * a  # mark is straight alpha

    full_img = to_image(full, 1024).convert('RGB')

    # Adaptive icon layers: 108dp canvas, mark kept inside the 66dp safe zone
    fg = mark(scale=0.70)
    mono = mark(scale=0.70, mono=True)
    bg_img = to_image(background(), 432)

    outputs = {}

    # Android
    res = os.path.join(root, 'android/app/src/main/res')
    dens = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
    for d, k in dens.items():
        folder = os.path.join(res, f'mipmap-{d}')
        legacy = round(48 * k)
        layer = round(108 * k)
        sq = full_img.resize((legacy, legacy), Image.LANCZOS).convert('RGBA')
        mask = Image.fromarray((squircle_mask(legacy) * 255).astype(np.uint8))
        sq.putalpha(mask)
        outputs[os.path.join(folder, 'ic_launcher.png')] = sq
        rnd = full_img.resize((legacy, legacy), Image.LANCZOS).convert('RGBA')
        xx, yy = grid(legacy)
        circ = smoothstep(legacy / 2, legacy / 2 - 1, np.hypot(xx - legacy / 2, yy - legacy / 2))
        rnd.putalpha(Image.fromarray((circ * 255).astype(np.uint8)))
        outputs[os.path.join(folder, 'ic_launcher_round.png')] = rnd
        outputs[os.path.join(folder, 'ic_launcher_foreground.png')] = to_image(fg, layer)
        outputs[os.path.join(folder, 'ic_launcher_background.png')] = bg_img.resize((layer, layer), Image.LANCZOS)
        outputs[os.path.join(folder, 'ic_launcher_monochrome.png')] = to_image(mono, layer)

    # iOS (opaque, full bleed; the OS applies the mask)
    ios = os.path.join(root, 'ios/Runner/Assets.xcassets/AppIcon.appiconset')
    contents = json.load(open(os.path.join(ios, 'Contents.json')))
    for im in contents['images']:
        if 'filename' not in im:
            continue
        w = float(im['size'].split('x')[0]) * float(im['scale'].rstrip('x'))
        outputs[os.path.join(ios, im['filename'])] = full_img.resize((round(w), round(w)), Image.LANCZOS)

    # In-app assets
    app_icon = full_img.convert('RGBA')
    mask = Image.fromarray((squircle_mask(1024) * 255).astype(np.uint8))
    app_icon.putalpha(mask)
    outputs[os.path.join(root, 'assets/icon/icon.png')] = app_icon
    outputs[os.path.join(root, 'assets/icon/mark.png')] = to_image(mark(scale=1.55), 512)

    if preview:
        os.makedirs(preview, exist_ok=True)
        app_icon.save(os.path.join(preview, 'icon_1024.png'))
        to_image(fg, 432).save(os.path.join(preview, 'fg.png'))
        to_image(mono, 432).save(os.path.join(preview, 'mono.png'))
        to_image(mark(scale=1.55), 512).save(os.path.join(preview, 'mark.png'))
        return

    for path, img in outputs.items():
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path, optimize=True)
        print('wrote', os.path.relpath(path, root))


if __name__ == '__main__':
    main()
