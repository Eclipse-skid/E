#!/usr/bin/env python3
"""Generates Epitaph's original item textures (16x16 item sprites + 64x64 shield) into
src/main/resources/resourcepacks/<pack>/assets/minecraft/textures/...
Run from the project root:  python tools/generate_textures.py   (needs Pillow)
"""
import json, os, sys
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "resourcepacks")
PACK_FORMAT = 75  # resource pack format for Minecraft 1.21.11

OUT = (11, 6, 20, 255)       # outline
D = (42, 15, 77, 255)        # deep purple
P = (75, 29, 138, 255)       # purple
V = (138, 77, 255, 255)      # violet
L = (201, 166, 255, 255)     # light violet
G = (255, 79, 216, 255)      # pink accent
W = (239, 230, 255, 255)     # highlight
K = (24, 16, 40, 255)        # near-black

def hexc(h, a=255): return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

# tier -> (blade body, blade edge/highlight, accent for guard + gem)
TIERS = {
    "wooden":    (hexc("5a3a66"), hexc("8d6a9a"), hexc("b08a5a")),
    "stone":     (hexc("55506e"), hexc("9a94b8"), hexc("6a6a8a")),
    "copper":    (hexc("8a3f5a"), hexc("e08a7a"), hexc("ff9a6a")),
    "iron":      (hexc("6d6a92"), hexc("d4d0f2"), hexc("a66cff")),
    "golden":    (hexc("8a6a2a"), hexc("ffe08a"), hexc("ffd24a")),
    "diamond":   (hexc("2f6f9a"), hexc("9af0ff"), hexc("5ce1ff")),
    "netherite": (hexc("1c1426"), hexc("7a5aa8"), hexc("ff4fd8")),
}

def canvas(): return [[None] * 16 for _ in range(16)]

def put(c, x, y, col):
    if 0 <= x < 16 and 0 <= y < 16: c[y][x] = col

def outline(c):
    out = [row[:] for row in c]
    for y in range(16):
        for x in range(16):
            if c[y][x] is None:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and c[ny][nx] is not None:
                        out[y][x] = OUT; break
    return out

def save(c, path):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if c[y][x] is not None: img.putpixel((x, y), c[y][x])
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)

def sword(tier):
    body, edge, acc = TIERS[tier]
    c = canvas()
    for i in range(0, 10):                       # blade, tip top-right
        put(c, 15 - i, i, edge if i % 3 != 1 else body)
        if i > 0: put(c, 14 - i, i, body)
        if i > 1: put(c, 15 - i, i - 1, edge) if i < 2 else None
    for i in range(1, 9): put(c, 15 - i, i - 1, edge)        # lit spine
    put(c, 15, 0, edge)
    for k in range(-2, 3): put(c, 5 + k, 10 + k, acc)       # cross guard
    put(c, 5, 10, W if tier != "golden" else W)
    for (x, y) in ((4, 11), (3, 12), (2, 13)): put(c, x, y, K if tier != "wooden" else hexc("3a2430"))
    for (x, y) in ((4, 12), (3, 13)): put(c, x, y, P)
    put(c, 1, 14, acc); put(c, 1, 15, acc); put(c, 0, 15, G)  # pommel gem
    return outline(c)

def axe(tier):
    body, edge, acc = TIERS[tier]
    c = canvas()
    for i in range(0, 11): put(c, 2 + i, 14 - i, K)         # handle diagonal up-right
    for i in range(1, 10): put(c, 2 + i, 15 - i, P) if i % 2 == 0 else None
    head = ["...XXXX.", "..XXXXXX", ".XXXXXXX", "XXXXXXX.", "XXXXXX..", ".XXXX...", "..XX...."]
    for r, line in enumerate(head):
        for q, ch in enumerate(line):
            if ch == "X":
                col = body
                if line[q + 1:q + 2] != "X" or r == 0: col = edge     # bright cutting edge
                put(c, 7 + q, 1 + r, col)
    put(c, 9, 4, acc); put(c, 10, 4, acc)                    # rune mark on the blade
    put(c, 2, 14, G); put(c, 1, 15, acc)
    return outline(c)

def mace():
    c = canvas()
    for i in range(0, 8): put(c, 2 + i, 14 - i, K)
    for i in range(1, 8, 2): put(c, 2 + i, 14 - i, P)
    head = [".X.X.X.", "XXXXXXX", ".XXXXX.", "XXXXXXX", ".XXXXX.", "XXXXXXX", ".X.X.X."]
    for r, line in enumerate(head):
        for q, ch in enumerate(line):
            if ch == "X":
                col = P
                if r in (1, 2) or q in (5, 6): col = V
                if (r, q) in ((2, 2), (3, 3), (4, 4)): col = L
                if (r, q) == (3, 3): col = G
                put(c, 8 + q, 0 + r, col)
    put(c, 1, 15, G); put(c, 2, 15, D)
    return outline(c)

def spear(tier):
    body, edge, acc = TIERS[tier]
    c = canvas()
    for i in range(0, 13): put(c, 1 + i, 14 - i, K if i % 4 else P)   # long shaft
    tip = [(15, 0), (14, 0), (15, 1), (14, 1), (13, 1), (14, 2), (13, 2), (12, 2), (13, 3), (12, 3), (11, 3)]
    for (x, y) in tip: put(c, x, y, edge if x + y >= 15 else body)
    put(c, 12, 4, acc); put(c, 11, 4, acc); put(c, 12, 5, acc)           # binding
    put(c, 0, 15, G); put(c, 1, 15, acc)
    return outline(c)

TOTEM_HALF = [
    "........",
    "....oooo",
    "...opppp",
    "...opgpp",
    "...opppp",
    "...oodpp",
    "..oopppp",
    "ooooppvp",
    "opppppvp",
    "oppppdpp",
    "ooooopdp",
    "...oppvp",
    "...opdpp",
    "...oppvp",
    "..ooopdp",
    "..oooooo",
]
def totem():
    cmap = {"o": OUT, "d": D, "p": P, "v": V, "l": L, "g": G, "w": W}
    c = canvas()
    for y, half in enumerate(TOTEM_HALF):
        row = half + half[::-1]
        for x, ch in enumerate(row):
            if ch in cmap: put(c, x, y, cmap[ch])
    for (x, y) in ((6, 4), (9, 4)): put(c, x, y, G)          # glowing eyes
    for (x, y) in ((7, 9), (8, 9), (7, 10), (8, 10)): put(c, x, y, L)  # chest rune
    put(c, 7, 9, W)
    return c

def shield():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    def rect(x0, y0, w, h, col):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w): img.putpixel((x, y), col)
    # plate box (12x22x1) at UV 0,0: top(1,0) bottom(13,0) left(0,1) front(1,1) right(13,1) back(14,1)
    rect(0, 0, 26, 23, D)
    rect(1, 1, 12, 22, K)                       # front
    rect(1, 1, 12, 1, V); rect(1, 22, 12, 1, V); rect(1, 1, 1, 22, V); rect(12, 1, 1, 22, V)  # border
    rect(2, 2, 10, 20, hexc("160c26"))
    cx, cy = 7, 12                              # centre of the 12x22 front face (absolute px)
    for dy in range(-8, 9):
        for dx in range(-5, 6):
            d = abs(dx) + abs(dy) * 0.7
            if d <= 5.2: img.putpixel((cx + dx, cy + dy), V if d > 4.0 else P)
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            d = abs(dx) + abs(dy)
            if d <= 3: img.putpixel((cx + dx, cy + dy), L if d == 3 else W if d == 2 else G)
    rect(14, 1, 12, 22, D)                      # back face
    rect(15, 2, 10, 20, K)
    rect(14, 1, 12, 1, V); rect(14, 22, 12, 1, V)
    rect(26, 0, 16, 12, hexc("2a1a3a"))         # handle box region (dark wood/leather)
    rect(26, 6, 16, 1, hexc("160c26"))
    return img

def write_pack(name, desc, files):
    base = os.path.join(ROOT, name)
    os.makedirs(base, exist_ok=True)
    with open(os.path.join(base, "pack.mcmeta"), "w") as f:
        json.dump({"pack": {"description": desc, "min_format": PACK_FORMAT, "max_format": PACK_FORMAT}}, f, indent=2)
    for rel, img in files.items():
        path = os.path.join(base, "assets", "minecraft", "textures", rel)
        if isinstance(img, Image.Image):
            os.makedirs(os.path.dirname(path), exist_ok=True); img.save(path)
        else: save(img, path)

def main():
    tiers = list(TIERS)
    write_pack("epitaph_swords", "Epitaph dark swords", {f"item/{t}_sword.png": sword(t) for t in tiers})
    write_pack("epitaph_axes", "Epitaph dark axes", {f"item/{t}_axe.png": axe(t) for t in tiers})
    write_pack("epitaph_maces", "Epitaph dark mace", {"item/mace.png": mace()})
    write_pack("epitaph_spears", "Epitaph dark spears", {f"item/{t}_spear.png": spear(t) for t in tiers})
    write_pack("epitaph_totem", "Epitaph dark totem", {"item/totem_of_undying.png": totem()})
    sh = shield()
    write_pack("epitaph_shield", "Epitaph dark shield", {
        "entity/shield_base_nopattern.png": sh, "entity/shield_base.png": sh,
        "entity/shield/shield_base_nopattern.png": sh, "entity/shield/shield_base.png": sh})
    print("textures written to", os.path.normpath(ROOT))

if __name__ == "__main__":
    main()
