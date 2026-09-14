"""P0 API encoding; does not generate or validate AI results/proofs."""
import re
from .errors import ApiError

UINT256_MAX = 2**256 - 1
MAX_PROOF_BYTES = 65536  # Transport bound for local P0, not a circuit constraint.


def hex_bytes(value, size=None, nonzero=False):
    if not isinstance(value, str) or not re.fullmatch(r"0x(?:[0-9a-fA-F]{2})*", value):
        raise ApiError(400, "INVALID_HEX")
    raw = bytes.fromhex(value[2:])
    if (size is not None and len(raw) != size) or (nonzero and not any(raw)):
        raise ApiError(400, "INVALID_BYTES")
    return raw


def uint_string(value):
    if not isinstance(value, str) or len(value) > 78 or not re.fullmatch(r"0|[1-9][0-9]*", value):
        raise ApiError(400, "INVALID_UINT_STRING")
    number = int(value)
    if number > UINT256_MAX:
        raise ApiError(400, "UINT_OVERFLOW")
    return number


def submission(body):
    fields = {"idempotencyKey", "nonce", "voiceprintHash", "riskScore", "proof", "publicInputs"}
    if type(body) is not dict or set(body) != fields:
        raise ApiError(400, "INVALID_FIELDS")
    key = body["idempotencyKey"]
    if not isinstance(key, str) or not re.fullmatch(r"[A-Za-z0-9._:-]{1,128}", key):
        raise ApiError(400, "INVALID_IDEMPOTENCY_KEY")
    score = body["riskScore"]
    if type(score) is not int or not 0 <= score <= 10000:
        raise ApiError(400, "INVALID_RISK_SCORE")
    proof = hex_bytes(body["proof"])
    if not 1 <= len(proof) <= MAX_PROOF_BYTES:
        raise ApiError(400, "INVALID_PROOF_LENGTH")
    inputs = body["publicInputs"]
    if type(inputs) is not list or len(inputs) != 8:
        raise ApiError(400, "INVALID_PUBLIC_INPUTS")
    # All values are supplied unchanged to the existing Registry. No fixture admission here.
    return {
        "idempotencyKey": key, "nonce": str(uint_string(body["nonce"])),
        "voiceprintHash": "0x" + hex_bytes(body["voiceprintHash"], 32, True).hex(),
        "riskScore": score, "proof": "0x" + proof.hex(),
        "publicInputs": [str(uint_string(value)) for value in inputs],
    }
