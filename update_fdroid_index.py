#!/usr/bin/env python3
from __future__ import annotations

import glob
import hashlib
import datetime
import os
import re
import shutil
import subprocess
import sys
import tempfile
from typing import Optional

APK_URL = "https://releases.grayjay.app/app-universal-release.apk"

FDROID_REPO_SSH = "git@gitlab.futo.org:fdroid/repo-v2.git"
FDROID_INDEX_PATH = "apps/Grayjay/index.yml"
UNIVERSAL_APK_GLOB = "app/build/outputs/apk/stable/release/*universal*.apk"

GIT_USER_NAME = "koen"
GIT_USER_EMAIL = "koen@futo.org"

class Fatal(Exception):
    pass

def run(cmd: list[str], *, cwd: Optional[str] = None) -> str:
    p = subprocess.run(cmd, cwd=cwd, check=False, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if p.returncode != 0:
        raise Fatal(f"Command failed ({p.returncode}): {' '.join(cmd)}\n{p.stdout}")
    return p.stdout.strip()

def sha256_of_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def pick_universal_apk() -> str:
    matches = sorted(glob.glob(UNIVERSAL_APK_GLOB))
    if not matches:
        raise Fatal(f"No universal APK found via glob: {UNIVERSAL_APK_GLOB}")

    for m in matches:
        base = os.path.basename(m)
        if "app-stable-universal" in base:
            return m

    return matches[-1]

def get_release_date_today() -> str:
    return datetime.datetime.now(datetime.timezone.utc).date().isoformat()

def get_version_code_from_tag() -> int:
    tag = os.environ.get("CI_COMMIT_TAG", "").strip()
    if not tag:
        tag = run(["git", "describe", "--tags"]).strip()

    m = re.search(r"(\d+)", tag)
    if not m:
        raise Fatal(f"Could not parse an integer versionCode from tag '{tag}'")

    return int(m.group(1))

def update_index_yml(path: str, sha256sum: str, date_str: str, version_code: int) -> None:
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    url_line_idx = None
    url_line_indent = ""
    for i, line in enumerate(lines):
        stripped = line.lstrip()
        if stripped.startswith("- url:") and APK_URL in stripped:
            url_line_idx = i
            url_line_indent = line[: len(line) - len(stripped)]
            break
    if url_line_idx is None:
        raise Fatal(f"Did not find an apk entry with url {APK_URL} in {path}")

    def is_url_line_same_level(s: str) -> bool:
        st = s.lstrip()
        indent = s[: len(s) - len(st)]
        return st.startswith("- url:") and indent == url_line_indent

    end = len(lines)
    for j in range(url_line_idx + 1, len(lines)):
        if is_url_line_same_level(lines[j]):
            end = j
            break

    child_indent = url_line_indent + "  "
    found_sha = found_date = found_vc = False

    for j in range(url_line_idx + 1, end):
        st = lines[j].lstrip()
        indent = lines[j][: len(lines[j]) - len(st)]
        if st.startswith("sha256sum:"):
            lines[j] = f"{indent}sha256sum: {sha256sum}\n"
            found_sha = True
        elif st.startswith("date:"):
            lines[j] = f"{indent}date: {date_str}\n"
            found_date = True
        elif st.startswith("version-code:"):
            lines[j] = f"{indent}version-code: {version_code}\n"
            found_vc = True

    insert_pos = url_line_idx + 1
    to_insert: list[str] = []
    if not found_sha:
        to_insert.append(f"{child_indent}sha256sum: {sha256sum}\n")
    if not found_date:
        to_insert.append(f"{child_indent}date: {date_str}\n")
    if not found_vc:
        to_insert.append(f"{child_indent}version-code: {version_code}\n")
    if to_insert:
        lines[insert_pos:insert_pos] = to_insert

    with open(path, "w", encoding="utf-8") as f:
        f.writelines(lines)


def main() -> int:
    version_code = get_version_code_from_tag()
    date_str = get_release_date_today()
    
    apk_path = pick_universal_apk()
    print(f"Computing sha256 for {apk_path} ...")
    sha = sha256_of_file(apk_path)
    print(f"sha256: {sha}")
    print(f"date: {date_str}")
    print(f"version-code: {version_code}")

    tmpdir = tempfile.mkdtemp(prefix="fdroid-repo-")
    try:
        print(f"Cloning {FDROID_REPO_SSH} ...")
        run(["git", "clone", "--depth", "1", FDROID_REPO_SSH, tmpdir])

        run(["git", "config", "user.name", GIT_USER_NAME], cwd=tmpdir)
        run(["git", "config", "user.email", GIT_USER_EMAIL], cwd=tmpdir)

        index_path = os.path.join(tmpdir, FDROID_INDEX_PATH)
        if not os.path.exists(index_path):
            raise Fatal(f"Missing {FDROID_INDEX_PATH} in repo-v2")

        update_index_yml(index_path, sha, date_str, version_code)

        run(["git", "add", FDROID_INDEX_PATH], cwd=tmpdir)

        diff_rc = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=tmpdir).returncode
        if diff_rc == 0:
            print("No changes to commit.")
            return 0

        msg = f"Grayjay: update sha/date/version-code to {version_code} ({date_str})"
        run(["git", "commit", "-m", msg], cwd=tmpdir)
        run(["git", "push"], cwd=tmpdir)

        print("Pushed update to fdroid/repo-v2.")
        return 0
    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Fatal as e:
        print(f"ERROR: {e}", file=sys.stderr)
        raise SystemExit(2)
