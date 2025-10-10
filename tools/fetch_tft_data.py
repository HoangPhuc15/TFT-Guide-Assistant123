"""Download Teamfight Tactics Data Dragon payloads for selected sets."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Iterable
from urllib import error, request

BASE_URL = "https://ddragon.leagueoflegends.com/cdn"  # Data Dragon CDN root

# Riot typically releases a new CDN version for every balance patch.  These
# versions capture the first release for Set 9 and the mid-set (9.5) update.
DEFAULT_SETS = {
    "set9": "13.13.1",
    "set9update": "13.20.1",
}

FILES = (
    "data/en_US/tft-champions.json",
    "data/en_US/tft-items.json",
    "data/en_US/tft-traits.json",
    "data/en_US/tft-augments.json",
)


def _opener() -> request.OpenerDirector:
    """Return a urllib opener that ignores proxy configuration.

    Hosted evaluation environments frequently inject proxy environment
    variables that either block access to Riot's CDN or respond with a 403
    during the CONNECT tunnel handshake.  Installing an opener with an empty
    :class:`ProxyHandler` bypasses those variables so we can attempt a direct
    connection first.
    """

    return request.build_opener(request.ProxyHandler({}))


def download_file(version: str, relative: str, dest: Path) -> None:
    url = f"{BASE_URL}/{version}/{relative}"
    dest.parent.mkdir(parents=True, exist_ok=True)
    opener = _opener()
    try:
        with opener.open(url) as response:
            dest.write_bytes(response.read())
    except error.URLError as exc:  # pragma: no cover - network specific
        print(f"Failed to download {url}: {exc}", file=sys.stderr)
        raise
    else:
        print(f"Downloaded {url} -> {dest}")


def fetch_sets(sets: Iterable[str], output: Path) -> None:
    for folder in sets:
        version = DEFAULT_SETS.get(folder)
        if version is None:
            raise ValueError(f"Unknown set identifier '{folder}'")
        for file in FILES:
            download_file(version, file, output / folder / file.split("/")[-1])


def main(args: argparse.Namespace) -> None:
    selected_sets = args.sets or tuple(DEFAULT_SETS)
    fetch_sets(selected_sets, args.output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "previous",
        help="Directory where the set data should be stored",
    )
    parser.add_argument(
        "--sets",
        nargs="*",
        metavar="SET",
        help="Subset of folders to sync (defaults to all known sets)",
    )
    main(parser.parse_args())
