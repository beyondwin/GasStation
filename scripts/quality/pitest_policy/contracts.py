"""Pure closed-schema contracts for Task-7 identity and capture evidence."""

from __future__ import annotations

import hashlib
import re
from collections.abc import Mapping

from . import MutationPolicyError, canonical_json_bytes


_SHA256 = re.compile(r"[0-9a-f]{64}")
_COMMIT = re.compile(r"[0-9a-f]{40}")
_NEUTRAL_JAVA = {
    "major": 21,
    "vendorFamily": "Eclipse Adoptium/Temurin",
    "toolchainRole": "mutation-runtime",
}
_PER_RUN_ONLY_NAMES = {
    "absolutePath",
    "captureProfile",
    "configurationSha256ByModule",
    "image",
    "imageIdentity",
    "javaExecutableSha256",
    "observedToolBundle",
    "observedToolBundleSha256",
    "perRunExecutionProvenance",
    "profileDefinitionSha256",
    "rawConfigurationSha256",
    "routeReceiptSha256",
    "selectedProfile",
}
_SUCCESSOR_OR_SELF_NAMES = {
    "baselineSha256",
    "candidateBaselineSha256",
    "candidateSha256",
    "captureReceiptHash",
    "captureReceiptSha256",
    "finalReceiptHash",
    "successorHash",
    "verificationReceiptHash",
}
_INITIAL_CAPTURE_COMPONENTS = {
    "policy",
    "sourceCommit",
    "route",
    "tasks",
    "routeReceipt",
    "attempt",
    "configuration:location",
    "configuration:settings",
    "configuration:station",
    "completion",
    "measurement",
    "xml:location",
    "xml:settings",
    "xml:station",
    "semantic:location",
    "semantic:settings",
    "semantic:station",
    "html:location",
    "html:settings",
    "html:station",
    "verificationSummary",
}


def _require_sha256(value: object, label: str, *, nullable: bool = False) -> str | None:
    if value is None and nullable:
        return None
    if not isinstance(value, str) or _SHA256.fullmatch(value) is None:
        raise MutationPolicyError(f"{label} must be an exact lowercase SHA-256")
    return value


def _all_keys(value: object) -> set[str]:
    if isinstance(value, Mapping):
        result = set(value)
        for child in value.values():
            result.update(_all_keys(child))
        return result
    if isinstance(value, list):
        result: set[str] = set()
        for child in value:
            result.update(_all_keys(child))
        return result
    return set()


def validate_identity_partition(
    host_neutral: Mapping[str, object],
    per_run: Mapping[str, object],
) -> None:
    if host_neutral.get("schema") != "host-neutral-mutation-identity-v1":
        raise MutationPolicyError("host-neutral identity schema differs")
    forbidden = sorted(_all_keys(host_neutral) & _PER_RUN_ONLY_NAMES)
    if forbidden:
        raise MutationPolicyError(
            "host-neutral identity contains per-run field: " + ",".join(forbidden)
        )
    if host_neutral.get("java") != _NEUTRAL_JAVA:
        raise MutationPolicyError("neutral Java identity must use the reviewed Temurin 21 mutation-runtime tuple")
    if per_run.get("schema") != "per-run-execution-provenance-v1":
        raise MutationPolicyError("per-run execution provenance schema differs")
    required = {
        "schema",
        "selectedProfile",
        "profileDefinitionSha256",
        "routeReceiptSha256",
        "configurationSha256ByModule",
        "javaExecutableSha256",
        "observedToolBundleSha256",
    }
    if set(per_run) != required:
        raise MutationPolicyError("per-run execution provenance keys differ from the closed schema")
    if per_run["selectedProfile"] not in {"darwin-arm64", "linux-x86_64"}:
        raise MutationPolicyError("per-run selected profile differs")
    for name in ("profileDefinitionSha256", "routeReceiptSha256", "javaExecutableSha256"):
        _require_sha256(per_run[name], name)
    _require_sha256(per_run["observedToolBundleSha256"], "observedToolBundleSha256", nullable=True)
    configuration = per_run["configurationSha256ByModule"]
    if not isinstance(configuration, Mapping) or sorted(configuration) not in (
        [],
        ["location", "settings", "station"],
        ["station"],
    ):
        raise MutationPolicyError("per-run module configuration identities differ")
    for module, digest in configuration.items():
        _require_sha256(digest, f"{module} configuration SHA-256")


def validate_linux_profile(profile: Mapping[str, object]) -> None:
    expected_top = {"kind", "runnerLabel", "platform", "architecture", "image", "tools"}
    if set(profile) != expected_top:
        raise MutationPolicyError("Linux profile keys differ from the closed schema")
    expected_identity = {
        "kind": "github-hosted-image-observed-v1",
        "runnerLabel": "ubuntu-24.04",
        "platform": "Linux",
        "architecture": "x86_64",
    }
    for name, expected in expected_identity.items():
        if profile.get(name) != expected:
            raise MutationPolicyError(f"Linux reviewed profile differs: {name}")
    expected_image = {
        "ImageOS": "ubuntu24",
        "ImageVersion": "20260816.277.1",
        "runnerImagesTag": "ubuntu24/20260816.277",
        "runnerImagesTagCommit": "3b5f596ffecb076aa5f3c3ded95b145f6daeb016",
        "inventoryAsset": "internal.ubuntu24.json",
        "inventoryAssetDigest": "sha256:35b3696018cc49cc1b307943091be1578a18771ee3e375632495d3a027216f19",
    }
    if profile.get("image") != expected_image:
        raise MutationPolicyError("Linux reviewed image identity differs")
    tools = profile.get("tools")
    if not isinstance(tools, Mapping) or sorted(tools) != ["bash", "env", "git", "python"]:
        raise MutationPolicyError("Linux observed-tool definitions differ")
    guessed = {"sha256", "contentSha256", "versionSha256", "mode", "numericMode"}
    for name, raw in tools.items():
        if not isinstance(raw, Mapping):
            raise MutationPolicyError(f"Linux {name} tool definition must be an object")
        if set(raw) & guessed:
            raise MutationPolicyError(f"Linux static profile must not preapprove executable bytes or full mode: {name}")
        if raw.get("fileType") != "regular" or raw.get("symlink") is not False:
            raise MutationPolicyError(f"Linux canonical {name} must be regular and non-symlink")
        if raw.get("modePredicate") != "any-executable-bit":
            raise MutationPolicyError(f"Linux canonical {name} must use the static executable-bit predicate")
    python = tools["python"]
    if (
        python.get("locator") != "/usr/bin/python3"
        or python.get("locatorType") != "symlink"
        or python.get("linkTarget") != "python3.12"
        or python.get("path") != "/usr/bin/python3.12"
        or python.get("semanticVersion") != "3.12"
    ):
        raise MutationPolicyError("Linux Python locator must be exactly /usr/bin/python3 -> python3.12")
    for name, expected_path in {"env": "/usr/bin/env", "bash": "/bin/bash", "git": "/usr/bin/git"}.items():
        if tools[name].get("path") != expected_path or "locator" in tools[name]:
            raise MutationPolicyError(f"Linux {name} path differs or adds an unsupported locator")


def build_capture_evidence_manifest(
    *,
    components: Mapping[str, bytes],
    policy_sha256: str,
    predecessor_baseline_sha256: str | None,
    predecessor_verification_receipt_sha256: str | None,
    source_commit: str,
    host_neutral_identity_sha256: str,
    per_run_provenance_sha256: str,
) -> dict[str, object]:
    _require_sha256(policy_sha256, "policySha256")
    _require_sha256(predecessor_baseline_sha256, "predecessorBaselineHash", nullable=True)
    _require_sha256(
        predecessor_verification_receipt_sha256,
        "predecessorVerificationReceiptHash",
        nullable=True,
    )
    _require_sha256(host_neutral_identity_sha256, "hostNeutralMutationIdentitySha256")
    _require_sha256(per_run_provenance_sha256, "perRunExecutionProvenanceSha256")
    if _COMMIT.fullmatch(source_commit) is None:
        raise MutationPolicyError("capture source commit must be an exact lowercase commit")
    if (predecessor_baseline_sha256 is None) != (predecessor_verification_receipt_sha256 is None):
        raise MutationPolicyError("initial capture predecessors must both be null; future capture predecessors must both exist")
    required = set(_INITIAL_CAPTURE_COMPONENTS)
    if predecessor_baseline_sha256 is not None:
        required.add("predecessorVerificationReceipt")
    if set(components) != required:
        missing = sorted(required - set(components))
        extra = sorted(set(components) - required)
        raise MutationPolicyError(
            f"capture evidence component set differs: missing={missing} extra={extra}"
        )
    if set(components) & _SUCCESSOR_OR_SELF_NAMES:
        raise MutationPolicyError("capture evidence contains a successor/self field")
    return {
        "schema": "pitest-capture-evidence-manifest-v1",
        "sourceCommit": source_commit,
        "policySha256": policy_sha256,
        "predecessorBaselineHash": predecessor_baseline_sha256,
        "predecessorVerificationReceiptHash": predecessor_verification_receipt_sha256,
        "hostNeutralMutationIdentitySha256": host_neutral_identity_sha256,
        "perRunExecutionProvenanceSha256": per_run_provenance_sha256,
        "components": {
            name: hashlib.sha256(payload).hexdigest()
            for name, payload in sorted(components.items())
        },
    }


def build_capture_candidate(
    *,
    payload: Mapping[str, object],
    predecessor_baseline_sha256: str | None,
    capture_evidence_digest: str,
) -> dict[str, object]:
    _require_sha256(predecessor_baseline_sha256, "predecessorBaselineHash", nullable=True)
    _require_sha256(capture_evidence_digest, "captureEvidenceDigest")
    forbidden = sorted(_all_keys(payload) & _SUCCESSOR_OR_SELF_NAMES)
    if forbidden:
        raise MutationPolicyError("candidate baseline contains successor/self field: " + ",".join(forbidden))
    if "predecessorBaselineHash" in payload or "captureEvidenceDigest" in payload:
        raise MutationPolicyError("candidate graph fields are owned by the capture builder")
    return {
        **payload,
        "predecessorBaselineHash": predecessor_baseline_sha256,
        "captureEvidenceDigest": capture_evidence_digest,
    }


def build_capture_receipt(
    *,
    candidate_baseline: bytes,
    evidence_manifest: Mapping[str, object],
    evidence_archive: Mapping[str, object] | None = None,
) -> dict[str, object]:
    if evidence_manifest.get("schema") != "pitest-capture-evidence-manifest-v1":
        raise MutationPolicyError("capture receipt requires the typed capture-evidence manifest")
    manifest_digest = hashlib.sha256(canonical_json_bytes(dict(evidence_manifest))).hexdigest()
    if evidence_archive is not None:
        if set(evidence_archive) != {"path", "sha256"}:
            raise MutationPolicyError("capture receipt archive reference keys differ")
        path = evidence_archive.get("path")
        archive_sha256 = evidence_archive.get("sha256")
        if not isinstance(path, str) or not path.startswith("config/quality/mutation-evidence/"):
            raise MutationPolicyError("capture receipt archive path differs")
        _require_sha256(archive_sha256, "capture receipt archive SHA-256")
    return {
        "schema": "pitest-capture-receipt-v2" if evidence_archive is not None else "pitest-capture-receipt-v1",
        "candidateBaselineSha256": hashlib.sha256(candidate_baseline).hexdigest(),
        "predecessorBaselineHash": evidence_manifest.get("predecessorBaselineHash"),
        "captureEvidenceDigest": manifest_digest,
        "evidenceManifest": dict(evidence_manifest),
        **({"evidenceArchive": dict(evidence_archive)} if evidence_archive is not None else {}),
    }


def compare_linux_history(
    history: Mapping[str, object],
    *,
    current_observed_digest: str,
    transition: bool,
    requested_establishment: bool = False,
) -> dict[str, object]:
    _require_sha256(current_observed_digest, "current Linux observed bundle")
    state = history.get("state")
    if state == "NOT_ESTABLISHED":
        if requested_establishment and not transition:
            raise MutationPolicyError("Linux comparator establishment requires a reviewed recapture-transition")
        if requested_establishment:
            return {
                "historicalLinuxComparison": "ESTABLISHED",
                "establishedDigest": current_observed_digest,
            }
        return {
            "historicalLinuxComparison": "NOT_ESTABLISHED",
            "establishedDigest": None,
        }
    if state == "ESTABLISHED":
        expected = _require_sha256(history.get("observedBundleSha256"), "established Linux observed bundle")
        if current_observed_digest != expected:
            raise MutationPolicyError("reviewed-recapture-required: established Linux observed bundle drifted")
        return {
            "historicalLinuxComparison": "ESTABLISHED",
            "establishedDigest": expected,
        }
    raise MutationPolicyError("Linux profile-history state is invalid")


__all__ = [
    "build_capture_candidate",
    "build_capture_evidence_manifest",
    "build_capture_receipt",
    "compare_linux_history",
    "validate_identity_partition",
    "validate_linux_profile",
]
