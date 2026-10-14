#!/usr/bin/env python3
"""Turns the six flags' SVGs into the design system's vector flags: python3 tools/flags/convert.py

Reads tools/flags/svg/<CC>.svg (Wikimedia Commons' public-domain originals, see SOURCES) and writes
app/designsystem/.../flag/FlagArt.kt, one ImageVector a flag, each shape a path of its own colour in the SVG's
order. Only what these files use is understood: rect, circle, ellipse, polygon, path, g and use, presentation
attributes and CSS classes, and transforms, which are applied to the points (an arc becomes curves first), so
every path is absolute M, L, C and Z, the flag 600 units high. --preview DIR also writes each flattened flag
as an SVG, to compare with its original in a browser. Standard library only; repeatable byte for byte.
"""

import argparse
import math
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SVG_DIR = Path(__file__).resolve().parent / "svg"
OUT = ROOT / "app/designsystem/src/commonMain/kotlin/io/ntole/kvizic/design/flag/FlagArt.kt"
ORDER = ["RS", "HR", "BA", "SI", "MK", "ME"]
HEIGHT = 600.0
SOURCES = {
    "RS": "Flag_of_Serbia.svg",
    "HR": "Flag_of_Croatia.svg",
    "BA": "Flag_of_Bosnia_and_Herzegovina.svg",
    "SI": "Flag_of_Slovenia.svg",
    "MK": "Flag_of_North_Macedonia.svg",
    "ME": "Flag_of_Montenegro.svg",
}
NAMES = {
    "RS": "Serbia", "HR": "Croatia", "BA": "Bosnia and Herzegovina", "SI": "Slovenia", "MK": "North Macedonia",
    "ME": "Montenegro",
}
COLOURS = {
    "black": "000000", "white": "ffffff", "red": "ff0000", "blue": "0000ff", "yellow": "ffff00", "gold": "ffd700",
    "green": "008000", "none": None,
}
INHERITED = ("fill", "fill-rule", "fill-opacity", "stroke", "stroke-width", "stroke-linejoin", "stroke-linecap",
             "stroke-miterlimit", "stroke-opacity", "opacity")
SVG = "{http://www.w3.org/2000/svg}"
XLINK = "{http://www.w3.org/1999/xlink}href"
NUMBER = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


def local(tag):
    return tag.split("}")[-1]


def multiply(m, n):
    a, b, c, d, e, f = m
    a2, b2, c2, d2, e2, f2 = n
    return (a * a2 + c * b2, b * a2 + d * b2, a * c2 + c * d2, b * c2 + d * d2, a * e2 + c * f2 + e, b * e2 + d * f2 + f)


IDENTITY = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


def transform_of(text):
    m = IDENTITY
    for name, args in re.findall(r"(\w+)\s*\(([^)]*)\)", text or ""):
        v = [float(x) for x in NUMBER.findall(args)]
        if name == "matrix":
            t = tuple(v)
        elif name == "translate":
            t = (1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0)
        elif name == "scale":
            t = (v[0], 0, 0, v[1] if len(v) > 1 else v[0], 0, 0)
        elif name == "rotate":
            r = math.radians(v[0])
            t = (math.cos(r), math.sin(r), -math.sin(r), math.cos(r), 0, 0)
            if len(v) == 3:
                t = multiply(multiply((1, 0, 0, 1, v[1], v[2]), t), (1, 0, 0, 1, -v[1], -v[2]))
        elif name == "skewX":
            t = (1, 0, math.tan(math.radians(v[0])), 1, 0, 0)
        elif name == "skewY":
            t = (1, math.tan(math.radians(v[0])), 0, 1, 0, 0)
        else:
            raise SystemExit(f"unknown transform {name}")
        m = multiply(m, t)
    return m


def apply(m, x, y):
    a, b, c, d, e, f = m
    return a * x + c * y + e, b * x + d * y + f


def arc_to_curves(x1, y1, rx, ry, phi, large, sweep, x2, y2):
    """An SVG arc as cubic curves (the W3C's endpoint-to-centre conversion), each of at most a quarter turn."""
    if rx == 0 or ry == 0:
        return [(x1, y1, x2, y2, x2, y2)]
    rx, ry = abs(rx), abs(ry)
    p = math.radians(phi)
    cp, sp = math.cos(p), math.sin(p)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = cp * dx + sp * dy, -sp * dx + cp * dy
    lam = (x1p ** 2) / (rx ** 2) + (y1p ** 2) / (ry ** 2)
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx ** 2 * ry ** 2 - rx ** 2 * y1p ** 2 - ry ** 2 * x1p ** 2
    den = rx ** 2 * y1p ** 2 + ry ** 2 * x1p ** 2
    coef = math.sqrt(max(0, num / den)) if den else 0
    if large == sweep:
        coef = -coef
    cxp, cyp = coef * rx * y1p / ry, -coef * ry * x1p / rx
    cx = cp * cxp - sp * cyp + (x1 + x2) / 2
    cy = sp * cxp + cp * cyp + (y1 + y2) / 2

    def angle(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a

    t1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sweep and dt > 0:
        dt -= 2 * math.pi
    elif sweep and dt < 0:
        dt += 2 * math.pi
    n = max(1, math.ceil(abs(dt) / (math.pi / 2) - 1e-9))
    step = dt / n
    k = 4 / 3 * math.tan(step / 4)
    out = []
    for i in range(n):
        a1, a2 = t1 + i * step, t1 + (i + 1) * step
        e1x, e1y = math.cos(a1), math.sin(a1)
        e2x, e2y = math.cos(a2), math.sin(a2)
        pts = [(e1x - k * e1y, e1y + k * e1x), (e2x + k * e2y, e2y - k * e2x), (e2x, e2y)]
        out.append(tuple(v for px, py in pts for v in (cx + rx * px * cp - ry * py * sp, cy + rx * px * sp + ry * py * cp)))
    return out


def path_commands(d):
    """The path's commands as absolute M, L, C and Z, in its own coordinates."""
    tokens = re.findall(r"[MmLlHhVvCcSsQqTtAaZz]|" + NUMBER.pattern, d)
    out, i, cmd = [], 0, None
    x = y = sx = sy = 0.0
    last_c = last_q = None

    def num():
        nonlocal i
        v = float(tokens[i])
        i += 1
        return v

    def flag():
        # An arc's flags may be written run together ("a1 1 0 01 2 2"); NUMBER read "01" as one number.
        nonlocal i
        t = tokens[i]
        if len(t) > 1 and t[0] in "01" and t[1] in "01":
            tokens[i] = t[1:]
            return float(t[0])
        i += 1
        return float(t)

    while i < len(tokens):
        t = tokens[i]
        if re.fullmatch(r"[A-Za-z]", t):
            cmd = t
            i += 1
            if cmd in "Zz":
                out.append(("Z",))
                x, y = sx, sy
                last_c = last_q = None
                continue
        elif cmd is None:
            raise SystemExit("path data starts with a number")
        rel = cmd.islower()
        c = cmd.upper()
        ox, oy = (x, y) if rel else (0.0, 0.0)
        if c == "M":
            x, y = ox + num(), oy + num()
            sx, sy = x, y
            out.append(("M", x, y))
            cmd = "l" if rel else "L"
            last_c = last_q = None
        elif c == "L":
            x, y = ox + num(), oy + num()
            out.append(("L", x, y))
            last_c = last_q = None
        elif c == "H":
            x = ox + num()
            out.append(("L", x, y))
            last_c = last_q = None
        elif c == "V":
            y = (y if rel else 0.0) + num()
            out.append(("L", x, y))
            last_c = last_q = None
        elif c == "C":
            x1, y1, x2, y2, ex, ey = ox + num(), oy + num(), ox + num(), oy + num(), ox + num(), oy + num()
            out.append(("C", x1, y1, x2, y2, ex, ey))
            last_c, last_q, x, y = (x2, y2), None, ex, ey
        elif c == "S":
            x1, y1 = (2 * x - last_c[0], 2 * y - last_c[1]) if last_c else (x, y)
            x2, y2, ex, ey = ox + num(), oy + num(), ox + num(), oy + num()
            out.append(("C", x1, y1, x2, y2, ex, ey))
            last_c, last_q, x, y = (x2, y2), None, ex, ey
        elif c in "QT":
            if c == "Q":
                qx, qy = ox + num(), oy + num()
            else:
                qx, qy = (2 * x - last_q[0], 2 * y - last_q[1]) if last_q else (x, y)
            ex, ey = ox + num(), oy + num()
            out.append(("C", x + 2 / 3 * (qx - x), y + 2 / 3 * (qy - y), ex + 2 / 3 * (qx - ex), ey + 2 / 3 * (qy - ey), ex, ey))
            last_q, last_c, x, y = (qx, qy), None, ex, ey
        elif c == "A":
            rx, ry, phi = num(), num(), num()
            large, sweep = flag(), flag()
            ex, ey = ox + num(), oy + num()
            for seg in arc_to_curves(x, y, rx, ry, phi, large, sweep, ex, ey):
                out.append(("C",) + seg)
            x, y = ex, ey
            last_c = last_q = None
        else:
            raise SystemExit(f"unknown path command {cmd}")
    return out


def ellipse(cx, cy, rx, ry):
    k = 0.5522847498307936
    return [
        ("M", cx + rx, cy),
        ("C", cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry),
        ("C", cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy),
        ("C", cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry),
        ("C", cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy),
        ("Z",),
    ]


def shape_commands(el):
    tag = local(el.tag)
    g = lambda k, dflt=0.0: float(NUMBER.findall(el.get(k, str(dflt)))[0]) if el.get(k) else dflt  # noqa: E731
    if tag == "path":
        return path_commands(el.get("d", ""))
    if tag == "rect":
        x, y, w, h = g("x"), g("y"), g("width"), g("height")
        return [("M", x, y), ("L", x + w, y), ("L", x + w, y + h), ("L", x, y + h), ("Z",)]
    if tag == "circle":
        r = g("r")
        return ellipse(g("cx"), g("cy"), r, r)
    if tag == "ellipse":
        return ellipse(g("cx"), g("cy"), g("rx"), g("ry"))
    if tag in ("polygon", "polyline"):
        v = [float(n) for n in NUMBER.findall(el.get("points", ""))]
        pts = list(zip(v[0::2], v[1::2]))
        cmds = [("M",) + pts[0]] + [("L",) + p for p in pts[1:]]
        return cmds + ([("Z",)] if tag == "polygon" else [])
    return None


def css_classes(root):
    rules = {}
    for style in root.iter(SVG + "style"):
        for selectors, body in re.findall(r"([^{}]+)\{([^}]*)\}", style.text or ""):
            props = dict((k.strip(), v.strip()) for k, v in (p.split(":", 1) for p in body.split(";") if ":" in p))
            for sel in selectors.split(","):
                sel = sel.strip()
                if sel.startswith("."):
                    rules.setdefault(sel[1:], {}).update(props)
    return rules


def own_style(el, classes):
    style = {}
    for cls in (el.get("class") or "").split():
        style.update(classes.get(cls, {}))
    for k in INHERITED + ("display", "visibility"):
        if el.get(k) is not None:
            style[k] = el.get(k)
    for part in (el.get("style") or "").split(";"):
        if ":" in part:
            k, v = part.split(":", 1)
            style[k.strip()] = v.strip()
    return style


def colour(value, where):
    if value is None:
        return None
    value = value.strip().lower()
    if value in COLOURS:
        return COLOURS[value]
    m = re.fullmatch(r"#([0-9a-f]{3}|[0-9a-f]{6})", value)
    if not m:
        raise SystemExit(f"{where}: unknown colour {value!r}")
    h = m.group(1)
    return "".join(c * 2 for c in h) if len(h) == 3 else h


def flatten(path):
    root = ET.parse(path).getroot()
    classes = css_classes(root)
    ids = {el.get("id"): el for el in root.iter() if el.get("id")}
    vb = root.get("viewBox")
    if vb:
        minx, miny, width, height = (float(v) for v in NUMBER.findall(vb))
    else:
        minx = miny = 0.0
        width, height = float(NUMBER.findall(root.get("width"))[0]), float(NUMBER.findall(root.get("height"))[0])
    # Every flag drawn 600 units high, so a whole unit is as fine on each (Bosnia's own is 8 high).
    k = HEIGHT / height
    base = (k, 0.0, 0.0, k, -minx * k, -miny * k)
    width, height = width * k, HEIGHT
    shapes = []

    def walk(el, m, style):
        tag = local(el.tag)
        if tag in ("defs", "symbol", "style", "title", "desc", "metadata", "clipPath", "mask"):
            return
        s = dict(style)
        s.update(own_style(el, classes))
        if s.get("display") == "none" or s.get("visibility") == "hidden":
            return
        m = multiply(m, transform_of(el.get("transform")))
        if tag == "use":
            ref = ids.get((el.get(XLINK) or el.get("href") or "").lstrip("#"))
            if ref is None:
                raise SystemExit(f"{path.name}: a use names no element")
            m2 = multiply(m, (1, 0, 0, 1, float(el.get("x", 0)), float(el.get("y", 0))))
            walk(ref, m2, s)
            return
        if tag in ("svg", "g", "a"):
            for child in el:
                walk(child, m, s)
            return
        cmds = shape_commands(el)
        if cmds is None:
            raise SystemExit(f"{path.name}: unknown element {tag}")
        fill = colour(s.get("fill", "#000"), path.name)
        stroke = colour(s.get("stroke"), path.name)
        opacity = float(s.get("opacity", 1))
        scale = math.sqrt(abs(m[0] * m[3] - m[1] * m[2]))
        pts = []
        for c in cmds:
            if c[0] == "Z":
                pts.append(c)
            else:
                xy = c[1:]
                pts.append((c[0],) + tuple(v for i in range(0, len(xy), 2) for v in apply(m, xy[i], xy[i + 1])))
        shapes.append(dict(
            cmds=pts,
            fill=fill,
            fill_alpha=float(s.get("fill-opacity", 1)) * opacity,
            even_odd=s.get("fill-rule") == "evenodd",
            stroke=stroke,
            stroke_alpha=float(s.get("stroke-opacity", 1)) * opacity,
            stroke_width=float(NUMBER.findall(s.get("stroke-width", "1"))[0]) * scale,
            join=s.get("stroke-linejoin", "miter"),
            cap=s.get("stroke-linecap", "butt"),
            miter=float(s.get("stroke-miterlimit", 4)),
        ))

    walk(root, base, {})
    return width, height, shapes


def visible(shapes, height, least):
    """The shapes whose box is at least [least] of the flag's height across: a dot of the arms' crown smaller than
    that is under a pixel at any size the game shows a flag at."""
    kept = []
    for s in shapes:
        xs = [v for c in s["cmds"] if c[0] != "Z" for v in c[1::2]]
        ys = [v for c in s["cmds"] if c[0] != "Z" for v in c[2::2]]
        if max(max(xs) - min(xs), max(ys) - min(ys)) >= least * height:
            kept.append(s)
    return kept


def fmt(v, digits):
    s = f"{v:.{digits}f}"
    if "." in s:
        s = s.rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def path_string(cmds, digits):
    parts = []
    for c in cmds:
        parts.append(c[0] + (" ".join(fmt(v, digits) for v in c[1:])))
    return "".join(parts)


def argb(hex6, alpha):
    return f"0x{round(alpha * 255):02X}{hex6.upper()}"


def kotlin(flags, digits):
    lines = [
        "// Generated by tools/flags/convert.py from Wikimedia Commons' public-domain flags; do not edit by hand.",
        '@file:Suppress("ktlint")',
        "",
        "package io.ntole.kvizic.design.flag",
        "",
        "import androidx.compose.ui.graphics.Color",
        "import androidx.compose.ui.graphics.PathFillType",
        "import androidx.compose.ui.graphics.SolidColor",
        "import androidx.compose.ui.graphics.StrokeCap",
        "import androidx.compose.ui.graphics.StrokeJoin",
        "import androidx.compose.ui.graphics.vector.ImageVector",
        "import androidx.compose.ui.graphics.vector.addPathNodes",
        "import androidx.compose.ui.unit.dp",
        "",
        "/**",
        " * The six countries' flags, each as its country flies it, in its own proportions: Serbia's 3:2, the rest 2:1.",
        " * Their colours are the flags' own, never a skin's. [of] finds one by its ISO code.",
        " */",
        "object FlagArt {",
        "    /** The flag of the country of ISO 3166 [code] (RS, HR, BA, SI, MK, ME), or null for another. */",
        "    fun of(code: String): ImageVector? =",
        "        when (code) {",
    ]
    lines += [f'            "{cc}" -> {cc}' for cc in ORDER]
    lines += ["            else -> null", "        }", ""]
    for cc in ORDER:
        width, height, shapes = flags[cc]
        lines.append(f"    /** {NAMES[cc]}'s flag. */")
        lines.append(f"    val {cc}: ImageVector by lazy {{")
        lines.append(f'        flag("{cc}", {fmt(width, 3)}f, {fmt(height, 3)}f) {{')
        for s in shapes:
            d = path_string(s["cmds"], digits)
            if s["fill"] is not None and s["fill_alpha"] > 0:
                eo = ", evenOdd = true" if s["even_odd"] else ""
                lines.append(f'            fill({argb(s["fill"], s["fill_alpha"])}, "{d}"{eo})')
            if s["stroke"] is not None and s["stroke_alpha"] > 0 and s["stroke_width"] > 0:
                lines.append(
                    f'            stroke({argb(s["stroke"], s["stroke_alpha"])}, {fmt(s["stroke_width"], 3)}f, '
                    f'StrokeJoin.{s["join"].capitalize()}, StrokeCap.{s["cap"].capitalize()}, '
                    f'{fmt(s["miter"], 3)}f, "{d}")'
                )
        lines.append("        }")
        lines.append("    }")
        lines.append("")
    lines += [
        "    /** A flag [width] by [height] in its own units, 16 dp high by default. */",
        "    private fun flag(",
        "        name: String,",
        "        width: Float,",
        "        height: Float,",
        "        draw: ImageVector.Builder.() -> Unit,",
        "    ): ImageVector =",
        "        ImageVector",
        "            .Builder(",
        '                name = "Flag$name",',
        "                defaultWidth = (DEFAULT_HEIGHT * width / height).dp,",
        "                defaultHeight = DEFAULT_HEIGHT.dp,",
        "                viewportWidth = width,",
        "                viewportHeight = height,",
        "            ).apply(draw)",
        "            .build()",
        "",
        "    private fun ImageVector.Builder.fill(",
        "        argb: Long,",
        "        path: String,",
        "        evenOdd: Boolean = false,",
        "    ) {",
        "        addPath(",
        "            pathData = addPathNodes(path),",
        "            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,",
        "            fill = SolidColor(Color(argb)),",
        "        )",
        "    }",
        "",
        "    private fun ImageVector.Builder.stroke(",
        "        argb: Long,",
        "        width: Float,",
        "        join: StrokeJoin,",
        "        cap: StrokeCap,",
        "        miter: Float,",
        "        path: String,",
        "    ) {",
        "        addPath(",
        "            pathData = addPathNodes(path),",
        "            stroke = SolidColor(Color(argb)),",
        "            strokeLineWidth = width,",
        "            strokeLineJoin = join,",
        "            strokeLineCap = cap,",
        "            strokeLineMiter = miter,",
        "        )",
        "    }",
        "",
        "    private const val DEFAULT_HEIGHT = 16f",
        "}",
        "",
    ]
    return "\n".join(lines)


def preview(flags, out, digits):
    out.mkdir(parents=True, exist_ok=True)
    for cc, (width, height, shapes) in flags.items():
        body = []
        for s in shapes:
            d = path_string(s["cmds"], digits)
            if s["fill"] is not None and s["fill_alpha"] > 0:
                rule = ' fill-rule="evenodd"' if s["even_odd"] else ""
                body.append(f'<path d="{d}" fill="#{s["fill"]}" fill-opacity="{s["fill_alpha"]}"{rule}/>')
            if s["stroke"] is not None and s["stroke_alpha"] > 0 and s["stroke_width"] > 0:
                body.append(f'<path d="{d}" fill="none" stroke="#{s["stroke"]}" stroke-width="{s["stroke_width"]}" '
                            f'stroke-linejoin="{s["join"]}" stroke-linecap="{s["cap"]}" '
                            f'stroke-miterlimit="{s["miter"]}"/>')
        (out / f"{cc}.svg").write_text(
            f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" '
            f'height="{height}">{"".join(body)}</svg>\n', encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--svg-dir", type=Path, default=SVG_DIR)
    parser.add_argument("--out", type=Path, default=OUT)
    parser.add_argument("--preview", type=Path, help="also write each flattened flag as an SVG here")
    parser.add_argument("--digits", type=int, default=0, help="decimals kept of a coordinate")
    parser.add_argument("--least", type=float, default=0.004, help="the smallest shape kept, a share of the height")
    args = parser.parse_args()
    flags = {}
    for cc in ORDER:
        width, height, shapes = flatten(args.svg_dir / f"{cc}.svg")
        flags[cc] = (width, height, visible(shapes, height, args.least))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(kotlin(flags, args.digits), encoding="utf-8")
    if args.preview:
        preview(flags, args.preview, args.digits)
    for cc in ORDER:
        print(f"{cc}: {len(flags[cc][2])} shapes")
    print(f"wrote {args.out.relative_to(ROOT)} ({args.out.stat().st_size // 1024} KiB)")


if __name__ == "__main__":
    sys.exit(main())
