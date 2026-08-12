#!/usr/bin/env python3
"""Behavior tests for the live-document contract validator."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest import mock


TEST_DIR = Path(__file__).resolve().parent
SCRIPT = TEST_DIR.parent / "validate.py"
CASE_DATA = json.loads((TEST_DIR / "fixtures" / "cases.json").read_text())


def load_validator():
    spec = importlib.util.spec_from_file_location("docs_validate_review", SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError("validator module could not be loaded")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


VALIDATOR = load_validator()
POLICY_TEXT = json.dumps(VALIDATOR.EXPECTED_STATION_DATA_POLICY, indent=2) + "\n"

LIVE_PATHS = [
    "AGENTS.md",
    "README.md",
    "CONTRIBUTING.md",
    "CHANGELOG.md",
    ".impeccable.md",
    "docs/README.md",
    "docs/AGENTS.md",
    "docs/onboarding/developer-onboarding-guide.md",
    "docs/agent-workflow.md",
    "docs/project-reading-guide.md",
    "docs/architecture.md",
    "docs/module-contracts.md",
    "docs/state-model.md",
    "docs/offline-strategy.md",
    "docs/test-strategy.md",
    "docs/verification-matrix.md",
    "docs/security-trade-offs.md",
    "docs/deployment.md",
    "docs/performance.md",
    "docs/build-velocity.md",
    "core/database/AGENTS.md",
    "benchmark/AGENTS.md",
    "docs/adr/2026-05-18-backend-proxy-escalation.md",
]


class FixtureRepository:
    def __init__(self, root: Path):
        self.root = root
        for path in LIVE_PATHS:
            if path.endswith(".json"):
                continue
            self.write(path, f"# {Path(path).stem}\n")
        self.write(
            "settings.gradle.kts",
            "include(\n"
            + "".join(f'    "{module}",\n' for module in CASE_DATA["activeModules"])
            + ")\n",
        )
        self.write(
            ".github/workflows/android.yml",
            "jobs:\n"
            + "".join(f"  {job}:\n    runs-on: ubuntu-latest\n" for job in CASE_DATA["ciJobs"]),
        )
        self.write("app/src/main/kotlin/App.kt", "class App\n")
        self.documents = [self.entry(path) for path in LIVE_PATHS]
        offline = next(entry for entry in self.documents if entry["path"] == VALIDATOR.STATION_DATA_POLICY_OWNER)
        offline["authoritativeSources"].insert(0, VALIDATOR.STATION_DATA_POLICY_PATH)
        offline["authoritativeSources"].insert(1, VALIDATOR.STATION_DATA_POLICY_CONSUMERS_PATH)
        self.write(VALIDATOR.STATION_DATA_POLICY_PATH, POLICY_TEXT)
        self.write(
            VALIDATOR.STATION_DATA_POLICY_CONSUMERS_PATH,
            json.dumps(
                {
                    "schemaVersion": 1,
                    "canonicalOwner": VALIDATOR.STATION_DATA_POLICY_OWNER,
                    "canonicalAnchor": "기계-판독-정책-계약",
                    "statementMode": "reference_only",
                    "consumers": {
                        "README.md": {"retry": 2},
                        "docs/agent-workflow.md": {"retry": 1},
                        "docs/onboarding/developer-onboarding-guide.md": {"retry": 1, "freshness": 1},
                        "docs/test-strategy.md": {"retry": 1, "freshness": 1},
                    },
                },
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
        )
        self.write(
            VALIDATOR.STATION_DATA_POLICY_OWNER,
            "# offline-strategy\n\n## 기계 판독 정책 계약\n\n"
            + VALIDATOR.STATION_DATA_POLICY_START
            + "\n```json\n"
            + POLICY_TEXT.rstrip()
            + "\n```\n"
            + VALIDATOR.STATION_DATA_POLICY_END
            + "\n",
        )
        reference_lines = {
            "README.md": (
                "<!-- station-data-policy-ref: retry -->"
                "[structured `retry` contract](docs/offline-strategy.md#기계-판독-정책-계약)\n"
            ) * 2,
            "docs/onboarding/developer-onboarding-guide.md": (
                "<!-- station-data-policy-ref: retry -->"
                "[structured `retry` contract](../offline-strategy.md#기계-판독-정책-계약)\n"
                "<!-- station-data-policy-ref: freshness -->"
                "[structured `freshness` contract](../offline-strategy.md#기계-판독-정책-계약)\n"
            ),
            "docs/agent-workflow.md": (
                "<!-- station-data-policy-ref: retry -->"
                "[structured `retry` contract](offline-strategy.md#기계-판독-정책-계약)\n"
            ),
            "docs/test-strategy.md": (
                "<!-- station-data-policy-ref: retry -->"
                "[structured `retry` contract](offline-strategy.md#기계-판독-정책-계약)\n"
                "<!-- station-data-policy-ref: freshness -->"
                "[structured `freshness` contract](offline-strategy.md#기계-판독-정책-계약)\n"
            ),
        }
        for path, references in reference_lines.items():
            self.append(path, references)
        self.write_hub_direct_links()
        self.write_catalog()

    def write(self, path: str, text: str) -> None:
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)

    def append(self, path: str, text: str) -> None:
        target = self.root / path
        target.write_text(target.read_text() + text)

    @staticmethod
    def entry(path: str) -> dict[str, object]:
        return {
            "path": path,
            "kind": "contract",
            "owner": f"owner for {path}",
            "authoritativeSources": ["settings.gradle.kts"],
            "reviewTriggers": ["source changes"],
            "verificationScope": "python3 scripts/docs/validate.py",
        }

    def write_catalog(self) -> None:
        self.write(
            "docs/documentation-catalog.json",
            json.dumps(
                {"schemaVersion": 1, "documents": self.documents},
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
        )

    def write_hub_direct_links(self) -> None:
        links = []
        for path in LIVE_PATHS:
            if path == "docs/README.md":
                continue
            target = os.path.relpath(path, "docs")
            links.append(f"- [{path}]({target})")
        self.write("docs/README.md", "# Documentation hub\n\n" + "\n".join(links) + "\n")


class ValidatorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.mkdtemp(prefix="docs-validator-")
        self.repo = FixtureRepository(Path(self.temp_dir))

    def tearDown(self) -> None:
        shutil.rmtree(self.temp_dir)

    def run_validator(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["python3", str(SCRIPT), "--root", str(self.repo.root), *arguments],
            text=True,
            capture_output=True,
        )

    def assert_rejected(self, expected: str) -> None:
        result = self.run_validator()
        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertIn(expected, result.stderr)

    def replace_policy_reference(self, path: str, old: str, new: str) -> None:
        target = self.repo.root / path
        text = target.read_text()
        self.assertIn(old, text)
        target.write_text(text.replace(old, new, 1))

    def test_rejects_external_suffix_as_canonical_policy_link(self) -> None:
        self.replace_policy_reference(
            "README.md",
            "docs/offline-strategy.md#기계-판독-정책-계약",
            "https://example.invalid/offline-strategy.md#기계-판독-정책-계약",
        )
        self.assert_rejected("must resolve to the canonical owner and anchor")

    def test_rejects_document_top_link_with_plain_anchor_decoy(self) -> None:
        self.replace_policy_reference(
            "README.md",
            "docs/offline-strategy.md#기계-판독-정책-계약)",
            "docs/offline-strategy.md) offline-strategy.md#기계-판독-정책-계약",
        )
        self.assert_rejected("station data policy marker must be a reference-only statement")

    def test_rejects_wrong_canonical_policy_fragment(self) -> None:
        self.replace_policy_reference(
            "docs/agent-workflow.md",
            "offline-strategy.md#기계-판독-정책-계약",
            "offline-strategy.md#stale-판정",
        )
        self.assert_rejected("must resolve to the canonical owner and anchor")

    def test_rejects_normative_synonym_appended_to_reference_statement(self) -> None:
        self.replace_policy_reference(
            "docs/onboarding/developer-onboarding-guide.md",
            "[structured `freshness` contract](../offline-strategy.md#기계-판독-정책-계약)",
            "[structured `freshness` contract](../offline-strategy.md#기계-판독-정책-계약) "
            "보관 후 300초까지는 최신이며 그 뒤에는 오래된 결과입니다.",
        )
        self.assert_rejected("must be a reference-only statement")

    def test_unrelated_retry_ui_prose_is_not_a_station_policy_claim(self) -> None:
        self.repo.append(
            "docs/onboarding/developer-onboarding-guide.md",
            "Network 실패 화면은 사용자에게 재시도 버튼을 제공합니다.\n",
        )
        result = self.run_validator()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_rejects_unregistered_marker_in_new_catalog_consumer(self) -> None:
        validator = load_validator()
        path = "docs/architecture.md"
        self.repo.append(
            path,
            "<!-- station-data-policy-ref: retry -->"
            "[structured `retry` contract](offline-strategy.md#기계-판독-정책-계약)\n",
        )
        entries, _ = validator.load_catalog(self.repo.root)
        texts = {
            entry["path"]: (self.repo.root / entry["path"]).read_text()
            for entry in entries
            if entry["path"].endswith(".md")
        }
        issues = validator.station_policy_reference_issues(self.repo.root, entries, texts)
        self.assertTrue(any("unregistered station data policy consumer" in issue for issue in issues))

    def test_registered_new_catalog_consumer_is_structurally_accepted(self) -> None:
        path = "docs/architecture.md"
        self.repo.append(
            path,
            "<!-- station-data-policy-ref: retry -->"
            "[structured `retry` contract](offline-strategy.md#기계-판독-정책-계약)\n",
        )
        manifest_path = self.repo.root / VALIDATOR.STATION_DATA_POLICY_CONSUMERS_PATH
        manifest = json.loads(manifest_path.read_text())
        manifest["consumers"][path] = {"retry": 1}
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False))
        result = self.run_validator()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_rejects_manifest_consumer_missing_from_catalog(self) -> None:
        manifest_path = self.repo.root / VALIDATOR.STATION_DATA_POLICY_CONSUMERS_PATH
        manifest = json.loads(manifest_path.read_text())
        manifest["consumers"]["docs/not-cataloged.md"] = {"freshness": 1}
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False))
        self.assert_rejected("consumer is not cataloged live Markdown")

    def test_rejects_registered_new_consumer_with_exclusive_retry_claim_without_marker(self) -> None:
        path = "docs/architecture.md"
        self.repo.append(path, "Timeout과 Network 실패에 한해 한 번 재시도합니다.\n")
        self.assert_rejected("duplicate automatic retry policy claim")

    def test_rejects_korean_synonym_automatic_retry_claim(self) -> None:
        self.repo.append(
            "docs/architecture.md",
            "타임아웃이나 통신 장애인 경우에만 반 초 뒤 단 한 차례 더 요청합니다.\n",
        )
        self.assert_rejected("duplicate automatic retry policy claim")

    def test_rejects_korean_synonym_freshness_boundary_claim(self) -> None:
        self.repo.append(
            "docs/architecture.md",
            "보관 후 300초까지는 최신이며 그 뒤에는 오래된 결과입니다.\n",
        )
        self.assert_rejected("duplicate freshness boundary claim")

    def test_rejects_structured_station_policy_source_drift(self) -> None:
        policy = json.loads(POLICY_TEXT)
        policy["retry"]["retryableHttpStatuses"].insert(0, 404)
        self.repo.write(VALIDATOR.STATION_DATA_POLICY_PATH, json.dumps(policy))
        self.assert_rejected("station data policy fields differ from the approved contract")

    def test_rejects_rendered_station_policy_drift(self) -> None:
        policy = json.loads(POLICY_TEXT)
        policy["schema"]["migrationEvidence"]["connectedDeviceExecuted"] = True
        owner = self.repo.root / VALIDATOR.STATION_DATA_POLICY_OWNER
        text = owner.read_text()
        start = text.index(VALIDATOR.STATION_DATA_POLICY_START) + len(VALIDATOR.STATION_DATA_POLICY_START)
        end = text.index(VALIDATOR.STATION_DATA_POLICY_END, start)
        owner.write_text(text[:start] + "\n```json\n" + json.dumps(policy) + "\n```\n" + text[end:])
        self.assert_rejected("station data policy fields differ from the approved contract")

    def test_rejects_duplicate_station_policy_owner_and_catalog_source(self) -> None:
        self.repo.append(
            "docs/architecture.md",
            VALIDATOR.STATION_DATA_POLICY_START + "\n" + VALIDATOR.STATION_DATA_POLICY_END + "\n",
        )
        self.assert_rejected("station data policy block owner must be exactly")

        self.repo = FixtureRepository(self.repo.root)
        architecture = next(entry for entry in self.repo.documents if entry["path"] == "docs/architecture.md")
        architecture["authoritativeSources"].append(VALIDATOR.STATION_DATA_POLICY_PATH)
        self.repo.write_catalog()
        self.assert_rejected("structured station policy catalog owner must be exactly")

        self.repo = FixtureRepository(self.repo.root)
        offline = next(
            entry for entry in self.repo.documents if entry["path"] == VALIDATOR.STATION_DATA_POLICY_OWNER
        )
        offline["authoritativeSources"].append(VALIDATOR.STATION_DATA_POLICY_PATH)
        self.repo.write_catalog()
        self.assert_rejected("structured station policy catalog owner must be exactly")

    def test_rejects_duplicate_station_policy_json_key(self) -> None:
        self.repo.write(VALIDATOR.STATION_DATA_POLICY_PATH, '{"schemaVersion": 1, "schemaVersion": 1}')
        self.assert_rejected("duplicate JSON key")

    def test_rejects_duplicate_catalog_entry(self) -> None:
        self.repo.documents.append(dict(self.repo.documents[0]))
        self.repo.write_catalog()
        self.assert_rejected("duplicate catalog entry: AGENTS.md")

    def test_rejects_missing_catalog_entry(self) -> None:
        self.repo.documents = self.repo.documents[:-1]
        self.repo.write_catalog()
        self.assert_rejected("live document missing from catalog")

    def test_rejects_absent_required_field(self) -> None:
        del self.repo.documents[0]["owner"]
        self.repo.write_catalog()
        self.assert_rejected("required field missing or empty: owner")

    def test_rejects_wrong_catalog_field_type(self) -> None:
        self.repo.documents[0]["verificationScope"] = ["not", "a", "string"]
        self.repo.write_catalog()
        self.assert_rejected("verificationScope must be a non-empty string")

    def test_rejects_unregistered_live_set_addition(self) -> None:
        self.repo.write("docs/unregistered.md", "# Not in the approved live set\n")
        self.repo.documents.append(self.repo.entry("docs/unregistered.md"))
        self.repo.write_catalog()
        self.assert_rejected("unexpected live catalog entry: docs/unregistered.md")

    def test_rejects_broken_relative_link(self) -> None:
        self.repo.append("README.md", "[broken](docs/does-not-exist.md)\n")
        self.assert_rejected("missing link target")

    def test_rejects_broken_local_image_link(self) -> None:
        self.repo.append("README.md", "![broken](docs/missing.png)\n")
        self.assert_rejected("missing link target")

    def test_accepts_decoded_heading_anchor(self) -> None:
        self.repo.write("docs/target.md", f"# {CASE_DATA['validAnchor']}\n")
        self.repo.append("README.md", "[target](docs/target.md#price-%26-distance)\n")
        result = self.run_validator()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_accepts_balanced_parenthesized_link_destinations_and_titles(self) -> None:
        self.repo.write("docs/API(v2).md", "# Compact\n")
        self.repo.write("docs/API (v3).md", "# Spaced\n")
        self.repo.append(
            "README.md",
            '[compact](docs/API(v2).md "title")\n'
            '[spaced](<docs/API (v3).md> "title")\n',
        )
        result = self.run_validator()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_ignores_links_and_references_inside_code(self) -> None:
        self.repo.append(
            "README.md",
            "```md\n[broken](docs/nope.md) :feature:missing\n```\n"
            "`[also broken](docs/nope.md)`\n",
        )
        result = self.run_validator()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_rejects_live_document_more_than_two_links_from_hub(self) -> None:
        self.repo.write_hub_direct_links()
        self.repo.write("docs/README.md", "# Hub\n\n[mid](middle.md)\n")
        self.repo.write("docs/middle.md", "# Middle\n\n[next](next.md)\n")
        self.repo.write("docs/next.md", "# Next\n\n[agents](../AGENTS.md)\n")
        self.assert_rejected("not reachable from docs/README.md within 2 links: AGENTS.md")

    def test_rejects_nonexistent_module_path_and_ci_job(self) -> None:
        cases = {
            "module": ("Unknown module `:feature:missing`\n", "inactive Gradle module"),
            "path": ("Repository path `app/src/main/kotlin/Missing.kt`\n", "missing repository path"),
            "job": ("CI job `not-a-job` must pass.\n", "missing CI job"),
        }
        for name, (text, expected) in cases.items():
            with self.subTest(name=name):
                fresh = FixtureRepository(self.repo.root)
                fresh.append("README.md", text)
                self.assert_rejected(expected)

    def test_rejects_personal_home_paths_in_live_prose(self) -> None:
        for personal_path in (
            "/Users/alice/project/file.txt",
            "/home/alice/project/file.txt",
            "C:\\Users\\alice\\project\\file.txt",
        ):
            with self.subTest(path=personal_path):
                FixtureRepository(self.repo.root).append("README.md", personal_path + "\n")
                self.assert_rejected("personal home path")

    def test_secret_detection_targets_values_not_names_or_examples(self) -> None:
        self.repo.append(
            "README.md",
            "Use API_KEY or TOKEN as variable names.\n"
            "```properties\nAPI_KEY=<issued-key>\nTOKEN=example-token\nSECRET=${SECRET}\n```\n",
        )
        result = self.run_validator()
        self.assertEqual(0, result.returncode, result.stderr)
        self.repo.append("README.md", "```properties\nSERVICE_API_KEY=sk-live-value-123\n```\n")
        self.assert_rejected("likely secret assignment")

    def test_rejects_duplicate_command_owner_id(self) -> None:
        self.repo.append("README.md", "<!-- command-owner: verification.fast -->\n")
        self.repo.append("CONTRIBUTING.md", "<!-- command-owner: verification.fast -->\n")
        self.assert_rejected("duplicate command-owner id: verification.fast")

    def test_accepts_valid_two_link_navigation(self) -> None:
        self.repo.write("docs/README.md", "# Hub\n\n[guide](project-reading-guide.md)\n")
        links = []
        for path in LIVE_PATHS:
            if path in {"docs/README.md", "docs/project-reading-guide.md"}:
                continue
            links.append(f"[{path}]({os.path.relpath(path, 'docs')})")
        self.repo.write("docs/project-reading-guide.md", "# Guide\n\n" + "\n".join(links))
        result = self.run_validator()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_historical_broken_links_and_placeholders_are_non_blocking(self) -> None:
        self.repo.write(
            "docs/history/old-plan.md",
            "# Old plan\n\n[missing](never-created.md) TODO <placeholder>\n",
        )
        result = self.run_validator()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_rejects_stale_ci_job_in_cataloged_evidence(self) -> None:
        for entry in self.repo.documents:
            if entry["path"] == "CHANGELOG.md":
                entry["kind"] = "evidence"
        self.repo.write_catalog()
        self.repo.append("CHANGELOG.md", "CI job `removed-job` used to run.\n")
        self.assert_rejected("missing CI job: removed-job")

    def test_optional_gradle_check_discovers_once_and_checks_only_owned_commands(self) -> None:
        self.repo.append(
            "docs/verification-matrix.md",
            "<!-- command-owner: verification.fast -->\n"
            "```bash\n./gradlew :app:test\n```\n"
            "Unowned example: `./gradlew :app:notARealTask`.\n",
        )
        self.repo.write(
            "gradlew",
            "#!/usr/bin/env bash\n"
            "printf 'call\\n' >> gradle-calls.txt\n"
            "printf '%s\\n' ':app:test - Runs tests'\n",
        )
        (self.repo.root / "gradlew").chmod(0o755)
        result = self.run_validator("--check-gradle-tasks")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("call\n", (self.repo.root / "gradle-calls.txt").read_text())

    def test_default_validation_never_invokes_gradle(self) -> None:
        self.repo.write(
            "gradlew",
            "#!/usr/bin/env bash\nprintf 'called\\n' > gradle-calls.txt\n",
        )
        (self.repo.root / "gradlew").chmod(0o755)
        result = self.run_validator()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse((self.repo.root / "gradle-calls.txt").exists())

    def test_gradle_check_extracts_all_owned_blocks_and_skips_option_values(self) -> None:
        validator = load_validator()
        owned_commands = (
            "<!-- command-owner: verification.full -->\n"
            "```bash\n./gradlew --warning-mode fail :app:first --continue\n```\n"
            "Some explanation.\n"
            "```bash\n./gradlew --max-workers 2 -Pprofile=ci :app:second --tests com.example.Test :app:third\n```\n"
        )
        self.assertEqual(
            {":app:first", ":app:second", ":app:third"},
            validator.canonical_gradle_tasks({"docs/verification-matrix.md": owned_commands}),
        )
        self.repo.append(
            "docs/verification-matrix.md",
            owned_commands,
        )
        self.repo.write(
            "gradlew",
            "#!/usr/bin/env bash\n"
            "printf '%s\\n' ':app:first - First' ':app:second - Second' ':app:third - Third'\n",
        )
        (self.repo.root / "gradlew").chmod(0o755)
        result = self.run_validator("--check-gradle-tasks")
        self.assertEqual(0, result.returncode, result.stderr)

    def test_owned_block_without_gradle_tasks_does_not_invoke_gradle(self) -> None:
        self.repo.append(
            "docs/verification-matrix.md",
            "<!-- command-owner: verification.none -->\n```bash\n./gradlew --version\n```\n",
        )
        self.repo.write("gradlew", "#!/usr/bin/env bash\nprintf called > gradle-calls.txt\n")
        (self.repo.root / "gradlew").chmod(0o755)
        result = self.run_validator("--check-gradle-tasks")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse((self.repo.root / "gradle-calls.txt").exists())

    def test_gradle_discovery_timeout_is_a_stable_issue(self) -> None:
        validator = load_validator()
        self.repo.write("gradlew", "#!/usr/bin/env bash\n")
        with mock.patch.object(
            validator.subprocess,
            "run",
            side_effect=subprocess.TimeoutExpired(["gradlew", "tasks", "--all"], 30),
        ):
            issues = validator.check_gradle_tasks(self.repo.root, {
                "README.md": "<!-- command-owner: slow -->\n```bash\n./gradlew :app:test\n```\n"
            })
        self.assertEqual(["gradlew:1: Gradle task discovery timed out after 30 seconds"], issues)

    def test_gradle_discovery_os_error_is_a_stable_issue(self) -> None:
        validator = load_validator()
        self.repo.write("gradlew", "#!/usr/bin/env bash\n")
        with mock.patch.object(validator.subprocess, "run", side_effect=OSError("permission denied")):
            issues = validator.check_gradle_tasks(self.repo.root, {
                "README.md": "<!-- command-owner: broken -->\n```bash\n./gradlew :app:test\n```\n"
            })
        self.assertEqual(["gradlew:1: Gradle task discovery could not start: permission denied"], issues)


if __name__ == "__main__":
    unittest.main()
