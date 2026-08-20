from __future__ import annotations

import io
import os
import shutil
import tarfile
import tempfile
from pathlib import Path, PurePosixPath


class ArchiveError(ValueError):
    """Archive bytes do not satisfy the closed JDK extraction contract."""


def _validated_relative(member: tarfile.TarInfo, archive_root: str) -> PurePosixPath:
    path = PurePosixPath(member.name)
    if path.is_absolute() or not path.parts or path.parts[0] != archive_root or any(part in {"", ".", ".."} for part in path.parts):
        raise ArchiveError(f"unsafe archive path: {member.name}")
    return PurePosixPath(*path.parts[1:])


def safe_extract_tar(data: bytes, *, destination: Path, archive_root: str) -> None:
    if destination.exists() or destination.is_symlink():
        raise ArchiveError(f"destination already exists: {destination.name}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=f".{destination.name}-", dir=destination.parent))
    seen: set[PurePosixPath] = set()
    try:
        try:
            archive = tarfile.open(fileobj=io.BytesIO(data), mode="r:gz")
        except (tarfile.TarError, OSError) as error:
            raise ArchiveError(f"invalid tar archive: {error}") from error
        with archive:
            for member in archive.getmembers():
                relative = _validated_relative(member, archive_root)
                if relative == PurePosixPath("."):
                    continue
                if relative in seen:
                    raise ArchiveError(f"duplicate archive path: {member.name}")
                seen.add(relative)
                target = staging.joinpath(*relative.parts)
                if member.islnk() or member.isdev() or member.isfifo():
                    raise ArchiveError(f"unsupported archive entry: {member.name}")
                if member.isdir():
                    target.mkdir(parents=True, exist_ok=False)
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                if member.issym():
                    link = PurePosixPath(member.linkname)
                    if link.is_absolute():
                        raise ArchiveError(f"unsafe archive link: {member.name}")
                    resolved = os.path.normpath(str(relative.parent / link))
                    if resolved == ".." or resolved.startswith("../"):
                        raise ArchiveError(f"unsafe archive link: {member.name}")
                    target.symlink_to(member.linkname)
                    continue
                if not member.isfile():
                    raise ArchiveError(f"unsupported archive entry: {member.name}")
                source = archive.extractfile(member)
                if source is None:
                    raise ArchiveError(f"archive entry has no bytes: {member.name}")
                with target.open("xb") as output:
                    shutil.copyfileobj(source, output)
                target.chmod(member.mode & 0o777)
        staging.rename(destination)
    except Exception:
        shutil.rmtree(staging, ignore_errors=True)
        raise
