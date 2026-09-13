import hashlib
import unittest
from dataclasses import FrozenInstanceError, replace

from deepvoice.models import AIResult, ProcessingState, ThreatRecord, ThreatStatus
from deepvoice.verification.artifact_detector import detect_artifacts
from deepvoice.verification.watermark_checker import check_watermark
from deepvoice.verification.provenance_checker import check_provenance


class ThreatRecordTests(unittest.TestCase):
    def setUp(self):
        # Synthetic bytes only. MOCK is fixture metadata, not an implemented detector.
        self.record = ThreatRecord(
            threatId=bytes.fromhex("11" * 32),
            audioHash=hashlib.sha256(b"synthetic-test-audio").digest(),
            aiResult=AIResult.DEEPVOICE, riskScore=90,
            modelVersion="MOCK-fixed-v1", timestamp=1_800_000_000,
        )

    def test_contract_argument_order_and_status(self):
        r = self.record
        self.assertEqual(r.to_contract_args(), (
            r.threatId, r.audioHash, 1, 90, "MOCK-fixed-v1", 1_800_000_000,
        ))
        self.assertEqual(r.status, ThreatStatus.SUSPECTED)

    def test_invalid_bytes32(self):
        for field in ("threatId", "audioHash"):
            for value in (bytes(32), b"", b"a" * 31, b"a" * 33, "ab" * 32, bytearray(b"a" * 32)):
                with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                    replace(self.record, **{field: value})

    def test_score_boundaries(self):
        for score in (0, 100):
            self.assertEqual(replace(self.record, riskScore=score).riskScore, score)
        for score in (-1, 101, 255, True, 90.0, "90", None):
            with self.subTest(score=score), self.assertRaises(ValueError):
                replace(self.record, riskScore=score)

    def test_timestamp_boundaries(self):
        for timestamp in (1, 2**64 - 1):
            self.assertEqual(replace(self.record, timestamp=timestamp).timestamp, timestamp)
        for timestamp in (0, -1, 2**64, True, 1.5, "123"):
            with self.subTest(timestamp=timestamp), self.assertRaises(ValueError):
                replace(self.record, timestamp=timestamp)

    def test_model_version_byte_length(self):
        for version in ("a", "a" * 128, "가" * 42):
            self.assertEqual(replace(self.record, modelVersion=version).modelVersion, version)
        for version in ("", " \t\n", "\x00", "a" * 129, "가" * 43, None):
            with self.subTest(version=version), self.assertRaises(ValueError):
                replace(self.record, modelVersion=version)

    def test_enum_values_match_solidity(self):
        self.assertEqual([int(s) for s in ThreatStatus], [0, 1, 2])
        self.assertEqual([int(r) for r in AIResult], [0, 1])
        for field in ("aiResult", "status"):
            for value in (True, 0, 3, "SUSPECTED"):
                with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                    replace(self.record, **{field: value})

    def test_registration_rejects_human(self):
        with self.assertRaises(ValueError):
            replace(self.record, aiResult=AIResult.HUMAN).to_contract_args()

    def test_registration_rejects_non_suspected(self):
        for status in (ThreatStatus.VERIFIED, ThreatStatus.DISPUTED):
            with self.subTest(status=status), self.assertRaises(ValueError):
                replace(self.record, status=status).to_contract_args()

    def test_frozen_record(self):
        with self.assertRaises(FrozenInstanceError):
            self.record.riskScore = 10

    def test_processing_state_is_separate(self):
        self.assertEqual(len(ProcessingState), 5)
        self.assertEqual(ProcessingState.CHAIN_CONFIRMED.value, "CHAIN_CONFIRMED")

    def test_stage_two_is_explicitly_unimplemented(self):
        for checker in (detect_artifacts, check_watermark, check_provenance):
            with self.subTest(checker=checker.__name__), self.assertRaises(NotImplementedError):
                checker(b"synthetic-test-audio")


if __name__ == "__main__":
    unittest.main()
