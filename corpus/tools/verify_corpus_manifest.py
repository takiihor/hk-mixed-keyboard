#!/usr/bin/env python3
"""Verify integrity, coverage, and provenance metadata for shipped corpora."""

from __future__ import annotations

import argparse
import csv
import hashlib
import importlib.util
import json
import sys
import tempfile
from pathlib import Path
from typing import Any


DEFAULT_MANIFEST = Path("corpus/sources/corpus_manifest.json")
SHIPPED_ASSET_DIRS = (
    Path("android/app/src/main/assets/corpus"),
    Path("android/app/src/main/assets/t2s"),
)
VALID_FORMATS = {"csv": ",", "tsv": "\t"}
VALID_PROVENANCE = {
    "reproducible",
    "reviewed-curated",
    "legacy-snapshot-only",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def data_row_count(path: Path, file_format: str, has_header: bool) -> int:
    delimiter = VALID_FORMATS[file_format]
    with path.open("r", encoding="utf-8", newline="") as handle:
        content_lines = (
            line
            for line in handle
            if line.strip() and not line.lstrip().startswith("#")
        )
        rows = sum(1 for row in csv.reader(content_lines, delimiter=delimiter) if row)
    if has_header:
        if rows == 0:
            raise ValueError("declares a header but contains no rows")
        rows -= 1
    return rows


def _is_shipped_asset(path: Path) -> bool:
    return any(path == root or root in path.parents for root in SHIPPED_ASSET_DIRS)


def _check_provenance(
    repo_root: Path,
    asset_path: str,
    provenance: Any,
) -> list[str]:
    errors: list[str] = []
    if not isinstance(provenance, dict):
        return [f"{asset_path}: provenance must be an object"]

    status = provenance.get("status")
    if status not in VALID_PROVENANCE:
        errors.append(f"{asset_path}: invalid provenance status {status!r}")
    licenses = provenance.get("licenses")
    if not isinstance(licenses, list) or not licenses or not all(
        isinstance(item, str) and item for item in licenses
    ):
        errors.append(f"{asset_path}: provenance licenses must be non-empty strings")

    if status in {"reviewed-curated", "legacy-snapshot-only"} and not provenance.get(
        "note"
    ):
        errors.append(f"{asset_path}: {status} provenance requires a note")

    if status == "reproducible":
        generator = provenance.get("generator")
        if not isinstance(generator, str) or not generator:
            errors.append(f"{asset_path}: reproducible provenance requires a generator")
        elif not (repo_root / generator).is_file():
            errors.append(f"{asset_path}: generator does not exist: {generator}")

        sources = provenance.get("sources")
        if sources is None:
            sources = [provenance.get("source")]
        if not isinstance(sources, list) or not sources or not all(isinstance(source, dict) for source in sources):
            errors.append(f"{asset_path}: reproducible provenance requires source objects")
            sources = []
        for source in sources:
            source_path = source.get("path")
            source_hash = source.get("sha256")
            if not all(source.get(field) for field in ("url", "date")):
                errors.append(f"{asset_path}: reproducible source requires url and date")
            if not isinstance(source_path, str) or not source_path:
                errors.append(f"{asset_path}: reproducible source requires a path")
            elif not (repo_root / source_path).is_file():
                errors.append(f"{asset_path}: source does not exist: {source_path}")
            elif not isinstance(source_hash, str) or sha256(repo_root / source_path) != source_hash:
                errors.append(f"{asset_path}: source SHA-256 mismatch: {source_path}")
        inputs = provenance.get("inputs", [])
        if not isinstance(inputs, list) or not all(isinstance(item, dict) for item in inputs):
            errors.append(f"{asset_path}: reproducible inputs must be objects")
        else:
            for item in inputs:
                input_path = item.get("path")
                input_hash = item.get("sha256")
                if not isinstance(input_path, str) or not (repo_root / input_path).is_file():
                    errors.append(f"{asset_path}: input does not exist: {input_path}")
                elif sha256(repo_root / input_path) != input_hash:
                    errors.append(f"{asset_path}: input SHA-256 mismatch: {input_path}")
    return errors


def _check_hkscs_supplement_reproducibility(
    repo_root: Path,
    asset_path: str,
    provenance: Any,
) -> list[str]:
    """Regenerate the HKSCS overlay from its declared pinned inputs."""
    if not isinstance(provenance, dict):
        return [f"{asset_path}: HKSCS supplement requires provenance"]
    source = provenance.get("source")
    if not isinstance(source, dict):
        return [f"{asset_path}: HKSCS supplement requires a source object"]

    source_path = source.get("path")
    source_hash = source.get("sha256")
    if not isinstance(source_path, str) or not isinstance(source_hash, str):
        return [f"{asset_path}: HKSCS supplement source path and SHA-256 are required"]

    inputs = provenance.get("inputs")
    if not isinstance(inputs, list):
        return [f"{asset_path}: HKSCS supplement requires declared Quick and Jyutping inputs"]
    input_paths = {
        item.get("path"): repo_root / item["path"]
        for item in inputs
        if isinstance(item, dict) and isinstance(item.get("path"), str)
    }
    quick_path = input_paths.get("android/app/src/main/assets/corpus/hk_core_chars.csv")
    jyutping_path = input_paths.get("android/app/src/main/assets/corpus/jyutping.csv")
    if quick_path is None or jyutping_path is None:
        return [f"{asset_path}: HKSCS supplement requires Quick and Jyutping inputs"]

    module_path = repo_root / "corpus/tools/hkscs_coverage.py"
    spec = importlib.util.spec_from_file_location("hkscs_coverage_for_manifest", module_path)
    if spec is None or spec.loader is None:
        return [f"{asset_path}: cannot load HKSCS supplement generator"]
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    try:
        spec.loader.exec_module(module)
        han = module.load_han(repo_root / source_path, source_hash)
        quick = module._single_char_texts(quick_path, text_col=0)
        jyutping = module._single_char_texts(jyutping_path, text_col=1)
        with tempfile.TemporaryDirectory() as directory:
            generated = Path(directory) / "hkscs_supplement.csv"
            module.emit_supplement(han, quick, jyutping, generated)
            if generated.read_bytes() != (repo_root / asset_path).read_bytes():
                return [f"{asset_path}: HKSCS supplement does not reproduce byte-for-byte"]
    except (OSError, ValueError, AttributeError, ImportError) as exc:
        return [f"{asset_path}: cannot regenerate HKSCS supplement: {exc}"]
    finally:
        sys.modules.pop(spec.name, None)
    return []


def _reproducible_sources_by_role(provenance: Any, roles: set[str]) -> dict[str, dict[str, Any]]:
    if not isinstance(provenance, dict):
        return {}
    sources = provenance.get("sources")
    if sources is None:
        sources = [provenance.get("source")]
    if not isinstance(sources, list):
        return {}
    found = {
        source.get("role"): source
        for source in sources
        if isinstance(source, dict) and source.get("role") in roles
    }
    return found if set(found) == roles else {}


def _reproducible_inputs_by_path(provenance: Any) -> dict[str, dict[str, Any]]:
    if not isinstance(provenance, dict) or not isinstance(provenance.get("inputs"), list):
        return {}
    return {
        item.get("path"): item
        for item in provenance["inputs"]
        if isinstance(item, dict) and isinstance(item.get("path"), str)
    }


def _check_english_assist_reproducibility(
    repo_root: Path,
    asset_path: str,
    provenance: Any,
) -> list[str]:
    """Regenerate English assist from its declared CC-CEDICT, vocabulary and rank inputs."""
    roles = {"cedict", "frequency-vocabulary", "ranking-frequency"}
    sources = _reproducible_sources_by_role(provenance, roles)
    inputs = _reproducible_inputs_by_path(provenance)
    curated_path = "corpus/internal_stage1/english_assist.csv"
    if not sources or curated_path not in inputs:
        return [f"{asset_path}: English assist requires declared role-tagged sources and curated input"]

    module_path = repo_root / "corpus/tools/build_english_assist_corpus.py"
    spec = importlib.util.spec_from_file_location("english_assist_for_manifest", module_path)
    if spec is None or spec.loader is None:
        return [f"{asset_path}: cannot load English assist generator"]
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    try:
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            generated = Path(directory) / "english_assist.csv"
            module.build_corpus(
                cedict_path=repo_root / sources["cedict"]["path"],
                cedict_sha256=sources["cedict"]["sha256"],
                frequency_words_path=repo_root / sources["frequency-vocabulary"]["path"],
                frequency_words_sha256=sources["frequency-vocabulary"]["sha256"],
                essay_source=repo_root / sources["ranking-frequency"]["path"],
                essay_sha256=sources["ranking-frequency"]["sha256"],
                curated_path=repo_root / curated_path,
                curated_sha256=inputs[curated_path]["sha256"],
                output_path=generated,
                source_url=sources["cedict"]["url"],
                source_date=sources["cedict"]["date"],
            )
            if generated.read_bytes() != (repo_root / asset_path).read_bytes():
                return [f"{asset_path}: English assist does not reproduce byte-for-byte"]
    except (OSError, ValueError, AttributeError, ImportError, KeyError, TypeError) as exc:
        return [f"{asset_path}: cannot regenerate English assist: {exc}"]
    finally:
        sys.modules.pop(spec.name, None)
    return []


def _check_t2s_reproducibility(
    repo_root: Path,
    asset_path: str,
    provenance: Any,
) -> list[str]:
    """Regenerate the OpenCC T2S map from its one pinned source archive."""
    sources = _reproducible_sources_by_role(provenance, {"opencc"})
    if not sources:
        return [f"{asset_path}: T2S map requires a declared OpenCC source"]
    source = sources["opencc"]
    module_path = repo_root / "corpus/tools/build_t2s_map.py"
    spec = importlib.util.spec_from_file_location("t2s_for_manifest", module_path)
    if spec is None or spec.loader is None:
        return [f"{asset_path}: cannot load T2S generator"]
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    try:
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            generated = Path(directory) / "t2s_map.tsv"
            module.build_map(
                repo_root / source["path"],
                source["sha256"],
                generated,
            )
            if generated.read_bytes() != (repo_root / asset_path).read_bytes():
                return [f"{asset_path}: T2S map does not reproduce byte-for-byte"]
    except (OSError, ValueError, AttributeError, ImportError, KeyError, TypeError) as exc:
        return [f"{asset_path}: cannot regenerate T2S map: {exc}"]
    finally:
        sys.modules.pop(spec.name, None)
    return []


def verify_manifest(repo_root: Path, manifest_path: Path) -> list[str]:
    errors: list[str] = []
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return [f"cannot read manifest {manifest_path}: {exc}"]

    if manifest.get("schema_version") != 1:
        errors.append("manifest schema_version must be 1")
    if not isinstance(manifest.get("corpus_content_version"), int):
        errors.append("manifest corpus_content_version must be an integer")

    assets = manifest.get("assets")
    if not isinstance(assets, list):
        return errors + ["manifest assets must be a list"]

    expected_paths = {
        path.relative_to(repo_root).as_posix()
        for asset_dir in SHIPPED_ASSET_DIRS
        for path in (repo_root / asset_dir).rglob("*")
        if path.is_file()
    }
    declared_paths: set[str] = set()

    for asset in assets:
        if not isinstance(asset, dict):
            errors.append("asset entries must be objects")
            continue
        asset_path = asset.get("path")
        if not isinstance(asset_path, str) or not asset_path:
            errors.append("asset path must be a non-empty string")
            continue
        relative_path = Path(asset_path)
        if relative_path.is_absolute() or ".." in relative_path.parts:
            errors.append(f"{asset_path}: path must be repository-relative")
            continue
        if not _is_shipped_asset(relative_path):
            errors.append(f"{asset_path}: path is outside the shipped asset directories")
        if asset_path in declared_paths:
            errors.append(f"{asset_path}: duplicate manifest entry")
            continue
        declared_paths.add(asset_path)

        file_format = asset.get("format")
        if file_format not in VALID_FORMATS:
            errors.append(f"{asset_path}: format must be csv or tsv")
            continue
        has_header = asset.get("header")
        if not isinstance(has_header, bool):
            errors.append(f"{asset_path}: header must be boolean")
            continue

        disk_path = repo_root / relative_path
        if not disk_path.is_file():
            errors.append(f"{asset_path}: file does not exist")
            continue
        actual_bytes = disk_path.stat().st_size
        if asset.get("bytes") != actual_bytes:
            errors.append(
                f"{asset_path}: byte size mismatch: expected {asset.get('bytes')}, got {actual_bytes}"
            )
        actual_hash = sha256(disk_path)
        if asset.get("sha256") != actual_hash:
            errors.append(f"{asset_path}: SHA-256 mismatch")
        try:
            actual_rows = data_row_count(disk_path, file_format, has_header)
        except (OSError, UnicodeError, csv.Error, ValueError) as exc:
            errors.append(f"{asset_path}: cannot count rows: {exc}")
        else:
            if asset.get("data_rows") != actual_rows:
                errors.append(
                    f"{asset_path}: row count mismatch: expected {asset.get('data_rows')}, got {actual_rows}"
                )
        errors.extend(_check_provenance(repo_root, asset_path, asset.get("provenance")))
        if relative_path.name == "hkscs_supplement.csv":
            errors.extend(
                _check_hkscs_supplement_reproducibility(
                    repo_root, asset_path, asset.get("provenance")
                )
            )
        if relative_path.name == "english_assist.csv":
            errors.extend(
                _check_english_assist_reproducibility(
                    repo_root, asset_path, asset.get("provenance")
                )
            )
        if relative_path.name == "t2s_map.tsv":
            errors.extend(
                _check_t2s_reproducibility(
                    repo_root, asset_path, asset.get("provenance")
                )
            )

    for missing in sorted(expected_paths - declared_paths):
        errors.append(f"unmanifested shipped asset: {missing}")
    for missing in sorted(declared_paths - expected_paths):
        errors.append(f"manifest entry is not a shipped file: {missing}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
    )
    parser.add_argument("--manifest", type=Path)
    args = parser.parse_args()
    repo_root = args.repo_root.resolve()
    manifest_path = args.manifest or repo_root / DEFAULT_MANIFEST
    if not manifest_path.is_absolute():
        manifest_path = repo_root / manifest_path

    errors = verify_manifest(repo_root, manifest_path)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print(f"Verified {manifest_path.relative_to(repo_root)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
