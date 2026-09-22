#!/usr/bin/env python3
"""Regenerates the light and dark colour schemes in ui/.../theme/CatsRadarColors.kt.

Run from the repository root:  python3 tools/make-palette.py

Prints both schemes as Kotlin to paste over the two values in CatsRadarColors.kt. Needs nothing
beyond the standard library.

Material 3 tonal palettes, with tone as CIELAB L* and hue/chroma in CIELAB LCh. HCT's tone is exactly
L*; its hue and chroma are CAM16, which CIELAB LCh only approximates. A colour outside sRGB loses
chroma until it fits. Refuses to print a scheme whose text falls under WCAG AA on its surface —
CatsRadarColorsTest checks the same thing again on the Kotlin side.
"""

import math

SEED = "#4CAF93"   # the launcher icon's teal
CORAL = "#E0724A"  # the tertiary accent
MIN_TEXT_CONTRAST = 4.5
WX, WY, WZ = 0.95047, 1.0, 1.08883


def srgb_to_lin(c):
    c /= 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def lin_to_srgb(c):
    c = 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055
    return c * 255


def f(t):
    return t ** (1 / 3) if t > (6 / 29) ** 3 else t / (3 * (6 / 29) ** 2) + 4 / 29


def finv(t):
    return t ** 3 if t > 6 / 29 else 3 * (6 / 29) ** 2 * (t - 4 / 29)


def hex_to_lch(h):
    r, g, b = (srgb_to_lin(int(h[i:i + 2], 16)) for i in (1, 3, 5))
    x = 0.4124 * r + 0.3576 * g + 0.1805 * b
    y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    z = 0.0193 * r + 0.1192 * g + 0.9505 * b
    lightness = 116 * f(y / WY) - 16
    a = 500 * (f(x / WX) - f(y / WY))
    bb = 200 * (f(y / WY) - f(z / WZ))
    return lightness, math.hypot(a, bb), math.degrees(math.atan2(bb, a)) % 360


def lch_to_rgb(lightness, chroma, hue):
    a, b = chroma * math.cos(math.radians(hue)), chroma * math.sin(math.radians(hue))
    fy = (lightness + 16) / 116
    x, y, z = WX * finv(fy + a / 500), WY * finv(fy), WZ * finv(fy - b / 200)
    r = 3.2406 * x - 1.5372 * y - 0.4986 * z
    g = -0.9689 * x + 1.8758 * y + 0.0415 * z
    bl = 0.0557 * x - 0.2040 * y + 1.0570 * z
    return [lin_to_srgb(v) if v >= 0 else -1 for v in (r, g, bl)]


def tone(hue, chroma, t):
    """The most saturated in-gamut colour at L* = t and this hue, with chroma at most `chroma`."""
    # HCT compresses chroma near white; CIELAB does not, so it is capped by hand at the light end.
    if t >= 85:
        chroma = min(chroma, 26)
    lo, hi = 0.0, chroma
    best = lch_to_rgb(t, 0, hue)
    for _ in range(40):
        mid = (lo + hi) / 2
        rgb = lch_to_rgb(t, mid, hue)
        if all(0 <= v <= 255 for v in rgb):
            best, lo = rgb, mid
        else:
            hi = mid
    return "#%02X%02X%02X" % tuple(round(min(max(v, 0), 255)) for v in best)


def luminance(hx):
    r, g, b = (srgb_to_lin(int(hx[i:i + 2], 16)) for i in (1, 3, 5))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    lighter, darker = sorted((luminance(a), luminance(b)), reverse=True)
    return (lighter + 0.05) / (darker + 0.05)


_, SEED_CHROMA, SEED_HUE = hex_to_lch(SEED)
PALETTES = {
    "p": (SEED_HUE, max(SEED_CHROMA, 48)),
    "s": (SEED_HUE, 16),
    "t": (hex_to_lch(CORAL)[2], 56),
    "n": (SEED_HUE, 5),
    "nv": (SEED_HUE, 9),
    "e": (hex_to_lch("#B3261E")[2], 70),
}

# Fixed roles hold the same tone in both themes.
FIXED = {
    "primaryFixed": ("p", 90), "primaryFixedDim": ("p", 80),
    "onPrimaryFixed": ("p", 10), "onPrimaryFixedVariant": ("p", 30),
    "secondaryFixed": ("s", 90), "secondaryFixedDim": ("s", 80),
    "onSecondaryFixed": ("s", 10), "onSecondaryFixedVariant": ("s", 30),
    "tertiaryFixed": ("t", 90), "tertiaryFixedDim": ("t", 80),
    "onTertiaryFixed": ("t", 10), "onTertiaryFixedVariant": ("t", 30),
}

LIGHT = {
    "primary": ("p", 40), "onPrimary": ("p", 100), "primaryContainer": ("p", 90),
    "onPrimaryContainer": ("p", 20), "inversePrimary": ("p", 80),
    "secondary": ("s", 40), "onSecondary": ("s", 100), "secondaryContainer": ("s", 90),
    "onSecondaryContainer": ("s", 20),
    "tertiary": ("t", 45), "onTertiary": ("t", 100), "tertiaryContainer": ("t", 90),
    "onTertiaryContainer": ("t", 20),
    "error": ("e", 40), "onError": ("e", 100), "errorContainer": ("e", 90), "onErrorContainer": ("e", 20),
    "background": ("n", 98), "onBackground": ("n", 10), "surface": ("n", 98), "onSurface": ("n", 10),
    "surfaceVariant": ("nv", 90), "onSurfaceVariant": ("nv", 30), "outline": ("nv", 50),
    "outlineVariant": ("nv", 80), "inverseSurface": ("n", 20), "inverseOnSurface": ("n", 95),
    "surfaceTint": ("p", 40), "surfaceBright": ("n", 98), "surfaceDim": ("n", 87),
    "surfaceContainerLowest": ("n", 100), "surfaceContainerLow": ("n", 96), "surfaceContainer": ("n", 94),
    "surfaceContainerHigh": ("n", 92), "surfaceContainerHighest": ("n", 90), "scrim": ("n", 0),
    **FIXED,
}

DARK = {
    "primary": ("p", 80), "onPrimary": ("p", 20), "primaryContainer": ("p", 30),
    "onPrimaryContainer": ("p", 90), "inversePrimary": ("p", 40),
    "secondary": ("s", 80), "onSecondary": ("s", 20), "secondaryContainer": ("s", 30),
    "onSecondaryContainer": ("s", 90),
    "tertiary": ("t", 80), "onTertiary": ("t", 20), "tertiaryContainer": ("t", 30),
    "onTertiaryContainer": ("t", 90),
    "error": ("e", 80), "onError": ("e", 20), "errorContainer": ("e", 30), "onErrorContainer": ("e", 90),
    "background": ("n", 6), "onBackground": ("n", 90), "surface": ("n", 6), "onSurface": ("n", 90),
    "surfaceVariant": ("nv", 30), "onSurfaceVariant": ("nv", 80), "outline": ("nv", 60),
    "outlineVariant": ("nv", 30), "inverseSurface": ("n", 90), "inverseOnSurface": ("n", 20),
    "surfaceTint": ("p", 80), "surfaceBright": ("n", 24), "surfaceDim": ("n", 6),
    "surfaceContainerLowest": ("n", 4), "surfaceContainerLow": ("n", 10), "surfaceContainer": ("n", 12),
    "surfaceContainerHigh": ("n", 17), "surfaceContainerHighest": ("n", 22), "scrim": ("n", 0),
    **FIXED,
}

TEXT_ON_SURFACE = [
    ("onPrimary", "primary"), ("onPrimaryContainer", "primaryContainer"),
    ("onSecondary", "secondary"), ("onSecondaryContainer", "secondaryContainer"),
    ("onTertiary", "tertiary"), ("onTertiaryContainer", "tertiaryContainer"),
    ("onError", "error"), ("onErrorContainer", "errorContainer"),
    ("onSurface", "surface"), ("onSurfaceVariant", "surface"),
    ("onSurface", "surfaceContainerHighest"), ("onSurfaceVariant", "surfaceContainerHighest"),
    ("inverseOnSurface", "inverseSurface"), ("secondary", "surfaceContainer"),
    ("primary", "primaryContainer"),
    ("onPrimaryFixed", "primaryFixed"), ("onSecondaryFixed", "secondaryFixed"),
    ("onTertiaryFixed", "tertiaryFixed"),
]


def build(spec):
    return {role: tone(*PALETTES[palette], t) for role, (palette, t) in spec.items()}


if __name__ == "__main__":
    for name, spec in (("Light", LIGHT), ("Dark", DARK)):
        scheme = build(spec)
        worst = min((contrast(scheme[a], scheme[b]), a, b) for a, b in TEXT_ON_SURFACE)
        if worst[0] < MIN_TEXT_CONTRAST:
            raise SystemExit(f"{name}: {worst[1]} on {worst[2]} is {worst[0]:.2f}:1")
        print(f"val CatsRadar{name}Colors: ColorScheme = {name.lower()}ColorScheme(")
        for role, value in scheme.items():
            print(f"    {role} = Color(0xFF{value[1:]}),")
        print(")")
