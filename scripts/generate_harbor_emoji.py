#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = [
#   "fonttools==4.67.0",
#   "uharfbuzz==0.56.3",
# ]
# ///
"""
Generate Harbor's emoji pool using a Twemoji font, for emoji fingerprints used in pairing.

Use `uv run scripts/generate_harbor_emoji.py <path to Harbor monorepo checkout>`.
"""

import hashlib
import json
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

import uharfbuzz as hb
from fontTools import subset
from fontTools.pens.boundsPen import BoundsPen
from fontTools.ttLib import TTFont

# Must match FINGERPRINT_EMOJI_CATEGORIES in Harbor's `publicKeyEmojiFingerprint.ts`.
FINGERPRINT_CATEGORIES = {
    "smileys & emotion",
    "animals & nature",
    "food & drink",
    "travel & places",
    "activities",
    "objects",
}

# Harbor's empty glyph for U+FE0F, so emojis match with or without it.
VS16_GLYPH = "uniFE0F"

REPO_ROOT = Path(__file__).resolve().parent.parent
POOL_KT = REPO_ROOT / "app/src/main/java/com/futo/platformplayer/polycentric/HarborEmojiPool.kt"
FONT_DIR = REPO_ROOT / "app/src/main/res/font"
LICENSE_TXT = REPO_ROOT / "app/src/main/assets/licenses/TWEMOJI-LICENSE.txt"

@dataclass
class FontMetrics:
    units_per_em: int
    advance: int
    space_before: int
    space_after: int

@dataclass
class Font:
    kotlin_name: str
    source_name: str
    output_name: str

FONTS = [
    Font("TWEMOJI_COLR", "Twemoji.ttf", "twemoji_fingerprint.ttf"),
    Font("TWEMOJI_CBDT", "TwemojiCbdt.ttf", "twemoji_fingerprint_cbdt.ttf"),
]


def fingerprint_pool(emojis_json: Path) -> list[str]:
    """Same as Harbor's emojiData.ts `categories`, filtered to the fingerprint categories."""
    data = json.loads(emojis_json.read_text(encoding="utf-8"))
    # Categories in order of first appearance, emojis in file order.
    by_category: dict[str, list[str]] = {}
    for entry in data["emojis"]:
        if entry["category"] == "Component":
            continue
        by_category.setdefault(entry["category"], []).append(entry["emoji"])
    return [
        emoji
        for category, emojis in by_category.items()
        if category.lower() in FINGERPRINT_CATEGORIES
        for emoji in emojis
    ]


def file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def kotlin_string(s: str) -> str:
    return '"' + "".join(f"\\u{ord(c):04x}" if ord(c) < 0x20 else c for c in s.replace("\\", "\\\\").replace('"', '\\"')) + '"'


def source_commit(harbor: Path) -> str:
    try:
        return subprocess.run(
            ["git", "-C", str(harbor), "rev-parse", "HEAD"],
            check=True, capture_output=True, text=True,
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def subset_font(src: Path, dst: Path, pool: list[str]) -> None:
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.hinting = False
    font = subset.load_font(str(src), options)
    subsetter = subset.Subsetter(options)
    subsetter.populate(text="".join(pool))
    subsetter.subset(font)
    subset.save_font(font, str(dst), options)


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
        x_min, y_min, x_max, y_max = pen.bounds
        top = y_max
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


def write_pool_kt(pool: list[str], commit: str, fonts: list[tuple[Font, str, FontMetrics]]) -> None:
    entries = "\n".join(f"    {kotlin_string(e)}," for e in pool)
    source_hashes = "\n".join(f"// {font.source_name} SHA-256: {source_hash}" for font, source_hash, _ in fonts)
    font_constants = "\n".join(
        f"""
/** Sizes in res/font/{font.output_name}, in font units. */
internal val {font.kotlin_name}_METRICS = TwemojiFontMetrics(
    unitsPerEm = {m.units_per_em},
    advance = {m.advance},
    spaceBefore = {m.space_before},
    spaceAfter = {m.space_after},
)"""
        for font, _, m in fonts
    )
    POOL_KT.write_text(
        f"""// GENERATED by scripts/generate_harbor_emoji.py. Do not edit.
// Source: Harbor {commit}
//   apps/harbor/src/features/reaction/emojis.json
//   apps/harbor/src/common/assets/fonts/{{{",".join(font.source_name for font in FONTS)}}}
{source_hashes}
// Pool size: {len(pool)}
package com.futo.platformplayer.polycentric

import com.futo.platformplayer.views.others.TwemojiFontMetrics
{font_constants}

/**
 * Emojis that fingerprints pick from. Must match Harbor exactly, or the two devices show different
 * emojis.
 */
internal val HARBOR_FINGERPRINT_POOL: List<String> = listOf(
{entries}
)
""",
        encoding="utf-8",
    )

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        sys.exit(2)

    harbor = Path(sys.argv[1]).resolve()
    app_src = harbor / "apps/harbor/src"
    emojis_json = app_src / "features/reaction/emojis.json"
    font_src_dir = app_src / "common/assets/fonts"
    license_txt = app_src / "common/emoji/TWEMOJI-LICENSE.txt"

    pool = fingerprint_pool(emojis_json)
    commit = source_commit(harbor)

    FONT_DIR.mkdir(parents=True, exist_ok=True)
    fonts = []
    for font in FONTS:
        src = font_src_dir / font.source_name
        dst = FONT_DIR / font.output_name
        subset_font(src, dst, pool)
        glyphs = verify_font(dst, pool)
        metrics = read_metrics(dst, glyphs)
        fonts.append((font, file_sha256(src), metrics))
        print(f"{dst.relative_to(REPO_ROOT)}: {dst.stat().st_size // 1024} KB, {metrics}")

    write_pool_kt(pool, commit, fonts)

    LICENSE_TXT.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(license_txt, LICENSE_TXT)

    print(f"Pool: {len(pool)} emojis")
    sys.exit(0)
