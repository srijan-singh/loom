"""
Pure-Python icon generator — no external deps.
Draws the Loom 3×3 weave mark as a PNG at each required macOS app-icon size.
"""
import struct, zlib, math, os

# ── colours ────────────────────────────────────────────────────────────────
BG   = (0xE8, 0xEA, 0xF8, 0xFF)   # #E8EAF8  accent-tint (light lavender)
FG   = (0x38, 0x46, 0xB0, 0xFF)   # #3846B0  accent (deep indigo)

# ── PNG writer ──────────────────────────────────────────────────────────────
def _chunk(tag: bytes, data: bytes) -> bytes:
    c = struct.pack('>I', len(data)) + tag + data
    return c + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)

def write_png(path: str, pixels, size: int):
    rows = []
    for row in pixels:
        raw = bytearray()
        for r, g, b, a in row:
            raw += bytes([r, g, b, a])
        rows.append(b'\x00' + bytes(raw))
    img_data = zlib.compress(b''.join(rows), 9)
    ihdr = struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0)
    png = (
        b'\x89PNG\r\n\x1a\n'
        + _chunk(b'IHDR', ihdr)
        + _chunk(b'IDAT', img_data)
        + _chunk(b'IEND', b'')
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)

# ── geometry helpers ─────────────────────────────────────────────────────────
def blend(src, dst):
    sa = src[3] / 255.0
    da = dst[3] / 255.0
    oa = sa + da * (1 - sa)
    if oa == 0:
        return (0, 0, 0, 0)
    r = int((src[0]*sa + dst[0]*da*(1-sa)) / oa)
    g = int((src[1]*sa + dst[1]*da*(1-sa)) / oa)
    b = int((src[2]*sa + dst[2]*da*(1-sa)) / oa)
    return (r, g, b, int(oa * 255))

def rounded_rect_coverage(px, py, x0, y0, x1, y1, r, aa=4):
    step = 1.0 / aa
    offset = step / 2
    hits = 0
    for sy in range(aa):
        for sx in range(aa):
            cx = px + offset + sx * step
            cy = py + offset + sy * step
            nearest_x = max(x0 + r, min(cx, x1 - r))
            nearest_y = max(y0 + r, min(cy, y1 - r))
            dx = cx - nearest_x
            dy = cy - nearest_y
            if dx * dx + dy * dy <= r * r:
                hits += 1
    return hits / (aa * aa)

def draw_rounded_rect(pixels, size, x0, y0, x1, y1, r, colour, aa=4):
    ix0 = max(0, math.floor(x0))
    ix1 = min(size - 1, math.ceil(x1))
    iy0 = max(0, math.floor(y0))
    iy1 = min(size - 1, math.ceil(y1))
    cr, cg, cb, ca = colour
    for py in range(iy0, iy1 + 1):
        for px in range(ix0, ix1 + 1):
            cov = rounded_rect_coverage(px, py, x0, y0, x1, y1, r, aa)
            if cov <= 0:
                continue
            src = (cr, cg, cb, int(ca * cov))
            pixels[py][px] = blend(src, pixels[py][px])

# ── icon painter ─────────────────────────────────────────────────────────────
def paint_icon(size: int) -> list:
    """
    Design matches the CSS .mark component:
      container: 56×56px  border-radius:14px  bg: #E8EAF8
      svg mark:  32×32px  (centred inside)    fill: #3846B0

    Padding ratio: (56-32)/2 / 56 = 12/56 ≈ 21.43% each side
    Mark ratio:    32/56 ≈ 57.14% of canvas

    SVG viewBox is 0-24. The mark is scaled to fill the inner 57.14% region.
    Corner radius of bg: 14/56 = 25% of canvas size.
    """
    s = size

    # padding: 12/56 of canvas on each side
    pad = s * (12 / 56)
    mark_size = s - 2 * pad          # 32/56 * s

    # SVG viewBox is 24 units; scale to mark_size pixels
    svg_scale = mark_size / 24.0

    # offset so the mark is centred
    ox = pad
    oy = pad

    # blank transparent canvas
    pixels = [[(0, 0, 0, 0)] * s for _ in range(s)]

    # background rounded rect — corner radius matches CSS r-control = 14/56 * s
    bg_r = s * (14 / 56)
    draw_rounded_rect(pixels, s, 0, 0, s - 1, s - 1, bg_r, BG)

    # SVG rect corner radius: rx=1 in 24-unit space, scaled to canvas
    rx = 1.0 * svg_scale

    # SVG rects: (x, y, width, height) → translated to canvas coords
    svg_rects = [
        (4.5, 3.5, 3, 5),    # top-left  vertical
        (9.5, 4.5, 5, 3),    # top-centre horizontal
        (16.5, 3.5, 3, 5),   # top-right vertical
        (3.5, 10.5, 5, 3),   # mid-left  horizontal
        (10.5, 9.5, 3, 5),   # centre    vertical
        (15.5, 10.5, 5, 3),  # mid-right horizontal
        (4.5, 15.5, 3, 5),   # bot-left  vertical
        (9.5, 16.5, 5, 3),   # bot-centre horizontal
        (16.5, 15.5, 3, 5),  # bot-right vertical
    ]

    aa = 4 if size >= 64 else 2
    for (x, y, w, h) in svg_rects:
        x0 = ox + x * svg_scale
        y0 = oy + y * svg_scale
        x1 = x0 + w * svg_scale
        y1 = y0 + h * svg_scale
        draw_rounded_rect(pixels, s, x0, y0, x1, y1, rx, FG, aa)

    return pixels


# ── main ─────────────────────────────────────────────────────────────────────
DEFAULT_PATH = '../macos/Runner/Assets.xcassets/AppIcon.appiconset' # mac
SIZES = [16, 32, 64, 128, 256, 512, 1024]
OUT_DIR = os.path.join(os.path.dirname(__file__), DEFAULT_PATH)

for sz in SIZES:
    pixels = paint_icon(sz)
    path = os.path.join(OUT_DIR, f'app_icon_{sz}.png')
    write_png(path, pixels, sz)
    print(f'  wrote {sz}×{sz}  →  {path}')

print('Done.')
