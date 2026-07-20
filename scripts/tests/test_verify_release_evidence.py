import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "scripts/verify_release_evidence.py"
COMMIT = "a" * 40


class VerifyReleaseEvidenceTest(unittest.TestCase):
    @staticmethod
    def record(record_name, aab_sha256):
        actor_field = "reviewer" if record_name in {
            "native_review",
            "accessibility_review",
        } else "owner"
        return {
            "status": "APPROVED",
            actor_field: f"{record_name}-signer",
            "date": "2026-07-16",
            "commit": COMMIT,
            "aab_sha256": aab_sha256,
        }

    def write_complete_bundle(self, directory):
        evidence_dir = directory / "evidence"
        evidence_dir.mkdir()
        aab_path = directory / "candidate.aab"
        aab_path.write_bytes(b"signed candidate artifact")
        aab_sha256 = hashlib.sha256(aab_path.read_bytes()).hexdigest()
        for record_name in (
            "native_review",
            "device_beta_matrix",
            "accessibility_review",
            "legal_signoff",
            "signed_aab",
            "beta_result",
        ):
            (evidence_dir / f"{record_name}.json").write_text(
                json.dumps(self.record(record_name, aab_sha256)),
                encoding="utf-8",
            )
        return evidence_dir, aab_path

    @staticmethod
    def run_verifier(evidence_dir, aab_path):
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--evidence-dir",
                str(evidence_dir),
                "--commit",
                COMMIT,
                "--aab",
                str(aab_path),
            ],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            check=False,
        )

    def test_accepts_only_a_complete_consistent_evidence_bundle(self):
        with tempfile.TemporaryDirectory() as tmp:
            evidence_dir, aab_path = self.write_complete_bundle(Path(tmp))

            completed = self.run_verifier(evidence_dir, aab_path)

        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertIn("release evidence verified", completed.stdout)

    def test_rejects_each_missing_external_gate(self):
        for record_name in (
            "native_review",
            "device_beta_matrix",
            "accessibility_review",
            "legal_signoff",
            "signed_aab",
            "beta_result",
        ):
            with self.subTest(record_name=record_name), tempfile.TemporaryDirectory() as tmp:
                evidence_dir, aab_path = self.write_complete_bundle(Path(tmp))
                (evidence_dir / f"{record_name}.json").unlink()

                completed = self.run_verifier(evidence_dir, aab_path)

            self.assertNotEqual(completed.returncode, 0)
            self.assertIn(f"missing external gate: {record_name}", completed.stderr)

    def test_rejects_open_and_hash_mismatched_records(self):
        with tempfile.TemporaryDirectory() as tmp:
            evidence_dir, aab_path = self.write_complete_bundle(Path(tmp))
            record_path = evidence_dir / "native_review.json"
            record = json.loads(record_path.read_text(encoding="utf-8"))
            record["status"] = "OPEN"
            record_path.write_text(json.dumps(record), encoding="utf-8")

            completed = self.run_verifier(evidence_dir, aab_path)

            self.assertNotEqual(completed.returncode, 0)
            self.assertIn("native_review status must be APPROVED", completed.stderr)

            record["status"] = "APPROVED"
            record["commit"] = "b" * 40
            record_path.write_text(json.dumps(record), encoding="utf-8")
            completed = self.run_verifier(evidence_dir, aab_path)

            self.assertNotEqual(completed.returncode, 0)
            self.assertIn("native_review commit does not match", completed.stderr)

            record["commit"] = COMMIT
            record["aab_sha256"] = "c" * 64
            record_path.write_text(json.dumps(record), encoding="utf-8")
            completed = self.run_verifier(evidence_dir, aab_path)

            self.assertNotEqual(completed.returncode, 0)
            self.assertIn("native_review AAB hash does not match", completed.stderr)


if __name__ == "__main__":
    unittest.main()
