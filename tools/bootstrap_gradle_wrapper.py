#!/usr/bin/env python3
"""Fetches the Gradle wrapper JAR from the official distribution.

This repository cannot check in binary artifacts, so run this script once
before invoking `./gradlew` or importing the project into Android Studio.
It will download the configured Gradle distribution, extract
`gradle-wrapper.jar`, and place it under `gradle/wrapper/`.
"""
from __future__ import annotations

import hashlib
import io
import os
import sys
import zipfile
from pathlib import Path
from urllib.error import URLError, HTTPError
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
WRAPPER_DIR = ROOT / "gradle" / "wrapper"
WRAPPER_JAR = WRAPPER_DIR / "gradle-wrapper.jar"
GRADLE_VERSION = os.environ.get("GRADLE_WRAPPER_VERSION", "8.7")
DIST_URL = os.environ.get(
    "GRADLE_WRAPPER_DIST",
    f"https://services.gradle.org/distributions/gradle-{GRADLE_VERSION}-bin.zip",
)
EXPECTED_SHA256 = os.environ.get(
    "GRADLE_WRAPPER_ZIP_SHA256",
    "2483bea7506add6bb733a9b242d403691b9a91037efb6adfb63f4643c81b646a",
)


def log(msg: str) -> None:
    print(msg, file=sys.stderr)


def ensure_wrapper_dir() -> None:
    WRAPPER_DIR.mkdir(parents=True, exist_ok=True)


def download_distribution() -> bytes:
    log(f"Downloading Gradle {GRADLE_VERSION} distribution…")
    try:
        with urlopen(DIST_URL) as response:
            data = response.read()
    except HTTPError as exc:
        raise SystemExit(f"HTTP error while downloading Gradle: {exc}") from exc
    except URLError as exc:
        raise SystemExit(f"Unable to download Gradle distribution: {exc}") from exc
    return data


def verify_checksum(data: bytes) -> None:
    if not EXPECTED_SHA256:
        return
    digest = hashlib.sha256(data).hexdigest()
    if digest != EXPECTED_SHA256:
        raise SystemExit(
            "Checksum mismatch for Gradle distribution.\n"
            f"Expected: {EXPECTED_SHA256}\n"
            f"Actual:   {digest}"
        )


def extract_wrapper_jar(zip_bytes: bytes) -> None:
    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as zf:
        target_path = f"gradle-{GRADLE_VERSION}/lib/gradle-wrapper.jar"
        try:
            data = zf.read(target_path)
        except KeyError as exc:
            raise SystemExit(
                f"Unable to locate {target_path} inside Gradle distribution"
            ) from exc
    WRAPPER_JAR.write_bytes(data)


def main() -> None:
    if WRAPPER_JAR.exists():
        log("gradle-wrapper.jar already present; nothing to do.")
        return
    ensure_wrapper_dir()
    zip_bytes = download_distribution()
    verify_checksum(zip_bytes)
    extract_wrapper_jar(zip_bytes)
    log(f"Saved wrapper JAR to {WRAPPER_JAR.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
