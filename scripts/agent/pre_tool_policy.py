#!/usr/bin/env python3
from __future__ import annotations

import json
import fnmatch
import os
import re
import shlex
import subprocess
import sys
from typing import List, Optional, Sequence, Tuple


PROTECTED_BRANCHES = {"main", "master", "trunk"}
DATABASE_CLIENTS = {"mysql", "psql", "sqlite3"}
PRINTING_COMMANDS = {"bat", "batcat", "cat", "cut", "head", "less", "more", "nl", "sed", "tail"}
SHELL_COMMANDS = {"bash", "sh", "zsh"}
MAX_NESTED_SHELL_DEPTH = 3
MAX_BRACE_ALTERNATIVES = 64
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
UNRESOLVED_CWD_REASON = "destructive command after unresolved cd is blocked"


def read_command() -> Tuple[Optional[str], str, Optional[str]]:
    process_cwd = os.path.realpath(os.getcwd())
    raw = sys.stdin.read().strip()
    if not raw:
        raw = os.environ.get("CLAUDE_TOOL_INPUT", "").strip()
    if not raw:
        return None, process_cwd, INVALID_INPUT_REASON
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        return None, process_cwd, INVALID_INPUT_REASON
    if not isinstance(payload, dict):
        return None, process_cwd, INVALID_INPUT_REASON
    payload_cwd = payload.get("cwd")
    command_cwd = (
        os.path.realpath(payload_cwd)
        if isinstance(payload_cwd, str) and os.path.isdir(payload_cwd)
        else process_cwd
    )
    # Claude/Codex send snake_case `tool_input`; Grok sends camelCase `toolInput`.
    if "tool_input" in payload:
        tool_input = payload.get("tool_input")
    else:
        tool_input = payload.get("toolInput")
    if not isinstance(tool_input, dict):
        return None, command_cwd, INVALID_INPUT_REASON
    command = tool_input.get("command")
    if not isinstance(command, str):
        return None, command_cwd, INVALID_INPUT_REASON
    return command, command_cwd, None


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


def git_command_cwd(words: Sequence[str], command_cwd: str) -> str:
    cwd = command_cwd
    index = 1
    while index < len(words):
        token = words[index]
        if token == "-C":
            if index + 1 >= len(words):
                return cwd
            candidate = words[index + 1]
            cwd = os.path.realpath(
                candidate if os.path.isabs(candidate) else os.path.join(cwd, candidate)
            )
            index += 2
            continue
        if token.startswith("-C") and len(token) > 2:
            candidate = token[2:]
            cwd = os.path.realpath(
                candidate if os.path.isabs(candidate) else os.path.join(cwd, candidate)
            )
            index += 1
            continue
        option_name = token.split("=", 1)[0]
        if option_name in GIT_GLOBAL_OPTIONS_WITH_VALUE:
            index += 1 if "=" in token else 2
            continue
        if token.startswith("-"):
            index += 1
            continue
        break
    return cwd


def protected_branch(branch: str, upstream: bool = False) -> Optional[str]:
    candidate = branch.strip()
    if candidate.startswith("refs/heads/"):
        candidate = candidate[len("refs/heads/") :]
    if candidate in PROTECTED_BRANCHES:
        return candidate
    if upstream:
        if candidate.startswith("refs/remotes/"):
            candidate = candidate[len("refs/remotes/") :]
        if candidate.count("/") == 1:
            remote_branch = candidate.split("/", 1)[1]
            if remote_branch in PROTECTED_BRANCHES:
                return remote_branch
    return None


def current_or_upstream_protected(command_cwd: str) -> Optional[str]:
    try:
        current_result = subprocess.run(
            ["git", "-C", command_cwd, "symbolic-ref", "--quiet", "--short", "HEAD"],
            text=True,
            capture_output=True,
            timeout=2,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    if current_result.returncode:
        return None
    current = current_result.stdout.strip()
    protected = protected_branch(current)
    if protected:
        return protected
    try:
        upstream_result = subprocess.run(
            ["git", "-C", command_cwd, "config", "--get", f"branch.{current}.merge"],
            text=True,
            capture_output=True,
            timeout=2,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    if upstream_result.returncode:
        return None
    return protected_branch(upstream_result.stdout.strip(), upstream=True)


def glob_prefix(token: str) -> Tuple[str, bool]:
    positions = [token.find(character) for character in "*?[" if character in token]
    if not positions:
        return token, False
    position = min(positions)
    return token[:position] or ".", True


def broad_restore_target(token: str, command_cwd: str) -> bool:
    if token == ":/" or os.path.normpath(token) == ".":
        return True
    repo_root = git_repo_root(command_cwd)
    if repo_root is None:
        return False

    pathspec = token
    base = command_cwd
    magic = re.match(r"^:\(([^)]*)\)(.*)$", pathspec)
    if magic:
        attributes, pathspec = magic.groups()
        if "top" in {attribute.strip() for attribute in attributes.split(",")}:
            base = repo_root
    elif pathspec.startswith(":/"):
        base = repo_root
        pathspec = pathspec[2:]

    prefix, has_glob = glob_prefix(pathspec)
    if not has_glob:
        return False
    resolved = os.path.realpath(
        prefix if os.path.isabs(prefix) else os.path.join(base, prefix)
    )
    return path_contains(resolved, repo_root)


def destructive_push_requested(words: Sequence[str]) -> bool:
    operation, args = git_operation(words)
    if operation != "push" or any(
        token == "--dry-run" or short_option_has(token, "n") for token in args
    ):
        return False
    return (
        "--mirror" in args
        or any(
            token == "--force"
            or token.startswith("--force-with-lease")
            or short_option_has(token, "f")
            for token in args
        )
        or any(token.startswith("+") for token in args)
    )


def git_denial(words: Sequence[str], command_cwd: str) -> Optional[str]:
    operation, args = git_operation(words)
    if operation == "reset" and "--hard" in args:
        return "destructive git reset is blocked"
    patch_mode = any(
        token == "--patch" or short_option_has(token, "p") for token in args
    )
    if not patch_mode and operation == "checkout":
        if any(
            broad_restore_target(token, command_cwd)
            for token in args
            if not token.startswith("-")
        ):
            return "broad git checkout restoration is blocked"
    if not patch_mode and operation == "restore":
        if any(
            broad_restore_target(token, command_cwd)
            for token in args
            if not token.startswith("-")
        ):
            return "broad git restore is blocked"
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
    if "--mirror" in args:
        return "mirror push is blocked"

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
    force_requested = force_option or any(refspec.startswith("+") for refspec in refspecs)
    wildcard_ref = any(glob_prefix(token)[1] for token in refspecs + lease_targets)
    if force_requested and ("--all" in args or wildcard_ref):
        return "broad force-push is blocked"
    for refspec in refspecs:
        branch = protected_ref_destination(refspec)
        if branch and (force_option or refspec.startswith("+")):
            return f"force-push to {branch} is blocked"
    for target in lease_targets:
        branch = protected_ref_destination(target.split(":", 1)[0])
        if branch:
            return f"force-push to {branch} is blocked"
    if not force_requested:
        return None
    has_explicit_destination = bool(refspecs) and all(
        refspec.lstrip("+") not in {"HEAD", "@"}
        for refspec in refspecs
    )
    if not has_explicit_destination:
        branch = current_or_upstream_protected(command_cwd)
        if branch:
            return f"force-push to {branch} is blocked"
    return None


def git_repo_root(command_cwd: str) -> Optional[str]:
    try:
        result = subprocess.run(
            ["git", "-C", command_cwd, "rev-parse", "--show-toplevel"],
            text=True,
            capture_output=True,
            timeout=2,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    if result.returncode:
        return None
    return os.path.realpath(result.stdout.strip())


def path_contains(candidate: str, nested: str) -> bool:
    try:
        return os.path.commonpath([candidate, nested]) == candidate
    except ValueError:
        return False


def rm_denial(words: Sequence[str], command_cwd: str) -> Optional[str]:
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
    if recursive and forced:
        protected_paths = {os.path.realpath("/"), os.path.realpath(os.path.expanduser("~"))}
        repo_root = git_repo_root(command_cwd)
        for target in targets:
            if "$(" in target or "`" in target:
                return "broad recursive deletion is blocked"
            expanded = os.path.expanduser(os.path.expandvars(target))
            expansion_positions = [
                expanded.find(character)
                for character in "*?[{$"
                if character in expanded
            ]
            prefix = (
                expanded[: min(expansion_positions)] or "."
                if expansion_positions
                else expanded
            )
            resolved = os.path.realpath(
                prefix
                if os.path.isabs(prefix)
                else os.path.join(command_cwd, prefix)
            )
            if resolved in protected_paths or (
                repo_root is not None and path_contains(resolved, repo_root)
            ):
                return "broad recursive deletion is blocked"
    return None


def protected_secret_name(name: str) -> bool:
    return name in {"local.properties", "keystore.properties", ".env"} or name.startswith(
        ".env."
    )


def sequence_alternatives(contents: str) -> Tuple[Optional[List[str]], bool]:
    numeric = re.fullmatch(r"(-?\d+)\.\.(-?\d+)(?:\.\.(-?\d+))?", contents)
    character = re.fullmatch(r"([A-Za-z])\.\.([A-Za-z])(?:\.\.(-?\d+))?", contents)
    if numeric:
        start_text, end_text, step_text = numeric.groups()
        try:
            start = int(start_text)
            end = int(end_text)
            step = abs(int(step_text)) if step_text is not None else 1
        except ValueError:
            return None, True
        padded = any(
            len(value.lstrip("-")) > 1 and value.lstrip("-").startswith("0")
            for value in (start_text, end_text)
        )
        width = max(len(start_text.lstrip("-")), len(end_text.lstrip("-")))
        formatter = lambda value: (
            ("-" if value < 0 else "") + f"{abs(value):0{width}d}"
            if padded
            else str(value)
        )
    elif character:
        start_text, end_text, step_text = character.groups()
        start = ord(start_text)
        end = ord(end_text)
        try:
            step = abs(int(step_text)) if step_text is not None else 1
        except ValueError:
            return None, True
        formatter = chr
    else:
        return (None, True) if ".." in contents else (None, False)

    if step == 0:
        return None, True
    count = abs(end - start) // step + 1
    if count > MAX_BRACE_ALTERNATIVES:
        return None, True
    direction = 1 if end >= start else -1
    return [
        formatter(value)
        for value in range(start, end + direction, direction * step)
    ], False


def brace_group(
    pattern: str,
) -> Tuple[Optional[Tuple[int, int, List[str]]], bool]:
    stack: List[int] = []
    candidate: Optional[Tuple[int, int, List[str]]] = None
    for index, character in enumerate(pattern):
        if character == "{":
            stack.append(index)
            continue
        if character != "}":
            continue
        if not stack:
            return None, "," in pattern or ".." in pattern
        start = stack.pop()
        contents = pattern[start + 1 : index]
        depth = 0
        alternatives = [""]
        for nested in contents:
            if nested == "{":
                depth += 1
            elif nested == "}":
                depth -= 1
            if nested == "," and depth == 0:
                alternatives.append("")
            else:
                alternatives[-1] += nested
        if len(alternatives) > 1 and candidate is None:
            candidate = (start, index, alternatives)
        elif candidate is None:
            sequence, invalid = sequence_alternatives(contents)
            if invalid:
                return None, True
            if sequence is not None:
                candidate = (start, index, sequence)
    malformed = any(
        "," in pattern[start:] or ".." in pattern[start:] for start in stack
    )
    return (None, True) if malformed else (candidate, False)


def brace_alternatives(pattern: str) -> Optional[List[str]]:
    alternatives = [pattern]
    index = 0
    while index < len(alternatives):
        candidate, malformed = brace_group(alternatives[index])
        if malformed:
            return None
        if candidate is None:
            index += 1
            continue
        start, end, replacements = candidate
        if len(alternatives) - 1 + len(replacements) > MAX_BRACE_ALTERNATIVES:
            return None
        current = alternatives[index]
        alternatives[index : index + 1] = [
            current[:start] + replacement + current[end + 1 :]
            for replacement in replacements
        ]
    return alternatives


def is_secret_path(token: str, command_cwd: str) -> bool:
    normalized = token.replace("\\", "/")
    directory, separator, pattern = normalized.rpartition("/")
    if not separator:
        directory = "."
        pattern = normalized
    if protected_secret_name(pattern):
        return True

    protected_samples = {
        "local.properties",
        "keystore.properties",
        ".env",
        ".env.local",
    }
    patterns = brace_alternatives(pattern)
    if patterns is None:
        return True
    if not any(
        fnmatch.fnmatchcase(name, candidate)
        for name in protected_samples
        for candidate in patterns
    ):
        return False
    if directory in {"", "."}:
        return True
    if glob_prefix(directory)[1] or "{" in directory or "$" in directory:
        return True

    expanded_directory = os.path.expanduser(os.path.expandvars(directory))
    resolved_directory = os.path.realpath(
        expanded_directory
        if os.path.isabs(expanded_directory)
        else os.path.join(command_cwd, expanded_directory)
    )
    try:
        names = os.listdir(resolved_directory)
    except OSError:
        return False
    return any(
        protected_secret_name(name)
        and any(fnmatch.fnmatchcase(name, candidate) for candidate in patterns)
        for name in names
    )


def grep_non_value_mode(words: Sequence[str]) -> bool:
    long_options = {
        "--quiet",
        "--silent",
        "--files-with-matches",
        "--files-without-match",
        "--count",
    }
    for token in words[1:]:
        if token == "--":
            break
        if token in long_options:
            return True
        if token.startswith("--"):
            continue
        if not token.startswith("-") or token == "-":
            break
        for option in token[1:]:
            if option in {"e", "f"}:
                break
            if option in {"q", "l", "L", "c"}:
                return True
    return False


def cut_file_operands(words: Sequence[str]) -> List[str]:
    files: List[str] = []
    value_options = {"-b", "--bytes", "-c", "--characters", "-d", "--delimiter", "-f", "--fields", "--output-delimiter"}
    options_done = False
    index = 1
    while index < len(words):
        token = words[index]
        if not options_done and token == "--":
            options_done = True
            index += 1
            continue
        if not options_done and token.startswith("-") and token != "-":
            option_name = token.split("=", 1)[0]
            if option_name in value_options:
                index += 1 if "=" in token else 2
                continue
            if token.startswith(("-b", "-c", "-d", "-f")) and len(token) > 2:
                index += 1
                continue
            index += 1
            continue
        files.append(token)
        index += 1
    return files


def grep_file_operands(words: Sequence[str]) -> List[str]:
    files: List[str] = []
    pattern_supplied = False
    options_done = False
    index = 1
    value_options = {
        "-A", "--after-context", "-B", "--before-context", "-C", "--context",
        "-m", "--max-count", "--binary-files", "--devices", "--directories",
        "--exclude", "--exclude-from", "--include", "--label",
    }
    while index < len(words):
        token = words[index]
        if not options_done and token == "--":
            options_done = True
            index += 1
            continue
        if not options_done and token.startswith("-") and token != "-":
            option_name = token.split("=", 1)[0]
            if option_name in {"-e", "--regexp", "-f", "--file"}:
                pattern_supplied = True
                index += 1 if "=" in token else 2
                continue
            if token.startswith(("-e", "-f")) and len(token) > 2:
                pattern_supplied = True
                index += 1
                continue
            if not token.startswith("--"):
                pattern_option = next(
                    (position for position in (token.find("e", 1), token.find("f", 1)) if position >= 1),
                    None,
                )
                if pattern_option is not None:
                    pattern_supplied = True
                    index += 1 if token[pattern_option + 1 :] else 2
                    continue
            if option_name in value_options:
                index += 1 if "=" in token else 2
                continue
            index += 1
            continue
        if not pattern_supplied:
            pattern_supplied = True
        else:
            files.append(token)
        index += 1
    return files


def awk_file_operands(words: Sequence[str]) -> List[str]:
    files: List[str] = []
    program_supplied = False
    options_done = False
    index = 1
    value_options = {
        "-F", "--field-separator", "-v", "--assign", "-f", "--file",
    }
    while index < len(words):
        token = words[index]
        if not options_done and token == "--":
            options_done = True
            index += 1
            continue
        if not options_done and token.startswith("-") and token != "-":
            option_name = token.split("=", 1)[0]
            if option_name in value_options:
                if option_name in {"-f", "--file"}:
                    program_supplied = True
                index += 1 if "=" in token else 2
                continue
            if token.startswith(("-F", "-v", "-f")) and len(token) > 2:
                if token.startswith("-f"):
                    program_supplied = True
                index += 1
                continue
            index += 1
            continue
        if not program_supplied:
            program_supplied = True
        elif not ASSIGNMENT.match(token):
            files.append(token)
        index += 1
    return files


def secret_read_denial(words: Sequence[str], command_cwd: str) -> Optional[str]:
    executable = executable_name(words)
    if executable == "grep":
        file_operands = grep_file_operands(words)
        if grep_non_value_mode(words):
            return None
    elif executable == "awk":
        file_operands = awk_file_operands(words)
    elif executable == "cut":
        file_operands = cut_file_operands(words)
    elif executable in PRINTING_COMMANDS:
        file_operands = list(words[1:])
    else:
        return None
    if executable == "sed" and any(
        token == "--in-place"
        or token.startswith("--in-place=")
        or (token.startswith("-i") and not token.startswith("--"))
        for token in words[1:]
    ):
        return None
    if any(is_secret_path(token, command_cwd) for token in file_operands):
        return "printing local secret files is blocked"
    return None


def nested_shell_command(words: Sequence[str]) -> Tuple[Optional[str], Optional[str]]:
    if executable_name(words) not in SHELL_COMMANDS:
        return None, None
    index = 1
    options_with_value = {"-o", "+o", "-O", "+O", "--rcfile", "--init-file"}
    while index < len(words):
        token = words[index]
        if token == "--":
            return None, None
        if token in options_with_value:
            index += 2
            continue
        if token.startswith("-") or token.startswith("+"):
            if "c" in token[1:]:
                if index + 1 >= len(words):
                    return None, MALFORMED_COMMAND_REASON
                return words[index + 1], None
            index += 1
            continue
        return None, None
    return None, None


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


def resolved_cd(words: Sequence[str], command_cwd: str) -> Optional[str]:
    arguments = [token for token in words[1:] if token not in {"--", "-L", "-P"}]
    if not arguments:
        target = os.path.expanduser("~")
    elif len(arguments) == 1:
        target = arguments[0]
    else:
        return None
    if target == "-" or "$(" in target or "`" in target or glob_prefix(target)[1]:
        return None
    expanded = os.path.expanduser(os.path.expandvars(target))
    if "$" in expanded:
        return None
    resolved = os.path.realpath(
        expanded if os.path.isabs(expanded) else os.path.join(command_cwd, expanded)
    )
    return resolved if os.path.isdir(resolved) else None


def denial_reason(command: str, command_cwd: str, depth: int = 0) -> Optional[str]:
    try:
        pipelines = shell_pipelines(command)
    except ValueError:
        return MALFORMED_COMMAND_REASON
    active_cwd = command_cwd
    cwd_unresolved = False
    for pipeline in pipelines:
        for index, segment in enumerate(pipeline):
            words = executable_words(segment)
            executable = executable_name(words)
            nested_command, nested_error = nested_shell_command(words)
            if nested_error:
                return nested_error
            if nested_command is not None:
                if depth >= MAX_NESTED_SHELL_DEPTH:
                    return MALFORMED_COMMAND_REASON
                nested_reason = denial_reason(nested_command, active_cwd, depth + 1)
                if nested_reason:
                    return nested_reason
            if executable == "cd" and len(pipeline) == 1:
                next_cwd = resolved_cd(words, active_cwd)
                if next_cwd is None:
                    cwd_unresolved = True
                else:
                    active_cwd = next_cwd
                    cwd_unresolved = False
                continue
            if executable == "git":
                if cwd_unresolved and destructive_push_requested(words):
                    return UNRESOLVED_CWD_REASON
                effective_git_cwd = git_command_cwd(words, active_cwd)
                reason = git_denial(words, effective_git_cwd)
            elif executable == "rm":
                reason = rm_denial(words, active_cwd)
            else:
                reason = secret_read_denial(words, active_cwd)
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
    command, command_cwd, input_error = read_command()
    if input_error:
        return emit_denial(input_error)
    reason = denial_reason(command or "", command_cwd)
    if reason:
        return emit_denial(reason)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
