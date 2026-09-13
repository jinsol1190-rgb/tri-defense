// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {ThreatRegistry} from "../src/ThreatRegistry.sol";

// Minimal official Foundry cheatcode interface; no third-party test dependency.
interface Vm {
    function prank(address caller) external;
    function expectRevert(bytes calldata revertData) external;
    function expectRevert(bytes4 selector) external;
    function expectEmit(bool topic1, bool topic2, bool topic3, bool data, address emitter) external;
    function assume(bool condition) external;
}

contract ThreatRegistryTest {
    Vm private constant VM = Vm(address(uint160(uint256(keccak256("hevm cheat code")))));
    ThreatRegistry private registry;
    bytes32 private constant ID = bytes32(uint256(1));
    uint64 private constant TIMESTAMP = 1_800_000_000;
    string private constant VERSION = "MOCK-fixed-v1";
    bytes32 private audioHash;

    event ThreatRegistered(
        bytes32 indexed threatId,
        bytes32 indexed audioHash,
        uint8 aiResult,
        uint8 riskScore,
        string modelVersion,
        uint64 timestamp,
        ThreatRegistry.Status status
    );

    function setUp() public {
        registry = new ThreatRegistry(address(this));
        audioHash = sha256(bytes("synthetic-test-audio"));
    }

    function _register() private {
        registry.registerThreat(ID, audioHash, 1, 90, VERSION, TIMESTAMP);
    }

    function _assertOriginal() private view {
        ThreatRegistry.ThreatRecord memory r = registry.getThreat(ID);
        require(r.threatId == ID, "threatId changed");
        require(r.audioHash == audioHash, "audioHash changed");
        require(r.aiResult == 1, "aiResult changed");
        require(r.riskScore == 90, "riskScore changed");
        require(keccak256(bytes(r.modelVersion)) == keccak256(bytes(VERSION)), "modelVersion changed");
        require(r.timestamp == TIMESTAMP, "timestamp changed");
        require(r.status == ThreatRegistry.Status.SUSPECTED, "status changed");
        require(registry.getThreatIdByAudioHash(audioHash) == ID, "hash index changed");
    }

    function testRegisterAndReadAllSevenFields() public {
        _register();
        require(registry.REGISTRAR() == address(this), "registrar");
        require(registry.threatExists(ID), "existence");
        _assertOriginal();
    }

    function testEventContainsExactRecord() public {
        VM.expectEmit(true, true, false, true, address(registry));
        emit ThreatRegistered(ID, audioHash, 1, 90, VERSION, TIMESTAMP, ThreatRegistry.Status.SUSPECTED);
        _register();
    }

    function testZeroRegistrarRejected() public {
        VM.expectRevert(ThreatRegistry.InvalidRegistrar.selector);
        new ThreatRegistry(address(0));
    }

    function testDesignatedRegistrarCanDifferFromDeployer() public {
        address writer = address(0xBEEF);
        ThreatRegistry other = new ThreatRegistry(writer);
        VM.expectRevert(abi.encodeWithSelector(ThreatRegistry.Unauthorized.selector, address(this)));
        other.registerThreat(ID, audioHash, 1, 90, VERSION, TIMESTAMP);
        VM.prank(writer);
        other.registerThreat(ID, audioHash, 1, 90, VERSION, TIMESTAMP);
        require(other.threatExists(ID), "designated registrar could not write");
    }

    function testFuzzUnauthorizedCannotWrite(address caller) public {
        VM.assume(caller != address(this));
        VM.expectRevert(abi.encodeWithSelector(ThreatRegistry.Unauthorized.selector, caller));
        VM.prank(caller);
        registry.registerThreat(ID, audioHash, 1, 90, VERSION, TIMESTAMP);
        require(!registry.threatExists(ID), "unauthorized write persisted");
        require(registry.getThreatIdByAudioHash(audioHash) == bytes32(0), "unauthorized index persisted");
    }

    function testDuplicateIdRejectedAndOriginalPreserved() public {
        _register();
        bytes32 otherHash = sha256(bytes("other audio"));
        VM.expectRevert(abi.encodeWithSelector(ThreatRegistry.DuplicateThreatId.selector, ID));
        registry.registerThreat(ID, otherHash, 1, 1, "another-model", 2);
        _assertOriginal();
        require(registry.getThreatIdByAudioHash(otherHash) == bytes32(0), "orphan index");
    }

    function testDuplicateHashRejectedAcrossModelsAndOriginalPreserved() public {
        _register();
        bytes32 otherId = bytes32(uint256(2));
        VM.expectRevert(abi.encodeWithSelector(ThreatRegistry.DuplicateAudioHash.selector, audioHash, ID));
        registry.registerThreat(otherId, audioHash, 1, 10, "another-model", 2);
        _assertOriginal();
        require(!registry.threatExists(otherId), "duplicate hash created record");
    }

    function testIdenticalRetryRejected() public {
        _register();
        VM.expectRevert(abi.encodeWithSelector(ThreatRegistry.DuplicateThreatId.selector, ID));
        _register();
        _assertOriginal();
    }

    function testMissingRecordReverts() public {
        VM.expectRevert(abi.encodeWithSelector(ThreatRegistry.ThreatNotFound.selector, ID));
        registry.getThreat(ID);
    }

    function testMissingHashAndZeroSentinels() public view {
        require(!registry.threatExists(ID), "missing ID exists");
        require(!registry.threatExists(bytes32(0)), "zero ID exists");
        require(registry.getThreatIdByAudioHash(audioHash) == bytes32(0), "missing hash exists");
        require(registry.getThreatIdByAudioHash(bytes32(0)) == bytes32(0), "zero hash exists");
    }

    function testZeroThreatIdRejected() public {
        VM.expectRevert(ThreatRegistry.InvalidThreatId.selector);
        registry.registerThreat(bytes32(0), audioHash, 1, 90, VERSION, TIMESTAMP);
    }

    function testZeroAudioHashRejected() public {
        VM.expectRevert(ThreatRegistry.InvalidAudioHash.selector);
        registry.registerThreat(ID, bytes32(0), 1, 90, VERSION, TIMESTAMP);
    }

    function testFuzzNonDeepvoiceResultRejected(uint8 result) public {
        VM.assume(result != 1);
        VM.expectRevert(ThreatRegistry.InvalidAIResult.selector);
        registry.registerThreat(ID, audioHash, result, 90, VERSION, TIMESTAMP);
        require(!registry.threatExists(ID), "invalid result persisted");
    }

    function testFuzzOutOfRangeScoreRejected(uint8 score) public {
        VM.assume(score > 100);
        VM.expectRevert(ThreatRegistry.InvalidRiskScore.selector);
        registry.registerThreat(ID, audioHash, 1, score, VERSION, TIMESTAMP);
        require(!registry.threatExists(ID), "invalid score persisted");
    }

    function testScoreAndTimestampBoundariesAccepted() public {
        registry.registerThreat(ID, audioHash, 1, 0, VERSION, 1);
        registry.registerThreat(bytes32(uint256(2)), sha256(bytes("second")), 1, 100, VERSION, type(uint64).max);
        require(registry.getThreat(ID).riskScore == 0, "zero score");
        require(registry.getThreat(ID).timestamp == 1, "min timestamp");
        ThreatRegistry.ThreatRecord memory r = registry.getThreat(bytes32(uint256(2)));
        require(r.riskScore == 100 && r.timestamp == type(uint64).max, "upper boundaries");
    }

    function testEmptyModelVersionRejected() public {
        VM.expectRevert(ThreatRegistry.InvalidModelVersion.selector);
        registry.registerThreat(ID, audioHash, 1, 90, "", TIMESTAMP);
    }

    function testWhitespaceModelVersionRejected() public {
        VM.expectRevert(ThreatRegistry.InvalidModelVersion.selector);
        registry.registerThreat(ID, audioHash, 1, 90, " \t\n", TIMESTAMP);
    }

    function testOverlongModelVersionRejected() public {
        VM.expectRevert(ThreatRegistry.InvalidModelVersion.selector);
        registry.registerThreat(ID, audioHash, 1, 90, string(new bytes(129)), TIMESTAMP);
    }

    function testMaximumModelVersionAccepted() public {
        bytes memory version = new bytes(128);
        for (uint256 i = 0; i < version.length; ++i) {
            version[i] = 0x61;
        }
        registry.registerThreat(ID, audioHash, 1, 90, string(version), TIMESTAMP);
        require(bytes(registry.getThreat(ID).modelVersion).length == 128, "max version length");
    }

    function testUtf8VersionByteLimit() public {
        bytes memory unit = bytes(unicode"가");
        bytes memory version = new bytes(129);
        for (uint256 i = 0; i < version.length; ++i) {
            version[i] = unit[i % 3];
        }
        VM.expectRevert(ThreatRegistry.InvalidModelVersion.selector);
        registry.registerThreat(ID, audioHash, 1, 90, string(version), TIMESTAMP);
    }

    function testZeroTimestampRejected() public {
        VM.expectRevert(ThreatRegistry.InvalidTimestamp.selector);
        registry.registerThreat(ID, audioHash, 1, 90, VERSION, 0);
    }

    function testPublicReadsDoNotRequireRegistrar() public {
        _register();
        VM.prank(address(0xBEEF));
        require(registry.getThreat(ID).riskScore == 90, "public record read");
        VM.prank(address(0xBEEF));
        require(registry.threatExists(ID), "public existence read");
        VM.prank(address(0xBEEF));
        require(registry.getThreatIdByAudioHash(audioHash) == ID, "public hash read");
    }

    function testFuzzRoundTrip(bytes32 id, bytes32 hash, uint8 score, uint64 timestamp) public {
        VM.assume(id != bytes32(0) && hash != bytes32(0) && timestamp != 0);
        score = score % 101;
        registry.registerThreat(id, hash, 1, score, VERSION, timestamp);
        ThreatRegistry.ThreatRecord memory r = registry.getThreat(id);
        require(r.threatId == id && r.audioHash == hash, "identifiers");
        require(r.aiResult == 1 && r.riskScore == score, "inference claim");
        require(keccak256(bytes(r.modelVersion)) == keccak256(bytes(VERSION)), "version");
        require(r.timestamp == timestamp && r.status == ThreatRegistry.Status.SUSPECTED, "time/status");
        require(registry.getThreatIdByAudioHash(hash) == id, "hash lookup");
    }

    function testSecondRecordDoesNotChangeFirst() public {
        _register();
        registry.registerThreat(bytes32(uint256(2)), sha256(bytes("second")), 1, 50, "MOCK-v2", 123);
        _assertOriginal();
    }
}
