import json
import sys
import tempfile
import unittest
from pathlib import Path


QUALITY_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(QUALITY_DIR))

import capture_baseline


FIXTURES = Path(__file__).resolve().parent / "fixtures"
COMMIT = "7b8c149c9f792aaf43cc00a94ba671929008979e"


class CaptureBaselineTest(unittest.TestCase):
    def test_captures_exact_coverage_and_mutation_counters(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "quality-baseline.json"

            exit_code = capture_baseline.main([
                "--commit", COMMIT,
                "--coverage", str(FIXTURES / "coverage.xml"),
                "--pitest", str(FIXTURES / "pitest-station.xml"),
                "--pitest", str(FIXTURES / "pitest-location.xml"),
                "--pitest", str(FIXTURES / "pitest-settings.xml"),
                "--output", str(output),
            ])

            self.assertEqual(0, exit_code)
            self.assertEqual(
                {
                    "branch": {"covered": 3, "missed": 2, "total": 5},
                    "line": {"covered": 4, "missed": 1, "total": 5},
                },
                json.loads(output.read_text())["coverage"],
            )
            self.assertEqual(
                {"KILLED": 5, "NO_COVERAGE": 4, "SURVIVED": 6, "total": 15},
                json.loads(output.read_text())["mutation"]["status"],
            )
            self.assertEqual(COMMIT, json.loads(output.read_text())["sourceCommit"])
            self.assertEqual(
                [{"dump": "1700000001000", "id": "fixture-session", "start": "1700000000000"}],
                json.loads(output.read_text())["environment"]["jacocoSessions"],
            )

    def test_rejects_missing_report(self):
        with self.assertRaises(SystemExit) as raised:
            capture_baseline.main(self.arguments(coverage=FIXTURES / "missing.xml"))
        self.assertNotEqual(0, raised.exception.code)

    def test_rejects_zero_total_coverage(self):
        with self.assertRaises(SystemExit) as raised:
            capture_baseline.main(self.arguments(coverage=FIXTURES / "coverage-zero.xml"))
        self.assertNotEqual(0, raised.exception.code)

    def test_rejects_malformed_xml(self):
        with self.assertRaises(SystemExit) as raised:
            capture_baseline.main(self.arguments(coverage=FIXTURES / "malformed.xml"))
        self.assertNotEqual(0, raised.exception.code)

    def test_rejects_mixed_explicit_input_commits(self):
        with self.assertRaises(SystemExit) as raised:
            capture_baseline.main(self.arguments(extra=[
                "--input-commit", f"{FIXTURES / 'coverage.xml'}={COMMIT}",
                "--input-commit", f"{FIXTURES / 'pitest-station.xml'}=different-commit",
                "--input-commit", f"{FIXTURES / 'pitest-location.xml'}={COMMIT}",
                "--input-commit", f"{FIXTURES / 'pitest-settings.xml'}={COMMIT}",
            ]))
        self.assertNotEqual(0, raised.exception.code)

    def arguments(self, coverage=FIXTURES / "coverage.xml", extra=None):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "quality-baseline.json"
            return [
                "--commit", COMMIT,
                "--coverage", str(coverage),
                "--pitest", str(FIXTURES / "pitest-station.xml"),
                "--pitest", str(FIXTURES / "pitest-location.xml"),
                "--pitest", str(FIXTURES / "pitest-settings.xml"),
                "--output", str(output),
                *(extra or []),
            ]


if __name__ == "__main__":
    unittest.main()
