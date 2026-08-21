from __future__ import annotations

import json
import os
import tempfile
import unittest
from unittest import mock
from pathlib import Path

from scripts.quality.build_inputs.contracts import BuildInputError, canonical_json_bytes, load_policy
from scripts.quality.build_inputs.local_colima_evidence import (
    CLEANUP_PHASES,
    CONFIG_DESCRIPTOR,
    CONTAINER_INHERITED_LABELS,
    DELETE_ARGV,
    INDEX_DESCRIPTOR,
    LAYER_DESCRIPTORS,
    SELECTED_MANIFEST_DESCRIPTOR,
    START_ARGV,
    _recover_prior_attempts,
    _container_labels,
    _owned_labels,
    _runtime_data_identity,
    _safe_error,
    _profile_config,
    aggregate_receipt,
    docker_argv,
    isolated_runtime_root,
    next_attempt,
    ownership_marker,
    sanitized_host_environment,
    validate_bundle_heads,
    validate_cli,
    validate_cleanup_proof,
    validate_context_inventory,
    validate_container_selection,
    validate_effective_config,
    validate_image_identity,
    validate_inner_architecture,
    validate_runtime_absence,
)
from scripts.quality.build_inputs.generate_policy import policy as generated_policy
from scripts.quality.verify_build_inputs import _run_group


SOURCE = "1" * 40
BASE = "7b8c149c9f792aaf43cc00a94ba671929008979e"
POLICY_SHA = "2" * 64


def image_inspect_fixture() -> str:
    return json.dumps(
        [
            {
                "Architecture": "",
                "Config": {},
                "Descriptor": dict(INDEX_DESCRIPTOR),
                "Id": INDEX_DESCRIPTOR["digest"],
                "Os": "",
                "RepoDigests": ["docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"]],
                "RootFS": {},
                "Size": 7112,
            },
        ],
    )


def manifest_inspect_fixture() -> str:
    return json.dumps(
        [
            {
                "Descriptor": dict(SELECTED_MANIFEST_DESCRIPTOR),
                "OCIManifest": {
                    "config": dict(CONFIG_DESCRIPTOR),
                    "layers": [dict(row) for row in LAYER_DESCRIPTORS],
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "schemaVersion": 2,
                },
                "Ref": "docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"],
            },
            {
                "Descriptor": {
                    "digest": "sha256:" + "9" * 64,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "platform": {"architecture": "arm64", "os": "linux"},
                    "size": 424,
                },
                "OCIManifest": {},
            },
        ],
    )


class LocalColimaEvidenceContractTest(unittest.TestCase):
    def test_generated_policy_fixes_the_sole_host_and_aggregate_entrypoint(self) -> None:
        host = generated_policy()["localEvidenceHost"]
        self.assertEqual("gasstation-task9-linux-amd64", host["profile"])
        self.assertEqual("colima-gasstation-task9-linux-amd64", host["context"])
        self.assertEqual(BASE, host["mainBaseCommit"])
        self.assertEqual("refs/heads/main", host["mainBaseRef"])
        self.assertEqual(list(START_ARGV), host["startArgv"])
        self.assertEqual(list(DELETE_ARGV), host["deleteArgv"])
        self.assertEqual([], host["hostMounts"])
        self.assertEqual(sorted(_owned_labels({
            "attemptId": "attempt-000001",
            "mainBaseCommit": BASE,
            "markerSha256": "3" * 64,
            "policySha256": POLICY_SHA,
            "sourceCommit": SOURCE,
            "taskId": "quality-task-9-local-linux-evidence",
        })), host["ownedLabelKeys"])
        self.assertEqual(
            {
                "configDescriptor": CONFIG_DESCRIPTOR,
                "containerSelection": {
                    "configImage": "docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"],
                    "image": INDEX_DESCRIPTOR["digest"],
                    "inheritedLabels": CONTAINER_INHERITED_LABELS,
                    "platform": "linux",
                },
                "indexDescriptor": INDEX_DESCRIPTOR,
                "indexReference": "docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"],
                "layerDescriptors": list(LAYER_DESCRIPTORS),
                "platform": "linux/amd64",
                "selectedManifestDescriptor": SELECTED_MANIFEST_DESCRIPTOR,
            },
            host["image"],
        )
        self.assertEqual(
            "scripts/quality/build_inputs/local_colima_evidence.py",
            next(
                row["owner"]
                for row in generated_policy()["evidenceGradleEntrypoints"]
                if row["id"] == "local-colima/aggregate"
            ),
        )

    def test_local_host_policy_mutations_fail_closed(self) -> None:
        baseline = generated_policy()
        mutations = []
        for field, value in (
            ("mainBaseCommit", "4" * 40),
            ("deleteArgv", ["/opt/homebrew/bin/colima", "delete", "gasstation-task9-linux-amd64", "--force"]),
            ("hostMounts", ["/Users/example/source"]),
        ):
            candidate = json.loads(json.dumps(baseline))
            candidate["localEvidenceHost"][field] = value
            mutations.append(candidate)
        unknown = json.loads(json.dumps(baseline))
        unknown["localEvidenceHost"]["effectiveConfig"]["unknown"] = True
        mutations.append(unknown)
        partial = json.loads(json.dumps(baseline))
        partial["localEvidenceHost"]["requiredEvidenceRows"].remove("releaseBinding")
        mutations.append(partial)
        missing_index = json.loads(json.dumps(baseline))
        del missing_index["localEvidenceHost"]["image"]["indexDescriptor"]
        mutations.append(missing_index)
        manifest_as_config = json.loads(json.dumps(baseline))
        manifest_as_config["localEvidenceHost"]["image"]["configDescriptor"] = {
            key: value
            for key, value in manifest_as_config["localEvidenceHost"]["image"]["selectedManifestDescriptor"].items()
            if key != "platform"
        }
        mutations.append(manifest_as_config)
        legacy_alias = json.loads(json.dumps(baseline))
        legacy_alias["localEvidenceHost"]["image"]["configurationDigest"] = SELECTED_MANIFEST_DESCRIPTOR["digest"]
        mutations.append(legacy_alias)
        wrong_container_image = json.loads(json.dumps(baseline))
        wrong_container_image["localEvidenceHost"]["image"]["containerSelection"]["image"] = CONFIG_DESCRIPTOR["digest"]
        mutations.append(wrong_container_image)
        missing_inherited_label = json.loads(json.dumps(baseline))
        missing_inherited_label["localEvidenceHost"]["image"]["containerSelection"]["inheritedLabels"] = {}
        mutations.append(missing_inherited_label)
        missing_owned_label = json.loads(json.dumps(baseline))
        missing_owned_label["localEvidenceHost"]["ownedLabelKeys"].pop()
        mutations.append(missing_owned_label)
        with tempfile.TemporaryDirectory() as directory:
            for index, mutation in enumerate(mutations):
                path = Path(directory) / f"mutation-{index}.json"
                path.write_bytes(canonical_json_bytes(mutation))
                with self.subTest(index=index), self.assertRaises(BuildInputError):
                    load_policy(path, root=Path.cwd())

    def test_complete_strict_matrix_reuses_the_same_home_for_offline_replay(self) -> None:
        session = Path(tempfile.mkdtemp())
        installed = object()
        environment = {"GRADLE_USER_HOME": str(session / "gradle-home")}
        policy = {"dependencyVerification": {"offlineRepresentative": ["./gradlew", "help"]}}
        with mock.patch(
            "scripts.quality.verify_build_inputs._prepare_session",
            return_value=(session, installed, environment),
        ), mock.patch(
            "scripts.quality.verify_build_inputs._run_closed_command",
            return_value="BUILD SUCCESSFUL",
        ) as run:
            _run_group(policy, [["./gradlew", "help"]], label="strict-complete")
        self.assertEqual(2, run.call_count)
        self.assertEqual(["./gradlew", "help", "--offline"], run.call_args_list[-1].args[0])
        self.assertEqual(environment, run.call_args_list[-1].kwargs["environment"])

    def test_cli_accepts_only_policy_and_full_source_commit(self) -> None:
        self.assertEqual(
            ("config/quality/build-inputs.json", SOURCE),
            validate_cli(
                [
                    "--policy",
                    "config/quality/build-inputs.json",
                    "--source-commit",
                    SOURCE,
                ],
            ),
        )
        for argv in (
            ["--policy", "config/quality/build-inputs.json"],
            ["--policy", "config/quality/build-inputs.json", "--source-commit", "1" * 39],
            ["--policy", "config/quality/build-inputs.json", "--source-commit", SOURCE, "--attempt", "1"],
            ["--policy", "config/quality/build-inputs.json", "--source-commit", SOURCE, "--", "sh"],
        ):
            with self.subTest(argv=argv), self.assertRaises(BuildInputError):
                validate_cli(argv)

    def test_host_environment_is_closed_and_rejects_inherited_configuration(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "runtime"
            environment = sanitized_host_environment(root, inherited={})
            self.assertEqual(
                {"COLIMA_HOME", "DOCKER_CONFIG", "HOME", "LANG", "LC_ALL", "PATH", "TZ"},
                set(environment),
            )
            self.assertEqual("UTC", environment["TZ"])
            for name in (
                "COLIMA_HOME",
                "LIMA_HOME",
                "DOCKER_CONFIG",
                "DOCKER_AUTH_CONFIG",
                "DOCKER_CONTEXT",
                "DOCKER_HOST",
                "XDG_CONFIG_HOME",
                "SSH_AUTH_SOCK",
                "GIT_CONFIG_GLOBAL",
                "HTTPS_PROXY",
            ):
                with self.subTest(name=name), self.assertRaisesRegex(
                    BuildInputError,
                    "inherited host configuration",
                ):
                    sanitized_host_environment(root, inherited={name: "poison"})

    def test_runtime_roots_are_short_opaque_marker_derived_and_not_attempt_paths(self) -> None:
        first = isolated_runtime_root(SOURCE, POLICY_SHA, "attempt-000001")
        replay = isolated_runtime_root(SOURCE, POLICY_SHA, "attempt-000001")
        next_attempt_root = isolated_runtime_root(SOURCE, POLICY_SHA, "attempt-000002")
        self.assertEqual(first, replay)
        self.assertNotEqual(first, next_attempt_root)
        self.assertEqual(Path("/tmp"), first.parent)
        self.assertRegex(first.name, r"^[0-9a-f]{10}$")
        self.assertLess(
            len(str(first.resolve() / "colima-home" / "_lima" / "colima-gasstation-task9-linux-amd64" / "lima-guestagent.sock")),
            104,
        )
        self.assertNotIn(SOURCE, str(first))
        self.assertNotIn(POLICY_SHA, str(first))
        with self.assertRaises(BuildInputError):
            isolated_runtime_root(SOURCE, POLICY_SHA, "attempt-user")

    def test_private_tmp_diagnostics_are_fully_redacted(self) -> None:
        safe = _safe_error(BuildInputError('fatal socket "/private/tmp/gst9-secret/agent.sock"'))
        self.assertNotIn("/private", safe)
        self.assertNotIn("gst9-secret", safe)
        self.assertIn("<opaque-path>", safe)

    def test_profile_config_ignores_lima_runtime_copy(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            exact = home / "gasstation-task9-linux-amd64/colima.yaml"
            lima_copy = home / "_lima/colima-gasstation-task9-linux-amd64/colima.yaml"
            exact.parent.mkdir(parents=True)
            lima_copy.parent.mkdir(parents=True)
            exact.write_text("profile: exact\n")
            lima_copy.write_text("profile: lima-copy\n")
            self.assertEqual(exact, _profile_config(home))

    def test_context_inventory_allows_only_active_owned_and_inactive_builtin_default(self) -> None:
        endpoint = "unix:///tmp/opaque/colima-home/gasstation-task9-linux-amd64/docker.sock"
        valid = "\n".join(
            json.dumps(row, sort_keys=True)
            for row in (
                {
                    "Current": True,
                    "Description": "colima [profile=gasstation-task9-linux-amd64]",
                    "DockerEndpoint": endpoint,
                    "Error": "",
                    "Name": "colima-gasstation-task9-linux-amd64",
                },
                {
                    "Current": False,
                    "Description": "Current DOCKER_HOST based configuration",
                    "DockerEndpoint": "unix:///var/run/docker.sock",
                    "Error": "",
                    "Name": "default",
                },
            )
        )
        self.assertEqual(
            {"active": "colima-gasstation-task9-linux-amd64", "builtinInactive": "default"},
            validate_context_inventory(valid, expected_endpoint=endpoint),
        )
        for mutation in (
            valid.replace('"Current": false', '"Current": true'),
            valid + "\n" + json.dumps({"Name": "foreign"}),
            valid.splitlines()[0],
            valid.replace(endpoint, "unix:///var/run/docker.sock"),
        ):
            with self.subTest(mutation=mutation), self.assertRaises(BuildInputError):
                validate_context_inventory(mutation, expected_endpoint=endpoint)

    def test_effective_config_parser_is_complete_duplicate_free_and_exact(self) -> None:
        expected = {
            "arch": "aarch64",
            "autoActivate": False,
            "docker": {},
            "env": {},
            "kubernetes": {"enabled": False, "k3sArgs": ["--disable=traefik"]},
            "mounts": [],
            "rosetta": True,
        }
        valid = (
            "arch: aarch64\n"
            "autoActivate: false\n"
            "docker: {}\n"
            "env: {}\n"
            "kubernetes:\n"
            "  enabled: false\n"
            "  k3sArgs: [--disable=traefik]\n"
            "mounts: []\n"
            "rosetta: true\n"
        )
        self.assertEqual(expected, validate_effective_config(valid, expected))
        block_list = valid.replace(
            "  k3sArgs: [--disable=traefik]\n",
            "  k3sArgs:\n    - --disable=traefik\n",
        )
        self.assertEqual(expected, validate_effective_config(block_list, expected))
        for mutation in (
            valid.replace("rosetta: true", "rosetta: false"),
            valid + "unknown: true\n",
            valid + "arch: aarch64\n",
            valid.replace("mounts: []\n", ""),
        ):
            with self.subTest(mutation=mutation), self.assertRaises(BuildInputError):
                validate_effective_config(mutation, expected)

    def test_attempt_ids_are_generated_monotonically_and_not_reused(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.assertEqual("attempt-000001", next_attempt(root).name)
            (root / "attempt-000001").mkdir()
            (root / "attempt-000003").mkdir()
            self.assertEqual("attempt-000004", next_attempt(root).name)
            (root / "attempt-user").mkdir()
            with self.assertRaisesRegex(BuildInputError, "foreign attempt entry"):
                next_attempt(root)

    def test_marker_is_canonical_and_binds_literal_base_and_runtime_data(self) -> None:
        marker = ownership_marker(
            source_commit=SOURCE,
            policy_sha256=POLICY_SHA,
            attempt_id="attempt-000001",
            main_base_commit=BASE,
            runtime_data_id="3" * 64,
        )
        self.assertEqual(BASE, marker["mainBaseCommit"])
        self.assertEqual("gasstation-task9-evidence", marker["container"])
        self.assertEqual(
            marker["markerSha256"],
            __import__("hashlib").sha256(
                canonical_json_bytes({key: value for key, value in marker.items() if key != "markerSha256"}),
            ).hexdigest(),
        )
        bad = dict(marker)
        bad["mainBaseCommit"] = "4" * 40
        with self.assertRaisesRegex(BuildInputError, "main base"):
            ownership_marker(existing=bad)

    def test_prior_no_runtime_attempt_is_preserved_and_mixed_marker_refused(self) -> None:
        host = generated_policy()["localEvidenceHost"]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            attempt = root / "attempt-000001"
            attempt.mkdir()
            runtime_root = isolated_runtime_root(SOURCE, POLICY_SHA, attempt.name)
            marker = ownership_marker(
                source_commit=SOURCE,
                policy_sha256=POLICY_SHA,
                attempt_id=attempt.name,
                main_base_commit=BASE,
                runtime_data_id=_runtime_data_identity(
                    runtime_root / "colima-home", SOURCE, POLICY_SHA, attempt.name,
                ),
            )
            (attempt / "ownership-marker.json").write_bytes(canonical_json_bytes(marker))
            self.assertFalse(runtime_root.exists())
            try:
                sanitized_host_environment(runtime_root, inherited={})
                (runtime_root / "ownership-marker.json").write_bytes(canonical_json_bytes(marker))
                _recover_prior_attempts(
                    root,
                    source_commit=SOURCE,
                    policy_sha256=POLICY_SHA,
                    host_policy=host,
                )
            finally:
                marker_path = runtime_root / "ownership-marker.json"
                if marker_path.exists():
                    marker_path.unlink()
                for name in ("docker-client", "colima-home", "host-home"):
                    path = runtime_root / name
                    if path.exists():
                        path.rmdir()
                if runtime_root.exists():
                    runtime_root.rmdir()
            self.assertEqual("PASS", json.loads((attempt / "recovery.json").read_text())["status"])

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            attempt = root / "attempt-000001"
            attempt.mkdir()
            marker = ownership_marker(
                source_commit=SOURCE,
                policy_sha256="4" * 64,
                attempt_id=attempt.name,
                main_base_commit=BASE,
                runtime_data_id="3" * 64,
            )
            (attempt / "ownership-marker.json").write_bytes(canonical_json_bytes(marker))
            with self.assertRaisesRegex(BuildInputError, "mixed source/policy/base"):
                _recover_prior_attempts(
                    root,
                    source_commit=SOURCE,
                    policy_sha256=POLICY_SHA,
                    host_policy=host,
                )

    def test_bundle_heads_must_be_exactly_head_and_literal_main(self) -> None:
        valid = f"{SOURCE} HEAD\n{BASE} refs/heads/main\n"
        self.assertEqual(
            {"HEAD": SOURCE, "refs/heads/main": BASE},
            validate_bundle_heads(valid, source_commit=SOURCE, main_base_commit=BASE),
        )
        for mutation in (
            f"{SOURCE} HEAD\n",
            f"{SOURCE} HEAD\n{'4' * 40} refs/heads/main\n",
            valid + f"{'5' * 40} refs/heads/extra\n",
            f"{BASE} refs/heads/main\n{SOURCE} refs/heads/main\n",
        ):
            with self.subTest(mutation=mutation), self.assertRaises(BuildInputError):
                validate_bundle_heads(mutation, source_commit=SOURCE, main_base_commit=BASE)

    def test_every_docker_command_has_literal_isolated_config_and_context(self) -> None:
        argv = docker_argv(Path("/opaque/client"), "version", "--format", "{{json .}}")
        self.assertEqual(
            [
                "/opt/homebrew/bin/docker",
                "--config",
                "/opaque/client",
                "--context",
                "colima-gasstation-task9-linux-amd64",
                "version",
                "--format",
                "{{json .}}",
            ],
            argv,
        )
        with self.assertRaises(BuildInputError):
            docker_argv(Path("relative"), "version")
        with self.assertRaises(BuildInputError):
            docker_argv(Path("/opaque/client"), "system", "prune")

    def test_image_identity_separates_index_store_manifest_config_and_layer_roles(self) -> None:
        identity = validate_image_identity(image_inspect_fixture(), manifest_inspect_fixture())
        self.assertEqual(INDEX_DESCRIPTOR, identity["indexDescriptor"])
        self.assertEqual(SELECTED_MANIFEST_DESCRIPTOR, identity["selectedManifestDescriptor"])
        self.assertEqual(CONFIG_DESCRIPTOR, identity["configDescriptor"])
        self.assertEqual(list(LAYER_DESCRIPTORS), identity["layerDescriptors"])
        self.assertEqual(
            {
                "architecture": "",
                "config": {},
                "id": INDEX_DESCRIPTOR["digest"],
                "os": "",
                "repoDigests": ["docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"]],
                "rootFS": {},
                "size": 7112,
            },
            identity["storeObservation"],
        )

    def test_image_identity_mutations_fail_closed_and_valid_fixture_recovers(self) -> None:
        baseline_image = json.loads(image_inspect_fixture())
        baseline_manifest = json.loads(manifest_inspect_fixture())
        mutations: list[tuple[object, object]] = []
        for field, value in (
            ("digest", CONFIG_DESCRIPTOR["digest"]),
            ("mediaType", "application/vnd.oci.image.manifest.v1+json"),
            ("size", 6689),
        ):
            image = json.loads(json.dumps(baseline_image))
            image[0]["Descriptor"][field] = value
            mutations.append((image, baseline_manifest))
        for descriptor_name, field, value in (
            ("Descriptor", "digest", CONFIG_DESCRIPTOR["digest"]),
            ("Descriptor", "mediaType", CONFIG_DESCRIPTOR["mediaType"]),
            ("Descriptor", "size", 425),
            ("config", "digest", SELECTED_MANIFEST_DESCRIPTOR["digest"]),
            ("config", "mediaType", SELECTED_MANIFEST_DESCRIPTOR["mediaType"]),
            ("config", "size", 2053),
            ("layer", "digest", CONFIG_DESCRIPTOR["digest"]),
            ("layer", "mediaType", CONFIG_DESCRIPTOR["mediaType"]),
            ("layer", "size", 29752808),
        ):
            manifest = json.loads(json.dumps(baseline_manifest))
            target = manifest[0]["Descriptor"] if descriptor_name == "Descriptor" else (
                manifest[0]["OCIManifest"]["config"] if descriptor_name == "config" else manifest[0]["OCIManifest"]["layers"][0]
            )
            target[field] = value
            mutations.append((baseline_image, manifest))
        wrong_platform = json.loads(json.dumps(baseline_manifest))
        wrong_platform[0]["Descriptor"]["platform"]["architecture"] = ""
        mutations.append((baseline_image, wrong_platform))
        duplicate_amd64 = json.loads(json.dumps(baseline_manifest))
        duplicate_amd64.append(json.loads(json.dumps(duplicate_amd64[0])))
        mutations.append((baseline_image, duplicate_amd64))
        promoted_store = json.loads(json.dumps(baseline_image))
        promoted_store[0]["Id"] = CONFIG_DESCRIPTOR["digest"]
        promoted_store[0]["Architecture"] = "amd64"
        mutations.append((promoted_store, baseline_manifest))
        for field, value in (
            ("Config", {"Labels": {"unexpected": "label"}}),
            ("RootFS", {"Type": "layers"}),
            ("RepoDigests", []),
            ("Size", 7113),
        ):
            image = json.loads(json.dumps(baseline_image))
            image[0][field] = value
            mutations.append((image, baseline_manifest))
        for image, manifest in mutations:
            with self.subTest(image=image, manifest=manifest), self.assertRaises(BuildInputError):
                validate_image_identity(json.dumps(image), json.dumps(manifest))
            self.assertEqual(
                INDEX_DESCRIPTOR,
                validate_image_identity(image_inspect_fixture(), manifest_inspect_fixture())["indexDescriptor"],
            )

    def test_container_selection_binds_config_platform_and_labels(self) -> None:
        marker = {
            "attemptId": "attempt-000001",
            "mainBaseCommit": BASE,
            "markerSha256": "3" * 64,
            "policySha256": POLICY_SHA,
            "sourceCommit": SOURCE,
            "taskId": "quality-task-9-local-linux-evidence",
        }
        owned_labels = _owned_labels(marker)
        labels = _container_labels(marker)
        valid = json.dumps([
            {
                "Config": {
                    "Image": "docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"],
                    "Labels": labels,
                },
                "Image": INDEX_DESCRIPTOR["digest"],
                "Platform": "linux",
            },
        ])
        self.assertEqual(
            {
                "configImage": "docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"],
                "image": INDEX_DESCRIPTOR["digest"],
                "inheritedLabels": CONTAINER_INHERITED_LABELS,
                "ownedLabels": owned_labels,
                "platform": "linux",
            },
            validate_container_selection(valid, expected_owned_labels=owned_labels),
        )
        mutations = [
            valid.replace(INDEX_DESCRIPTOR["digest"], SELECTED_MANIFEST_DESCRIPTOR["digest"], 1),
            valid.replace("docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"], SELECTED_MANIFEST_DESCRIPTOR["digest"]),
            valid.replace('"Platform": "linux"', '"Platform": ""'),
            valid.replace("3" * 64, "4" * 64),
            valid.replace('"org.opencontainers.image.version": "24.04"', '"org.opencontainers.image.version": "latest"'),
        ]
        missing_inherited = json.loads(valid)
        del missing_inherited[0]["Config"]["Labels"]["org.opencontainers.image.version"]
        mutations.append(json.dumps(missing_inherited))
        extra_inherited = json.loads(valid)
        extra_inherited[0]["Config"]["Labels"]["org.opencontainers.image.revision"] = "unexpected"
        mutations.append(json.dumps(extra_inherited))
        for mutation in mutations:
            with self.subTest(mutation=mutation), self.assertRaises(BuildInputError):
                validate_container_selection(mutation, expected_owned_labels=owned_labels)

    def test_inner_architecture_requires_exact_uname_and_dpkg_facts(self) -> None:
        self.assertEqual(
            {"dpkg": "amd64", "uname": "x86_64"},
            validate_inner_architecture("uname=x86_64\ndpkg=amd64\n"),
        )
        for mutation in (
            "uname=aarch64\ndpkg=amd64\n",
            "uname=x86_64\ndpkg=arm64\n",
            "uname=x86_64\ndpkg=amd64\nextra=true\n",
            "x86_64 amd64\n",
        ):
            with self.subTest(mutation=mutation), self.assertRaises(BuildInputError):
                validate_inner_architecture(mutation)

    def test_cleanup_is_exactly_ordered_and_requires_data_delete_and_live_absence(self) -> None:
        valid = {
            "phases": list(CLEANUP_PHASES),
            "containerAbsentWhileDaemonLive": True,
            "volumesAbsentWhileDaemonLive": True,
            "deleteArgv": [
                "/opt/homebrew/bin/colima",
                "delete",
                "gasstation-task9-linux-amd64",
                "--data",
                "--force",
            ],
            "profileAbsent": True,
            "contextAbsent": True,
            "runtimeDataAbsent": True,
        }
        validate_cleanup_proof(valid)
        mutations = []
        reordered = json.loads(json.dumps(valid))
        reordered["phases"][1], reordered["phases"][2] = reordered["phases"][2], reordered["phases"][1]
        mutations.append(reordered)
        no_data = json.loads(json.dumps(valid))
        no_data["deleteArgv"].remove("--data")
        mutations.append(no_data)
        no_live = json.loads(json.dumps(valid))
        no_live["volumesAbsentWhileDaemonLive"] = False
        mutations.append(no_live)
        retained = json.loads(json.dumps(valid))
        retained["runtimeDataAbsent"] = False
        mutations.append(retained)
        for mutation in mutations:
            with self.subTest(mutation=mutation), self.assertRaises(BuildInputError):
                validate_cleanup_proof(mutation)

    def test_runtime_absence_checks_profile_lima_instance_disk_and_context(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            colima_home = root / "colima-home"
            docker_config = root / "docker-client"
            colima_home.mkdir()
            docker_config.mkdir()
            self.assertTrue(validate_runtime_absence(colima_home, docker_config))
            retained = (
                colima_home / "gasstation-task9-linux-amd64",
                colima_home / "_lima/colima-gasstation-task9-linux-amd64",
                colima_home / "_lima/_disks/colima-gasstation-task9-linux-amd64",
            )
            for path in retained:
                path.mkdir(parents=True)
                with self.subTest(path=path):
                    self.assertFalse(validate_runtime_absence(colima_home, docker_config))
                path.rmdir()
            meta = docker_config / "contexts/meta/owned/meta.json"
            meta.parent.mkdir(parents=True)
            meta.write_text(json.dumps({"Name": "colima-gasstation-task9-linux-amd64"}))
            self.assertFalse(validate_runtime_absence(colima_home, docker_config))

    def test_aggregate_is_all_or_nothing_and_same_identity(self) -> None:
        rows = {
            name: {"status": "PASS", "sourceCommit": SOURCE, "policySha256": POLICY_SHA}
            for name in (
                "configurationCache",
                "evidenceSessions",
                "metadataCapture",
                "mutations",
                "offlineStrict",
                "onlineColdStrict",
                "productStrict",
                "releaseBinding",
                "reproducibility",
            )
        }
        receipt = aggregate_receipt(
            source_commit=SOURCE,
            policy_sha256=POLICY_SHA,
            attempt_id="attempt-000001",
            rows=rows,
            cleanup_status="PASS",
        )
        self.assertEqual("PASS", receipt["status"])
        partial = dict(rows)
        partial.pop("offlineStrict")
        with self.assertRaisesRegex(BuildInputError, "exact required evidence rows"):
            aggregate_receipt(
                source_commit=SOURCE,
                policy_sha256=POLICY_SHA,
                attempt_id="attempt-000001",
                rows=partial,
                cleanup_status="PASS",
            )
        mixed = json.loads(json.dumps(rows))
        mixed["mutations"]["sourceCommit"] = "6" * 40
        with self.assertRaisesRegex(BuildInputError, "mixed evidence identity"):
            aggregate_receipt(
                source_commit=SOURCE,
                policy_sha256=POLICY_SHA,
                attempt_id="attempt-000001",
                rows=mixed,
                cleanup_status="PASS",
            )


if __name__ == "__main__":
    unittest.main()
