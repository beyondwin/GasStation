#!/usr/bin/env python3
"""Behavior tests for the live-document contract validator."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


TEST_DIR = Path(__file__).resolve().parent
SCRIPT = TEST_DIR.parent / "validate.py"
CASE_DATA = json.loads((TEST_DIR / "fixtures" / "cases.json").read_text())

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


if __name__ == "__main__":
    unittest.main()
