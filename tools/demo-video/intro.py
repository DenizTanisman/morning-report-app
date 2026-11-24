"""Renders the logo intro of the demo video as PNG frames (1080x2400, 30 fps).

The background is the same gradient as the app's empty start screen, so the cross-fade into the
recording is seamless. Usage: python3 intro.py <out_dir> [seconds]
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ICON = ROOT / "composeApp/icons/app.png"
W, H, FPS = 1080, 2400, 30
FONT = "/System/Library/Fonts/SFNS.ttf"  # macOS system font; any bold sans works


def lerp(a, b, t):
    return a + (b - a) * t


def clamp(t):
    return max(0.0, min(1.0, t))


def ease_out(t):
    return 1 - (1 - t) ** 3


def ease_back(t, s=1.4):
    t -= 1
    return t * t * ((s + 1) * t + s) + 1


def background():
    # Sampled from the app's empty screen: lighter steel blue at the top, deep navy from the middle down.
    stops = [(0, (46, 83, 123)), (300, (38, 70, 105)), (800, (32, 61, 94)), (1200, (19, 35, 63)), (1600, (17, 31, 54)), (H, (17, 31, 54))]
    img = Image.new("RGB", (W, H))
    px = img.load()
    for y in range(H):
        for i in range(len(stops) - 1):
            y0, c0 = stops[i]
            y1, c1 = stops[i + 1]
            if y0 <= y <= y1:
                t = (y - y0) / max(1, y1 - y0)
                c = tuple(int(lerp(c0[k], c1[k], t)) for k in range(3))
                break
        for x in range(W):
            px[x, y] = c
    return img


def font(size, style):
    f = ImageFont.truetype(FONT, size)
    try:
        f.set_variation_by_name(style)  # SF is a variable font; without this it falls back to an odd width
    except Exception:
        pass
    return f


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    seconds = float(sys.argv[2]) if len(sys.argv) > 2 else 2.6
    bg = background()
    icon = Image.open(ICON).convert("RGBA")
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((W / 2 - 330, 980 - 330, W / 2 + 330, 980 + 330), fill=(100, 190, 255, 120))
    glow = glow.filter(ImageFilter.GaussianBlur(120))
    title_f = font(100, "Bold")
    tag_f = font(42, "Regular")
    title = "Morning Report"
    tag = "Your news sites and YouTube channels,\nin one calm morning feed."

    for n in range(int(seconds * FPS)):
        t = n / FPS
        frame = bg.copy().convert("RGBA")
        a_logo = ease_out(clamp(t / 0.45))
        if a_logo > 0:
            g = glow.copy()
            g.putalpha(g.getchannel("A").point(lambda v: int(v * a_logo)))
            frame = Image.alpha_composite(frame, g)
            scale = 0.55 + 0.45 * ease_back(clamp(t / 0.6))
            size = int(360 * scale)
            ic = icon.resize((size, size), Image.LANCZOS)
            ic.putalpha(ic.getchannel("A").point(lambda v: int(v * a_logo)))
            # The sun rises a little into place.
            y = int(980 - size / 2 + 40 * (1 - ease_out(clamp(t / 0.7))))
            frame.alpha_composite(ic, (int(W / 2 - size / 2), y))
        d = ImageDraw.Draw(frame)
        a_title = ease_out(clamp((t - 0.35) / 0.45))
        if a_title > 0:
            dy = 30 * (1 - a_title)
            d.text((W / 2, 1290 + dy), title, font=title_f, anchor="mm", fill=(255, 255, 255, int(255 * a_title)))
        a_tag = ease_out(clamp((t - 0.6) / 0.45))
        if a_tag > 0:
            dy = 24 * (1 - a_tag)
            d.multiline_text((W / 2, 1420 + dy), tag, font=tag_f, anchor="mm", align="center", spacing=14,
                             fill=(176, 198, 224, int(255 * a_tag)))
        # Logo and text fade away to the bare background before the cross-fade into the app's own start screen,
        # so the two never sit on top of each other.
        gone = ease_out(clamp((t - (seconds - 0.75)) / 0.35))
        if gone > 0:
            frame = Image.blend(frame, bg.convert("RGBA"), gone)
        frame.convert("RGB").save(out / f"i{n:04d}.png")


if __name__ == "__main__":
    main()
