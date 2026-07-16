#!/usr/bin/env python3
"""Score candidate-ranking results for the locked three-mode benchmark."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
from typing import Any

from verify_benchmark_templates import (
    COMPARATIVE_CLASSIFICATIONS,
    validate_comparative_evidence,
)


def read_tsv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def read_evidence(path: Path) -> dict[str, Any]:
    try:
        evidence = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"cannot read evidence: {path}") from error
    if not isinstance(evidence, dict):
        raise ValueError("evidence must be a JSON object")
    return evidence


def evidence_summary(evidence: dict[str, Any] | None, case_count: int) -> dict[str, Any]:
    if evidence is None:
        classification = "OPEN"
    else:
        classification = validate_comparative_evidence(evidence, case_count)
    return {
        "classification": classification,
        "independent": classification in {
            "NATIVE",
            "COMPETITOR_COMPARATIVE",
        },
    }


def require_unique_case_ids(rows: list[dict[str, str]], label: str) -> None:
    seen: set[str] = set()
    for row in rows:
        case_id = row.get("case_id", "")
        if not case_id:
            raise ValueError(f"{label} contains an empty case_id")
        if case_id in seen:
            raise ValueError(f"{label} contains duplicate case_id: {case_id}")
        seen.add(case_id)


def validate_case_schema(cases: list[dict[str, str]]) -> None:
    for case in cases:
        try:
            acceptable = json.loads(case.get("acceptable_outputs", ""))
        except json.JSONDecodeError as error:
            raise ValueError(
                f"{case['case_id']} acceptable_outputs must be a non-empty JSON string list"
            ) from error
        if not acceptable or not all(
            isinstance(value, str) and value for value in acceptable
        ):
            raise ValueError(
                f"{case['case_id']} acceptable_outputs must be a non-empty JSON string list"
            )


def validate_locked_holdout(cases: list[dict[str, str]], minimum_cases: int) -> None:
    if len(cases) < minimum_cases:
        raise ValueError(
            f"locked holdout requires at least {minimum_cases} cases; found {len(cases)}"
        )
    for case in cases:
        if case.get("split") != "locked_holdout":
            raise ValueError(f"{case['case_id']} is not labelled locked_holdout")
        if case.get("provenance") == "production_corpus":
            raise ValueError(
                f"{case['case_id']} uses forbidden production_corpus provenance"
            )


def validate_result_coverage(
    cases: list[dict[str, str]], results: list[dict[str, str]]
) -> None:
    result_ids = {row["case_id"] for row in results}
    for case in cases:
        if case["case_id"] not in result_ids:
            raise ValueError(f"missing result for case_id: {case['case_id']}")


def best_rank(candidates: list[str], acceptable: list[str]) -> int | None:
    ranks = [candidates.index(value) + 1 for value in acceptable if value in candidates]
    return min(ranks) if ranks else None


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    value = ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)
    return round(value, 3)


def ranking_summary(
    cases: list[dict[str, str]], results_by_id: dict[str, dict[str, str]]
) -> dict[str, Any]:
    ranks = []
    for case in cases:
        acceptable = json.loads(case["acceptable_outputs"])
        candidates = json.loads(results_by_id[case["case_id"]]["candidates"])
        ranks.append(best_rank(candidates, acceptable))
    total = len(cases)

    def rate(limit: int) -> float:
        return sum(rank is not None and rank <= limit for rank in ranks) / total

    return {
        "total": total,
        "top1_rate": rate(1),
        "top3_rate": rate(3),
        "top5_rate": rate(5),
        "mean_reciprocal_rank": sum(1 / rank for rank in ranks if rank) / total,
        "oov_rate": sum(rank is None for rank in ranks) / total,
    }


def score(cases: list[dict[str, str]], results: list[dict[str, str]]) -> dict[str, Any]:
    results_by_id = {row["case_id"]: row for row in results}
    correct_commits = 0
    correct_characters = 0
    correct_keystrokes = 0
    corrections = 0
    wrong_auto_commits = 0
    elapsed_ms: list[float] = []

    for case in cases:
        result = results_by_id[case["case_id"]]
        acceptable = json.loads(case["acceptable_outputs"])
        committed_text = result["committed_text"]
        if committed_text in acceptable:
            correct_commits += 1
            correct_characters += len(committed_text)
            correct_keystrokes += int(result["keystrokes"])
        corrections += int(result["corrections"])
        wrong_auto_commits += int(result["wrong_auto_commits"])
        elapsed_ms.append(float(result["elapsed_ms"]))

    total = len(cases)
    report = {
        **ranking_summary(cases, results_by_id),
        "committed_accuracy": correct_commits / total,
        "keystrokes_per_correct_character": (
            correct_keystrokes / correct_characters if correct_characters else None
        ),
        "corrections_per_case": corrections / total,
        "wrong_auto_commit_rate": wrong_auto_commits / total,
        "latency_ms": {
            "p50": percentile(elapsed_ms, 0.50),
            "p95": percentile(elapsed_ms, 0.95),
            "p99": percentile(elapsed_ms, 0.99),
        },
    }
    breakdowns = {}
    for field in ("mode", "category", "frequency_band", "phrase_length", "hkscs_status"):
        groups = {}
        values = sorted({case.get(field, "unspecified") or "unspecified" for case in cases})
        for value in values:
            group_cases = [
                case for case in cases if (case.get(field, "unspecified") or "unspecified") == value
            ]
            groups[value] = ranking_summary(group_cases, results_by_id)
        breakdowns[field] = groups
    report["breakdowns"] = breakdowns
    return report


def render_markdown(report: dict[str, Any]) -> str:
    latency = report["latency_ms"]
    kpc = report["keystrokes_per_correct_character"]
    kpc_text = "n/a" if kpc is None else f"{kpc:.3f}"
    evidence = report["evidence"]
    evidence_label = evidence["classification"]
    if not evidence["independent"]:
        evidence_label += " (non-independent; not market-comparison evidence)"
    return "\n".join(
        (
            "# Three-Mode Benchmark Report",
            "",
            f"Cases scored: {report['total']}",
            f"Evidence: {evidence_label}",
            "",
            "| Metric | Result |",
            "|---|---:|",
            f"| Top-1 | {report['top1_rate']:.2%} |",
            f"| Top-3 | {report['top3_rate']:.2%} |",
            f"| Top-5 | {report['top5_rate']:.2%} |",
            f"| Mean reciprocal rank | {report['mean_reciprocal_rank']:.4f} |",
            f"| OOV rate | {report['oov_rate']:.2%} |",
            f"| Committed accuracy | {report['committed_accuracy']:.2%} |",
            f"| Keystrokes/correct character | {kpc_text} |",
            f"| Corrections/case | {report['corrections_per_case']:.3f} |",
            f"| Wrong automatic commit rate | {report['wrong_auto_commit_rate']:.2%} |",
            f"| Latency p50 | {latency['p50']:.3f} ms |",
            f"| Latency p95 | {latency['p95']:.3f} ms |",
            f"| Latency p99 | {latency['p99']:.3f} ms |",
            "",
        )
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--results", type=Path, required=True)
    parser.add_argument("--json-output", type=Path, required=True)
    parser.add_argument("--markdown-output", type=Path)
    parser.add_argument("--mode", choices=("quick", "jyutping", "pinyin", "mixed"))
    parser.add_argument("--locked-holdout", action="store_true")
    parser.add_argument("--minimum-cases", type=int, default=1000)
    parser.add_argument("--evidence", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    cases = read_tsv(args.cases)
    results = read_tsv(args.results)
    try:
        require_unique_case_ids(cases, "cases")
        require_unique_case_ids(results, "results")
        validate_case_schema(cases)
    except ValueError as error:
        raise SystemExit(f"error: {error}") from error
    if args.mode:
        cases = [row for row in cases if row["mode"] == args.mode]
    if args.locked_holdout:
        try:
            validate_locked_holdout(cases, args.minimum_cases)
        except ValueError as error:
            raise SystemExit(f"error: {error}") from error
    try:
        validate_result_coverage(cases, results)
        evidence = read_evidence(args.evidence) if args.evidence else None
        report_evidence = evidence_summary(evidence, len(cases))
        if (
            report_evidence["classification"].lower() in COMPARATIVE_CLASSIFICATIONS
            and not args.locked_holdout
        ):
            raise ValueError("comparative reports require --locked-holdout")
    except ValueError as error:
        raise SystemExit(f"error: {error}") from error
    report = score(cases, results)
    report["evidence"] = report_evidence
    args.json_output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    if args.markdown_output:
        args.markdown_output.write_text(render_markdown(report), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
