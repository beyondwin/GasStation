#!/usr/bin/env python3
import json
import os
import runpy
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
        self,
        input_text: str,
        surface: str = "codex",
        from_env: bool = False,
        cwd: Path = ROOT,
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
            cwd=cwd,
        )

    def run_policy(
        self,
        command: str,
        surface: str = "codex",
        from_env: bool = False,
        cwd: Path = ROOT,
        payload_cwd=None,
    ):
        payload = {
            "hook_event_name": "PreToolUse",
            "tool_name": "Bash",
            "tool_input": {"command": command},
        }
        if payload_cwd is not None:
            payload["cwd"] = payload_cwd
        return self.run_raw_policy(
            json.dumps(payload), surface=surface, from_env=from_env, cwd=cwd
        )

    def codex_denial(self, command: str, **kwargs):
        result = self.run_policy(command, **kwargs)
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
            "rm -rf build/*",
            "rm -rf 'build/{*,.*}'",
            "rm -rf /tmp/gasstation-agent-safe-build",
            "rm -rf /tmp/gasstation-agent-safe-*",
            "git checkout feature/topic",
            "git checkout -- README.md",
            "git checkout -- 'docs/*.md'",
            "git checkout -p -- .",
            "git restore README.md",
            "git restore 'feature/*'",
            "git restore --patch .",
            "git restore --patch '*'",
            "git push --all",
            "git push --dry-run --mirror",
            "git push --dry-run --force --all",
            "git push --dry-run --force origin 'refs/heads/*:refs/heads/*'",
            "echo 'DROP TABLE users;'",
            "printf 'DROP TABLE users;'",
            "cat build/*.txt",
            "cat build/*",
            "cut -d= -f2 build/output.txt",
            "grep local.properties README.md",
            "grep -e local.properties README.md",
            "grep -inelocal.properties README.md",
            "grep -- local.properties README.md",
            "grep . build/*",
            "grep -q . local.properties",
            "grep --quiet . local.properties",
            "grep --silent . local.properties",
            "grep -l . local.properties",
            "grep -L . local.properties",
            "grep --files-with-matches . local.properties",
            "grep --files-without-match . local.properties",
            "grep -c . local.properties",
            "grep --count . local.properties",
            "grep -nq . local.properties",
            "grep -inc . local.properties",
            "awk '/local.properties/' README.md",
            "awk -f local.properties README.md",
            "sed -i 's/a/b/' .env",
            "bash -c \"echo 'rm -rf /'\"",
            "sh -c \"printf '%s\\n' 'git restore .'\"",
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
            "git push --mirror",
            "git push --force --all",
            "git push --all --force-with-lease",
            "git push --force origin 'refs/heads/*:refs/heads/*'",
            "git push origin '+refs/heads/*:refs/heads/*'",
            "git push --force origin 'HEAD:refs/heads/*'",
            "git checkout -- .",
            "git checkout -- ./",
            "git checkout -- '*'",
            "git checkout -- '**'",
            "git checkout -- './*'",
            "git checkout -- ':(top)**'",
            "git restore .",
            "git restore --worktree .",
            "git restore --source=HEAD -- .",
            "git restore '*'",
            "git restore '**'",
            "git restore './*'",
            "git restore ':(top)**'",
            "git restore ':(glob,top)**/*'",
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
            "rm -rf ./*",
            "rm -rf './{*,.*}'",
            "rm -rf '$HOME/*'",
            "rm -rf \"$(git rev-parse --show-toplevel)\"",
            "rm -rf `git rev-parse --show-toplevel`",
            "psql app -c 'DROP TABLE users;'",
            "mysql app -e 'DROP DATABASE fuel;'",
            "sqlite3 app.db 'DROP SCHEMA cache;'",
            "printf 'DROP TABLE users;' | psql app",
            "cat local.properties",
            "head keystore.properties",
            "cat '.env'",
            "tail config/.env.prod",
            "sed -n '1p' .env",
            "cat local.propert*",
            "cat .env*",
            "cut -d= -f2 local.properties",
            "grep . local.properties",
            "grep . local.propert*",
            "grep -n . local.properties",
            "grep -e . local.properties",
            "grep -ine. local.properties",
            "grep -- . local.properties",
            "awk '{print}' local.properties",
            "awk -F= '{print $2}' local.properties",
            "nl local.properties",
            "bat config/.env.prod",
            "bash -c 'rm -rf /'",
            "/bin/sh -c 'git reset --hard HEAD'",
            "env FUEL=demo /bin/zsh -lc 'git checkout -- .'",
            "command bash -c \"sh -c 'rm -rf /'\"",
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

    def test_grok_camelcase_payload_allows_safe_command(self):
        payload = {
            "hookEventName": "pre_tool_use",
            "toolName": "run_terminal_command",
            "toolInput": {"command": "./gradlew :app:testDemoDebugUnitTest"},
            "cwd": str(ROOT),
        }
        result = self.run_raw_policy(json.dumps(payload), surface="claude")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("", result.stdout)
        self.assertEqual("", result.stderr)

    def test_grok_camelcase_payload_denies_hard_reset_on_claude_surface(self):
        payload = {
            "hookEventName": "pre_tool_use",
            "toolName": "run_terminal_command",
            "toolInput": {"command": "git reset --hard HEAD~1"},
            "cwd": str(ROOT),
        }
        result = self.run_raw_policy(json.dumps(payload), surface="claude")
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

    def test_nested_shell_missing_or_malformed_command_fails_closed(self):
        commands = ["bash -c", "bash -c \"cat '\""]
        for command in commands:
            with self.subTest(command=command):
                output = self.codex_denial(command)
                self.assertIn(
                    "malformed",
                    output["hookSpecificOutput"]["permissionDecisionReason"],
                )

    def test_recursive_force_rm_resolves_protected_directories_without_leaking_paths(self):
        commands = [
            "rm -rf .",
            "rm -rf ./",
            "rm -rf /.",
            "rm -rf '$HOME/'",
            f"rm -rf {ROOT}",
            f"rm -rf {ROOT.parent}",
        ]
        for command in commands:
            with self.subTest(command=command):
                output = self.codex_denial(command)
                reason = output["hookSpecificOutput"]["permissionDecisionReason"]
                self.assertEqual("broad recursive deletion is blocked", reason)
                self.assertNotIn(str(ROOT), reason)
                self.assertNotIn(str(Path.home()), reason)

    def test_rm_resolution_uses_valid_payload_cwd_and_falls_back_for_invalid_cwd(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            process_cwd = Path(temp_dir)
            valid = self.codex_denial(
                "rm -rf .", cwd=process_cwd, payload_cwd=str(ROOT)
            )
            invalid = self.codex_denial(
                "rm -rf .", cwd=ROOT, payload_cwd=str(process_cwd / "missing")
            )

        self.assertEqual(
            "broad recursive deletion is blocked",
            valid["hookSpecificOutput"]["permissionDecisionReason"],
        )
        self.assertEqual(
            "broad recursive deletion is blocked",
            invalid["hookSpecificOutput"]["permissionDecisionReason"],
        )

    def test_unqualified_force_push_uses_current_and_upstream_branch(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            fixture = Path(temp_dir)
            protected_repos = [fixture / branch for branch in ("main", "master", "trunk")]
            main_repo = protected_repos[0]
            feature_repo = fixture / "feature"
            for repo, branch in zip(protected_repos, ("main", "master", "trunk")):
                subprocess.run(
                    ["git", "init", "-q", "-b", branch, str(repo)], check=True
                )
            subprocess.run(
                ["git", "init", "-q", "-b", "feature", str(feature_repo)], check=True
            )

            for repo, branch in zip(protected_repos, ("main", "master", "trunk")):
                with self.subTest(branch=branch):
                    output = self.codex_denial("git push --force", payload_cwd=str(repo))
                    self.assertIn(
                        branch,
                        output["hookSpecificOutput"]["permissionDecisionReason"],
                    )
            output = self.codex_denial(
                "git push --force origin", payload_cwd=str(main_repo)
            )
            self.assertIn(
                "main", output["hookSpecificOutput"]["permissionDecisionReason"]
            )
            git_c_output = self.codex_denial(
                f"git -C {main_repo} push --force", cwd=fixture
            )
            self.assertIn(
                "main", git_c_output["hookSpecificOutput"]["permissionDecisionReason"]
            )

            allowed = [
                "git push --force",
                "git push --force origin",
                "git push --force origin feature",
                "git push --dry-run --force origin main",
            ]
            for command in allowed:
                with self.subTest(command=command):
                    result = self.run_policy(command, payload_cwd=str(feature_repo))
                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertEqual("", result.stdout)

            subprocess.run(
                [
                    "git",
                    "-C",
                    str(feature_repo),
                    "config",
                    "branch.feature.merge",
                    "refs/heads/main",
                ],
                check=True,
            )
            upstream = self.codex_denial(
                "git push --force", payload_cwd=str(feature_repo)
            )
            self.assertIn(
                "main", upstream["hookSpecificOutput"]["permissionDecisionReason"]
            )

            explicit_feature = self.run_policy(
                "git push --force origin feature", payload_cwd=str(main_repo)
            )
            self.assertEqual(0, explicit_feature.returncode, explicit_feature.stderr)
            self.assertEqual("", explicit_feature.stdout)
            protected_dry_run = self.run_policy(
                "git push --dry-run --force", payload_cwd=str(main_repo)
            )
            self.assertEqual(0, protected_dry_run.returncode, protected_dry_run.stderr)
            self.assertEqual("", protected_dry_run.stdout)

    def test_nested_shell_tracks_cd_before_force_push_and_fails_closed_when_unresolved(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            fixture = Path(temp_dir)
            main_repo = fixture / "main"
            feature_repo = fixture / "feature"
            subprocess.run(
                ["git", "init", "-q", "-b", "main", str(main_repo)], check=True
            )
            subprocess.run(
                ["git", "init", "-q", "-b", "feature", str(feature_repo)], check=True
            )

            main_output = self.codex_denial(
                f"bash -c 'cd {main_repo} && git push --force'", cwd=fixture
            )
            self.assertIn(
                "main", main_output["hookSpecificOutput"]["permissionDecisionReason"]
            )

            unresolved_output = self.codex_denial(
                "bash -c 'cd \"$MISSING_WORKTREE\" && git push --force'",
                cwd=fixture,
            )
            self.assertIn(
                "unresolved",
                unresolved_output["hookSpecificOutput"]["permissionDecisionReason"],
            )

            feature_result = self.run_policy(
                f"bash -c 'cd {feature_repo} && git push --force'", cwd=fixture
            )
            self.assertEqual(0, feature_result.returncode, feature_result.stderr)
            self.assertEqual("", feature_result.stdout)

    def test_git_c_effective_cwd_controls_broad_pathspecs(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo = Path(temp_dir) / "repo"
            subprocess.run(
                ["git", "init", "-q", "-b", "feature", str(repo)], check=True
            )
            nested = repo / "nested"
            (repo / "docs").mkdir()
            nested.mkdir()

            denied = [
                "git -C .. checkout -- '*'",
                "git -C .. checkout -- './*'",
                "git -C .. restore '*'",
                "git -C .. restore './*'",
            ]
            for command in denied:
                with self.subTest(command=command):
                    output = self.codex_denial(command, cwd=nested)
                    self.assertEqual(
                        "deny", output["hookSpecificOutput"]["permissionDecision"]
                    )

            allowed = [
                "git -C .. checkout -- 'docs/*'",
                "git -C .. restore 'docs/*'",
            ]
            for command in allowed:
                with self.subTest(command=command):
                    result = self.run_policy(command, cwd=nested)
                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertEqual("", result.stdout)

    def test_secret_glob_uses_directory_names_without_reading_contents(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            fixture = Path(temp_dir)
            build_dir = fixture / "build"
            build_dir.mkdir()
            (build_dir / "output.txt").touch()

            for command in (
                "cat {*,.*}",
                "grep . {*,.*}",
                "cat {*,.*}{,}",
                "grep . {*,.*}{,}",
                "cat {local,{keystore,other}}.{properties,txt}",
                "cat {k..l}ocal.properties",
                "cat build/{,}{,}{,}{,}{,}{,}{,}output.txt",
                "cat build/{*,.*",
            ):
                with self.subTest(command=command, protected="repository-root"):
                    output = self.codex_denial(command, cwd=fixture)
                    self.assertEqual(
                        "deny", output["hookSpecificOutput"]["permissionDecision"]
                    )

            for command in (
                "cat build/*",
                "grep . build/*",
                "cat build/{*,.*}",
                "grep . build/{*,.*}",
                "cat build/{*,.*}{,}",
                "grep . build/{*,.*}{,}",
                "cat build/{literal}",
                "cat build/output{1..3}.txt",
                "grep . build/output{3..1}.txt",
            ):
                with self.subTest(command=command, protected=False):
                    result = self.run_policy(command, cwd=fixture)
                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertEqual("", result.stdout)

            (build_dir / "local.properties").touch()
            for command in (
                "cat build/*",
                "grep . build/*",
                "cat build/{*,.*}",
                "grep . build/{*,.*}",
                "cat build/{*,.*}{,}",
                "grep . build/{*,.*}{,}",
                "cat build/local.propert{i..j}{e..f}{s..t}",
                "grep . build/local.propert{i..j}{e..f}{s..t}",
            ):
                with self.subTest(command=command, protected=True):
                    output = self.codex_denial(command, cwd=fixture)
                    self.assertEqual(
                        "deny", output["hookSpecificOutput"]["permissionDecision"]
                    )

    def test_sequence_brace_expansion_is_bounded_and_shell_relevant(self):
        brace_alternatives = runpy.run_path(
            str(POLICY), run_name="pre_tool_policy_test"
        )["brace_alternatives"]

        self.assertEqual(["a", "b", "c"], brace_alternatives("{a..c}"))
        self.assertEqual(["c", "b", "a"], brace_alternatives("{c..a}"))
        self.assertEqual(["1", "2", "3"], brace_alternatives("{1..3}"))
        self.assertEqual(["3", "2", "1"], brace_alternatives("{3..1}"))
        self.assertEqual(["1", "3", "5"], brace_alternatives("{1..5..2}"))
        self.assertEqual(["03", "02", "01"], brace_alternatives("{03..01}"))
        self.assertIn(
            "local.properties",
            brace_alternatives("local.propert{i..j}{e..f}{s..t}"),
        )
        self.assertIsNone(brace_alternatives("{1..65}"))
        self.assertIsNone(brace_alternatives("{1..3..0}"))
        self.assertIsNone(brace_alternatives("{a..3}"))
        self.assertEqual(["{literal}"], brace_alternatives("{literal}"))


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
