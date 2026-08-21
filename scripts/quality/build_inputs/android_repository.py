#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any, Mapping, Sequence

from .contracts import BuildInputError, canonical_json_bytes, load_policy, sha256_file
from .receipts import load_canonical_receipt, write_canonical_receipt


_MAX_REPOSITORY_BYTES = 2 * 1024 * 1024
_PLATFORM_COORDINATE = "platforms;android-37.0"
_OLD_COORDINATE = "platforms;android-37"


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # type: ignore[no-untyped-def]
        return None


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _children(parent: ET.Element, name: str) -> list[ET.Element]:
    return [child for child in parent if _local_name(child.tag) == name]


def _one(parent: ET.Element, name: str, *, context: str) -> ET.Element:
    values = _children(parent, name)
    if len(values) != 1:
        raise BuildInputError(f"{context} must contain exactly one {name}")
    return values[0]


def _text(parent: ET.Element, name: str, *, context: str, allow_empty: bool = False) -> str:
    node = _one(parent, name, context=context)
    value = node.text or ""
    if value != value.strip() or (not allow_empty and not value):
        raise BuildInputError(f"{context}.{name} text is not canonical")
    return value


def _integer(parent: ET.Element, name: str, *, context: str) -> int:
    value = _text(parent, name, context=context)
    if not value.isascii() or not value.isdecimal():
        raise BuildInputError(f"{context}.{name} must be an integer")
    return int(value)


def _revision_record(revision: ET.Element, *, context: str) -> dict[str, int]:
    allowed = {"major", "micro", "minor", "preview"}
    result: dict[str, int] = {}
    for child in revision:
        name = _local_name(child.tag)
        if name not in allowed or name in result:
            raise BuildInputError(f"{context} revision structure drift")
        raw = child.text or ""
        if not raw.isascii() or not raw.isdecimal():
            raise BuildInputError(f"{context} revision value is malformed")
        result[name] = int(raw)
    if "major" not in result:
        raise BuildInputError(f"{context} revision major is missing")
    return {name: result[name] for name in sorted(result)}


def _parse_xml(body: bytes, *, context: str) -> ET.Element:
    if len(body) > _MAX_REPOSITORY_BYTES or b"<!DOCTYPE" in body or b"<!ENTITY" in body:
        raise BuildInputError(f"{context} XML violates the closed parser contract")
    try:
        return ET.fromstring(body)
    except ET.ParseError as error:
        raise BuildInputError(f"{context} XML is malformed") from error


def _platform_record(package: ET.Element, *, include_archive: bool) -> dict[str, Any]:
    context = "Android platform record"
    if include_archive and set(package.attrib) != {"path"}:
        raise BuildInputError("Android repository platform attributes drift")
    details = _one(package, "type-details", context=context)
    type_values = [value for key, value in details.attrib.items() if _local_name(key) == "type"]
    if len(type_values) != 1 or type_values[0].rsplit(":", 1)[-1] != "platformDetailsType":
        raise BuildInputError("Android platform type-details identity drift")
    layout = _one(details, "layoutlib", context=f"{context}.typeDetails")
    if set(layout.attrib) != {"api"} or not layout.attrib["api"].isdecimal():
        raise BuildInputError("Android platform layoutlib identity is malformed")
    revision = _one(package, "revision", context=context)
    record: dict[str, Any] = {
        "coordinate": package.attrib.get("path"),
        "displayName": _text(package, "display-name", context=context),
        "layoutlibApi": int(layout.attrib["api"]),
        "revisionMajor": _integer(revision, "major", context=f"{context}.revision"),
        "typeKind": "platformDetailsType",
        "typeDetails": {
            "apiLevel": _text(details, "api-level", context=f"{context}.typeDetails"),
            "baseExtension": _text(details, "base-extension", context=f"{context}.typeDetails") == "true",
            "codename": _text(details, "codename", context=f"{context}.typeDetails", allow_empty=True),
            "extensionLevel": _integer(details, "extension-level", context=f"{context}.typeDetails"),
        },
    }
    if include_archive:
        channel = _one(package, "channelRef", context=context)
        if set(channel.attrib) != {"ref"} or not channel.attrib["ref"]:
            raise BuildInputError("Android platform channel identity is malformed")
        archives = _one(package, "archives", context=context)
        archive = _one(archives, "archive", context=f"{context}.archives")
        complete = _one(archive, "complete", context=f"{context}.archive")
        checksum = _one(complete, "checksum", context=f"{context}.complete")
        if checksum.attrib != {"type": "sha1"}:
            raise BuildInputError("Android platform repository checksum type drift")
        relative_url = _text(complete, "url", context=f"{context}.complete")
        resolved_url = urllib.parse.urljoin(
            "https://dl.google.com/android/repository/repository2-3.xml",
            relative_url,
        )
        record["archive"] = {
            "relativeUrl": relative_url,
            "repositorySha1": checksum.text or "",
            "resolvedUrl": resolved_url,
            "size": _integer(complete, "size", context=f"{context}.complete"),
        }
        record["channel"] = channel.attrib["ref"]
    return record


def validate_android_repository_inventory(
    body: bytes,
    contract: Mapping[str, Any],
) -> dict[str, Any]:
    if hashlib.sha256(body).hexdigest() != contract.get("repositorySha256"):
        raise BuildInputError("Android repository body SHA-256 differs from policy")
    root = _parse_xml(body, context="Android repository")
    packages = [node for node in root if _local_name(node.tag) == "remotePackage"]
    old_rows = [node for node in packages if node.attrib.get("path") == _OLD_COORDINATE]
    accepted_rows = [node for node in packages if node.attrib.get("path") == _PLATFORM_COORDINATE]
    if old_rows or len(accepted_rows) != 1:
        raise BuildInputError("Android repository API-37 coordinate inventory drift")
    actual = _platform_record(accepted_rows[0], include_archive=True)
    if actual != contract.get("acceptedRecord"):
        raise BuildInputError("Android repository accepted platform record drift")
    if contract.get("absentCoordinates") != [_OLD_COORDINATE]:
        raise BuildInputError("Android repository old-coordinate absence contract drift")
    if contract.get("repositoryUrl") != "https://dl.google.com/android/repository/repository2-3.xml":
        raise BuildInputError("Android repository URL drift")
    return {
        "absentCoordinates": [_OLD_COORDINATE],
        "acceptedRecord": actual,
        "repositorySha256": hashlib.sha256(body).hexdigest(),
        "repositorySize": len(body),
        "repositoryUrl": contract["repositoryUrl"],
        "schemaVersion": 1,
        "status": "PASS",
    }


def fetch_android_repository_inventory(contract: Mapping[str, Any]) -> dict[str, Any]:
    url = contract.get("repositoryUrl")
    if not isinstance(url, str):
        raise BuildInputError("Android repository URL is missing")
    opener = urllib.request.build_opener(_NoRedirect())
    request = urllib.request.Request(url, headers={"User-Agent": "GasStation-build-input-verifier/1"})
    try:
        with opener.open(request, timeout=60) as response:
            if response.status != 200 or response.geturl() != url:
                raise BuildInputError("Android repository response identity drift")
            body = response.read(_MAX_REPOSITORY_BYTES + 1)
    except urllib.error.HTTPError as error:
        error.close()
        raise BuildInputError("Android repository request status drift") from None
    except (urllib.error.URLError, OSError):
        raise BuildInputError("Android repository request failed") from None
    return validate_android_repository_inventory(body, contract)


def capture_installed_android_packages(
    android_policy: Mapping[str, Any],
    sdk_root: Path,
    source_receipt: Mapping[str, Any],
) -> dict[str, Any]:
    contract = android_policy.get("repositoryInventory")
    if not isinstance(contract, dict) or source_receipt.get("status") != "PASS":
        raise BuildInputError("Android repository source receipt is missing")
    if (
        source_receipt.get("repositorySha256") != contract.get("repositorySha256")
        or source_receipt.get("acceptedRecord") != contract.get("acceptedRecord")
        or source_receipt.get("absentCoordinates") != contract.get("absentCoordinates")
    ):
        raise BuildInputError("Android repository source receipt drift")
    expected_roots = {
        "build-tools/36.0.0": "build-tools;36.0.0",
        "cmdline-tools/latest": None,
        "platform-tools": "platform-tools",
        "platforms/android-37.0": _PLATFORM_COORDINATE,
    }
    if sdk_root.is_symlink() or not sdk_root.is_dir():
        raise BuildInputError("Android SDK root must be a regular directory")
    if any(path.is_symlink() for path in sdk_root.rglob("*")):
        raise BuildInputError("installed Android SDK contains a symlink")
    actual_files = sorted(
        path.relative_to(sdk_root).as_posix()
        for path in sdk_root.rglob("package.xml")
        if path.is_file() and not path.is_symlink()
    )
    expected_files = sorted(f"{root}/package.xml" for root in expected_roots)
    if actual_files != expected_files:
        raise BuildInputError("installed Android package.xml inventory drift")
    rows: list[dict[str, Any]] = []
    for relative_root, expected_coordinate in expected_roots.items():
        package_xml = sdk_root / relative_root / "package.xml"
        root = _parse_xml(package_xml.read_bytes(), context="installed Android package")
        local_packages = [node for node in root.iter() if _local_name(node.tag) == "localPackage"]
        if len(local_packages) != 1:
            raise BuildInputError("installed Android package identity is ambiguous")
        package = local_packages[0]
        coordinate = package.attrib.get("path")
        if expected_coordinate is None:
            if not isinstance(coordinate, str) or not coordinate.startswith("cmdline-tools;") or coordinate == "cmdline-tools;latest":
                raise BuildInputError("installed command-line tools coordinate drift")
        elif coordinate != expected_coordinate:
            raise BuildInputError("installed Android package coordinate drift")
        revision = _one(package, "revision", context="installed Android package")
        channel_rows = _children(package, "channelRef")
        if len(channel_rows) > 1 or (
            channel_rows and (set(channel_rows[0].attrib) != {"ref"} or not channel_rows[0].attrib["ref"])
        ):
            raise BuildInputError("installed Android package channel identity drift")
        obsolete = package.attrib.get("obsolete", "false")
        if obsolete not in {"false", "true"}:
            raise BuildInputError("installed Android package obsolete identity drift")
        row: dict[str, Any] = {
            "channel": channel_rows[0].attrib["ref"] if channel_rows else None,
            "coordinate": coordinate,
            "displayName": _text(package, "display-name", context="installed Android package"),
            "obsolete": obsolete == "true",
            "packageXmlSha256": sha256_file(package_xml),
            "packageXmlSize": package_xml.stat().st_size,
            "relativeRoot": relative_root,
            "revision": _revision_record(revision, context="installed Android package"),
        }
        if coordinate == _PLATFORM_COORDINATE:
            platform = _platform_record(package, include_archive=False)
            expected_platform = dict(contract["acceptedRecord"])
            expected_platform.pop("archive")
            expected_platform.pop("channel")
            if platform != expected_platform:
                raise BuildInputError("installed Android platform metadata drift")
            row["platformRecord"] = platform
        rows.append(row)
    binary_paths = (
        "build-tools/36.0.0/aapt2",
        "build-tools/36.0.0/apksigner",
        "build-tools/36.0.0/zipalign",
        "cmdline-tools/latest/bin/avdmanager",
        "cmdline-tools/latest/bin/sdkmanager",
        "platform-tools/adb",
    )
    binaries: list[dict[str, Any]] = []
    for relative in binary_paths:
        binary = sdk_root / relative
        if binary.is_symlink() or not binary.is_file():
            raise BuildInputError("installed Android selected binary is missing or unsafe")
        binaries.append(
            {
                "relativePath": relative,
                "sha256": sha256_file(binary),
                "size": binary.stat().st_size,
            }
        )
    return {
        "binaries": binaries,
        "packages": sorted(rows, key=lambda row: str(row["coordinate"])),
        "requestedPlatformCoordinate": _PLATFORM_COORDINATE,
        "repositorySha256": contract["repositorySha256"],
        "schemaVersion": 1,
        "sourceReceiptSha256": hashlib.sha256(canonical_json_bytes(dict(source_receipt))).hexdigest(),
        "status": "PASS",
    }


def _parse_cli(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    fetch = subparsers.add_parser("fetch")
    fetch.add_argument("--policy", required=True)
    fetch.add_argument("--output", required=True)
    installed = subparsers.add_parser("installed")
    installed.add_argument("--policy", required=True)
    installed.add_argument("--source-receipt", required=True)
    installed.add_argument("--sdk-root", required=True)
    installed.add_argument("--output", required=True)
    return parser.parse_args(list(argv))


def main(argv: Sequence[str] | None = None) -> int:
    import sys

    arguments = _parse_cli(sys.argv[1:] if argv is None else argv)
    policy = load_policy(Path(arguments.policy), root=Path.cwd())
    if arguments.command == "fetch":
        receipt = fetch_android_repository_inventory(policy["android"]["repositoryInventory"])
    else:
        source = load_canonical_receipt(Path(arguments.source_receipt))
        receipt = capture_installed_android_packages(policy["android"], Path(arguments.sdk_root), source)
    write_canonical_receipt(Path(arguments.output), receipt)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
