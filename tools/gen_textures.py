#!/usr/bin/env python3
# Generates 16x16 pixel-art textures + mod icon for MiniEdit (pure stdlib).
import struct, zlib, os, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")

def write_png(path, pixels, w, h):
    assert len(pixels) == w * h * 4, f"{path}: expected {w*h*4} bytes, got {len(pixels)}"
    raw = b""
    stride = w * 4
    for y in range(h):
        raw += b"\x00" + bytes(pixels[y*stride:(y+1)*stride])
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote", path)

T = 255  # opaque alpha

# ---------------- edit_axe.png ----------------
# palette
AX_L = (156, 231, 255, T)   # light cyan
AX_M = (79, 169, 232, T)    # mid blue
AX_D = (42, 109, 181, T)    # dark blue
GD_L = (200, 148, 95, T)    # wood light
GD_D = (138, 100, 48, T)    # wood dark
GEM  = (255, 224, 102, T)   # edit-gem yellow
W, H = 16, 16
px = [0]*W*H*4

def setp(x, y, c):
    i = (y*W + x)*4
    px[i:i+4] = list(c)

def fill(x0, y0, x1, y1, c):
    for y in range(y0, y1+1):
        for x in range(x0, x1+1):
            setp(x, y, c)

# axe head (blue), rows 1..7
fill(4, 1, 9, 1, AX_L)
setp(3, 2, AX_L); fill(4, 2, 9, 2, AX_M); setp(10, 2, AX_D)
setp(3, 3, AX_L); fill(4, 3, 9, 3, AX_M); fill(10, 3, 10, 3, AX_D)
fill(4, 4, 10, 4, AX_M); setp(10, 4, AX_D)
setp(7, 4, GEM)
fill(5, 5, 10, 5, AX_M); fill(10, 5, 10, 5, AX_D); setp(7, 5, GEM)
fill(5, 6, 9, 6, AX_D)
setp(6, 7, AX_D); fill(7, 7, 8, 7, AX_D)
# handle (wood), rows 8..15
setp(7, 8, AX_D)
for y in range(8, 16):
    setp(8, y, GD_L); setp(9, y, GD_D)
setp(9, 8, AX_D)
write_png("src/main/resources/assets/miniedit/textures/item/edit_axe.png", px, W, H)

# ---------------- edit_panel.png ----------------
EDGE = (27, 42, 68, T)      # border navy
BASE = (46, 65, 98, T)      # panel base
TITLE= (255, 201, 60, T)    # yellow strip
GREEN= (55, 208, 108, T)    # green text line
GREEN2=(42, 165, 84, T)
RED  = (227, 94, 94, T)
CYAN = (90, 214, 240, T)
W, H = 16, 16
px = [0]*W*H*4

fill(0, 0, 15, 15, EDGE)
fill(1, 1, 14, 14, BASE)
# title strip
fill(2, 2, 13, 3, TITLE)
setp(3, 2, (46,65,98,T)); setp(4, 2, (46,65,98,T))
setp(11, 3, (46,65,98,T))
# coordinate lines (green)
def line(y, x0, width):
    fill(x0, y, x0+width, y, GREEN)
    if width > 3:
        setp(x0+width, y, GREEN2)
line(5, 3, 5)
line(6, 3, 9)
line(8, 3, 6)
line(9, 3, 8)
line(11, 3, 7)
# bottom buttons
fill(2, 13, 4, 13, RED)
fill(6, 13, 8, 13, TITLE)
fill(10, 13, 12, 13, CYAN)
write_png("src/main/resources/assets/miniedit/textures/item/edit_panel.png", px, W, H)

# ---------------- icon.png (64x64, panel scaled x4) ----------------
# reuse last pixels: read them from generated array semantics; simplest: rebuild scaled
scale = 8
W2 = H2 = 16*scale
px2 = [0]*W2*H2*4
for y in range(W2):
    for x in range(W2):
        sx, sy = x//scale, y//scale
        i = (sy*W + sx)*4
        j = (y*W2 + x)*4
        px2[j:j+4] = px[i:i+4]
write_png("src/main/resources/assets/miniedit/icon.png", px2, W2, W2)
print("done")
