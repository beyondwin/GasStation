from __future__ import annotations

import hashlib
import os
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


class DownloadError(RuntimeError):
    """A versioned payload could not be downloaded with the required byte identity."""


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
