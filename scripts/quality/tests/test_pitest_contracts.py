from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from pitest_policy import GitCommand, GitExecutor, MutationPolicyError, canonical_json_bytes
from pitest_policy.contracts import (
    build_capture_candidate,
    build_capture_evidence_manifest,
    build_capture_receipt,
    compare_linux_history,
    validate_identity_partition,
    validate_linux_profile,
)
from verify_pitest import (
    BaselineSnapshot,
    _validate_archived_components,
    _checked_blob,
    _capture_receipt_link,
    _observe_java_home,
    baseline_policy_identity_matches,
    host_neutral_mutation_identity,
    load_policy,
    install_successor_atomically,
    validate_baseline_schema,
    validate_baseline_capture_receipt,
    seal_verification,
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
    def _clone_checked_repository(self, destination: Path) -> None:
        subprocess.run(
            ["git", "clone", "--quiet", "--no-local", str(ROOT), str(destination)],
            check=True,
        )
        subprocess.run(["git", "config", "user.name", "Task 7 Test"], cwd=destination, check=True)
        subprocess.run(["git", "config", "user.email", "task7-test@example.invalid"], cwd=destination, check=True)
        (destination / "config/quality/mutation-policy.json").write_bytes(
            (ROOT / "config/quality/mutation-policy.json").read_bytes()
        )
        changed = subprocess.run(
            ["git", "diff", "--quiet", "--", "config/quality/mutation-policy.json"],
            cwd=destination,
            check=False,
        ).returncode
        if changed:
            subprocess.run(["git", "add", "config/quality/mutation-policy.json"], cwd=destination, check=True)
            subprocess.run(["git", "commit", "--quiet", "-m", "install round3 policy fixture"], cwd=destination, check=True)

    def _validate_clone_baseline(self, repository: Path) -> None:
        baseline_path = repository / "config/quality/mutation-baseline.json"
        receipt_root = repository / "config/quality/mutation-captures"
        transition_root = repository / "config/quality/mutation-transitions"
        baseline_raw = baseline_path.read_bytes()
        baseline = json.loads(baseline_raw)
        git_path = Path(shutil.which("git") or "/usr/bin/git")
        git = GitExecutor(
            repository,
            git_path=git_path,
            home=repository / ".test-git-home",
            tmpdir=repository / ".test-git-tmp",
        )
        git.assert_original_full_history()
        patches = {
            "REPOSITORY_ROOT": repository,
            "POLICY_PATH": repository / "config/quality/mutation-policy.json",
            "BASELINE_PATH": baseline_path,
            "CAPTURE_RECEIPT_ROOT": receipt_root,
            "TRANSITION_ROOT": transition_root,
            "EVIDENCE_ARCHIVE_ROOT": repository / "config/quality/mutation-evidence",
        }
        with mock.patch.multiple("verify_pitest", **patches):
            validate_baseline_capture_receipt(baseline_raw, baseline, git=git)

    @staticmethod
    def _commit_clone(repository: Path, message: str) -> None:
        subprocess.run(["git", "add", "config/quality"], cwd=repository, check=True)
        subprocess.run(["git", "commit", "--quiet", "-m", message], cwd=repository, check=True)

    def test_newly_committed_resigned_successor_rejects_correctly_hashed_typed_invalid_attempt(self) -> None:
        """Catches accepting a correctly hashed archive whose attempt has no typed links."""
        with tempfile.TemporaryDirectory(dir=ROOT / "build") as directory:
            repository = Path(directory) / "forged-successor"
            self._clone_checked_repository(repository)
            baseline_path = repository / "config/quality/mutation-baseline.json"
            receipt_root = repository / "config/quality/mutation-captures"
            transition_root = repository / "config/quality/mutation-transitions"
            predecessor_raw = baseline_path.read_bytes()
            predecessor = json.loads(predecessor_raw)
            predecessor_hash = hashlib.sha256(predecessor_raw).hexdigest()
            predecessor_receipt = json.loads((receipt_root / f"{predecessor_hash}.json").read_text())
            predecessor_archive = json.loads(
                (repository / predecessor_receipt["evidenceArchive"]["path"]).read_text()
            )
            component_bytes = {
                name: (repository / record["path"]).read_bytes()
                for name, record in predecessor_archive["components"].items()
            }
            component_bytes["attempt"] = canonical_json_bytes({
                "schema": "forged-attempt-with-no-typed-links",
            })
            component_hashes = {
                name: hashlib.sha256(raw).hexdigest()
                for name, raw in component_bytes.items()
            }
            manifest = {
                **predecessor_receipt["evidenceManifest"],
                "predecessorBaselineHash": predecessor_hash,
                "predecessorVerificationReceiptHash": component_hashes[
                    "predecessorVerificationReceipt"
                ],
                "components": component_hashes,
            }
            evidence_digest = hashlib.sha256(canonical_json_bytes(manifest)).hexdigest()
            candidate = {
                **predecessor,
                "predecessorBaselineHash": predecessor_hash,
                "predecessorVerificationReceiptHash": component_hashes[
                    "predecessorVerificationReceipt"
                ],
                "captureEvidenceDigest": evidence_digest,
            }
            candidate_raw = canonical_json_bytes(candidate)
            candidate_hash = hashlib.sha256(candidate_raw).hexdigest()
            archive_root = repository / "config/quality/mutation-evidence" / candidate_hash
            archive_records = {}
            for index, (name, claimed_digest) in enumerate(sorted(manifest["components"].items())):
                component_path = archive_root / "components" / f"{index:02d}.bin"
                component_path.parent.mkdir(parents=True, exist_ok=True)
                component_path.write_bytes(component_bytes[name])
                archive_records[name] = {
                    "path": component_path.relative_to(repository).as_posix(),
                    "sha256": claimed_digest,
                }
            archive = {
                "schema": "pitest-capture-evidence-archive-v1",
                "candidateBaselineSha256": candidate_hash,
                "components": archive_records,
            }
            archive_raw = canonical_json_bytes(archive)
            archive_path = archive_root / "archive.json"
            archive_path.write_bytes(archive_raw)
            archive_reference = {
                "path": archive_path.relative_to(repository).as_posix(),
                "sha256": hashlib.sha256(archive_raw).hexdigest(),
            }
            receipt = build_capture_receipt(
                candidate_baseline=candidate_raw,
                evidence_manifest=manifest,
                evidence_archive=archive_reference,
            )
            receipt_raw = canonical_json_bytes(receipt)
            (receipt_root / f"{candidate_hash}.json").write_bytes(receipt_raw)

            previous_transition = next(
                json.loads(path.read_text())
                for path in transition_root.glob("*.json")
                if json.loads(path.read_text())["candidateBaselineSha256"] == predecessor_hash
            )
            input_identity = predecessor["mutationInputIdentitySha256"]
            transition = {
                **previous_transition,
                "schema": "pitest-recapture-transition-v2",
                "predecessorBaselineSha256": predecessor_hash,
                "candidateBaselineSha256": candidate_hash,
                "captureReceiptSha256": hashlib.sha256(receipt_raw).hexdigest(),
                "predecessorVerificationReceiptSha256": component_hashes[
                    "predecessorVerificationReceipt"
                ],
                "routeSha256": component_hashes["route"],
                "completionSha256": component_hashes["completion"],
                "oldInputIdentity": {
                    "schemaVersion": 2,
                    "mutationInputIdentitySha256": input_identity,
                    "hostNeutralMutationIdentitySha256": predecessor["hostNeutralMutationIdentitySha256"],
                },
                "newInputIdentity": {
                    "schemaVersion": 2,
                    "mutationInputIdentitySha256": input_identity,
                    "hostNeutralMutationIdentitySha256": predecessor["hostNeutralMutationIdentitySha256"],
                },
                "evidenceArchive": archive_reference,
            }
            (transition_root / f"{predecessor_hash}-to-{input_identity}.json").write_bytes(
                canonical_json_bytes(transition),
            )
            baseline_path.write_bytes(candidate_raw)
            self._commit_clone(repository, "forge complete successor")

            with self.assertRaisesRegex(MutationPolicyError, "archived attempt schema differs"):
                self._validate_clone_baseline(repository)

    def test_newly_committed_successor_accepts_a_post_i1_predecessor_capture_receipt(self) -> None:
        """Proves the next checked recapture can archive a receipt sealed after I1."""
        with tempfile.TemporaryDirectory(dir=ROOT / "build") as directory:
            repository = Path(directory) / "post-i1-successor"
            self._clone_checked_repository(repository)
            baseline_path = repository / "config/quality/mutation-baseline.json"
            receipt_root = repository / "config/quality/mutation-captures"
            transition_root = repository / "config/quality/mutation-transitions"
            predecessor_raw = baseline_path.read_bytes()
            predecessor = json.loads(predecessor_raw)
            predecessor_hash = hashlib.sha256(predecessor_raw).hexdigest()
            predecessor_capture_receipt_raw = (
                receipt_root / f"{predecessor_hash}.json"
            ).read_bytes()
            predecessor_capture_receipt_hash = hashlib.sha256(
                predecessor_capture_receipt_raw,
            ).hexdigest()
            predecessor_capture_receipt = json.loads(predecessor_capture_receipt_raw)
            predecessor_archive = json.loads(
                (repository / predecessor_capture_receipt["evidenceArchive"]["path"]).read_text()
            )
            component_bytes = {
                name: (repository / record["path"]).read_bytes()
                for name, record in predecessor_archive["components"].items()
            }

            summary = json.loads(component_bytes["verificationSummary"])
            summary["predecessorBaselineHash"] = predecessor_hash
            summary["predecessorCaptureReceiptSha256"] = predecessor_capture_receipt_hash
            component_bytes["verificationSummary"] = canonical_json_bytes(summary)

            predecessor_verification_receipt = json.loads(
                component_bytes["predecessorVerificationReceipt"],
            )
            predecessor_verification_receipt["predecessors"]["baseline"] = predecessor_hash
            predecessor_verification_receipt["predecessors"]["baselineCaptureReceipt"] = (
                predecessor_capture_receipt_hash
            )
            predecessor_verification_receipt["predecessors"]["summary"] = hashlib.sha256(
                component_bytes["verificationSummary"],
            ).hexdigest()
            component_bytes["predecessorVerificationReceipt"] = canonical_json_bytes(
                predecessor_verification_receipt,
            )

            component_hashes = {
                name: hashlib.sha256(raw).hexdigest()
                for name, raw in component_bytes.items()
            }
            manifest = {
                **predecessor_capture_receipt["evidenceManifest"],
                "predecessorBaselineHash": predecessor_hash,
                "predecessorVerificationReceiptHash": component_hashes[
                    "predecessorVerificationReceipt"
                ],
                "components": component_hashes,
            }
            evidence_digest = hashlib.sha256(canonical_json_bytes(manifest)).hexdigest()
            candidate = {
                **predecessor,
                "predecessorBaselineHash": predecessor_hash,
                "predecessorVerificationReceiptHash": component_hashes[
                    "predecessorVerificationReceipt"
                ],
                "captureEvidenceDigest": evidence_digest,
            }
            candidate_raw = canonical_json_bytes(candidate)
            candidate_hash = hashlib.sha256(candidate_raw).hexdigest()
            archive_root = repository / "config/quality/mutation-evidence" / candidate_hash
            archive_records = {}
            for index, (name, claimed_digest) in enumerate(sorted(component_hashes.items())):
                component_path = archive_root / "components" / f"{index:02d}.bin"
                component_path.parent.mkdir(parents=True, exist_ok=True)
                component_path.write_bytes(component_bytes[name])
                archive_records[name] = {
                    "path": component_path.relative_to(repository).as_posix(),
                    "sha256": claimed_digest,
                }
            archive = {
                "schema": "pitest-capture-evidence-archive-v1",
                "candidateBaselineSha256": candidate_hash,
                "components": archive_records,
            }
            archive_raw = canonical_json_bytes(archive)
            archive_path = archive_root / "archive.json"
            archive_path.write_bytes(archive_raw)
            archive_reference = {
                "path": archive_path.relative_to(repository).as_posix(),
                "sha256": hashlib.sha256(archive_raw).hexdigest(),
            }
            receipt = build_capture_receipt(
                candidate_baseline=candidate_raw,
                evidence_manifest=manifest,
                evidence_archive=archive_reference,
            )
            receipt_raw = canonical_json_bytes(receipt)
            (receipt_root / f"{candidate_hash}.json").write_bytes(receipt_raw)

            previous_transition = next(
                json.loads(path.read_text())
                for path in transition_root.glob("*.json")
                if json.loads(path.read_text())["candidateBaselineSha256"] == predecessor_hash
            )
            input_identity = predecessor["mutationInputIdentitySha256"]
            transition = {
                **previous_transition,
                "predecessorBaselineSha256": predecessor_hash,
                "candidateBaselineSha256": candidate_hash,
                "captureReceiptSha256": hashlib.sha256(receipt_raw).hexdigest(),
                "predecessorVerificationReceiptSha256": component_hashes[
                    "predecessorVerificationReceipt"
                ],
                "routeSha256": component_hashes["route"],
                "completionSha256": component_hashes["completion"],
                "oldInputIdentity": {
                    "schemaVersion": 2,
                    "mutationInputIdentitySha256": input_identity,
                    "hostNeutralMutationIdentitySha256": predecessor[
                        "hostNeutralMutationIdentitySha256"
                    ],
                },
                "newInputIdentity": {
                    "schemaVersion": 2,
                    "mutationInputIdentitySha256": input_identity,
                    "hostNeutralMutationIdentitySha256": predecessor[
                        "hostNeutralMutationIdentitySha256"
                    ],
                },
                "evidenceArchive": archive_reference,
            }
            (transition_root / f"{predecessor_hash}-to-{input_identity}.json").write_bytes(
                canonical_json_bytes(transition),
            )
            baseline_path.write_bytes(candidate_raw)
            self._commit_clone(repository, "install post-I1 successor")

            self._validate_clone_baseline(repository)

    def test_post_i1_archive_rejects_an_omitted_or_swapped_predecessor_capture_receipt(self) -> None:
        baseline_raw = (ROOT / "config/quality/mutation-baseline.json").read_bytes()
        predecessor_hash = hashlib.sha256(baseline_raw).hexdigest()
        predecessor_capture_receipt_raw = (
            ROOT / "config/quality/mutation-captures" / f"{predecessor_hash}.json"
        ).read_bytes()
        predecessor_capture_receipt_hash = hashlib.sha256(
            predecessor_capture_receipt_raw,
        ).hexdigest()
        predecessor_capture_receipt = json.loads(predecessor_capture_receipt_raw)
        archive = json.loads(
            (ROOT / predecessor_capture_receipt["evidenceArchive"]["path"]).read_text()
        )
        original_components = {
            name: (ROOT / record["path"]).read_bytes()
            for name, record in archive["components"].items()
        }
        previous_transition = next(
            json.loads(path.read_text())
            for path in (ROOT / "config/quality/mutation-transitions").glob("*.json")
            if json.loads(path.read_text())["candidateBaselineSha256"] == predecessor_hash
        )

        for label, capture_link in (
            ("omitted", None),
            ("swapped", "0" * 64),
        ):
            with self.subTest(label=label):
                components = dict(original_components)
                summary = json.loads(components["verificationSummary"])
                summary["predecessorBaselineHash"] = predecessor_hash
                summary["predecessorCaptureReceiptSha256"] = (
                    predecessor_capture_receipt_hash
                )
                components["verificationSummary"] = canonical_json_bytes(summary)
                predecessor_verification_receipt = json.loads(
                    components["predecessorVerificationReceipt"],
                )
                predecessor_verification_receipt["predecessors"]["baseline"] = (
                    predecessor_hash
                )
                predecessor_verification_receipt["predecessors"]["summary"] = (
                    hashlib.sha256(components["verificationSummary"]).hexdigest()
                )
                if capture_link is None:
                    predecessor_verification_receipt["predecessors"].pop(
                        "baselineCaptureReceipt",
                        None,
                    )
                else:
                    predecessor_verification_receipt["predecessors"][
                        "baselineCaptureReceipt"
                    ] = capture_link
                components["predecessorVerificationReceipt"] = canonical_json_bytes(
                    predecessor_verification_receipt,
                )
                component_hashes = {
                    name: hashlib.sha256(raw).hexdigest()
                    for name, raw in components.items()
                }
                candidate = hashlib.sha256(label.encode()).hexdigest()
                receipt_value = {
                    **predecessor_capture_receipt,
                    "evidenceManifest": {
                        **predecessor_capture_receipt["evidenceManifest"],
                        "predecessorBaselineHash": predecessor_hash,
                        "predecessorVerificationReceiptHash": component_hashes[
                            "predecessorVerificationReceipt"
                        ],
                        "components": component_hashes,
                    },
                }
                transition = {
                    **previous_transition,
                    "predecessorBaselineSha256": predecessor_hash,
                    "candidateBaselineSha256": candidate,
                    "predecessorVerificationReceiptSha256": component_hashes[
                        "predecessorVerificationReceipt"
                    ],
                    "routeSha256": component_hashes["route"],
                    "completionSha256": component_hashes["completion"],
                }

                with self.assertRaisesRegex(
                    MutationPolicyError,
                    "predecessor verification receipt typed links differ",
                ):
                    _validate_archived_components(
                        components,
                        candidate=candidate,
                        receipt_value=receipt_value,
                        transition=transition,
                        predecessor_capture_receipt_raw=predecessor_capture_receipt_raw,
                    )

    def test_deleting_history_and_committing_a_replacement_null_root_is_rejected(self) -> None:
        """Catches deriving the trust root only from paths surviving at HEAD."""
        with tempfile.TemporaryDirectory(dir=ROOT / "build") as directory:
            repository = Path(directory) / "replacement-root"
            self._clone_checked_repository(repository)
            baseline_path = repository / "config/quality/mutation-baseline.json"
            receipt_root = repository / "config/quality/mutation-captures"
            transition_root = repository / "config/quality/mutation-transitions"
            baseline = json.loads(baseline_path.read_text())
            current_hash = hashlib.sha256(baseline_path.read_bytes()).hexdigest()
            current_receipt = json.loads((receipt_root / f"{current_hash}.json").read_text())
            manifest = {
                **current_receipt["evidenceManifest"],
                "predecessorBaselineHash": None,
                "predecessorVerificationReceiptHash": None,
                "components": {
                    key: value
                    for key, value in current_receipt["evidenceManifest"]["components"].items()
                    if key != "predecessorVerificationReceipt"
                },
            }
            evidence_digest = hashlib.sha256(canonical_json_bytes(manifest)).hexdigest()
            replacement = {
                **baseline,
                "predecessorBaselineHash": None,
                "predecessorVerificationReceiptHash": None,
                "captureEvidenceDigest": evidence_digest,
            }
            replacement_raw = canonical_json_bytes(replacement)
            replacement_hash = hashlib.sha256(replacement_raw).hexdigest()
            replacement_receipt = build_capture_receipt(
                candidate_baseline=replacement_raw,
                evidence_manifest=manifest,
            )
            for path in receipt_root.glob("*.json"):
                path.unlink()
            for path in transition_root.glob("*.json"):
                path.unlink()
            (receipt_root / f"{replacement_hash}.json").write_bytes(canonical_json_bytes(replacement_receipt))
            baseline_path.write_bytes(replacement_raw)
            self._commit_clone(repository, "replace complete capture history")

            with self.assertRaisesRegex(MutationPolicyError, "trust anchor|deleted|history"):
                self._validate_clone_baseline(repository)

    def test_closed_archive_decoder_rejects_each_of_22_typed_component_kinds(self) -> None:
        baseline_raw = (ROOT / "config/quality/mutation-baseline.json").read_bytes()
        candidate = hashlib.sha256(baseline_raw).hexdigest()
        receipt_value = json.loads(
            (ROOT / "config/quality/mutation-captures" / f"{candidate}.json").read_text()
        )
        archive = json.loads(
            (ROOT / receipt_value["evidenceArchive"]["path"]).read_text()
        )
        raw_components = {
            name: (ROOT / record["path"]).read_bytes()
            for name, record in archive["components"].items()
        }
        transition = next(
            json.loads(path.read_text())
            for path in (ROOT / "config/quality/mutation-transitions").glob("*.json")
            if json.loads(path.read_text())["candidateBaselineSha256"] == candidate
        )

        self.assertEqual(22, len(raw_components))
        decoded = _validate_archived_components(
            raw_components,
            candidate=candidate,
            receipt_value=receipt_value,
            transition=transition,
        )
        self.assertEqual(transition["sourceCommit"], decoded["sourceCommit"])

        for name in sorted(raw_components):
            with self.subTest(component=name):
                forged = dict(raw_components)
                forged[name] = (
                    b'<mutations partial="true"><broken></mutations>\n'
                    if name.startswith("xml:")
                    else b"{}\n"
                )
                with self.assertRaises(MutationPolicyError) as raised:
                    _validate_archived_components(
                        forged,
                        candidate=candidate,
                        receipt_value=receipt_value,
                        transition=transition,
                    )
                self.assertNotIn("component bytes differ", str(raised.exception))

    def test_transition_chain_successor_requires_the_full_capture_schema(self) -> None:
        candidate = "1" * 64
        minimal = canonical_json_bytes({
            "schema": "pitest-capture-receipt-v1",
            "candidateBaselineSha256": candidate,
            "predecessorBaselineHash": "2" * 64,
            "captureEvidenceDigest": "3" * 64,
        })
        with self.assertRaisesRegex(MutationPolicyError, "closed schema"):
            _capture_receipt_link(minimal, f"config/quality/mutation-captures/{candidate}.json")

    def test_policy_pins_initial_anchor_checkpoint_and_only_round3_successor_axis(self) -> None:
        policy, _, _ = load_policy()
        self.assertEqual(
            ["task7-spec-review-round3-corrections"],
            policy["capturePolicy"]["reviewedTransitionAxes"],
        )
        self.assertEqual(
            {
                "baselineCandidateSha256": "0839a284eb2e6b1617969d3427ba1b4d1ac7c7bf81441d12801eebba127be201",
                "captureReceiptPath": "config/quality/mutation-captures/0839a284eb2e6b1617969d3427ba1b4d1ac7c7bf81441d12801eebba127be201.json",
                "captureReceiptSha256": "4da85a7c98dda7c26287d215b6b59c37c68a448e9d3e19b28916283615a68219",
                "introductionCommit": "4a958727679440a53657a0adcf1a7017cedcb804",
            },
            policy["capturePolicy"]["initialAnchor"],
        )
        self.assertEqual(
            "dfb5439c76bb860ca545e4744dca937057c3b85c",
            policy["capturePolicy"]["legacyCheckpoint"]["commit"],
        )

    def test_checked_capture_artifact_must_be_append_only_from_its_introduction(self) -> None:
        path = next((ROOT / "config/quality/mutation-captures").glob("*.json"))
        raw = path.read_bytes()

        class ModifiedHistoryGit:
            def bytes(self, command: GitCommand, *arguments: str) -> bytes:
                if command is GitCommand.SHOW:
                    return raw
                if command is GitCommand.LOG:
                    return ("1" * 40 + "\n" + "2" * 40 + "\n").encode("ascii")
                raise AssertionError((command, arguments))

        with self.assertRaisesRegex(MutationPolicyError, "append-only history"):
            _checked_blob(ModifiedHistoryGit(), path, "capture receipt")  # type: ignore[arg-type]

    def test_initial_capture_is_candidate_then_separate_receipt(self) -> None:
        components = {
            name: f"{name}\n".encode()
            for name in {
                "policy", "sourceCommit", "route", "tasks", "routeReceipt", "attempt",
                "configuration:location", "configuration:settings", "configuration:station",
                "completion", "measurement", "xml:location", "xml:settings", "xml:station",
                "semantic:location", "semantic:settings", "semantic:station",
                "html:location", "html:settings", "html:station", "verificationSummary",
            }
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

    def test_initial_manifest_requires_the_exact_full_component_set(self) -> None:
        required = {
            "policy", "sourceCommit", "route", "tasks", "routeReceipt", "attempt",
            "configuration:location", "configuration:settings", "configuration:station",
            "completion", "measurement", "xml:location", "xml:settings", "xml:station",
            "semantic:location", "semantic:settings", "semantic:station",
            "html:location", "html:settings", "html:station", "verificationSummary",
        }
        for missing in sorted(required):
            with self.subTest(missing=missing), self.assertRaisesRegex(MutationPolicyError, "component set"):
                build_capture_evidence_manifest(
                    components={name: name.encode() for name in required - {missing}},
                    policy_sha256="1" * 64,
                    predecessor_baseline_sha256=None,
                    predecessor_verification_receipt_sha256=None,
                    source_commit="2" * 40,
                    host_neutral_identity_sha256="3" * 64,
                    per_run_provenance_sha256="4" * 64,
                )
        with self.assertRaisesRegex(MutationPolicyError, "component set"):
            build_capture_evidence_manifest(
                components={**{name: name.encode() for name in required}, "arbitrary": b"x"},
                policy_sha256="1" * 64,
                predecessor_baseline_sha256=None,
                predecessor_verification_receipt_sha256=None,
                source_commit="2" * 40,
                host_neutral_identity_sha256="3" * 64,
                per_run_provenance_sha256="4" * 64,
            )

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

    def test_checked_baseline_receipt_rejects_a_minimal_forged_pair(self) -> None:
        baseline = {"captureEvidenceDigest": "a" * 64, "predecessorBaselineHash": None}
        baseline_raw = canonical_json_bytes(baseline)
        candidate_hash = hashlib.sha256(baseline_raw).hexdigest()
        forged_receipt = {
            "schema": "pitest-capture-receipt-v1",
            "candidateBaselineSha256": candidate_hash,
            "predecessorBaselineHash": None,
            "captureEvidenceDigest": "a" * 64,
            "components": {"route": "b" * 64},
        }
        with tempfile.TemporaryDirectory() as directory:
            receipt_root = Path(directory)
            (receipt_root / f"{candidate_hash}.json").write_bytes(canonical_json_bytes(forged_receipt))
            with mock.patch("verify_pitest.CAPTURE_RECEIPT_ROOT", receipt_root):
                with self.assertRaisesRegex(MutationPolicyError, "baseline schema|evidence manifest"):
                    validate_baseline_capture_receipt(baseline_raw, baseline)

    def test_full_baseline_schema_rejects_top_level_complete_but_empty_inventories(self) -> None:
        baseline = {
            "schemaVersion": 2,
            "sourceCommit": "1" * 40,
            "policySha256": "2" * 64,
            "observationGitConfig": {},
            "toolchainIdentity": {},
            "hostNeutralMutationIdentity": {},
            "hostNeutralMutationIdentitySha256": "3" * 64,
            "effectiveCommandPlan": {},
            "executionEnvironmentIdentity": {},
            "gitObjectViewIdentity": {},
            "wrapperIdentity": {},
            "moduleInventories": {name: {} for name in ("location", "settings", "station")},
            "mutationInputIdentitySha256": "4" * 64,
            "captureProfile": "darwin-arm64",
            "profileHistory": {"linux-x86_64": {"state": "NOT_ESTABLISHED"}},
            "reports": [{"module": name} for name in ("location", "settings", "station")],
            "predecessorBaselineHash": None,
            "predecessorVerificationReceiptHash": None,
            "captureEvidenceDigest": "5" * 64,
        }
        with self.assertRaisesRegex(MutationPolicyError, "observation Git config schema"):
            validate_baseline_schema(baseline)

    def test_complete_resigned_manifest_cannot_replace_a_checked_capture(self) -> None:
        baseline_path = ROOT / "config/quality/mutation-baseline.json"
        real = json.loads(baseline_path.read_text())
        real_receipt_path = ROOT / "config/quality/mutation-captures" / f"{hashlib.sha256(baseline_path.read_bytes()).hexdigest()}.json"
        manifest = json.loads(real_receipt_path.read_text())["evidenceManifest"]
        forged_manifest = {**manifest, "components": {**manifest["components"], "attempt": "f" * 64}}
        forged_digest = hashlib.sha256(canonical_json_bytes(forged_manifest)).hexdigest()
        forged_baseline = {**real, "captureEvidenceDigest": forged_digest}
        forged_baseline_raw = canonical_json_bytes(forged_baseline)
        forged_receipt = build_capture_receipt(
            candidate_baseline=forged_baseline_raw,
            evidence_manifest=forged_manifest,
        )

        with tempfile.TemporaryDirectory() as directory:
            receipt_root = Path(directory)
            candidate_hash = hashlib.sha256(forged_baseline_raw).hexdigest()
            (receipt_root / f"{candidate_hash}.json").write_bytes(canonical_json_bytes(forged_receipt))
            with mock.patch("verify_pitest.CAPTURE_RECEIPT_ROOT", receipt_root):
                with self.assertRaisesRegex(MutationPolicyError, "checked Git|trusted capture"):
                    validate_baseline_capture_receipt(forged_baseline_raw, forged_baseline)

    def test_current_successor_requires_the_complete_checked_transition_chain(self) -> None:
        baseline_path = ROOT / "config/quality/mutation-baseline.json"
        baseline_raw = baseline_path.read_bytes()
        baseline = json.loads(baseline_raw)
        with tempfile.TemporaryDirectory(dir=ROOT / "config/quality") as directory:
            with mock.patch("verify_pitest.TRANSITION_ROOT", Path(directory)):
                with self.assertRaisesRegex(MutationPolicyError, "file inventory differs|disconnected from current baseline"):
                    validate_baseline_capture_receipt(baseline_raw, baseline)


class ReceiptChainTest(unittest.TestCase):
    def test_successor_install_is_atomic_on_a_mid_install_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            baseline = root / "baseline.json"
            receipt = root / "captures" / "candidate.json"
            transition = root / "transitions" / "transition.json"
            predecessor = b"predecessor\n"
            baseline.write_bytes(predecessor)
            real_replace = os.replace
            calls = 0

            def fail_second(source: Path, target: Path) -> None:
                nonlocal calls
                calls += 1
                if calls == 2:
                    raise OSError("synthetic transition install failure")
                real_replace(source, target)

            with mock.patch("verify_pitest.os.replace", side_effect=fail_second):
                with self.assertRaisesRegex(OSError, "synthetic transition install failure"):
                    install_successor_atomically(
                        baseline_path=baseline,
                        predecessor_raw=predecessor,
                        candidate_raw=b"candidate\n",
                        receipt_path=receipt,
                        receipt_raw=b"receipt\n",
                        transition_path=transition,
                        transition_raw=b"transition\n",
                    )
            self.assertEqual(predecessor, baseline.read_bytes())
            self.assertFalse(receipt.exists())
            self.assertFalse(transition.exists())
            self.assertEqual([], list(root.rglob("*.tmp")))

    def test_seal_rejects_arbitrary_attempt_completion_and_baseline_predecessors(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = {name: root / name for name in (
                "route", "tasks", "route-receipt", "attempt", "completion", "baseline", "summary", "final",
            )}
            paths["route"].write_bytes(b"route")
            paths["tasks"].write_bytes(b"tasks")
            paths["route-receipt"].write_bytes(b"route-receipt")
            paths["attempt"].write_bytes(b"TAMPERED ATTEMPT")
            paths["completion"].write_bytes(b"TAMPERED COMPLETION")
            paths["baseline"].write_bytes(b"TAMPERED BASELINE")
            paths["summary"].write_bytes(canonical_json_bytes({
                "status": "pass",
                "hostNeutralMutationIdentitySha256": "c" * 64,
                "perRunExecutionProvenanceSha256": "d" * 64,
            }))
            policy = {
                "canonicalGradleFlags": ["--rerun-tasks"],
                "executionEnvironmentPolicy": {"policyVersion": "pitest-sealed-v1"},
                "gitObjectViewPolicy": {"policyVersion": "original-object-view-v1"},
            }
            route = {
                "status": "selected", "sourceCommit": "e" * 40,
                "selectedTasks": [":domain:station:pitestVerified"],
                "bootstrap": {}, "hostNeutralMutationIdentity": {},
                "hostNeutralMutationIdentitySha256": "f" * 64,
                "perRunExecutionProvenance": {},
            }
            patches = {
                "ROUTE_PATH": paths["route"], "TASKS_PATH": paths["tasks"],
                "ROUTE_RECEIPT_PATH": paths["route-receipt"], "ATTEMPT_PATH": paths["attempt"],
                "COMPLETION_PATH": paths["completion"], "BASELINE_PATH": paths["baseline"],
                "SUMMARY_PATH": paths["summary"], "FINAL_RECEIPT_PATH": paths["final"],
            }
            with mock.patch("verify_pitest.validate_route", return_value=(policy, route, b"policy", "a" * 64)):
                with mock.patch.multiple("verify_pitest", **patches):
                    with self.assertRaisesRegex(MutationPolicyError, "invalid JSON|attempt differs"):
                        seal_verification("/ignored")
            self.assertFalse(paths["final"].exists())

    def test_seal_binds_the_single_validated_baseline_snapshot_if_the_path_is_replaced(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = {name: root / name for name in (
                "route", "tasks", "route-receipt", "attempt", "completion", "baseline", "summary", "final",
            )}
            for name in ("route", "tasks", "route-receipt", "attempt", "completion"):
                paths[name].write_bytes(name.encode())
            validated_baseline = canonical_json_bytes({"validated": True})
            tampered_baseline = canonical_json_bytes({"tampered": True})
            paths["baseline"].write_bytes(validated_baseline)
            capture_receipt_raw = canonical_json_bytes({"capture": True})
            baseline_sha256 = hashlib.sha256(validated_baseline).hexdigest()
            capture_receipt_sha256 = hashlib.sha256(capture_receipt_raw).hexdigest()
            summary = {
                "status": "pass",
                "hostNeutralMutationIdentitySha256": "c" * 64,
                "perRunExecutionProvenanceSha256": "d" * 64,
                "predecessorBaselineSha256": baseline_sha256,
                "predecessorCaptureReceiptSha256": capture_receipt_sha256,
            }
            paths["summary"].write_bytes(canonical_json_bytes(summary))
            policy = {"modules": {}}
            route = {"status": "selected", "sourceCommit": "e" * 40}

            def replace_after_validation(*, observation: bool, java_home: str, baseline_snapshot=None):
                self.assertEqual(validated_baseline, baseline_snapshot.raw)
                paths["baseline"].write_bytes(tampered_baseline)
                return summary

            patches = {
                "ROUTE_PATH": paths["route"], "TASKS_PATH": paths["tasks"],
                "ROUTE_RECEIPT_PATH": paths["route-receipt"], "ATTEMPT_PATH": paths["attempt"],
                "COMPLETION_PATH": paths["completion"], "BASELINE_PATH": paths["baseline"],
                "SUMMARY_PATH": paths["summary"], "FINAL_RECEIPT_PATH": paths["final"],
            }
            with mock.patch("verify_pitest.validate_route", return_value=(policy, route, b"policy", "a" * 64)):
                with mock.patch(
                    "verify_pitest.read_baseline_snapshot",
                    return_value=BaselineSnapshot(
                        raw=validated_baseline,
                        value={"validated": True},
                        sha256=baseline_sha256,
                        capture_receipt_raw=capture_receipt_raw,
                        capture_receipt_sha256=capture_receipt_sha256,
                    ),
                ):
                    with mock.patch("verify_pitest.verify", side_effect=replace_after_validation):
                        with mock.patch("verify_pitest.validate_completion_value", return_value=({"reports": []}, b"completion")):
                            with mock.patch.multiple("verify_pitest", **patches):
                                sealed = seal_verification("/ignored")

            self.assertEqual(
                hashlib.sha256(validated_baseline).hexdigest(),
                sealed["predecessors"]["baseline"],
            )
            self.assertNotEqual(
                hashlib.sha256(tampered_baseline).hexdigest(),
                sealed["predecessors"]["baseline"],
            )

    def test_seal_rejects_summary_baseline_identity_omission_and_swap(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = {name: root / name for name in (
                "route", "tasks", "route-receipt", "attempt", "completion", "summary", "final",
            )}
            for name in ("route", "tasks", "route-receipt", "attempt", "completion"):
                paths[name].write_bytes(name.encode())
            baseline_raw = canonical_json_bytes({"validated": True})
            snapshot = BaselineSnapshot(
                raw=baseline_raw,
                value={"validated": True},
                sha256=hashlib.sha256(baseline_raw).hexdigest(),
                capture_receipt_raw=b"capture receipt",
                capture_receipt_sha256=hashlib.sha256(b"capture receipt").hexdigest(),
            )
            policy = {"modules": {}}
            route = {"status": "selected", "sourceCommit": "e" * 40}
            common_summary = {
                "status": "pass",
                "hostNeutralMutationIdentitySha256": "c" * 64,
                "perRunExecutionProvenanceSha256": "d" * 64,
            }
            cases = {
                "omitted": common_summary,
                "swapped": {
                    **common_summary,
                    "predecessorBaselineSha256": "f" * 64,
                    "predecessorCaptureReceiptSha256": "0" * 64,
                },
            }
            patches = {
                "ROUTE_PATH": paths["route"], "TASKS_PATH": paths["tasks"],
                "ROUTE_RECEIPT_PATH": paths["route-receipt"], "ATTEMPT_PATH": paths["attempt"],
                "COMPLETION_PATH": paths["completion"], "SUMMARY_PATH": paths["summary"],
                "FINAL_RECEIPT_PATH": paths["final"],
            }
            for label, summary in cases.items():
                with self.subTest(label=label):
                    paths["summary"].write_bytes(canonical_json_bytes(summary))
                    paths["final"].unlink(missing_ok=True)
                    with mock.patch(
                        "verify_pitest.validate_route",
                        return_value=(policy, route, b"policy", "a" * 64),
                    ):
                        with mock.patch("verify_pitest.read_baseline_snapshot", return_value=snapshot):
                            with mock.patch("verify_pitest.verify", return_value=summary):
                                with mock.patch(
                                    "verify_pitest.validate_completion_value",
                                    return_value=({"reports": []}, b"completion"),
                                ):
                                    with mock.patch.multiple("verify_pitest", **patches):
                                        with self.assertRaisesRegex(
                                            MutationPolicyError,
                                            "verification summary baseline identity differs",
                                        ):
                                            seal_verification("/ignored")
                    self.assertFalse(paths["final"].exists())


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
