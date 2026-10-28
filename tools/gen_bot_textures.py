#!/usr/bin/env python3
"""Generate simple PNG textures for MineBot with zero dependencies."""
import struct, zlib, os

def png(path, w, h, pixel):
    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    raw = b''
    for y in range(h):
        raw += b'\x00'
        for x in range(w):
            raw += bytes(pixel(x, y))
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b'IDAT', zlib.compress(raw)))
        f.write(chunk(b'IEND', b''))

def clamp(v):
    return max(0, min(255, int(v)))

def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)

STEEL = (44, 52, 66)
STEEL_HI = (86, 100, 124)
FRAME = (196, 200, 214)
EYE_OFF = (40, 40, 44)
EYE_ON = (63, 240, 160)
VENT = (24, 30, 40)
GOLD = (255, 205, 70)

def bot_face(x, y):
    base = STEEL if ((x // 3 + y // 2) % 2 == 0) else STEEL_HI
    # metallic banding
    if (x + y) % 7 == 0:
        base = mix(base, STEEL_HI, 0.35)
    # frame border
    if x in (0, 15) or y in (0, 15):
        base = FRAME
    if x in (1, 14) and 2 <= y <= 13:
        base = mix(FRAME, STEEL, 0.5)
    if y in (1, 14) and 2 <= x <= 13:
        base = mix(FRAME, STEEL, 0.5)
    # eyes
    if 4 <= x <= 6 and 4 <= y <= 6:
        base = EYE_ON if not (x == 4 and y == 4) else EYE_OFF
        base = EYE_ON
    if 9 <= x <= 11 and 4 <= y <= 6:
        base = EYE_ON
    if (x == 5 and y == 5) or (x == 10 and y == 5):
        base = mix(EYE_ON, (255, 255, 255), 0.6)
    # mouth vent grid
    if 4 <= x <= 11 and 10 <= y <= 12 and (4 <= x <= 11):
        if (x - 4) % 2 == 0:
            base = VENT
    # corner rivets
    for rx, ry in ((2, 2), (13, 2), (2, 13), (13, 13)):
        if x == rx and y == ry:
            base = mix(FRAME, GOLD, 0.4)
    return base

def icon_scale():
    img = [[bot_face(x // 8, y // 8) for x in range(128)] for y in range(128)]
    # dark background plate
    for y in range(128):
        for x in range(128):
            if x < 8 or x > 119 or y < 8 or y > 119:
                img[y][x] = (12, 16, 24, 255)
    return img

BASE = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'minebot')
png(os.path.join(BASE, 'textures', 'block', 'bot.png'), 16, 16, bot_face)
ICON = icon_scale()
png(os.path.join(BASE, 'icon.png'), 128, 128, lambda x, y: ICON[y][x])
print('textures written')
