#!/usr/bin/env python3
"""Builds `app/src/main/assets/countries.bin` from Natural Earth admin-0 country polygons.

Source: Natural Earth 1:50m admin-0 countries (public domain, naturalearthdata.com),
read from the official `nvkelso/natural-earth-vector` GitHub repository as GeoJSON.
Downloads the GeoJSON unless `--input` points at an already-downloaded copy.

Each country polygon is simplified with Douglas-Peucker in a locally metric-scaled
space so the tolerance can be given in kilometres; coordinates are then quantized to
micro-degrees (1e-5, about 1.1 m at the equator) and written as a compact binary file.
See `decodeCountryShapes` in `app/src/main/java/app/restvolt/camperlog/domain/CountryShapes.kt`
for the exact binary layout.

Country codes: ISO_A2, falling back to ISO_A2_EH for Natural Earth's "-99" placeholder
(covers France, Norway, Kosovo); features where both are "-99" or missing are skipped.
Multiple features sharing a code (e.g. outlying territories mapped to their sovereign
state) contribute their rings to the same country.

Usage:
    python3 scripts/build-country-shapes.py [--input FILE] [--output FILE] [--tolerance-km KM]
"""

from __future__ import annotations

import argparse
import json
import math
import struct
import sys
import urllib.request
from pathlib import Path

DEFAULT_URL = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson"
DEFAULT_OUTPUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "countries.bin"
DEFAULT_TOLERANCE_KM = 0.8
KM_PER_DEGREE = 111.32
MAGIC = b"CLC1"
COORD_SCALE = 100_000  # degrees -> micro-degrees (1e-5 deg, ~1.1 m at the equator)


def iso_a2(properties: dict) -> str | None:
    """Country code for a Natural Earth feature, with the "-99" fallback described in the module docstring.
    Codes that are not two ASCII letters (e.g. Taiwan's "CN-TW") are treated as unmapped, like "-99"."""
    for key in ("ISO_A2", "ISO_A2_EH"):
        value = properties.get(key)
        if value and value != "-99" and len(value) == 2 and value.isalpha() and value.isascii():
            return value.upper()
    return None


def feature_rings(geometry: dict) -> list[list[tuple[float, float]]]:
    """All linear rings of a Polygon or MultiPolygon geometry, as (lon, lat) point lists."""
    if geometry["type"] == "Polygon":
        polygons = [geometry["coordinates"]]
    elif geometry["type"] == "MultiPolygon":
        polygons = geometry["coordinates"]
    else:
        return []
    return [[(float(lon), float(lat)) for lon, lat in ring] for polygon in polygons for ring in polygon]


def simplify_ring(points: list[tuple[float, float]], tolerance_km: float) -> list[tuple[float, float]]:
    """Douglas-Peucker simplification of a closed ring; perpendicular distance is measured in
    kilometres using an equirectangular projection scaled to the ring's mean latitude, so
    `tolerance_km` has a consistent real-world meaning at any latitude."""
    if len(points) <= 3:
        return points
    mean_lat = sum(p[1] for p in points) / len(points)
    scale_x = KM_PER_DEGREE * math.cos(math.radians(mean_lat))
    scale_y = KM_PER_DEGREE

    keep = bytearray(len(points))
    keep[0] = 1
    keep[-1] = 1
    stack = [(0, len(points) - 1)]
    while stack:
        start, end = stack.pop()
        if end - start < 2:
            continue
        ax, ay = points[start][0] * scale_x, points[start][1] * scale_y
        bx, by = points[end][0] * scale_x, points[end][1] * scale_y
        dx, dy = bx - ax, by - ay
        line_len = math.hypot(dx, dy)
        best_dist = -1.0
        best_index = -1
        for i in range(start + 1, end):
            px, py = points[i][0] * scale_x, points[i][1] * scale_y
            if line_len == 0:
                dist = math.hypot(px - ax, py - ay)
            else:
                dist = abs((px - ax) * dy - (py - ay) * dx) / line_len
            if dist > best_dist:
                best_dist = dist
                best_index = i
        if best_dist > tolerance_km:
            keep[best_index] = 1
            stack.append((start, best_index))
            stack.append((best_index, end))
    return [p for p, k in zip(points, keep) if k]


def quantize(value: float) -> int:
    return round(value * COORD_SCALE)


def build_shapes(features: list[dict], tolerance_km: float) -> dict[str, list[list[tuple[float, float]]]]:
    """Maps country code to its simplified rings, merging rings of features sharing a code."""
    shapes: dict[str, list[list[tuple[float, float]]]] = {}
    for feature in features:
        code = iso_a2(feature["properties"])
        if code is None:
            continue
        rings = [simplify_ring(ring, tolerance_km) for ring in feature_rings(feature["geometry"])]
        rings = [ring for ring in rings if len(ring) >= 3]
        if rings:
            shapes.setdefault(code, []).extend(rings)
    return shapes


def encode(shapes: dict[str, list[list[tuple[float, float]]]]) -> bytes:
    out = bytearray()
    out += MAGIC
    codes = sorted(shapes)
    out += struct.pack(">H", len(codes))
    for code in codes:
        rings = shapes[code]
        out += code.encode("ascii")
        out += struct.pack(">H", len(rings))
        for ring in rings:
            out += struct.pack(">H", len(ring))
            for lon, lat in ring:
                out += struct.pack(">ii", quantize(lat), quantize(lon))
    return bytes(out)


def load_geojson(input_path: Path | None, url: str) -> dict:
    if input_path is not None:
        return json.loads(input_path.read_text(encoding="utf-8"))
    with urllib.request.urlopen(url, timeout=120) as response:  # noqa: S310 - fixed, trusted Natural Earth URL
        return json.loads(response.read().decode("utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", type=Path, help="local GeoJSON file, instead of downloading it")
    parser.add_argument("--url", default=DEFAULT_URL, help="GeoJSON URL used when --input is not given")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="destination for countries.bin")
    parser.add_argument("--tolerance-km", type=float, default=DEFAULT_TOLERANCE_KM, help="Douglas-Peucker tolerance in km")
    args = parser.parse_args()

    geojson = load_geojson(args.input, args.url)
    shapes = build_shapes(geojson["features"], args.tolerance_km)
    data = encode(shapes)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(data)
    print(f"{len(shapes)} countries, {sum(len(r) for r in shapes.values())} rings, {len(data) / 1024:.1f} KiB -> {args.output}")


if __name__ == "__main__":
    sys.exit(main())
