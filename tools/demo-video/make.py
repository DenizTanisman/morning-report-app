"""Builds docs/demo.mp4 (and docs/demo.webp) from a raw `adb shell screenrecord` of the demo run.

  1. Records nothing itself. On an emulator (pixel_8, dark mode, status bar in demo mode at 07:30):
       adb shell pm clear app.morningreport
       adb shell "nohup screenrecord --bit-rate 16000000 --time-limit 150 /sdcard/demo.mp4 >/dev/null 2>&1 &"
       adb shell am start -n app.morningreport/.MainActivity --ez demo true --es lang en
     and pull /sdcard/demo.mp4 when the demo has finished (about a minute).
  2. python3 tools/demo-video/make.py raw.mp4

What it does with the recording:
  - drops the start (app launch and the look-up of every source, while the empty screen just sits there),
  - drops the frames where the video player is still black (YouTube loading),
  - plays the rest 1.2x faster,
  - drops the still frames after the demo finished and holds the last picture for 1.2 s,
  - puts the logo intro (intro.py) in front with a short cross-fade.
"""
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
SW, SH, FPS = 54, 120, 30          # tiny analysis frames
OUT_W, OUT_H = 720, 1600
INTRO_S, FADE_S = 2.8, 0.4
SPEED = 1.2                        # the app part plays a little faster than it was recorded


def frames(path):
    p = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-vf", f"fps={FPS},scale={SW}:{SH},format=gray",
                        "-f", "rawvideo", "-"], capture_output=True, check=True).stdout
    n = SW * SH
    return [p[i:i + n] for i in range(0, len(p) - n + 1, n)]


def diff(a, b):
    return sum(abs(x - y) for x, y in zip(a, b)) / len(a)


def segments(fr):
    # The empty start screen: the first frame that then stays the same for 3 seconds.
    ref = None
    for i in range(len(fr) - 3 * FPS):
        if all(diff(fr[i], fr[j]) < 1.0 for j in range(i, i + 3 * FPS, 10)):
            ref = i
            break
    if ref is None:
        raise SystemExit("no empty start screen found")
    first_change = next(i for i in range(ref, len(fr)) if diff(fr[ref], fr[i]) > 3.0)
    start = max(ref, first_change - int(0.6 * FPS))
    # End: the last frame that differs from its neighbour (screenrecord writes nothing while the screen is still;
    # the final picture is held for a moment when the video is put together).
    end = max(i for i in range(start + 1, len(fr)) if diff(fr[i - 1], fr[i]) > 0.5)
    # Player still loading: the video box (upper middle of the screen) is black left and right of the spinner.
    def black(f):
        rows = range(int(SH * 0.43), int(SH * 0.48))
        cols = [c for c in range(int(SW * 0.08), int(SW * 0.92)) if not SW * 0.35 < c < SW * 0.65]
        vals = [f[r * SW + c] for r in rows for c in cols]
        return sum(v < 12 for v in vals) / len(vals) > 0.9
    cut = [black(fr[i]) for i in range(len(fr))]
    keep, i = [], start
    while i <= end:
        if cut[i]:
            j = i
            while j <= end and cut[j]:
                j += 1
            # Leave a few black frames so the player visibly opens, then jump to the picture.
            keep.append((i, min(j, i + 6)))
            i = j
            continue
        j = i
        while j <= end and not cut[j]:
            j += 1
        keep.append((i, j))
        i = j
    # Merge neighbours.
    merged = []
    for a, b in keep:
        if merged and merged[-1][1] == a:
            merged[-1] = (merged[-1][0], b)
        else:
            merged.append((a, b))
    return merged


def main():
    raw = Path(sys.argv[1]).resolve()
    out = ROOT / "docs/demo.mp4"
    fr = frames(raw)
    segs = segments(fr)
    total = sum(b - a for a, b in segs) / FPS
    print("segments (s):", [(round(a / FPS, 2), round(b / FPS, 2)) for a, b in segs], "app part", round(total / SPEED, 1), "s after speed-up")
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        subprocess.run([sys.executable, str(HERE / "intro.py"), str(tmp / "intro"), str(INTRO_S)], check=True)
        sel = "+".join(f"between(n\\,{a}\\,{b - 1})" for a, b in segs)
        app = tmp / "app.mp4"
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(raw), "-vf",
                        f"fps={FPS},select='{sel}',setpts=N/{FPS}/TB/{SPEED},fps={FPS},tpad=stop_mode=clone:stop_duration=1.2,scale=1080:2400",
                        "-an", "-c:v", "libx264", "-crf", "14", "-preset", "fast", str(app)], check=True)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-framerate", str(FPS), "-i", str(tmp / "intro/i%04d.png"),
                        "-i", str(app), "-filter_complex",
                        f"[0:v]format=yuv420p,settb=1/{FPS}[a];[1:v]format=yuv420p,settb=1/{FPS}[b];"
                        f"[a][b]xfade=transition=fade:duration={FADE_S}:offset={INTRO_S - FADE_S},"
                        f"scale={OUT_W}:{OUT_H}:flags=lanczos,format=yuv420p[v]",
                        "-map", "[v]", "-an", "-c:v", "libx264", "-crf", "24", "-preset", "slow",
                        "-profile:v", "high", "-movflags", "+faststart", str(out)], check=True)
        # README preview: animated WebP (320 px wide, 12 fps).
        wf = tmp / "webp"
        wf.mkdir()
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(out), "-vf", "fps=12,scale=320:-2:flags=lanczos",
                        str(wf / "w%04d.png")], check=True)
        pngs = sorted(str(p) for p in wf.glob("*.png"))
        subprocess.run(["img2webp", "-loop", "0", "-lossy", "-q", "70", "-m", "4", "-d", str(round(1000 / 12)), *pngs,
                        "-o", str(ROOT / "docs/demo.webp")], check=True, capture_output=True)
    d = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(out)],
                       capture_output=True, text=True).stdout.strip()
    print(out, round(float(d), 1), "s,", out.stat().st_size // 1024, "KB")


if __name__ == "__main__":
    main()
