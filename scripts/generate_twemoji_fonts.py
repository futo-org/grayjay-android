#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = [
#   "fonttools==4.67.0",
#   "uharfbuzz==0.56.3",
#   "nanoemoji",
#   # nanoemoji's CBDT build rasterizes with it.
#   "resvg-cli",
#   # nanoemoji needs these but doesn't declare them.
#   "ninja",
#   "typing_extensions",
# ]
# ///
"""
Generate the Twemoji fonts for emoji fingerprints used in pairing, trimmed to the emojis in
HARBOR_FINGERPRINT_POOL.

Use `uv run scripts/generate_twemoji_fonts.py`. The build may take a while, even up to an hour on
slower machines.

Writes:
- `res/font/twemoji_fingerprint.ttf`: COLRv0 (vector), for Android 10+.
- `res/font/twemoji_fingerprint_cbdt.ttf`: CBDT (bitmap), for Android 9, which draws COLR glyphs
  blank.
- `TwemojiFonts.kt`: the space around each glyph in both fonts.
"""

import io
import os
import re
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from pathlib import Path

import uharfbuzz as hb
from fontTools import subset
from fontTools.feaLib.builder import addOpenTypeFeaturesFromString
from fontTools.otlLib.builder import LOOKUP_FLAG_IGNORE_MARKS
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont
from fontTools.ttLib.tables._c_m_a_p import CmapSubtable

TWEMOJI_VERSION = "17.0.3"
TWEMOJI_URL = f"https://github.com/jdecked/twemoji/archive/refs/tags/v{TWEMOJI_VERSION}.tar.gz"
TWEMOJI_RELEASE_TIME = 1780352164
EMOJI_VARIATION_SEQUENCES_URL = "https://www.unicode.org/Public/17.0.0/ucd/emoji/emoji-variation-sequences.txt"

# Twemoji spacing, in font units
UNITS_PER_EM = 1000
SPACE_BEFORE = 100
SPACE_AFTER = 50
BASELINE_SHIFT = 100

CBDT_BITMAP_SIZE = 96
VS16_GLYPH = "uniFE0F"
REPO_ROOT = Path(__file__).resolve().parent.parent
POLYCENTRIC_DIR = REPO_ROOT / "app/src/main/java/com/futo/platformplayer/polycentric"
POOL_KT = POLYCENTRIC_DIR / "HarborEmojiPool.kt"
FONTS_KT = POLYCENTRIC_DIR / "TwemojiFonts.kt"
FONT_DIR = REPO_ROOT / "app/src/main/res/font"


@dataclass
class FontMetrics:
    units_per_em: int
    advance: int
    space_before: int
    space_after: int


@dataclass
class Font:
    kotlin_name: str
    color_format: str
    output_name: str


FONTS = [
    Font("TWEMOJI_COLR", "glyf_colr_0", "twemoji_fingerprint.ttf"),
    Font("TWEMOJI_CBDT", "cbdt", "twemoji_fingerprint_cbdt.ttf"),
]


def read_pool() -> list[str]:
    """The emojis in HARBOR_FINGERPRINT_POOL."""
    source = POOL_KT.read_text(encoding="utf-8")
    body = re.search(r"HARBOR_FINGERPRINT_POOL: List<String> = listOf\((.*?)\n\)", source, re.S)
    if body is None:
        raise SystemExit(f"{POOL_KT.name}: HARBOR_FINGERPRINT_POOL not found")
    pool = [
        re.sub(r"\\u([0-9a-fA-F]{4})|\\(.)", lambda m: chr(int(m[1], 16)) if m[1] else m[2], literal)
        for literal in re.findall(r'"((?:[^"\\]|\\.)*)"', body[1])
    ]
    if not pool:
        raise SystemExit(f"{POOL_KT.name}: HARBOR_FINGERPRINT_POOL is empty")
    return pool


def download_twemoji(build_dir: Path) -> Path:
    """Extracts the release's SVGs. Returns their directory."""
    with urllib.request.urlopen(TWEMOJI_URL) as response:
        archive = tarfile.open(fileobj=io.BytesIO(response.read()), mode="r:gz")
    svg_dir = build_dir / "twemoji"
    svg_dir.mkdir()
    for member in archive.getmembers():
        parts = Path(member.name).parts
        if member.isfile() and parts[1:3] == ("assets", "svg") and member.name.endswith(".svg"):
            (svg_dir / parts[-1]).write_bytes(archive.extractfile(member).read())
    return svg_dir


def stage_svgs(svg_dir: Path, build_dir: Path) -> Path:
    # Only some file names keep VS16 (`fe0f`); without it, every sequence is keyed the same way
    # (see `ignore_variation_selector`).
    stage_dir = build_dir / "svg"
    stage_dir.mkdir()
    for svg in svg_dir.glob("*.svg"):
        (stage_dir / svg.name.replace("-fe0f", "")).write_text(make_view_box_square(svg.read_text()))
    return stage_dir


def make_view_box_square(svg_text: str) -> str:
    """
    nanoemoji scales a viewBox's height to the glyph's, so the one SVG that isn't square (🍉,
    36x25.22) would come out 1.43em wide. Pads such that the short side is centered.
    """
    match = re.search(r'viewBox="([^"]+)"', svg_text)
    x, y, width, height = map(float, match[1].split())
    size = max(width, height)
    view_box = f"{x - (size - width) / 2} {y - (size - height) / 2} {size} {size}"
    return svg_text.replace(match[0], f'viewBox="{view_box}"', 1)


def build_font(stage_dir: Path, build_dir: Path, color_format: str) -> Path:
    # nanoemoji scales each viewBox to fill ascender to descender, centered in the advance; the
    # transform moves it off center. The CBDT build ignores the transform, so its glyphs stay
    # centered.
    subprocess.run(
        [
            sys.executable,
            "-m",
            "nanoemoji.nanoemoji",
            f"--color_format={color_format}",
            "--family=Twemoji",
            f"--upem={UNITS_PER_EM}",
            f"--ascender={UNITS_PER_EM - BASELINE_SHIFT}",
            f"--descender={-BASELINE_SHIFT}",
            f"--width={SPACE_BEFORE + UNITS_PER_EM + SPACE_AFTER}",
            f"--transform=matrix(1 0 0 1 {(SPACE_BEFORE - SPACE_AFTER) / 2} 0)",
            f"--build_dir={build_dir}",
            *([f"--bitmap_resolution={CBDT_BITMAP_SIZE}"] if color_format == "cbdt" else []),
            "--output_file=Twemoji.ttf",
            *sorted(str(svg) for svg in stage_dir.iterdir()),
        ],
        check=True,
    )
    return build_dir / "Twemoji.ttf"


def ignore_variation_selector(font: TTFont) -> None:
    """Makes sequences match with or without VS16, as an empty glyph the ligatures skip."""
    font.setGlyphOrder([*font.getGlyphOrder(), VS16_GLYPH])
    font["glyf"].glyphs[VS16_GLYPH] = TTGlyphPen(None).glyph()
    font["glyf"].glyphOrder = font.getGlyphOrder()
    font["hmtx"].metrics[VS16_GLYPH] = (0, 0)
    for table in font["cmap"].tables:
        table.cmap[0xFE0F] = VS16_GLYPH
    # A mark, which the ligature lookups then skip.
    addOpenTypeFeaturesFromString(font, f"table GDEF {{ GlyphClassDef , , [{VS16_GLYPH}], ; }} GDEF;", tables={"GDEF"})
    for lookup in font["GSUB"].table.LookupList.Lookup:
        lookup.LookupFlag |= LOOKUP_FLAG_IGNORE_MARKS


def list_emoji_style_bases() -> list[int]:
    with urllib.request.urlopen(EMOJI_VARIATION_SEQUENCES_URL) as response:
        lines = response.read().decode().splitlines()
    sequences = [line.split(";")[0].split() for line in lines if line and not line.startswith("#")]
    return sorted(int(base, 16) for base, selector in sequences if selector == "FE0F")


def declare_emoji_style_variants(font: TTFont, emoji_style_bases: list[int]) -> None:
    """
    Android picks a font for an emoji with VS16 only if the font declares that variant,
    otherwise the system emoji font draws it. A cmap 14 table declares it for every base Unicode
    lists.
    """
    table = CmapSubtable.newSubtable(14)
    table.platformID = 0
    table.platEncID = 5
    table.language = 0xFF
    table.cmap = {}
    # Default variants: drawn with the glyph the base character maps to.
    table.uvsDict = {0xFE0F: [(code_point, None) for code_point in emoji_style_bases]}
    font["cmap"].tables.append(table)


def subset_font(font: TTFont, pool: list[str]) -> None:
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.hinting = False
    subsetter = subset.Subsetter(options)
    subsetter.populate(text="".join(pool))
    subsetter.subset(font)


def remove_outlines(font: TTFont) -> None:
    """
    Removes the empty outlines nanoemoji gives a bitmap font. With them, Android 9 treats the font as
    an outline font and draws the empty outlines, except at exactly the bitmaps' size.
    """
    del font["glyf"]
    del font["loca"]
    # The version for fonts without outlines.
    font["maxp"].tableVersion = 0x00005000


def shaped_glyphs(font: TTFont, hb_font: hb.Font, emoji: str) -> list[str]:
    """Glyphs the font draws for `emoji`, without the empty U+FE0F glyph."""
    buf = hb.Buffer()
    buf.add_str(emoji)
    buf.guess_segment_properties()
    hb.shape(hb_font, buf)
    names = [font.getGlyphName(info.codepoint) for info in buf.glyph_infos]
    return [name for name in names if name != VS16_GLYPH]


def verify_font(path: Path, pool: list[str]) -> dict[str, str]:
    """Checks that the font draws each emoji as one color glyph. Returns each emoji's glyph name."""
    font = TTFont(path)
    hb_font = hb.Font(hb.Face(path.read_bytes()))
    if not any(table.format == 14 for table in font["cmap"].tables):
        raise SystemExit(f"{path.name}: no cmap format 14 table")
    if "CBDT" in font and "glyf" in font:
        raise SystemExit(f"{path.name}: bitmap font has outlines")

    color_glyphs = set()
    if "COLR" in font:
        color_glyphs = set(font["COLR"].ColorLayers)
    if "CBDT" in font:
        color_glyphs = set(font["CBDT"].strikeData[0])

    glyphs = {}
    misses = []
    for emoji in pool:
        names = shaped_glyphs(font, hb_font, emoji)
        if len(names) == 1 and names[0] in color_glyphs:
            glyphs[emoji] = names[0]
        else:
            misses.append(f"{emoji} -> {names}")
    if misses:
        raise SystemExit(f"{path.name}: {len(misses)} pool emojis don't shape to one color glyph:\n  " + "\n  ".join(misses))
    return glyphs


def read_metrics(path: Path, glyphs: dict[str, str]) -> FontMetrics:
    """Reads where the emojis sit in the font, so the view can trim the space around them."""
    font = TTFont(path)
    upem = font["head"].unitsPerEm
    ascent = font["hhea"].ascent
    advances = {font["hmtx"][name][0] for name in glyphs.values()}
    if len(advances) != 1:
        raise SystemExit(f"{path.name}: pool glyphs have different advances: {sorted(advances)}")
    advance = advances.pop()

    if "COLR" in font:
        # Together, the emojis' outlines fill the em square.
        glyph_set = font.getGlyphSet()
        pen = BoundsPen(glyph_set)
        for name in set(glyphs.values()):
            for layer in font["COLR"].ColorLayers[name]:
                glyph_set[layer.name].draw(pen)
        x_min, y_min, x_max, top = pen.bounds
    else:
        # The bitmap font is centered differently, so read the position from the bitmaps.
        strike = font["CBDT"].strikeData[0]
        ppem = font["CBLC"].strikes[0].bitmapSizeTable.ppemX
        placements = set()
        for name in set(glyphs.values()):
            m = strike[name].metrics
            placements.add((m.BearingX, m.BearingY, m.width, m.height))
        if len(placements) != 1:
            raise SystemExit(f"{path.name}: pool bitmaps are placed differently: {sorted(placements)}")
        bearing_x, bearing_y, width, height = placements.pop()
        scale = upem / ppem
        x_min, x_max = bearing_x * scale, (bearing_x + width) * scale
        y_min, top = (bearing_y - height) * scale, bearing_y * scale

    tolerance = upem / 64
    if abs((x_max - x_min) - upem) > tolerance or abs(top - ascent) > tolerance:
        raise SystemExit(
            f"{path.name}: glyph box ({x_min}, {y_min}, {x_max}, {top}) isn't a 1 em square "
            f"from the ascent ({ascent})"
        )
    # The outlines don't quite reach the edges, so center the square on them.
    space_before = round((x_min + x_max - upem) / 2)
    return FontMetrics(upem, advance, space_before, advance - upem - space_before)


def write_fonts_kt(fonts: list[tuple[Font, FontMetrics]]) -> None:
    font_constants = "\n".join(
        f"""
/** Sizes in res/font/{font.output_name}, in font units. */
internal val {font.kotlin_name}_METRICS = TwemojiFontMetrics(
    unitsPerEm = {m.units_per_em},
    advance = {m.advance},
    spaceBefore = {m.space_before},
    spaceAfter = {m.space_after},
)"""
        for font, m in fonts
    )
    FONTS_KT.write_text(
        f"""// GENERATED by scripts/generate_twemoji_fonts.py from Twemoji {TWEMOJI_VERSION}. Do not edit.
package com.futo.platformplayer.polycentric

import com.futo.platformplayer.views.others.TwemojiFontMetrics
{font_constants}
""",
        encoding="utf-8",
    )


if __name__ == "__main__":
    if len(sys.argv) != 1:
        print(__doc__, file=sys.stderr)
        sys.exit(2)

    # Read by fontTools here and in nanoemoji's build.
    os.environ["SOURCE_DATE_EPOCH"] = str(TWEMOJI_RELEASE_TIME)
    pool = read_pool()
    emoji_style_bases = list_emoji_style_bases()

    FONT_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as build_dir:
        build_dir = Path(build_dir)
        stage_dir = stage_svgs(download_twemoji(build_dir), build_dir)
        with ThreadPoolExecutor() as executor:
            built = list(executor.map(
                lambda font: build_font(stage_dir, build_dir / font.color_format, font.color_format),
                FONTS,
            ))

        fonts = []
        for font, built_file in zip(FONTS, built):
            ttf = TTFont(built_file)
            ignore_variation_selector(ttf)
            declare_emoji_style_variants(ttf, emoji_style_bases)
            subset_font(ttf, pool)
            if "CBDT" in ttf:
                remove_outlines(ttf)
            dst = FONT_DIR / font.output_name
            ttf.save(dst)

            glyphs = verify_font(dst, pool)
            metrics = read_metrics(dst, glyphs)
            fonts.append((font, metrics))
            print(f"{dst.relative_to(REPO_ROOT)}: {dst.stat().st_size // 1024} KB, {metrics}")

    write_fonts_kt(fonts)
    print(f"Pool: {len(pool)} emojis")
