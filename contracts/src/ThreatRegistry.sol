// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {VerifierAdapter} from "./VerifierAdapter.sol";

/// @notice REAL append-only registry mechanics; trust is limited by the deployed verifier.
/// @dev P0 deployments use MOCK_PROOF. No AI accuracy, provenance, or number ownership claim.
contract ThreatRegistry {
    struct ThreatRecord {
        bytes32 threatId;
        bytes32 voiceprintHash;
        uint16 riskScore;
        uint64 registeredAt;
        bytes zkProof;
    }

    uint16 public constant MAX_RISK_SCORE = 10_000;
    uint256 public constant LOCAL_CHAIN_ID = 31337;
    VerifierAdapter public immutable VERIFIER_ADAPTER;
    uint16 public immutable MIN_RISK_SCORE;
    address public immutable PROMOTION_AUTHORITY;
    bytes32 public immutable TEST_PHONE_KEY;
    bytes32 public immutable TEST_NUMBER_EVIDENCE;

    mapping(bytes32 => ThreatRecord) private threats;
    mapping(bytes32 => bool) public exists;
    mapping(bytes32 => bool) public usedSubmission;
    mapping(bytes32 => bool) private blacklistedPhoneKeys;

    error InvalidConfiguration();
    error InvalidVoiceprintHash();
    error InvalidRiskScore();
    error DuplicateSubmission(bytes32 threatId);
    error InvalidProof();
    error PublicInputMismatch();
    error BelowRiskThreshold();
    error TimestampOverflow();
    error ThreatNotFound(bytes32 threatId);
    error Unauthorized();
    error LocalPromotionOnly();
    error UnapprovedTestNumber();
    error AlreadyBlacklisted();

    event ThreatRegistered(
        bytes32 indexed threatId, bytes32 indexed voiceprintHash, uint16 riskScore, uint64 registeredAt
    );
    event BlacklistPromoted(bytes32 indexed threatId, bytes32 indexed phoneKey);

    /// @dev Phone/evidence are a local synthetic fixture approved at deployment, not a real-number policy.
    /// The authorized promotion transaction separately attests that the fixture applies to this incident.
    constructor(
        VerifierAdapter adapter,
        uint16 minRiskScore,
        address promotionAuthority,
        bytes32 testPhoneKey,
        bytes32 testNumberEvidence
    ) {
        if (
            address(adapter).code.length == 0 || minRiskScore > MAX_RISK_SCORE || promotionAuthority == address(0)
                || testPhoneKey == bytes32(0) || testNumberEvidence == bytes32(0)
        ) revert InvalidConfiguration();
        VERIFIER_ADAPTER = adapter;
        MIN_RISK_SCORE = minRiskScore;
        PROMOTION_AUTHORITY = promotionAuthority;
        TEST_PHONE_KEY = testPhoneKey;
        TEST_NUMBER_EVIDENCE = testNumberEvidence;
    }

    function registerThreat(
        bytes32 voiceprintHash,
        uint16 riskScore,
        uint256 nonce,
        bytes calldata proof,
        uint256[] calldata publicInputs
    ) external returns (bytes32 threatId) {
        if (voiceprintHash == bytes32(0)) revert InvalidVoiceprintHash();
        if (riskScore > MAX_RISK_SCORE) revert InvalidRiskScore();
        threatId = keccak256(abi.encode(block.chainid, address(this), msg.sender, nonce));
        if (usedSubmission[threatId]) revert DuplicateSubmission(threatId);
        if (!VERIFIER_ADAPTER.verifyProof(proof, publicInputs)) revert InvalidProof();
        // P0 envelope: circuit, score, hash high/low 128 bits, chain, registry, submitter, nonce.
        if (
            publicInputs.length != 8 || publicInputs[0] != VERIFIER_ADAPTER.CIRCUIT_ID() || publicInputs[1] != riskScore
                || publicInputs[2] != uint256(voiceprintHash) >> 128
                || publicInputs[3] != uint256(uint128(uint256(voiceprintHash))) || publicInputs[4] != block.chainid
                || publicInputs[5] != uint256(uint160(address(this))) || publicInputs[6] != uint256(uint160(msg.sender))
                || publicInputs[7] != nonce
        ) revert PublicInputMismatch();
        if (riskScore < MIN_RISK_SCORE) revert BelowRiskThreshold();
        if (block.timestamp > type(uint64).max) revert TimestampOverflow();
        uint64 registeredAt = uint64(block.timestamp);
        usedSubmission[threatId] = true;
        exists[threatId] = true;
        threats[threatId] = ThreatRecord(threatId, voiceprintHash, riskScore, registeredAt, proof);
        emit ThreatRegistered(threatId, voiceprintHash, riskScore, registeredAt);
    }

    function getThreat(bytes32 threatId) external view returns (ThreatRecord memory) {
        if (!exists[threatId]) revert ThreatNotFound(threatId);
        return threats[threatId];
    }

    function isBlacklisted(bytes32 phoneKey) external view returns (bool) {
        return blacklistedPhoneKeys[phoneKey];
    }

    /// @notice SAMPLE promotion policy: a separate authorized attestation for the configured test fixture.
    /// @dev Disabled outside local chain; general registration never contains a phone key.
    function promoteToBlacklist(bytes32 threatId, bytes32 phoneKey) external {
        if (block.chainid != LOCAL_CHAIN_ID) revert LocalPromotionOnly();
        if (msg.sender != PROMOTION_AUTHORITY) revert Unauthorized();
        if (!exists[threatId]) revert ThreatNotFound(threatId);
        if (phoneKey != TEST_PHONE_KEY) revert UnapprovedTestNumber();
        if (blacklistedPhoneKeys[phoneKey]) revert AlreadyBlacklisted();
        blacklistedPhoneKeys[phoneKey] = true;
        emit BlacklistPromoted(threatId, phoneKey);
    }
}
