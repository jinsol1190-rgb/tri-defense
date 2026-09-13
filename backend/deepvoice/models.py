"""Wire-compatible record types, not AI inference or stage 2 verification."""

from dataclasses import dataclass
from enum import IntEnum, Enum
from typing import Any, Tuple


class AIResult(IntEnum):
    HUMAN = 0
    DEEPVOICE = 1


class ThreatStatus(IntEnum):
    SUSPECTED = 0
    VERIFIED = 1
    DISPUTED = 2


class ProcessingState(str, Enum):
    CREATED = "CREATED"
    AI_ANALYZED = "AI_ANALYZED"
    CHAIN_PENDING = "CHAIN_PENDING"
    CHAIN_CONFIRMED = "CHAIN_CONFIRMED"
    CHAIN_FAILED = "CHAIN_FAILED"


def _bytes32(name: str, value: bytes) -> None:
    if type(value) is not bytes or len(value) != 32 or value == bytes(32):
        raise ValueError(f"{name} must be nonzero bytes32")


def _integer(name: str, value: int, minimum: int, maximum: int) -> None:
    # bool is an int subclass; reject coercion to avoid ambiguous persisted data.
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError(f"{name} must be an integer in [{minimum}, {maximum}]")


@dataclass(frozen=True)
class ThreatRecord:
    threatId: bytes
    audioHash: bytes
    aiResult: AIResult
    riskScore: int
    modelVersion: str
    timestamp: int
    status: ThreatStatus = ThreatStatus.SUSPECTED

    def __post_init__(self) -> None:
        _bytes32("threatId", self.threatId)
        _bytes32("audioHash", self.audioHash)
        if type(self.aiResult) is not AIResult:
            raise ValueError("aiResult must be an AIResult enum")
        if type(self.status) is not ThreatStatus:
            raise ValueError("status must be a ThreatStatus enum")
        _integer("riskScore", self.riskScore, 0, 100)
        _integer("timestamp", self.timestamp, 1, 2**64 - 1)
        if type(self.modelVersion) is not str:
            raise ValueError("modelVersion must be a string")
        version = self.modelVersion.encode("utf-8")
        if not 1 <= len(version) <= 128 or not any(b > 0x20 for b in version):
            raise ValueError("modelVersion must be 1..128 UTF-8 bytes and not ASCII whitespace only")

    def to_contract_args(self) -> Tuple[Any, ...]:
        """Arguments for registerThreat; status is enforced by the contract."""
        if self.aiResult != AIResult.DEEPVOICE:
            raise ValueError("Only DEEPVOICE records can be registered")
        if self.status != ThreatStatus.SUSPECTED:
            raise ValueError("Only SUSPECTED registration is implemented")
        return (
            self.threatId, self.audioHash, int(self.aiResult), self.riskScore,
            self.modelVersion, self.timestamp,
        )
