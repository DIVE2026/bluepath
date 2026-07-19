#!/usr/bin/env python3
"""Validate BluePath chat datasets, quiz schema, secrets, and split isolation."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any

ALLOWED_ROLES = {"system", "user", "assistant"}
SECRET_PATTERNS = [
    re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b", re.I),
    re.compile(r"\b(?:API|JWT|YOUTUBE|MODEL)_?(?:KEY|SECRET)\s*[=:]\s*[^\s]{12,}", re.I),
    re.compile(r"Bearer\s+[A-Za-z0-9._-]{20,}", re.I),
]
PLACEHOLDER_MARKERS = {"example", "replace", "dummy", "test", "redacted", "do-not-repeat"}


def normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def fingerprint_messages(messages: list[dict[str, Any]]) -> str:
    canonical = json.dumps(
        [
            {
                "role": item.get("role"),
                "content": normalize_text(str(item.get("content", ""))),
            }
            for item in messages
        ],
        ensure_ascii=False,
        sort_keys=True,
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def looks_like_placeholder(value: str) -> bool:
    lowered = value.lower()
    return any(marker in lowered for marker in PLACEHOLDER_MARKERS)


def validate_role_sequence(messages: list[dict[str, Any]], line_no: int, errors: list[str]) -> None:
    roles = [item.get("role") for item in messages]
    if any(role not in ALLOWED_ROLES for role in roles):
        errors.append(f"line {line_no}: unsupported role sequence {roles}")
        return
    if roles[0] != "system" or roles[-1] != "assistant":
        errors.append(f"line {line_no}: conversation must start with system and end with assistant")
    if "user" not in roles:
        errors.append(f"line {line_no}: at least one user turn is required")
    for index in range(1, len(roles)):
        if roles[index] == roles[index - 1]:
            errors.append(f"line {line_no}: adjacent messages cannot share role {roles[index]}")
            break


def validate_quiz_payload(payload: Any, line_no: int, errors: list[str]) -> None:
    if not isinstance(payload, dict):
        errors.append(f"line {line_no}: quiz output must be a JSON object")
        return
    questions = payload.get("questions")
    if not isinstance(questions, list) or not questions:
        errors.append(f"line {line_no}: quiz output requires a non-empty questions array")
        return

    for q_index, question in enumerate(questions, 1):
        prefix = f"line {line_no}, question {q_index}"
        if not isinstance(question, dict):
            errors.append(f"{prefix}: question must be an object")
            continue
        for field in ["topic", "question", "explanation"]:
            if not isinstance(question.get(field), str) or not question[field].strip():
                errors.append(f"{prefix}: non-empty {field} is required")
        options = question.get("options")
        if not isinstance(options, list) or len(options) != 4:
            errors.append(f"{prefix}: exactly four options required")
        elif any(not isinstance(option, str) or not option.strip() for option in options):
            errors.append(f"{prefix}: every option must be non-empty text")
        elif len({normalize_text(option) for option in options}) != 4:
            errors.append(f"{prefix}: options must be unique")

        answer_index = question.get("answerIndex")
        if isinstance(answer_index, bool) or not isinstance(answer_index, int) or answer_index not in range(4):
            errors.append(f"{prefix}: answerIndex must be an integer from 0 to 3")

        source_numbers = question.get("sourceNumbers")
        if not isinstance(source_numbers, list) or not source_numbers:
            errors.append(f"{prefix}: sourceNumbers must be a non-empty array")
        elif any(isinstance(value, bool) or not isinstance(value, int) or value < 1 for value in source_numbers):
            errors.append(f"{prefix}: sourceNumbers must contain positive integers")
        elif len(set(source_numbers)) != len(source_numbers):
            errors.append(f"{prefix}: sourceNumbers must not contain duplicates")


def validate_row(row: Any, line_no: int) -> tuple[list[str], str | None]:
    errors: list[str] = []
    if not isinstance(row, dict):
        return [f"line {line_no}: each JSONL row must be an object"], None

    messages = row.get("messages")
    if not isinstance(messages, list) or len(messages) < 3:
        return [f"line {line_no}: messages must contain system, user, and assistant turns"], None

    validate_role_sequence(messages, line_no, errors)
    for message_index, item in enumerate(messages, 1):
        if not isinstance(item, dict):
            errors.append(f"line {line_no}, message {message_index}: message must be an object")
            continue
        content = item.get("content")
        if not isinstance(content, str) or not content.strip():
            errors.append(f"line {line_no}, message {message_index}: non-empty content is required")

    assistant = messages[-1].get("content", "") if isinstance(messages[-1], dict) else ""
    if isinstance(assistant, str):
        stripped = assistant.strip()
        if stripped.startswith("{") or stripped.startswith("```"):
            cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", stripped, flags=re.I)
            try:
                payload = json.loads(cleaned)
                validate_quiz_payload(payload, line_no, errors)
            except json.JSONDecodeError as exc:
                errors.append(f"line {line_no}: assistant output looks like JSON but cannot be parsed ({exc})")

        for pattern in SECRET_PATTERNS:
            for match in pattern.findall(assistant):
                value = match if isinstance(match, str) else " ".join(match)
                if not looks_like_placeholder(value):
                    errors.append(f"line {line_no}: assistant output appears to contain a secret")
                    break

    return errors, fingerprint_messages(messages)


def validate(path: Path) -> tuple[int, list[str], dict[str, int]]:
    errors: list[str] = []
    count = 0
    fingerprints: dict[str, int] = {}
    for line_no, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip():
            continue
        count += 1
        try:
            row = json.loads(raw)
        except json.JSONDecodeError as exc:
            errors.append(f"line {line_no}: invalid JSON ({exc})")
            continue

        row_errors, fingerprint = validate_row(row, line_no)
        errors.extend(row_errors)
        if fingerprint:
            previous = fingerprints.get(fingerprint)
            if previous:
                errors.append(f"line {line_no}: duplicate of line {previous}")
            else:
                fingerprints[fingerprint] = line_no
    return count, errors, fingerprints


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="+", type=Path)
    parser.add_argument(
        "--allow-cross-file-duplicates",
        action="store_true",
        help="Skip duplicate detection between supplied dataset files.",
    )
    args = parser.parse_args()

    failed = False
    global_fingerprints: dict[str, tuple[Path, int]] = {}
    for path in args.paths:
        count, errors, fingerprints = validate(path)
        print(f"{path}: {count} examples, {len(errors)} errors")
        for error in errors:
            print(f"  - {error}")
        failed |= bool(errors)

        if not args.allow_cross_file_duplicates:
            for fingerprint, line_no in fingerprints.items():
                previous = global_fingerprints.get(fingerprint)
                if previous:
                    print(
                        f"  - line {line_no}: duplicate across files "
                        f"({previous[0]} line {previous[1]})"
                    )
                    failed = True
                else:
                    global_fingerprints[fingerprint] = (path, line_no)

    raise SystemExit(1 if failed else 0)


if __name__ == "__main__":
    main()
