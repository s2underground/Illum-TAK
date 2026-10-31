"""Rasterize timezone-boundary-builder (ODbL) into Illum's compact tz map.

Usage: python3 build_tzmap.py combined-with-oceans.json 0.025 tzmap.bin
Source: https://github.com/evansiroky/timezone-boundary-builder/releases
(timezones-with-oceans.geojson.zip). Data (c) OpenStreetMap contributors,
ODbL 1.0; the generated tzmap.bin is likewise made available under ODbL 1.0.

Format (big-endian), deflated:
  magic 'ITZ1', u16 cols, u16 rows, f32 res_deg, u16 nZones,
  nZones x (u8 len, utf8 tzid),
  rows x (u16 nRuns, nRuns x (u16 startCol, u16 zone))
Row 0 is the band from 90N down; col 0 starts at 180W. Zone 0xFFFF = none.
"""
import json, sys, zlib, struct, numpy as np
from PIL import Image, ImageDraw

src, res, out = sys.argv[1], float(sys.argv[2]), sys.argv[3]
feats = json.load(open(src))['features']
ids = sorted({f['properties']['tzid'] for f in feats})
idx = {t: i for i, t in enumerate(ids)}
W, H = int(round(360 / res)), int(round(180 / res))
grid = np.full((H, W), 0xFFFF, dtype=np.uint16)

def rings(g):
    return g['coordinates'] if g['type'] == 'MultiPolygon' else [g['coordinates']]

# Draw order: ocean zones, then Asia/Urumqi (it overlaps Asia/Shanghai; China's
# official time is Shanghai, so Shanghai is drawn over it), then all other land.
def order(f):
    t = f['properties']['tzid']
    return 0 if t.startswith('Etc/') else 1 if t == 'Asia/Urumqi' else 2
feats.sort(key=order)
for f in feats:
    v = idx[f['properties']['tzid']]
    for poly in rings(f['geometry']):
        ext = np.asarray(poly[0], dtype=float)
        x = (ext[:, 0] + 180) / res
        y = (90 - ext[:, 1]) / res
        x0, x1 = max(0, int(np.floor(x.min())) - 1), min(W, int(np.ceil(x.max())) + 1)
        y0, y1 = max(0, int(np.floor(y.min())) - 1), min(H, int(np.ceil(y.max())) + 1)
        if x1 <= x0 or y1 <= y0:
            continue
        m = Image.new('L', (x1 - x0, y1 - y0), 0)
        d = ImageDraw.Draw(m)
        # pixel centers: PIL treats integer coords as pixel centers, shift by 0.5
        d.polygon([(a - x0 - 0.5, b - y0 - 0.5) for a, b in zip(x, y)], fill=1)
        for hole in poly[1:]:
            h = np.asarray(hole, dtype=float)
            d.polygon([((a + 180) / res - x0 - 0.5, (90 - b) / res - y0 - 0.5)
                       for a, b in h], fill=0)
        mk = np.asarray(m, dtype=bool)
        grid[y0:y1, x0:x1][mk] = v

blob = bytearray(b'ITZ1') + struct.pack('>HHfH', W, H, res, len(ids))
for t in ids:
    b = t.encode()
    blob += struct.pack('>B', len(b)) + b
runs = 0
for r in range(H):
    row = grid[r]
    starts = np.concatenate(([0], np.flatnonzero(np.diff(row)) + 1))
    runs += len(starts)
    blob += struct.pack('>H', len(starts))
    for s in starts:
        blob += struct.pack('>HH', int(s), int(row[s]))
z = zlib.compress(bytes(blob), 9)
open(out, 'wb').write(z)
if len(sys.argv) > 4:
    np.save(sys.argv[4], grid)
print(f"res {res} grid {W}x{H} zones {len(ids)} runs {runs} file {len(z)/1e3:.0f} KB "
      f"unassigned {(grid == 0xFFFF).mean()*100:.4f}%")
