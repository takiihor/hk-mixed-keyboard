#!/usr/bin/env python3
"""Fail closed when a merged Android manifest exceeds the app's privacy surface."""

from __future__ import annotations

import argparse
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
EXPECTED_EXPORTED = {
    "com.hkmixedkeyboard.ime.HkImeService": "android.permission.BIND_INPUT_METHOD",
    "com.hkmixedkeyboard.settings.SettingsActivity": "",
    "androidx.profileinstaller.ProfileInstallReceiver": "android.permission.DUMP",
}


def verify(
    manifest_path: Path,
    expected_package: str,
    expected_min_sdk: int,
    expected_target_sdk: int,
    expected_version_code: int | None = None,
    expected_version_name: str | None = None,
) -> None:
    root = ET.parse(manifest_path).getroot()
    package = root.get("package", "")
    if package != expected_package:
        raise ValueError(f"package is {package!r}; expected {expected_package!r}")
    if expected_version_code is not None and root.get(f"{ANDROID}versionCode") != str(
        expected_version_code
    ):
        raise ValueError(f"versionCode must be {expected_version_code}")
    if expected_version_name is not None and root.get(f"{ANDROID}versionName") != expected_version_name:
        raise ValueError(f"versionName must be {expected_version_name}")

    uses_sdk = root.find("uses-sdk")
    if uses_sdk is None:
        raise ValueError("merged manifest has no uses-sdk")
    if uses_sdk.get(f"{ANDROID}minSdkVersion") != str(expected_min_sdk):
        raise ValueError(f"minSdkVersion must remain {expected_min_sdk}")
    if uses_sdk.get(f"{ANDROID}targetSdkVersion") != str(expected_target_sdk):
        raise ValueError(f"targetSdkVersion must remain {expected_target_sdk}")

    permissions = {
        element.get(f"{ANDROID}name", "") for element in root.findall("uses-permission")
    }
    permitted = {"android.permission.VIBRATE"}
    debug_permission = f"{package}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    if package.endswith(".debug"):
        permitted.add(debug_permission)
    unexpected_permissions = permissions - permitted
    if unexpected_permissions:
        raise ValueError(
            "unexpected merged permissions: " + ", ".join(sorted(unexpected_permissions))
        )
    if "android.permission.VIBRATE" not in permissions:
        raise ValueError("expected VIBRATE permission is missing")

    application = root.find("application")
    if application is None:
        raise ValueError("merged manifest has no application")
    if application.get(f"{ANDROID}allowBackup") != "false":
        raise ValueError("allowBackup must remain false")

    exported: dict[str, str] = {}
    for component_type in ("activity", "service", "receiver", "provider"):
        for component in application.findall(component_type):
            if component.get(f"{ANDROID}exported") != "true":
                continue
            name = component.get(f"{ANDROID}name", "")
            exported[name] = component.get(f"{ANDROID}permission", "")
    if exported != EXPECTED_EXPORTED:
        expected = ", ".join(
            f"{name} [{permission or 'launcher-only'}]"
            for name, permission in sorted(EXPECTED_EXPORTED.items())
        )
        actual = ", ".join(
            f"{name} [{permission or 'unprotected'}]"
            for name, permission in sorted(exported.items())
        )
        raise ValueError(f"exported component set changed; expected {expected}; found {actual}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--package", required=True)
    parser.add_argument("--min-sdk", type=int, default=26)
    parser.add_argument("--target-sdk", type=int, default=36)
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--version-name")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        verify(
            args.manifest,
            args.package,
            args.min_sdk,
            args.target_sdk,
            args.version_code,
            args.version_name,
        )
    except (OSError, ET.ParseError, ValueError) as error:
        raise SystemExit(f"manifest policy failed: {error}") from error
    print(f"manifest policy verified: {args.package}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
