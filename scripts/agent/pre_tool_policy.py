#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import shlex
import sys
from typing import List, Optional, Sequence, Tuple


PROTECTED_BRANCHES = {"main", "master", "trunk"}
DATABASE_CLIENTS = {"mysql", "psql", "sqlite3"}
PRINTING_COMMANDS = {"cat", "head", "less", "more", "sed", "tail"}
GIT_GLOBAL_OPTIONS_WITH_VALUE = {
    "-C",
    "-c",
    "--config-env",
    "--exec-path",
    "--git-dir",
    "--namespace",
    "--super-prefix",
    "--work-tree",
}
ENV_OPTIONS_WITH_VALUE = {"-C", "-u", "--chdir", "--unset"}
ENV_FLAG_OPTIONS = {"-0", "-i", "-v", "--debug", "--ignore-environment", "--null"}
DROP_STATEMENT = re.compile(r"\bDROP\s+(?:TABLE|DATABASE|SCHEMA)\b", re.I)
ASSIGNMENT = re.compile(r"[A-Za-z_][A-Za-z0-9_]*=")
INVALID_INPUT_REASON = "invalid PreToolUse input is blocked"
MALFORMED_COMMAND_REASON = "malformed shell command is blocked"


def read_command() -> Tuple[Optional[str], Optional[str]]:
    raw = sys.stdin.read().strip()
    if not raw:
        raw = os.environ.get("CLAUDE_TOOL_INPUT", "").strip()
    if not raw:
        return None, INVALID_INPUT_REASON
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        return None, INVALID_INPUT_REASON
    if not isinstance(payload, dict):
        return None, INVALID_INPUT_REASON
    tool_input = payload.get("tool_input")
    if not isinstance(tool_input, dict):
        return None, INVALID_INPUT_REASON
    command = tool_input.get("command")
    if not isinstance(command, str):
        return None, INVALID_INPUT_REASON
    return command, None


def shell_pipelines(command: str) -> List[List[List[str]]]:
    lexer = shlex.shlex(command, posix=True, punctuation_chars=";&|\n")
    lexer.whitespace_split = True
    lexer.whitespace = lexer.whitespace.replace("\n", "")
    lexer.commenters = ""
    tokens = list(lexer)

    pipelines: List[List[List[str]]] = []
    pipeline: List[List[str]] = []
    segment: List[str] = []
    for token in tokens:
        if token in {"|", "|&"}:
            if segment:
                pipeline.append(segment)
            segment = []
        elif token and all(character in ";&|\n" for character in token):
            if segment:
                pipeline.append(segment)
            if pipeline:
                pipelines.append(pipeline)
            pipeline = []
            segment = []
        else:
            segment.append(token)
    if segment:
        pipeline.append(segment)
    if pipeline:
        pipelines.append(pipeline)
    return pipelines


def command_name(token: str) -> str:
    return token.replace("\\", "/").rsplit("/", 1)[-1]


def executable_words(segment: Sequence[str]) -> List[str]:
    index = 0
    while index < len(segment) and ASSIGNMENT.match(segment[index]):
        index += 1
    for _ in range(4):
        if index >= len(segment):
            break
        wrapper_index = index
        wrapper = command_name(segment[index])
        if wrapper == "command":
            index += 1
            while index < len(segment) and segment[index] == "-p":
                index += 1
            if index < len(segment) and segment[index] == "--":
                index += 1
            elif index < len(segment) and segment[index].startswith("-"):
                return list(segment[wrapper_index:])
            continue
        if wrapper != "env":
            break
        index += 1
        while index < len(segment):
            token = segment[index]
            option_name = token.split("=", 1)[0]
            if ASSIGNMENT.match(token):
                index += 1
            elif token == "--":
                index += 1
                break
            elif token in ENV_FLAG_OPTIONS:
                index += 1
            elif option_name in ENV_OPTIONS_WITH_VALUE:
                index += 1 if "=" in token else 2
            elif token.startswith("-"):
                return list(segment[wrapper_index:])
            else:
                break
    return list(segment[index:])


def executable_name(words: Sequence[str]) -> str:
    if not words:
        return ""
    return command_name(words[0])


def git_operation(words: Sequence[str]) -> Tuple[Optional[str], List[str]]:
    index = 1
    while index < len(words):
        token = words[index]
        option_name = token.split("=", 1)[0]
        if option_name in GIT_GLOBAL_OPTIONS_WITH_VALUE:
            index += 1 if "=" in token else 2
            continue
        if token == "--":
            index += 1
            break
        if token.startswith("-"):
            index += 1
            continue
        return token, list(words[index + 1 :])
    if index < len(words):
        return words[index], list(words[index + 1 :])
    return None, []


def short_option_has(token: str, option: str) -> bool:
    return token.startswith("-") and not token.startswith("--") and option in token[1:]


def protected_ref_destination(refspec: str) -> Optional[str]:
    candidate = refspec[1:] if refspec.startswith("+") else refspec
    if ":" in candidate:
        candidate = candidate.rsplit(":", 1)[1]
    if candidate.startswith("refs/heads/"):
        candidate = candidate[len("refs/heads/") :]
    return candidate if candidate in PROTECTED_BRANCHES else None


def git_denial(words: Sequence[str]) -> Optional[str]:
    operation, args = git_operation(words)
    if operation == "reset" and "--hard" in args:
        return "destructive git reset is blocked"
    if operation == "clean":
        if any(
            token == "--dry-run" or short_option_has(token, "n") for token in args
        ):
            return None
        forced = any(
            token == "--force" or short_option_has(token, "f") for token in args
        )
        if forced:
            return "destructive git clean is blocked"
    if operation != "push":
        return None
    if any(
        token == "--dry-run" or short_option_has(token, "n") for token in args
    ):
        return None

    force_option = any(
        token == "--force"
        or token.startswith("--force-with-lease")
        or short_option_has(token, "f")
        for token in args
    )
    lease_targets = [
        token.split("=", 1)[1]
        for token in args
        if token.startswith("--force-with-lease=")
    ]
    positional = [token for token in args if not token.startswith("-")]
    refspecs = positional[1:] if positional else []
    for refspec in refspecs:
        branch = protected_ref_destination(refspec)
        if branch and (force_option or refspec.startswith("+")):
            return f"force-push to {branch} is blocked"
    for target in lease_targets:
        branch = protected_ref_destination(target.split(":", 1)[0])
        if branch:
            return f"force-push to {branch} is blocked"
    return None


def rm_denial(words: Sequence[str]) -> Optional[str]:
    recursive = False
    forced = False
    targets = []
    options_done = False
    for token in words[1:]:
        if token == "--":
            options_done = True
        elif not options_done and token.startswith("-"):
            recursive = recursive or (
                token == "--recursive"
                or short_option_has(token, "r")
                or short_option_has(token, "R")
            )
            forced = forced or token == "--force" or short_option_has(token, "f")
        else:
            targets.append(token)
    broad_targets = {"/", "~", "$HOME", "${HOME}"}
    normalized_targets = [target.rstrip("/") or "/" for target in targets]
    if recursive and forced and any(
        target in broad_targets for target in normalized_targets
    ):
        return "broad recursive deletion is blocked"
    return None


def is_secret_path(token: str) -> bool:
    name = token.replace("\\", "/").rsplit("/", 1)[-1]
    return (
        name in {"local.properties", "keystore.properties", ".env"}
        or name.startswith(".env.")
    )


def secret_read_denial(words: Sequence[str]) -> Optional[str]:
    executable = executable_name(words)
    if executable not in PRINTING_COMMANDS:
        return None
    if executable == "sed" and any(
        token == "--in-place"
        or token.startswith("--in-place=")
        or (token.startswith("-i") and not token.startswith("--"))
        for token in words[1:]
    ):
        return None
    if any(is_secret_path(token) for token in words[1:]):
        return "printing local secret files is blocked"
    return None


def database_denial(pipeline: Sequence[Sequence[str]], index: int) -> Optional[str]:
    words = executable_words(pipeline[index])
    if executable_name(words) not in DATABASE_CLIENTS:
        return None
    sql_inputs = list(words[1:])
    for upstream in pipeline[:index]:
        sql_inputs.extend(upstream)
    if DROP_STATEMENT.search(" ".join(sql_inputs)):
        return "destructive database command is blocked"
    return None


def denial_reason(command: str) -> Optional[str]:
    try:
        pipelines = shell_pipelines(command)
    except ValueError:
        return MALFORMED_COMMAND_REASON
    for pipeline in pipelines:
        for index, segment in enumerate(pipeline):
            words = executable_words(segment)
            executable = executable_name(words)
            if executable == "git":
                reason = git_denial(words)
            elif executable == "rm":
                reason = rm_denial(words)
            else:
                reason = secret_read_denial(words)
            if reason:
                return reason
            reason = database_denial(pipeline, index)
            if reason:
                return reason
    return None


def emit_denial(reason: str) -> int:
    if os.environ.get("GASSTATION_HOOK_SURFACE") == "claude":
        print(reason, file=sys.stderr)
        return 2
    print(
        json.dumps(
            {
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": reason,
                }
            }
        )
    )
    return 0


def main() -> int:
    command, input_error = read_command()
    if input_error:
        return emit_denial(input_error)
    reason = denial_reason(command or "")
    if reason:
        return emit_denial(reason)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
