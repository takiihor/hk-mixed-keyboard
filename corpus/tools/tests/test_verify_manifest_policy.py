import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[3] / "scripts/verify_manifest_policy.py"
SPEC = importlib.util.spec_from_file_location("verify_manifest_policy", SCRIPT)
verifier = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(verifier)


class VerifyManifestPolicyTest(unittest.TestCase):
    def write_manifest(self, extra_permission: str = "", extra_exported: str = "") -> Path:
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / "AndroidManifest.xml"
        permission = (
            f'<uses-permission android:name="{extra_permission}" />' if extra_permission else ""
        )
        path.write_text(
            f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.hkmixedkeyboard.debug">
              <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36" />
              <uses-permission android:name="android.permission.VIBRATE" />
              <uses-permission android:name="com.hkmixedkeyboard.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
              {permission}
              <application android:allowBackup="false">
                <service android:name="com.hkmixedkeyboard.ime.HkImeService"
                    android:permission="android.permission.BIND_INPUT_METHOD" android:exported="true" />
                <activity android:name="com.hkmixedkeyboard.settings.SettingsActivity"
                    android:exported="true" />
                <receiver android:name="androidx.profileinstaller.ProfileInstallReceiver"
                    android:permission="android.permission.DUMP" android:exported="true" />
                {extra_exported}
              </application>
            </manifest>''',
            encoding="utf-8",
        )
        return path

    def verify(self, path: Path):
        verifier.verify(path, "com.hkmixedkeyboard.debug", 26, 36)

    def test_expected_debug_manifest_passes(self):
        self.verify(self.write_manifest())

    def test_internet_permission_fails(self):
        with self.assertRaisesRegex(ValueError, "unexpected merged permissions"):
            self.verify(self.write_manifest(extra_permission="android.permission.INTERNET"))

    def test_new_exported_component_fails(self):
        with self.assertRaisesRegex(ValueError, "exported component set changed"):
            self.verify(
                self.write_manifest(
                    extra_exported='<activity android:name="example.Unsafe" android:exported="true" />'
                )
            )


if __name__ == "__main__":
    unittest.main()
