// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

/// @notice Append-only detection claims; does not prove AI correctness or audio provenance.
contract ThreatRegistry {
    enum Status {
        SUSPECTED,
        VERIFIED,
        DISPUTED
    }

    struct ThreatRecord {
        bytes32 threatId;
        bytes32 audioHash;
        uint8 aiResult;
        uint8 riskScore;
        string modelVersion;
        uint64 timestamp;
        Status status;
    }

    uint8 public constant DEEPVOICE = 1;
    address public immutable REGISTRAR;
    mapping(bytes32 => ThreatRecord) private records;
    mapping(bytes32 => bytes32) private threatIdsByAudioHash;

    error InvalidRegistrar();
    error Unauthorized(address caller);
    error InvalidThreatId();
    error InvalidAudioHash();
    error InvalidAIResult();
    error InvalidRiskScore();
    error InvalidModelVersion();
    error InvalidTimestamp();
    error DuplicateThreatId(bytes32 threatId);
    error DuplicateAudioHash(bytes32 audioHash, bytes32 existingThreatId);
    error ThreatNotFound(bytes32 threatId);

    event ThreatRegistered(
        bytes32 indexed threatId,
        bytes32 indexed audioHash,
        uint8 aiResult,
        uint8 riskScore,
        string modelVersion,
        uint64 timestamp,
        Status status
    );

    constructor(address registrar_) {
        if (registrar_ == address(0)) revert InvalidRegistrar();
        REGISTRAR = registrar_;
    }

    /// @dev Input timestamp is the claimed detection time, not block.timestamp.
    /// The caller supplies SHA-256 of audio bytes; the contract cannot verify the preimage.
    /// All new records are SUSPECTED. Repeated IDs or hashes revert, including retries.
    function registerThreat(
        bytes32 threatId,
        bytes32 audioHash,
        uint8 aiResult,
        uint8 riskScore,
        string calldata modelVersion,
        uint64 timestamp
    ) external {
        if (msg.sender != REGISTRAR) revert Unauthorized(msg.sender);
        if (threatId == bytes32(0)) revert InvalidThreatId();
        if (audioHash == bytes32(0)) revert InvalidAudioHash();
        if (aiResult != DEEPVOICE) revert InvalidAIResult();
        if (riskScore > 100) revert InvalidRiskScore();
        _validateModelVersion(modelVersion);
        if (timestamp == 0) revert InvalidTimestamp();
        if (threatExists(threatId)) revert DuplicateThreatId(threatId);
        bytes32 existingId = threatIdsByAudioHash[audioHash];
        if (existingId != bytes32(0)) revert DuplicateAudioHash(audioHash, existingId);

        records[threatId] = ThreatRecord({
            threatId: threatId,
            audioHash: audioHash,
            aiResult: aiResult,
            riskScore: riskScore,
            modelVersion: modelVersion,
            timestamp: timestamp,
            status: Status.SUSPECTED
        });
        threatIdsByAudioHash[audioHash] = threatId;
        emit ThreatRegistered(threatId, audioHash, aiResult, riskScore, modelVersion, timestamp, Status.SUSPECTED);
    }

    function _validateModelVersion(string calldata modelVersion) private pure {
        bytes memory version = bytes(modelVersion);
        if (version.length == 0 || version.length > 128) revert InvalidModelVersion();
        for (uint256 i = 0; i < version.length; ++i) {
            if (uint8(version[i]) > 0x20) return;
        }
        revert InvalidModelVersion();
    }

    function getThreat(bytes32 threatId) external view returns (ThreatRecord memory) {
        if (!threatExists(threatId)) revert ThreatNotFound(threatId);
        return records[threatId];
    }

    function threatExists(bytes32 threatId) public view returns (bool) {
        return records[threatId].threatId != bytes32(0);
    }

    /// @return Zero if no record exists. An audio hash is not a speaker identity.
    function getThreatIdByAudioHash(bytes32 audioHash) external view returns (bytes32) {
        return threatIdsByAudioHash[audioHash];
    }
}
