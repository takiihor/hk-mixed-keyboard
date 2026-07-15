#!/usr/bin/env python3
"""Classify app-scoped device logs without hiding framework-only disk reads."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Classification:
    actionable: tuple[str, ...]
    framework_only: tuple[str, ...]


def classify(
    text: str,
    package: str,
    namespace: str,
    system_text: str = "",
    monkey_text: str = "",
) -> Classification:
    lines = text.splitlines()
    actionable: list[str] = []
    framework_only: list[str] = []
    index = 0
    while index < len(lines):
        line = lines[index]
        if "FATAL EXCEPTION" in line or f"ANR in {package}" in line:
            actionable.append(line)
            index += 1
            continue
        if "StrictMode policy violation" not in line:
            index += 1
            continue

        block = [line]
        index += 1
        while index < len(lines) and "StrictMode:" in lines[index]:
            block.append(lines[index])
            index += 1
        rendered = "\n".join(block)
        is_framework_disk_io = (
            ("DiskReadViolation" in rendered or "DiskWriteViolation" in rendered)
            and f"at {namespace}." not in rendered
        )
        if is_framework_disk_io:
            framework_only.append(rendered)
        else:
            actionable.append(rendered)
    for line in system_text.splitlines():
        if (
            f"ANR in {package}" in line
            or f"Process: {package}," in line
            or f"Cmdline: {package}" in line
        ):
            actionable.append(line)
    for line in monkey_text.splitlines():
        if f"// CRASH: {package}" in line or f"// NOT RESPONDING: {package}" in line:
            actionable.append(line)
    return Classification(tuple(actionable), tuple(framework_only))


def write_blocks(path: Path, blocks: tuple[str, ...]) -> None:
    content = "\n\n".join(blocks)
    path.write_text(content + ("\n" if content else ""), encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--package", required=True)
    parser.add_argument("--namespace", default="com.hkmixedkeyboard")
    parser.add_argument("--markers", type=Path, required=True)
    parser.add_argument("--framework-markers", type=Path, required=True)
    parser.add_argument("--system-log", type=Path)
    parser.add_argument("--monkey-log", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    system_text = (
        args.system_log.read_text(encoding="utf-8", errors="replace")
        if args.system_log else ""
    )
    monkey_text = (
        args.monkey_log.read_text(encoding="utf-8", errors="replace")
        if args.monkey_log else ""
    )
    result = classify(
        args.log.read_text(encoding="utf-8", errors="replace"),
        args.package,
        args.namespace,
        system_text,
        monkey_text,
    )
    write_blocks(args.markers, result.actionable)
    write_blocks(args.framework_markers, result.framework_only)
    if result.actionable:
        raise SystemExit(
            f"app crash, ANR or actionable StrictMode marker found; see {args.markers}"
        )
    print(
        "device log verified: no app crash, ANR or actionable StrictMode marker; "
        f"framework-only disk I/O blocks recorded={len(result.framework_only)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
