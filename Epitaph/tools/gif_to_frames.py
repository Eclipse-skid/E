#!/usr/bin/env python3
"""Turns an animated GIF into Epitaph's BoyKisser frame sequence.

    python tools/gif_to_frames.py my_boykisser.gif

Writes src/main/resources/assets/epitaph/textures/gui/screenmates/boykisser/frame_000.png ...
plus animation.json ({"frames": N, "frametime_ms": ...}). Epitaph plays them automatically.
White background connected to the image border is made transparent (interior whites are kept);
every frame is cropped to the same box so the character does not jitter. Needs Pillow + numpy + scipy.
Options: --height 128   --keep-watermark-box x0,y0,x1,y1 (pixels in the GIF to erase, e.g. a site logo)
"""
import argparse, json, os, shutil
import numpy as np
from PIL import Image, ImageSequence
from scipy import ndimage as ndi

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "epitaph", "textures", "gui", "screenmates", "boykisser")

def cutout(rgb, erase):
    a = np.array(rgb.convert("RGBA"))
    if erase:
        x0, y0, x1, y1 = erase; a[y0:y1, x0:x1, :3] = 255
    white = (a[..., :3].min(axis=2) > 235)
    lab, _ = ndi.label(white)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    outer = np.isin(lab, list(border))
    a[outer, 3] = 0
    return a

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("gif"); ap.add_argument("--height", type=int, default=128)
    ap.add_argument("--erase", default="", help="x0,y0,x1,y1 box to blank out (watermark)")
    args = ap.parse_args()
    erase = tuple(int(v) for v in args.erase.split(",")) if args.erase else None
    gif = Image.open(args.gif)
    frames, durs = [], []
    for fr in ImageSequence.Iterator(gif):
        durs.append(fr.info.get("duration", 80))
        frames.append(cutout(fr.convert("RGB"), erase))
    ys = np.concatenate([np.where(f[..., 3] > 0)[0] for f in frames]); xs = np.concatenate([np.where(f[..., 3] > 0)[1] for f in frames])
    box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    shutil.rmtree(OUT, ignore_errors=True); os.makedirs(OUT)
    for i, f in enumerate(frames):
        im = Image.fromarray(f).crop(box)
        scale = args.height / im.height
        im = im.resize((max(1, round(im.width * scale)), args.height), Image.LANCZOS)
        im.save(os.path.join(OUT, f"frame_{i:03d}.png"))
        with open(os.path.join(OUT, f"frame_{i:03d}.png.mcmeta"), "w") as m:
            m.write('{"texture":{"blur":true}}')   # smooth scaling in the HUD
    ms = int(sum(durs) / len(durs)) or 80
    with open(os.path.join(OUT, "animation.json"), "w") as fh:
        json.dump({"frames": len(frames), "frametime_ms": ms}, fh)
    print(f"wrote {len(frames)} frames ({ms} ms each) to {os.path.normpath(OUT)}")

if __name__ == "__main__":
    main()
