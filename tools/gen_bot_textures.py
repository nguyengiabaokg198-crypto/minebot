#!/usr/bin/env python3
"""Generate MineBot textures (block face + mod icon) with zero dependencies.

Design: bold, chunky robot face that stays readable at 16x16:
- solid dark steel plate with faint speckle
- 2px light metal frame
- two 4x4 cyan eyes with dark outline + white highlight
- mouth grille: dark slot with 3 bright teeth
- 2x2 gold bolts in the corners
"""
import struct, zlib, os

def png(path, w, h, pixel):
    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    raw = b''
    for y in range(h):
        raw += b'\x00'
        for x in range(w):
            px = pixel(x, y)
            # PNG is RGBA: pad RGB tuples with full alpha
            raw += bytes((*px, 255)) if len(px) == 3 else bytes(px)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b'IDAT', zlib.compress(raw)))
        f.write(chunk(b'IEND', b''))

def clamp(v):
    return max(0, min(255, int(v)))

def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)

STEEL      = (56, 66, 84)     # base plate
STEEL_DK   = (40, 48, 62)     # plate speckle / bevel shadow
FRAME      = (176, 184, 202)  # outer metal frame
FRAME_DK   = (120, 128, 148)  # frame inner edge
EYE_OUT    = (16, 20, 30)     # eye outline
EYE_ON     = (80, 225, 255)   # eye glow (cyan)
EYE_HI     = (215, 250, 255)  # eye highlight
TOOTH      = (200, 208, 224)  # mouth grille bars
VENT       = (18, 22, 32)     # mouth slot
GOLD       = (255, 200, 64)   # corner bolts
LED        = (255, 96, 80)    # status light

def bot_face(x, y):
    # ---- base plate (solid, with faint deterministic speckle) ----
    base = STEEL
    if (x * 7 + y * 5) % 13 == 0:
        base = STEEL_DK

    # ---- 2px metal frame ----
    if x <= 1 or x >= 14 or y <= 1 or y >= 14:
        base = FRAME
    # frame shading: darker inner edge for depth
    if x == 2 or x == 13 or y == 2 or y == 13:
        if 2 <= x <= 13 and 2 <= y <= 13:
            base = FRAME_DK
    # frame top-left highlight / bottom-right shadow (1px bevel)
    if (x == 0 and y <= 15) or (y == 0):
        base = mix(FRAME, (255, 255, 255), 0.25)
    if (x == 15) or (y == 15):
        base = mix(FRAME, (0, 0, 0), 0.30)

    # ---- eyes: 4x4 cyan with 1px dark outline ----
    for ex in (3, 9):
        # outline box 6x6
        if ex - 1 <= x <= ex + 4 and 3 <= y <= 8:
            base = EYE_OUT
        # eye body 4x4
        if ex <= x <= ex + 3 and 4 <= y <= 7:
            base = EYE_ON
        # white highlight 2x2 top-left of each eye
        if ex <= x <= ex + 1 and 4 <= y <= 5:
            base = EYE_HI

    # ---- mouth grille: slot y 10-12, teeth at x 4,6,8,10 ----
    if 4 <= x <= 11 and 10 <= y <= 12:
        base = VENT
        if (x % 2 == 0) and y == 11:
            base = TOOTH

    # ---- status LED top center ----
    if 7 <= x <= 8 and 3 <= y <= 4:
        base = LED if (x + y) % 2 == 0 else mix(LED, (0, 0, 0), 0.25)

    # ---- 2x2 gold bolts in the four corners (on the frame) ----
    for rx, ry in ((0, 0), (14, 0), (0, 14), (14, 14)):
        if rx <= x <= rx + 1 and ry <= y <= ry + 1:
            inner = (x == rx and y == ry) or (x == rx + 1 and y == ry + 1)
            base = mix(GOLD, (120, 90, 20), 0.4) if inner else GOLD

    return base

def controller_tex(x, y):
    """16x16 item texture: small gamepad/remote, transparent outside the body."""
    # transparent background
    inside = 2 <= x <= 13 and 4 <= y <= 11
    if not inside:
        return (0, 0, 0, 0)
    base = (56, 62, 76, 255)  # body
    # rounded corners -> transparent
    if (x in (2, 13) and y in (4, 11)) or (x in (3, 12) and y in (4, 11)):
        return (0, 0, 0, 0)
    # body border (1px)
    if x in (2, 13) or y in (4, 11):
        base = (148, 156, 174, 255)
    # d-pad cross (left side)
    if 4 <= x <= 7 and y in (7, 8):
        base = (206, 212, 228, 255)
    if x in (5, 6) and 6 <= y <= 9:
        base = (206, 212, 228, 255)
    # buttons (right side): green + gold
    if (x, y) == (10, 6):
        base = (96, 232, 120, 255)
    if (x, y) == (11, 8):
        base = (255, 200, 64, 255)
    if (x, y) == (9, 8):
        base = (96, 180, 255, 255)
    return base


def icon_scale():
    # nearest-neighbour 8x scale of the 16x16 face onto a dark plate
    img = [[bot_face(x // 8, y // 8) for x in range(128)] for y in range(128)]
    for y in range(128):
        for x in range(128):
            if x < 6 or x > 121 or y < 6 or y > 121:
                img[y][x] = (12, 16, 24, 255)
    return img

BASE = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'minebot')
png(os.path.join(BASE, 'textures', 'block', 'bot.png'), 16, 16, bot_face)
png(os.path.join(BASE, 'textures', 'item', 'controller.png'), 16, 16, controller_tex)
ICON = icon_scale()
png(os.path.join(BASE, 'icon.png'), 128, 128, lambda x, y: ICON[y][x])
print('textures written')
