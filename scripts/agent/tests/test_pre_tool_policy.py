#!/usr/bin/env python3
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
POLICY = ROOT / "scripts" / "agent" / "pre_tool_policy.py"
STOP_CHECK = ROOT / "scripts" / "agent" / "stop_check.py"
INVALID_POLICY_PAYLOADS = [
    "{",
    "null",
    "[]",
    json.dumps({"tool_input": None}),
    json.dumps({"tool_input": "git reset --hard HEAD"}),
    json.dumps({"tool_input": {"command": None}}),
    json.dumps({"tool_input": {"command": 1}}),
    json.dumps({"tool_input": {"command": True}}),
    json.dumps({"tool_input": {"command": ["git", "reset", "--hard"]}}),
]


class PreToolPolicyTest(unittest.TestCase):
    def run_raw_policy(
        self, input_text: str, surface: str = "codex", from_env: bool = False
    ):
        env = os.environ.copy()
        if surface == "claude":
            env["GASSTATION_HOOK_SURFACE"] = "claude"
        if from_env:
            env["CLAUDE_TOOL_INPUT"] = input_text
            input_text = ""
        return subprocess.run(
            [sys.executable, str(POLICY)],
            input=input_text,
            text=True,
            capture_output=True,
            env=env,
        )

    def run_policy(self, command: str, surface: str = "codex", from_env: bool = False):
        payload = {
            "hook_event_name": "PreToolUse",
            "tool_name": "Bash",
            "tool_input": {"command": command},
        }
        return self.run_raw_policy(
            json.dumps(payload), surface=surface, from_env=from_env
        )

    def codex_denial(self, command: str):
        result = self.run_policy(command)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotEqual("", result.stdout, f"expected denial for: {command}")
        return json.loads(result.stdout)

    def test_allows_safe_command_variants(self):
        commands = [
            "./gradlew :app:testDemoDebugUnitTest",
            "echo 'git reset --hard HEAD'",
            "echo 'safe\ngit reset --hard HEAD'",
            "git reset --soft HEAD",
            "git clean -d",
            "git clean -n -d -f",
            "git clean --dry-run -d --force",
            "git push origin main",
            "git push origin feature --force",
            "git push --dry-run --force origin main",
            "git push origin main --force --dry-run",
            "rm -rf build",
            "echo 'DROP TABLE users;'",
            "printf 'DROP TABLE users;'",
            "sed -i 's/a/b/' .env",
        ]
        for command in commands:
            with self.subTest(command=command):
                result = self.run_policy(command)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual("", result.stdout)

    def test_denies_explicit_protected_command_variants(self):
        commands = [
            "git reset --hard HEAD~1",
            "git -C . reset --hard HEAD",
            "echo safe && git reset --hard HEAD",
            "echo safe\ngit reset --hard HEAD",
            "command git reset --hard HEAD",
            "command -p git reset --hard HEAD",
            "command -- git reset --hard HEAD",
            "env git reset --hard HEAD",
            "env FUEL=demo git reset --hard HEAD",
            "env -i FUEL=demo git reset --hard HEAD",
            "env -u FUEL git reset --hard HEAD",
            "env -- git reset --hard HEAD",
            "git clean -fd",
            "git clean -d -f",
            "git -C . clean -d --force",
            "git push origin main --force",
            "git push --force origin main",
            "git push origin --force-with-lease main",
            "git push -f origin main",
            "git push origin +HEAD:main",
            "rm -rf /",
            "rm -r -f /",
            "rm -R -f /",
            "rm --recursive --force /",
            "rm -fr '$HOME'",
            "rm -rf '$HOME/'",
            "rm -rf '${HOME}/'",
            "rm -rf '~/'",
            "rm -rf //",
            "rm -rf ////",
            "psql app -c 'DROP TABLE users;'",
            "mysql app -e 'DROP DATABASE fuel;'",
            "sqlite3 app.db 'DROP SCHEMA cache;'",
            "printf 'DROP TABLE users;' | psql app",
            "cat local.properties",
            "head keystore.properties",
            "cat '.env'",
            "tail config/.env.prod",
            "sed -n '1p' .env",
        ]
        for command in commands:
            with self.subTest(command=command):
                output = self.codex_denial(command)
                self.assertEqual(
                    "deny", output["hookSpecificOutput"]["permissionDecision"]
                )

    def test_denies_hard_reset_with_official_codex_shape(self):
        output = self.codex_denial("git reset --hard HEAD~1")

        self.assertEqual({"hookSpecificOutput"}, set(output))
        self.assertEqual(
            {
                "hookEventName": "PreToolUse",
                "permissionDecision": "deny",
                "permissionDecisionReason": "destructive git reset is blocked",
            },
            output["hookSpecificOutput"],
        )

    def test_claude_denial_parses_environment_payload_and_uses_exit_two(self):
        result = self.run_policy(
            "git reset --hard HEAD~1", surface="claude", from_env=True
        )
        self.assertEqual(2, result.returncode)
        self.assertEqual("", result.stdout)
        self.assertIn("destructive git reset", result.stderr)

    def test_codex_invalid_payloads_fail_closed_without_traceback(self):
        for raw in INVALID_POLICY_PAYLOADS:
            with self.subTest(raw=raw):
                result = self.run_raw_policy(raw)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertNotIn("Traceback", result.stderr)
                output = json.loads(result.stdout)
                self.assertEqual({"hookSpecificOutput"}, set(output))
                specific = output["hookSpecificOutput"]
                self.assertEqual(
                    {"hookEventName", "permissionDecision", "permissionDecisionReason"},
                    set(specific),
                )
                self.assertEqual("PreToolUse", specific["hookEventName"])
                self.assertEqual("deny", specific["permissionDecision"])
                self.assertIn("invalid", specific["permissionDecisionReason"])

    def test_claude_invalid_payloads_fail_closed_without_traceback(self):
        for raw in INVALID_POLICY_PAYLOADS:
            with self.subTest(raw=raw):
                result = self.run_raw_policy(raw, surface="claude")
                self.assertEqual(2, result.returncode)
                self.assertEqual("", result.stdout)
                self.assertNotIn("Traceback", result.stderr)
                self.assertIn("invalid", result.stderr)

    def test_malformed_shell_command_fails_closed(self):
        output = self.codex_denial("cat '")
        self.assertIn(
            "malformed", output["hookSpecificOutput"]["permissionDecisionReason"]
        )


class StopCheckTest(unittest.TestCase):
    def run_stop_raw(self, input_text, cwd=ROOT):
        return subprocess.run(
            [sys.executable, str(STOP_CHECK)],
            input=input_text,
            text=True,
            capture_output=True,
            cwd=cwd,
        )

    def run_stop(self, payload, cwd=ROOT):
        return self.run_stop_raw(json.dumps(payload), cwd=cwd)

    def test_active_stop_hook_returns_empty_output_object(self):
        result = self.run_stop({"stop_hook_active": True})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual({}, json.loads(result.stdout))

    def test_stop_invalid_payloads_warn_without_traceback_or_loop(self):
        invalid_payloads = [
            "{",
            "null",
            "[]",
            '"invalid"',
            json.dumps({"stop_hook_active": "true"}),
            json.dumps({"stop_hook_active": 1}),
            json.dumps({"stop_hook_active": None}),
        ]
        for raw in invalid_payloads:
            with self.subTest(raw=raw):
                result = self.run_stop_raw(raw)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertNotIn("Traceback", result.stderr)
                output = json.loads(result.stdout)
                self.assertEqual({"continue", "systemMessage"}, set(output))
                self.assertIs(True, output["continue"])
                self.assertIn("invalid", output["systemMessage"].lower())

    def test_inactive_clean_stop_returns_empty_output_object(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            fixture_root = Path(temp_dir)
            subprocess.run(
                ["git", "init", "--quiet", str(fixture_root)], check=True
            )
            subprocess.run(
                ["git", "-C", str(fixture_root), "config", "user.name", "Agent Test"],
                check=True,
            )
            subprocess.run(
                [
                    "git",
                    "-C",
                    str(fixture_root),
                    "config",
                    "user.email",
                    "agent-test@example.invalid",
                ],
                check=True,
            )
            agent_dir = fixture_root / "scripts" / "agent"
            agent_dir.mkdir(parents=True)
            checker = agent_dir / "check-contracts.sh"
            checker.write_text("#!/bin/sh\nexit 0\n")
            checker.chmod(0o755)
            subprocess.run(
                ["git", "-C", str(fixture_root), "add", "scripts/agent/check-contracts.sh"],
                check=True,
            )
            subprocess.run(
                ["git", "-C", str(fixture_root), "commit", "--quiet", "-m", "fixture"],
                check=True,
            )

            result = self.run_stop(
                {"hook_event_name": "Stop", "stop_hook_active": False},
                cwd=fixture_root,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual({}, json.loads(result.stdout))

    def test_warning_uses_supported_fields_without_continuation_decision(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            fixture_root = Path(temp_dir)
            subprocess.run(
                ["git", "init", "--quiet", str(fixture_root)], check=True
            )
            agent_dir = fixture_root / "scripts" / "agent"
            agent_dir.mkdir(parents=True)
            checker = agent_dir / "check-contracts.sh"
            checker.write_text(
                "#!/bin/sh\n"
                "printf '%s' \"$*\" > check-args.txt\n"
                "printf 'fixture contract warning\\n' >&2\n"
                "exit 1\n"
            )
            checker.chmod(0o755)

            result = self.run_stop(
                {"hook_event_name": "Stop", "stop_hook_active": False},
                cwd=fixture_root,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            output = json.loads(result.stdout)
            self.assertEqual({"continue", "systemMessage"}, set(output))
            self.assertIs(True, output["continue"])
            self.assertIn("fixture contract warning", output["systemMessage"])
            self.assertEqual("--quick", (fixture_root / "check-args.txt").read_text())


if __name__ == "__main__":
    unittest.main()
