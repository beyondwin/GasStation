from __future__ import annotations

import hashlib
import os
import re
import base64
import binascii
import uuid
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping


class DownloadError(RuntimeError):
    """A versioned payload could not be downloaded with the required byte identity."""


@dataclass(frozen=True)
class VerifiedDownload:
    path: Path
    receipt: dict[str, Any]


_JWT = re.compile(r"^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$")
_UTC_SECOND = re.compile(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")
_PERCENT_ESCAPE = re.compile(r"%(?![0-9A-Fa-f]{2})")


def _validate_url(url: str, *, allowed_hosts: set[str], allow_loopback_http: bool) -> None:
    parsed = urllib.parse.urlsplit(url)
    loopback = allow_loopback_http and parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "::1", "localhost"}
    if parsed.scheme != "https" and not loopback:
        raise DownloadError("download URL must use HTTPS")
    if (
        not parsed.hostname
        or parsed.hostname not in allowed_hosts
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
    ):
        raise DownloadError("download URL host/userinfo/query/fragment violates policy")


class _ClosedRedirectHandler(urllib.request.HTTPRedirectHandler):
    def __init__(self, *, allowed_hosts: set[str], allow_loopback_http: bool) -> None:
        super().__init__()
        self._allowed_hosts = allowed_hosts
        self._allow_loopback_http = allow_loopback_http

    def redirect_request(self, req, fp, code, msg, headers, newurl):  # type: ignore[no-untyped-def]
        _validate_url(
            newurl,
            allowed_hosts=self._allowed_hosts,
            allow_loopback_http=self._allow_loopback_http,
        )
        return super().redirect_request(req, fp, code, msg, headers, newurl)


class _NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # type: ignore[no-untyped-def]
        return None


def _decode_form_component(raw: str) -> str:
    if _PERCENT_ESCAPE.search(raw):
        raise DownloadError("signed redirect query encoding violates policy")
    try:
        value = urllib.parse.unquote_to_bytes(raw.replace("+", " ")).decode("utf-8", "strict")
    except UnicodeError as error:
        raise DownloadError("signed redirect query encoding violates policy") from error
    if not value or any(ord(character) < 32 or ord(character) == 127 for character in value):
        raise DownloadError("signed redirect query value violates policy")
    return value


def _parse_signed_query(query: str) -> dict[str, str]:
    if not query:
        raise DownloadError("signed redirect query is missing")
    result: dict[str, str] = {}
    for segment in query.split("&"):
        if not segment or segment.count("=") != 1:
            raise DownloadError("signed redirect query shape violates policy")
        raw_key, raw_value = segment.split("=", 1)
        key = _decode_form_component(raw_key)
        value = _decode_form_component(raw_value)
        if key in result:
            raise DownloadError("signed redirect query contains a duplicate key")
        result[key] = value
    return result


def _parse_utc_second(value: str) -> datetime:
    if _UTC_SECOND.fullmatch(value) is None:
        raise DownloadError("signed redirect timestamp grammar violates policy")
    try:
        parsed = datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    except ValueError as error:
        raise DownloadError("signed redirect timestamp grammar violates policy") from error
    return parsed


def _validate_redirect_endpoint(
    parsed: urllib.parse.SplitResult,
    *,
    expected_host: str,
    expected_path: str,
    allow_loopback_http: bool,
) -> None:
    loopback = (
        allow_loopback_http
        and parsed.scheme == "http"
        and parsed.hostname in {"127.0.0.1", "::1", "localhost"}
        and expected_host in {"127.0.0.1", "::1", "localhost"}
    )
    if parsed.scheme != "https" and not loopback:
        raise DownloadError("signed redirect endpoint must use HTTPS")
    if (
        parsed.hostname != expected_host
        or (not loopback and parsed.netloc != expected_host)
        or parsed.username is not None
        or parsed.password is not None
        or parsed.fragment
        or parsed.path != expected_path
        or (not loopback and parsed.port is not None)
    ):
        raise DownloadError("signed redirect endpoint violates policy")


def validate_github_release_asset_redirect(
    location: str,
    *,
    redirect_contract: Mapping[str, Any],
    allow_loopback_http: bool = False,
) -> dict[str, Any]:
    """Validate one ephemeral GitHub release-asset Location without retaining secrets."""
    try:
        parsed = urllib.parse.urlsplit(location)
        expected_host = redirect_contract["finalHost"]
        expected_path = redirect_contract["finalPath"]
        expected_keys = redirect_contract["queryKeys"]
        fixed_values = redirect_contract["fixedQueryValues"]
        timestamp_keys = redirect_contract["timestampKeys"]
        uuid_keys = redirect_contract["uuidKeys"]
        if not isinstance(expected_host, str) or not isinstance(expected_path, str):
            raise DownloadError("signed redirect contract endpoint is malformed")
        if not isinstance(expected_keys, list) or not all(isinstance(key, str) for key in expected_keys):
            raise DownloadError("signed redirect contract keys are malformed")
        if not isinstance(fixed_values, dict) or not all(
            isinstance(key, str) and isinstance(value, str) for key, value in fixed_values.items()
        ):
            raise DownloadError("signed redirect fixed values are malformed")
        _validate_redirect_endpoint(
            parsed,
            expected_host=expected_host,
            expected_path=expected_path,
            allow_loopback_http=allow_loopback_http,
        )
        values = _parse_signed_query(parsed.query)
        if set(values) != set(expected_keys) or len(values) != len(expected_keys):
            raise DownloadError("signed redirect query keys violate policy")
        if any(values.get(key) != value for key, value in fixed_values.items()):
            raise DownloadError("signed redirect fixed query values violate policy")
        jwt = values["jwt"]
        if len(jwt) != redirect_contract["jwtLength"] or _JWT.fullmatch(jwt) is None:
            raise DownloadError("signed redirect JWT grammar violates policy")
        signature = values["sig"]
        if len(signature) != redirect_contract["signatureLength"]:
            raise DownloadError("signed redirect signature grammar violates policy")
        try:
            decoded_signature = base64.b64decode(signature, validate=True)
        except (ValueError, binascii.Error) as error:
            raise DownloadError("signed redirect signature grammar violates policy") from error
        if len(decoded_signature) != 32:
            raise DownloadError("signed redirect signature grammar violates policy")
        if timestamp_keys != ["skt", "se", "ske"] or uuid_keys != ["skoid", "sktid"]:
            raise DownloadError("signed redirect grammar contract is malformed")
        times = [_parse_utc_second(values[key]) for key in timestamp_keys]
        if not times[0] <= times[1] <= times[2]:
            raise DownloadError("signed redirect timestamp order violates policy")
        for key in uuid_keys:
            try:
                parsed_uuid = uuid.UUID(values[key])
            except ValueError as error:
                raise DownloadError("signed redirect UUID grammar violates policy") from error
            if str(parsed_uuid) != values[key]:
                raise DownloadError("signed redirect UUID grammar violates policy")
        raw = location.encode("utf-8")
        return {
            "host": expected_host,
            "path": expected_path.removeprefix("/"),
            "queryKeys": sorted(values),
            "rawSha256": hashlib.sha256(raw).hexdigest(),
            "rawSize": len(raw),
            "scheme": parsed.scheme,
            "validation": {
                "fixedValues": True,
                "jwtGrammar": True,
                "signatureGrammar": True,
                "timestampGrammarAndOrder": True,
                "uuidGrammar": True,
            },
        }
    except (KeyError, TypeError) as error:
        raise DownloadError("signed redirect contract is malformed") from error


def _validate_initial_release_url(url: str, contract: Mapping[str, Any], *, allow_loopback_http: bool) -> None:
    if contract.get("initialUrl") != url:
        raise DownloadError("initial release URL differs from policy")
    parsed = urllib.parse.urlsplit(url)
    loopback = allow_loopback_http and parsed.scheme == "http" and parsed.hostname in {
        "127.0.0.1", "::1", "localhost"
    }
    if parsed.scheme != "https" and not loopback:
        raise DownloadError("initial release URL must use HTTPS")
    if (
        not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
        or (not loopback and (parsed.hostname != "github.com" or parsed.port is not None))
    ):
        raise DownloadError("initial release URL violates policy")


def download_verified_github_release_asset(
    url: str,
    *,
    destination: Path,
    expected_size: int,
    expected_sha256: str,
    redirect_contract: Mapping[str, Any],
    allow_loopback_http: bool = False,
) -> VerifiedDownload:
    """Download one reviewed GitHub asset through its exact one-hop signed redirect."""
    _validate_initial_release_url(url, redirect_contract, allow_loopback_http=allow_loopback_http)
    if redirect_contract.get("redirectCount") != 1 or redirect_contract.get("initialStatus") != 302:
        raise DownloadError("release redirect count/status contract is malformed")
    if redirect_contract.get("finalStatus") != 200:
        raise DownloadError("release final status contract is malformed")
    if type(expected_size) is not int or expected_size <= 0:
        raise DownloadError("expected size must be a positive integer")
    if len(expected_sha256) != 64 or any(character not in "0123456789abcdef" for character in expected_sha256):
        raise DownloadError("expected digest must be a lowercase SHA-256")
    headers_contract = redirect_contract.get("finalHeaders")
    if headers_contract != {
        "acceptRanges": "bytes",
        "contentLength": expected_size,
        "contentType": "application/octet-stream",
    }:
        raise DownloadError("release final header contract is malformed")
    if destination.exists() or destination.is_symlink():
        raise DownloadError("download destination already exists")
    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = destination.parent / f".{destination.name}.partial"
    if partial.exists() or partial.is_symlink():
        raise DownloadError("stale partial download exists")
    opener = urllib.request.build_opener(_NoRedirectHandler())
    request_headers = {"User-Agent": "GasStation-build-input-verifier/1"}
    try:
        initial_request = urllib.request.Request(url, headers=request_headers)
        try:
            opener.open(initial_request, timeout=60)
        except urllib.error.HTTPError as response:
            if response.code != 302:
                response.close()
                raise DownloadError("initial release request status violates policy") from None
            location = response.headers.get("Location")
            response.close()
        except (urllib.error.URLError, OSError):
            raise DownloadError("initial release request failed") from None
        else:
            raise DownloadError("initial release request did not return the required redirect")
        if not isinstance(location, str):
            raise DownloadError("initial release redirect is missing")
        location_receipt = validate_github_release_asset_redirect(
            location,
            redirect_contract=redirect_contract,
            allow_loopback_http=allow_loopback_http,
        )
        final_request = urllib.request.Request(location, headers=request_headers)
        try:
            response = opener.open(final_request, timeout=60)
        except urllib.error.HTTPError as error:
            error.close()
            raise DownloadError("release asset request status violates policy") from None
        except (urllib.error.URLError, OSError):
            raise DownloadError("release asset request failed") from None
        with response, partial.open("xb") as output:
            if response.status != 200:
                raise DownloadError("release asset request status violates policy")
            final_url = response.geturl()
            if final_url != location:
                raise DownloadError("release asset effective URL violates policy")
            declared_headers = {
                "acceptRanges": response.headers.get("Accept-Ranges"),
                "contentLength": response.headers.get("Content-Length"),
                "contentType": response.headers.get("Content-Type"),
            }
            if declared_headers != {
                "acceptRanges": "bytes",
                "contentLength": str(expected_size),
                "contentType": "application/octet-stream",
            }:
                raise DownloadError("release asset response headers violate policy")
            digest = hashlib.sha256()
            count = 0
            while True:
                chunk = response.read(min(1024 * 1024, expected_size + 1 - count))
                if not chunk:
                    break
                count += len(chunk)
                if count > expected_size:
                    raise DownloadError("release asset exceeds policy size")
                digest.update(chunk)
                output.write(chunk)
            output.flush()
            os.fsync(output.fileno())
        actual_sha256 = digest.hexdigest()
        if count != expected_size:
            raise DownloadError("release asset is truncated")
        if actual_sha256 != expected_sha256:
            raise DownloadError("release asset SHA-256 differs from policy")
        os.replace(partial, destination)
        effective_raw = location.encode("utf-8")
        final_parsed = urllib.parse.urlsplit(location)
        receipt = {
            "archiveSha256": actual_sha256,
            "archiveSize": count,
            "final": {
                "acceptRanges": "bytes",
                "contentLength": expected_size,
                "contentType": "application/octet-stream",
                "effectiveUrlSha256": hashlib.sha256(effective_raw).hexdigest(),
                "effectiveUrlSize": len(effective_raw),
                "host": redirect_contract["finalHost"],
                "path": str(redirect_contract["finalPath"]).removeprefix("/"),
                "scheme": final_parsed.scheme,
                "status": 200,
            },
            "initialStatus": 302,
            "initialUrl": url,
            "location": location_receipt,
            "redirectCount": 1,
        }
        return VerifiedDownload(path=destination, receipt=receipt)
    except Exception:
        partial.unlink(missing_ok=True)
        raise


def download_verified(
    url: str,
    *,
    destination: Path,
    expected_size: int,
    expected_sha256: str,
    allowed_hosts: set[str],
    allow_loopback_http: bool = False,
) -> Path:
    _validate_url(url, allowed_hosts=allowed_hosts, allow_loopback_http=allow_loopback_http)
    if type(expected_size) is not int or expected_size <= 0:
        raise DownloadError("expected size must be a positive integer")
    if len(expected_sha256) != 64 or any(character not in "0123456789abcdef" for character in expected_sha256):
        raise DownloadError("expected digest must be a lowercase SHA-256")
    if destination.exists() or destination.is_symlink():
        raise DownloadError("download destination already exists")
    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = destination.parent / f".{destination.name}.partial"
    if partial.exists() or partial.is_symlink():
        raise DownloadError("stale partial download exists")
    opener = urllib.request.build_opener(
        _ClosedRedirectHandler(
            allowed_hosts=allowed_hosts,
            allow_loopback_http=allow_loopback_http,
        ),
    )
    digest = hashlib.sha256()
    count = 0
    try:
        request = urllib.request.Request(url, headers={"User-Agent": "GasStation-build-input-verifier/1"})
        try:
            response = opener.open(request, timeout=60)
        except (urllib.error.URLError, OSError) as error:
            raise DownloadError(f"download failed: {error}") from error
        with response, partial.open("xb") as output:
            final_url = response.geturl()
            _validate_url(
                final_url,
                allowed_hosts=allowed_hosts,
                allow_loopback_http=allow_loopback_http,
            )
            declared = response.headers.get("Content-Length")
            if declared is not None:
                try:
                    declared_size = int(declared)
                except ValueError as error:
                    raise DownloadError("payload Content-Length is malformed") from error
                if declared_size != expected_size:
                    raise DownloadError("payload Content-Length differs from policy")
            while True:
                chunk = response.read(min(1024 * 1024, expected_size + 1 - count))
                if not chunk:
                    break
                count += len(chunk)
                if count > expected_size:
                    raise DownloadError("payload exceeds policy size")
                digest.update(chunk)
                output.write(chunk)
            output.flush()
            os.fsync(output.fileno())
        if count != expected_size:
            raise DownloadError("payload is truncated")
        if digest.hexdigest() != expected_sha256:
            raise DownloadError("payload SHA-256 differs from policy")
        os.replace(partial, destination)
        return destination
    except Exception:
        partial.unlink(missing_ok=True)
        raise
