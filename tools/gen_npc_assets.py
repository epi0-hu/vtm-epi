#!/usr/bin/env python3
"""Generates the NPC rig, animations and skins under src/main/resources/assets/vtm.

The rig uses the vanilla player skin layout, so any 64x64 player skin works with it. Run from the repo root:
    python3 tools/gen_npc_assets.py
"""
import glob
import json
import os
import random

from PIL import Image

ASSETS = 'src/main/resources/assets/vtm'
CLAN_DIR = 'src/main/resources/data/vtm/vtm/clan'


# --------------------------------------------------------------------------- model

def cube(origin, size, uv, inflate=None):
    c = {'origin': origin, 'size': size, 'uv': uv}
    if inflate:
        c['inflate'] = inflate
    return c


def write_model():
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        {'name': 'body', 'parent': 'root', 'pivot': [0, 12, 0],
         'cubes': [cube([-4, 12, -2], [8, 12, 4], [16, 16]), cube([-4, 12, -2], [8, 12, 4], [16, 32], 0.25)]},
        {'name': 'head', 'parent': 'body', 'pivot': [0, 24, 0],
         'cubes': [cube([-4, 24, -4], [8, 8, 8], [0, 0]), cube([-4, 24, -4], [8, 8, 8], [32, 0], 0.5)]},
        {'name': 'right_arm', 'parent': 'body', 'pivot': [-6, 22, 0],
         'cubes': [cube([-8, 12, -2], [4, 12, 4], [40, 16]), cube([-8, 12, -2], [4, 12, 4], [40, 32], 0.25)]},
        {'name': 'left_arm', 'parent': 'body', 'pivot': [6, 22, 0],
         'cubes': [cube([4, 12, -2], [4, 12, 4], [32, 48]), cube([4, 12, -2], [4, 12, 4], [48, 48], 0.25)]},
        {'name': 'right_leg', 'parent': 'root', 'pivot': [-2, 12, 0],
         'cubes': [cube([-4, 0, -2], [4, 12, 4], [0, 16]), cube([-4, 0, -2], [4, 12, 4], [0, 32], 0.25)]},
        {'name': 'left_leg', 'parent': 'root', 'pivot': [2, 12, 0],
         'cubes': [cube([0, 0, -2], [4, 12, 4], [16, 48]), cube([0, 0, -2], [4, 12, 4], [0, 48], 0.25)]},
    ]
    model = {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {'identifier': 'geometry.vtm.npc', 'texture_width': 64, 'texture_height': 64,
                            'visible_bounds_width': 3, 'visible_bounds_height': 3.5, 'visible_bounds_offset': [0, 1.25, 0]},
            'bones': bones}]}
    with open(f'{ASSETS}/geo/entity/npc.geo.json', 'w') as f:
        json.dump(model, f, indent=2)


# --------------------------------------------------------------------------- animation

def keys(*pairs):
    return {str(t): v for t, v in pairs}


def swing(length, amp, phase, axis_index=0, steps=4):
    """A sine-ish limb swing as keyframes, rotating only around one axis."""
    out = []
    for i in range(steps + 1):
        t = round(length * i / steps, 3)
        import math
        angle = amp * math.sin(2 * math.pi * (i / steps) + phase)
        vec = [0, 0, 0]
        vec[axis_index] = round(angle, 2)
        out.append((t, vec))
    return keys(*out)


def write_animations():
    import math

    def cycle(length, leg, arm, bob, lean=0.0):
        return {
            'loop': True, 'animation_length': length,
            'bones': {
                'left_leg': {'rotation': swing(length, leg, 0)},
                'right_leg': {'rotation': swing(length, leg, math.pi)},
                'left_arm': {'rotation': swing(length, arm, math.pi)},
                'right_arm': {'rotation': swing(length, arm, 0)},
                'body': {'rotation': keys((0, [lean, 0, 0]), (length, [lean, 0, 0])),
                         'position': keys((0, [0, 0, 0]), (length * 0.25, [0, bob, 0]), (length * 0.5, [0, 0, 0]),
                                          (length * 0.75, [0, bob, 0]), (length, [0, 0, 0]))},
            }}

    anims = {
        'idle': {
            'loop': True, 'animation_length': 4.0,
            'bones': {
                'body': {'position': keys((0, [0, 0, 0]), (2, [0, 0.3, 0]), (4, [0, 0, 0]))},
                'head': {'rotation': keys((0, [0, 0, 0]), (1, [1, 3, 0]), (3, [-1, -3, 0]), (4, [0, 0, 0]))},
                'left_arm': {'rotation': keys((0, [0, 0, -2]), (2, [0, 0, -4]), (4, [0, 0, -2]))},
                'right_arm': {'rotation': keys((0, [0, 0, 2]), (2, [0, 0, 4]), (4, [0, 0, 2]))},
            }},
        'walk': cycle(1.0, 30, 24, 0.5),
        'run': cycle(0.6, 55, 60, 1.0, lean=12),
        'attack': {
            'animation_length': 0.5,
            'bones': {
                'right_arm': {'rotation': keys((0, [0, 0, 0]), (0.15, [-110, 0, 0]), (0.3, [40, 0, 0]), (0.5, [0, 0, 0]))},
                'body': {'rotation': keys((0, [0, 0, 0]), (0.15, [0, -15, 0]), (0.3, [0, 12, 0]), (0.5, [0, 0, 0]))},
            }},
        'bite': {
            'animation_length': 1.2,
            'bones': {
                'body': {'rotation': keys((0, [0, 0, 0]), (0.3, [35, 0, 0]), (0.9, [35, 0, 0]), (1.2, [0, 0, 0]))},
                'head': {'rotation': keys((0, [0, 0, 0]), (0.3, [20, 0, 0]), (0.9, [20, 0, 0]), (1.2, [0, 0, 0]))},
                'left_arm': {'rotation': keys((0, [0, 0, 0]), (0.3, [-70, 0, -15]), (0.9, [-70, 0, -15]), (1.2, [0, 0, 0]))},
                'right_arm': {'rotation': keys((0, [0, 0, 0]), (0.3, [-70, 0, 15]), (0.9, [-70, 0, 15]), (1.2, [0, 0, 0]))},
            }},
        'cower': {
            'loop': True, 'animation_length': 1.0,
            'bones': {
                'body': {'rotation': keys((0, [25, 0, 0]), (1, [25, 0, 0])),
                         'position': keys((0, [0, -2, 0]), (1, [0, -2, 0]))},
                'head': {'rotation': keys((0, [20, 0, 0]), (1, [20, 0, 0]))},
                'left_arm': {'rotation': keys((0, [-130, 0, 20]), (0.1, [-130, 4, 20]), (0.2, [-130, -4, 20]),
                                              (0.3, [-130, 4, 20]), (1, [-130, 0, 20]))},
                'right_arm': {'rotation': keys((0, [-130, 0, -20]), (0.1, [-130, -4, -20]), (0.2, [-130, 4, -20]),
                                               (0.3, [-130, -4, -20]), (1, [-130, 0, -20]))},
            }},
        'sleep': {
            'loop': True, 'animation_length': 4.0,
            'bones': {
                'root': {'rotation': keys((0, [90, 0, 0]), (4, [90, 0, 0])),
                         'position': keys((0, [0, 2, 0]), (2, [0, 2.4, 0]), (4, [0, 2, 0]))},
                'left_arm': {'rotation': keys((0, [0, 0, -4]), (4, [0, 0, -4]))},
                'right_arm': {'rotation': keys((0, [0, 0, 4]), (4, [0, 0, 4]))},
            }},
        'talk': {
            'loop': True, 'animation_length': 2.0,
            'bones': {
                'right_arm': {'rotation': keys((0, [-40, 0, 10]), (0.5, [-70, 0, 15]), (1, [-45, 0, 10]),
                                               (1.5, [-75, 0, 18]), (2, [-40, 0, 10]))},
                'head': {'rotation': keys((0, [0, 0, 0]), (0.5, [3, 4, 0]), (1, [-2, -3, 0]), (1.5, [3, 3, 0]), (2, [0, 0, 0]))},
            }},
    }
    with open(f'{ASSETS}/animations/entity/npc.animation.json', 'w') as f:
        json.dump({'format_version': '1.8.0', 'animations': anims}, f, indent=2)


# --------------------------------------------------------------------------- skins

def rgb(hexstr):
    h = hexstr.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(color, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in color[:3]) + (255,)


def fill(img, x, y, w, h, color):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            img.putpixel((xx, yy), color)


def box(img, u, v, w, h, d, color, top=None, bottom=None, front=None, back=None, side=None):
    """Fill the six faces of a vanilla-layout box."""
    faces = {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
    }
    overrides = {'top': top, 'bottom': bottom, 'front': front, 'back': back, 'right': side, 'left': side}
    for name, (x, y, fw, fh) in faces.items():
        fill(img, x, y, fw, fh, overrides[name] or color)


def make_skin(path, skin, hair, eyes, shirt, trim, pants, shoes, fangs=False, long_hair=False, seed=0):
    rng = random.Random(seed)
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    dark = shade(skin, 0.8)

    # base layer: skin everywhere it can show
    box(img, 0, 0, 8, 8, 8, skin)          # head
    box(img, 16, 16, 8, 12, 4, shirt)      # body
    box(img, 40, 16, 4, 12, 4, skin)       # right arm
    box(img, 32, 48, 4, 12, 4, skin)       # left arm
    box(img, 0, 16, 4, 12, 4, pants)       # right leg
    box(img, 16, 48, 4, 12, 4, pants)      # left leg

    # face: brows, eyes, nose shadow, mouth
    fx, fy = 8, 8
    brow = shade(hair, 0.9)
    fill(img, fx + 1, fy + 3, 2, 1, brow)
    fill(img, fx + 5, fy + 3, 2, 1, brow)
    for ex in (1, 5):
        fill(img, fx + ex, fy + 4, 2, 1, (240, 240, 240, 255))
        img.putpixel((fx + ex + (1 if ex == 1 else 0), fy + 4), eyes)
    fill(img, fx + 3, fy + 5, 2, 1, dark)
    fill(img, fx + 3, fy + 6, 2, 1, shade(skin, 0.6) if not fangs else (120, 30, 40, 255))
    if fangs:
        img.putpixel((fx + 3, fy + 7), (245, 245, 245, 255))
        img.putpixel((fx + 4, fy + 7), (245, 245, 245, 255))

    # overlay layer: hair (hat layer), jacket, sleeves, trousers
    hat_u, hat_v = 32, 0
    box(img, hat_u, hat_v, 8, 8, 8, (0, 0, 0, 0))
    fill(img, hat_u + 8, hat_v, 8, 8, hair)                      # top
    fill(img, hat_u + 8, hat_v + 8, 8, 2, hair)                  # front fringe
    for sx in (hat_u, hat_u + 16):                               # sides
        fill(img, sx, hat_v + 8, 8, 4 if not long_hair else 8, hair)
    fill(img, hat_u + 24, hat_v + 8, 8, 8 if not long_hair else 8, hair)  # back
    if long_hair:
        fill(img, hat_u + 16, hat_v + 12, 8, 4, hair)

    box(img, 16, 32, 8, 12, 4, shirt)                            # jacket over body
    fill(img, 16 + 4 + 3, 32 + 4, 2, 12, trim)                   # front placket / tie
    for (u, v) in ((40, 32), (48, 48)):                          # long sleeves, bare hands
        box(img, u, v, 4, 12, 4, (0, 0, 0, 0))
        fill(img, u + 4, v, 4, 4, shirt)                         # shoulder
        for x in (u, u + 4, u + 8, u + 12):                      # four sides, upper 9 of 12 rows
            fill(img, x, v + 4, 4, 9, shirt)
    for (u, v) in ((0, 32), (0, 48)):                            # trousers with shoes
        box(img, u, v, 4, 12, 4, (0, 0, 0, 0))
        faces = {'right': (u, v + 4, 4, 12), 'front': (u + 4, v + 4, 4, 12),
                 'left': (u + 8, v + 4, 4, 12), 'back': (u + 12, v + 4, 4, 12)}
        for (x, y, w, h) in faces.values():
            fill(img, x, y, w, 9, pants)
            fill(img, x, y + 9, w, 3, shoes)
    # a little cloth noise so flat colours don't look dead
    for _ in range(60):
        x, y = rng.randrange(64), rng.randrange(64)
        r, g, b, a = img.getpixel((x, y))
        if a:
            f = rng.uniform(0.93, 1.07)
            img.putpixel((x, y), (min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f)), a))
    img.save(path)


def write_skins():
    out = f'{ASSETS}/textures/entity/npc'
    os.makedirs(out, exist_ok=True)
    skin_tones = [(240, 200, 170), (224, 172, 140), (198, 134, 100), (150, 98, 70), (120, 78, 56), (232, 190, 160)]
    hairs = [(40, 30, 25), (90, 60, 35), (170, 130, 70), (200, 190, 170), (25, 22, 25), (120, 50, 30), (210, 170, 90), (70, 70, 75)]
    shirts = ['#8A6D3B', '#4A6FA5', '#7D8F69', '#A5584A', '#6B5B95', '#C9A66B', '#5B7B7A', '#8E8E8E']
    pants = ['#3B3B4F', '#4B3B2B', '#2F4F4F', '#5C4033', '#3E4A5B', '#46423A', '#2B2B2B', '#53473C']
    for i in range(8):
        rng = random.Random(100 + i)
        make_skin(f'{out}/civilian_{i}.png', rng.choice(skin_tones) + (255,), hairs[i] + (255,),
                  rng.choice([(70, 100, 140, 255), (90, 60, 30, 255), (60, 120, 70, 255), (50, 50, 50, 255)]),
                  rgb(shirts[i]), shade(rgb(shirts[i]), 1.25), rgb(pants[i]), (35, 30, 28, 255),
                  long_hair=(i % 3 == 0), seed=i)
    clans = {}
    for f in glob.glob(f'{CLAN_DIR}/*.json'):
        clans[os.path.basename(f)[:-5]] = json.load(open(f))['color']
    clans['default'] = '#8B0000'
    for name, color in clans.items():
        accent = rgb(color)
        for v in range(2):
            rng = random.Random(hash(name) % 1000 + v)
            hair = rng.choice([(20, 18, 22), (45, 30, 25), (200, 190, 170), (90, 20, 20), (30, 30, 45)])
            make_skin(f'{out}/vampire_{name}_{v}.png', (214, 205, 205, 255), hair + (255,), (190, 30, 40, 255),
                      shade(accent, 0.55), accent, (26, 24, 30, 255), (15, 14, 18, 255),
                      fangs=True, long_hair=(v == 1), seed=v + len(name))


if __name__ == '__main__':
    os.makedirs(f'{ASSETS}/geo/entity', exist_ok=True)
    os.makedirs(f'{ASSETS}/animations/entity', exist_ok=True)
    write_model()
    write_animations()
    write_skins()
    print('generated')
