#!/usr/bin/env python3

import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile


ROOT = Path(__file__).resolve().parent
REPOSITORY = ROOT.parent.parent
NDK_VERSION = "28.2.13676358"
ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
PROTOC_URL = "https://github.com/protocolbuffers/protobuf/releases/download/v21.12/protoc-21.12-linux-x86_64.zip"
PROTOC_SHA256 = "3a4c1e5f2516c639d3079b1586e703fc7bcfa2136d58bda24d1d54f949c315e8"


def digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def download(url, destination, checksum):
    if not destination.exists():
        temporary = destination.with_suffix(destination.suffix + ".download")
        try:
            with urllib.request.urlopen(url, timeout=120) as response, temporary.open("wb") as stream:
                shutil.copyfileobj(response, stream)
            if digest(temporary) != checksum:
                raise RuntimeError("Checksum mismatch: " + url)
            temporary.replace(destination)
        finally:
            temporary.unlink(missing_ok=True)
    if digest(destination) != checksum:
        raise RuntimeError("Checksum mismatch: " + str(destination))


def source_filter(member, destination):
    try:
        return tarfile.data_filter(member, destination)
    except tarfile.LinkOutsideDestinationError:
        # AOSP formatting and platform-only headers link into other repositories.
        return None


def fetch_sources(work):
    lock = json.loads((ROOT / "sources.lock.json").read_text())
    archives = work / "archives"
    sources = work / "sources"
    archives.mkdir(parents=True, exist_ok=True)
    sources.mkdir(parents=True, exist_ok=True)

    def fetch(item):
        name, entry = item
        archive = archives / (name + ".tar.gz")
        url = entry["repository"] + "/+archive/" + entry["commit"] + ".tar.gz"
        download(url, archive, entry["sha256"])
        destination = sources / name
        # Re-extract pristine sources before applying this build's patches.
        if destination.exists():
            shutil.rmtree(destination)
        destination.mkdir()
        with tarfile.open(archive) as source:
            source.extractall(destination, filter=source_filter)
        print("Verified source:", name, entry["commit"], flush=True)

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        list(executor.map(fetch, lock.items()))
    subprocess.run(["git", "apply", "--unsafe-paths", "--directory", str(sources),
                    str(ROOT / "android.patch")], check=True, cwd=REPOSITORY)
    return sources


def protoc_binary(work, configured):
    if configured:
        executable = Path(configured).resolve()
    else:
        if os.uname().sysname != "Linux" or os.uname().machine != "x86_64":
            raise RuntimeError("Supply --protoc pointing to Protobuf 3.21.12 on this host")
        tools = work / "tools"
        tools.mkdir(parents=True, exist_ok=True)
        archive = tools / "protoc-21.12-linux-x86_64.zip"
        download(PROTOC_URL, archive, PROTOC_SHA256)
        executable = tools / "protoc-21.12/bin/protoc"
        with zipfile.ZipFile(archive) as source:
            source.extractall(executable.parent.parent)
        executable.chmod(0o755)
    version = subprocess.check_output([str(executable), "--version"], text=True).strip()
    if version != "libprotoc 3.21.12":
        raise RuntimeError("Expected libprotoc 3.21.12, found " + version)
    return executable


def verify_binary(binary, llvm):
    headers = subprocess.check_output([str(llvm / "llvm-readelf"), "-l", "-d", str(binary)], text=True)
    dependencies = set(re.findall(r"Shared library: \[([^]]+)\]", headers))
    if not dependencies.issubset({"libc.so", "libdl.so", "libm.so", "liblog.so"}):
        raise RuntimeError("Unexpected runtime dependencies: " + str(dependencies))
    if "PIE" not in headers or "/system/bin/linker" not in headers:
        raise RuntimeError("Expected an Android PIE executable")
    loads = [line.split() for line in headers.splitlines() if line.strip().startswith("LOAD ")]
    if not loads or any(int(line[-1], 16) < 16384 for line in loads):
        raise RuntimeError("Native executable must support 16 KB pages")


def main():
    sdk = Path(os.environ.get("ANDROID_HOME", ""))
    parser = argparse.ArgumentParser(description="Build Grayjay's Android port of the official AOSP ADB client")
    parser.add_argument("--ndk", type=Path, default=sdk / "ndk" / NDK_VERSION)
    parser.add_argument("--cmake", type=Path, default=sdk / "cmake/3.22.1/bin/cmake")
    parser.add_argument("--protoc", help="Host protoc 3.21.12; defaults to Google's pinned Linux executable")
    parser.add_argument("--work", type=Path, default=REPOSITORY / "build/tv-adb")
    parser.add_argument("--abi", action="append", choices=ABIS, help="Repeat to build selected ABIs; defaults to all")
    parser.add_argument("--jobs", type=int, default=4)
    args = parser.parse_args()
    ndk = args.ndk.resolve()
    cmake = args.cmake.resolve()
    work = args.work.resolve()
    if not work.is_relative_to(REPOSITORY) or work == REPOSITORY:
        parser.error("--work must be a build directory inside this clone")
    properties = ndk / "source.properties"
    if not properties.exists() or "Pkg.Revision = " + NDK_VERSION not in properties.read_text():
        parser.error("Provide NDK " + NDK_VERSION + " with --ndk; shared tools are preferred")
    if not cmake.is_file() or not (cmake.parent / "ninja").is_file():
        parser.error("Provide CMake 3.22.1 (including Ninja) with --cmake")
    version = subprocess.check_output([str(cmake), "--version"], text=True).splitlines()[0]
    if not version.startswith("cmake version 3.22.1"):
        parser.error("Expected CMake 3.22.1, found " + version)
    if args.jobs < 1:
        parser.error("--jobs must be positive")
    work.mkdir(parents=True, exist_ok=True)
    sources = fetch_sources(work)
    protoc = protoc_binary(work, args.protoc)
    artifacts = {}
    for abi in args.abi or ABIS:
        build = work / "build" / abi
        subprocess.run([
            str(cmake), "-S", str(ROOT), "-B", str(build), "-G", "Ninja",
            "-DCMAKE_MAKE_PROGRAM=" + str(cmake.parent / "ninja"),
            "-DCMAKE_TOOLCHAIN_FILE=" + str(ndk / "build/cmake/android.toolchain.cmake"),
            "-DANDROID_ABI=" + abi, "-DANDROID_PLATFORM=android-28", "-DANDROID_STL=c++_static",
            "-DCMAKE_BUILD_TYPE=Release", "-DAOSP_SOURCES=" + str(sources),
            "-DPROTOC=" + str(protoc)
        ], check=True)
        subprocess.run([str(cmake), "--build", str(build), "--target", "grayjay_adb",
                        "-j", str(args.jobs)], check=True)
        binary = build / "libgrayjay_adb.so"
        llvm = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
        verify_binary(binary, llvm)
        subprocess.run([str(llvm / "llvm-strip"), "--strip-unneeded", str(binary)], check=True)
        destination = REPOSITORY / "app/src/main/jniLibs" / abi / binary.name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(binary, destination)
        artifacts[abi] = digest(destination)
        print("Built:", abi, artifacts[abi], flush=True)
    (work / "artifacts.json").write_text(json.dumps(artifacts, indent=2) + "\n")


if __name__ == "__main__":
    main()
