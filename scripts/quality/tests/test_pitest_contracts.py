from __future__ import annotations

import hashlib
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from pitest_policy import MutationPolicyError, canonical_json_bytes
from pitest_policy.contracts import (
    build_capture_candidate,
    build_capture_evidence_manifest,
    build_capture_receipt,
    compare_linux_history,
    validate_identity_partition,
    validate_linux_profile,
)
from verify_pitest import (
    _observe_java_home,
    baseline_policy_identity_matches,
    host_neutral_mutation_identity,
    load_policy,
)

ROOT = Path(__file__).resolve().parents[3]


LINUX_PROFILE = {
    "kind": "github-hosted-image-observed-v1",
    "runnerLabel": "ubuntu-24.04",
    "platform": "Linux",
    "architecture": "x86_64",
    "image": {
        "ImageOS": "ubuntu24",
        "ImageVersion": "20260816.277.1",
        "runnerImagesTag": "ubuntu24/20260816.277",
        "runnerImagesTagCommit": "3b5f596ffecb076aa5f3c3ded95b145f6daeb016",
        "inventoryAsset": "internal.ubuntu24.json",
        "inventoryAssetDigest": "sha256:35b3696018cc49cc1b307943091be1578a18771ee3e375632495d3a027216f19",
    },
    "tools": {
        "env": {"path": "/usr/bin/env", "fileType": "regular", "symlink": False, "modePredicate": "any-executable-bit"},
        "bash": {"path": "/bin/bash", "fileType": "regular", "symlink": False, "modePredicate": "any-executable-bit"},
        "python": {
            "locator": "/usr/bin/python3",
            "locatorType": "symlink",
            "linkTarget": "python3.12",
            "path": "/usr/bin/python3.12",
            "fileType": "regular",
            "symlink": False,
            "modePredicate": "any-executable-bit",
            "semanticVersion": "3.12",
        },
        "git": {"path": "/usr/bin/git", "fileType": "regular", "symlink": False, "modePredicate": "any-executable-bit"},
    },
}


class IdentityPartitionTest(unittest.TestCase):
    def test_blocking_runner_and_one_time_policy_phase_transition_are_exact(self) -> None:
        runner = (ROOT / "scripts/quality/run_pitest.sh").read_text()
        self.assertNotIn("run_policy observe", runner)
        self.assertEqual(2, runner.count("run_policy verify"))

        observation = b'{"enforcementPhase":"observe","schemaVersion":1}\n'
        blocking = b'{"enforcementPhase":"blocking","schemaVersion":1}\n'
        observation_hash = hashlib.sha256(observation).hexdigest()
        self.assertTrue(baseline_policy_identity_matches(observation_hash, blocking, "blocking"))
        self.assertFalse(
            baseline_policy_identity_matches(
                observation_hash,
                b'{"enforcementPhase":"blocking","schemaVersion":2}\n',
                "blocking",
            ),
        )

    def test_checked_policy_and_neutral_identity_bind_explicit_utf8(self) -> None:
        policy, _, _ = load_policy()

        self.assertEqual("UTF-8", policy["pitest"]["defaultCharacterEncoding"])
        neutral = host_neutral_mutation_identity(policy)
        self.assertEqual("UTF-8", neutral["reportGeneration"]["defaultCharacterEncoding"])

    def test_java_runtime_identity_ignores_nondeterministic_vm_layout_diagnostics(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            java = Path(temporary) / "bin/java"
            java.parent.mkdir()
            java.write_bytes(b"stable-java-executable")
            java.chmod(0o755)
            common = b"    java.vendor = Eclipse Adoptium\n    java.version = 21.0.12.1\n"
            outputs = (
                common + b"    java.vm.compressedOopsMode = Non-zero based\n",
                common + b"    java.vm.compressedOopsMode = Non-zero disjoint base\n",
            )
            observations = []
            for output in outputs:
                with mock.patch(
                    "verify_pitest.subprocess.run",
                    return_value=subprocess.CompletedProcess([str(java)], 0, stdout=output),
                ):
                    observations.append(_observe_java_home(temporary))

        self.assertEqual(observations[0], observations[1])
        self.assertNotIn("runtimeOutputSha256", observations[0])
        self.assertEqual(
            hashlib.sha256(b"stable-java-executable").hexdigest(),
            observations[0]["executableSha256"],
        )

    def test_cross_host_identity_excludes_per_run_fields(self) -> None:
        neutral = {
            "schema": "host-neutral-mutation-identity-v1",
            "pitestPlugin": "1.19.0",
            "pitestEngine": "1.25.7",
            "java": {
                "major": 21,
                "vendorFamily": "Eclipse Adoptium/Temurin",
                "toolchainRole": "mutation-runtime",
            },
            "reportGeneration": {"threads": 2, "mutators": ["DEFAULTS"]},
        }
        per_run = {
            "schema": "per-run-execution-provenance-v1",
            "selectedProfile": "linux-x86_64",
            "profileDefinitionSha256": "a" * 64,
            "routeReceiptSha256": "b" * 64,
            "configurationSha256ByModule": {"station": "c" * 64},
            "javaExecutableSha256": "d" * 64,
            "observedToolBundleSha256": "e" * 64,
        }

        validate_identity_partition(neutral, per_run)

        for forbidden in (
            "selectedProfile",
            "routeReceiptSha256",
            "configurationSha256ByModule",
            "javaExecutableSha256",
            "observedToolBundleSha256",
        ):
            with self.subTest(forbidden=forbidden):
                forged = dict(neutral)
                forged[forbidden] = "x"
                with self.assertRaisesRegex(MutationPolicyError, "host-neutral identity contains per-run field"):
                    validate_identity_partition(forged, per_run)

    def test_neutral_java_tuple_is_exact(self) -> None:
        neutral = {
            "schema": "host-neutral-mutation-identity-v1",
            "pitestPlugin": "1.19.0",
            "pitestEngine": "1.25.7",
            "java": {"major": 21, "vendorFamily": "Microsoft", "toolchainRole": "mutation-runtime"},
            "reportGeneration": {},
        }
        with self.assertRaisesRegex(MutationPolicyError, "neutral Java identity"):
            validate_identity_partition(
                neutral,
                {
                    "schema": "per-run-execution-provenance-v1",
                    "selectedProfile": "darwin-arm64",
                    "profileDefinitionSha256": "a" * 64,
                    "routeReceiptSha256": "b" * 64,
                    "configurationSha256ByModule": {},
                    "javaExecutableSha256": "c" * 64,
                    "observedToolBundleSha256": None,
                },
            )


class LinuxObservedProfileTest(unittest.TestCase):
    def test_static_profile_has_image_identity_and_no_guessed_bytes_or_full_modes(self) -> None:
        validate_linux_profile(LINUX_PROFILE)

        for key, value in (("sha256", "0" * 64), ("mode", 0o755), ("versionSha256", "1" * 64)):
            with self.subTest(key=key):
                forged = {**LINUX_PROFILE, "tools": {name: dict(spec) for name, spec in LINUX_PROFILE["tools"].items()}}
                forged["tools"]["git"][key] = value
                with self.assertRaisesRegex(MutationPolicyError, "must not preapprove"):
                    validate_linux_profile(forged)

    def test_python_is_the_only_one_hop_locator_exception(self) -> None:
        forged = {**LINUX_PROFILE, "tools": {name: dict(spec) for name, spec in LINUX_PROFILE["tools"].items()}}
        forged["tools"]["python"]["linkTarget"] = "python3.11"
        with self.assertRaisesRegex(MutationPolicyError, "python3.12"):
            validate_linux_profile(forged)

    def test_checked_policy_uses_observed_linux_and_initial_not_established(self) -> None:
        policy, raw, digest = load_policy()

        self.assertEqual(64, len(digest))
        self.assertTrue(raw.endswith(b"\n"))
        validate_linux_profile(policy["bootstrapProfiles"]["linux-x86_64"])
        self.assertEqual("NOT_ESTABLISHED", policy["linuxHistoricalComparator"]["initialState"])
        self.assertEqual("reviewed-recapture-transition-only", policy["linuxHistoricalComparator"]["establishmentMode"])
        self.assertEqual("acyclic-candidate-and-separate-receipt-v1", policy["capturePolicy"]["schema"])


class AcyclicCaptureTest(unittest.TestCase):
    def test_initial_capture_is_candidate_then_separate_receipt(self) -> None:
        components = {
            "route": b"route\n",
            "tasks": b"tasks\n",
            "routeReceipt": b"route-receipt\n",
            "attempt": b"attempt\n",
            "completion": b"completion\n",
            "verificationSummary": b"initial-summary\n",
        }
        manifest = build_capture_evidence_manifest(
            components=components,
            policy_sha256="1" * 64,
            predecessor_baseline_sha256=None,
            predecessor_verification_receipt_sha256=None,
            source_commit="2" * 40,
            host_neutral_identity_sha256="3" * 64,
            per_run_provenance_sha256="4" * 64,
        )
        digest = hashlib.sha256(canonical_json_bytes(manifest)).hexdigest()
        candidate = build_capture_candidate(
            payload={"schemaVersion": 1, "sourceCommit": "2" * 40},
            predecessor_baseline_sha256=None,
            capture_evidence_digest=digest,
        )
        candidate_raw = canonical_json_bytes(candidate)
        capture_receipt = build_capture_receipt(
            candidate_baseline=candidate_raw,
            evidence_manifest=manifest,
        )

        self.assertIsNone(candidate["predecessorBaselineHash"])
        self.assertEqual(digest, candidate["captureEvidenceDigest"])
        for forbidden in ("baselineSha256", "candidateSha256", "captureReceiptHash", "verificationReceiptHash"):
            self.assertNotIn(forbidden, candidate)
        self.assertEqual(hashlib.sha256(candidate_raw).hexdigest(), capture_receipt["candidateBaselineSha256"])
        self.assertEqual(digest, capture_receipt["captureEvidenceDigest"])
        self.assertNotIn("captureReceiptSha256", capture_receipt)

    def test_initial_capture_rejects_predecessors_and_candidate_self_links(self) -> None:
        with self.assertRaisesRegex(MutationPolicyError, "initial capture predecessors must both be null"):
            build_capture_evidence_manifest(
                components={"route": b"route"},
                policy_sha256="1" * 64,
                predecessor_baseline_sha256="2" * 64,
                predecessor_verification_receipt_sha256=None,
                source_commit="3" * 40,
                host_neutral_identity_sha256="4" * 64,
                per_run_provenance_sha256="5" * 64,
            )
        with self.assertRaisesRegex(MutationPolicyError, "successor/self field"):
            build_capture_candidate(
                payload={"schemaVersion": 1, "sourceCommit": "3" * 40, "candidateSha256": "x"},
                predecessor_baseline_sha256=None,
                capture_evidence_digest="4" * 64,
            )


class LinuxComparatorStateTest(unittest.TestCase):
    def test_not_established_is_truthful_and_only_transition_can_establish(self) -> None:
        initial = {"state": "NOT_ESTABLISHED"}
        self.assertEqual(
            {"historicalLinuxComparison": "NOT_ESTABLISHED", "establishedDigest": None},
            compare_linux_history(initial, current_observed_digest="a" * 64, transition=False),
        )
        with self.assertRaisesRegex(MutationPolicyError, "reviewed recapture-transition"):
            compare_linux_history(initial, current_observed_digest="a" * 64, transition=False, requested_establishment=True)

        established = compare_linux_history(
            initial,
            current_observed_digest="a" * 64,
            transition=True,
            requested_establishment=True,
        )
        self.assertEqual("ESTABLISHED", established["historicalLinuxComparison"])
        self.assertEqual("a" * 64, established["establishedDigest"])

        with self.assertRaisesRegex(MutationPolicyError, "reviewed-recapture-required"):
            compare_linux_history(
                {"state": "ESTABLISHED", "observedBundleSha256": "a" * 64},
                current_observed_digest="b" * 64,
                transition=False,
            )


if __name__ == "__main__":
    unittest.main()
