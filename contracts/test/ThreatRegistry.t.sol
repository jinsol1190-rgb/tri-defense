// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {ThreatRegistry} from "../src/ThreatRegistry.sol";
import {VerifierAdapter} from "../src/VerifierAdapter.sol";
import {MockVerifier} from "../src/mocks/MockVerifier.sol";

// Minimal Foundry cheatcodes; no external Solidity dependencies.
interface Vm {
    struct Log {
        bytes32[] topics;
        bytes data;
        address emitter;
    }
    function prank(address caller) external;
    function expectRevert(bytes calldata data) external;
    function expectRevert(bytes4 selector) external;
    function expectEmit(bool, bool, bool, bool, address) external;
    function chainId(uint256) external;
    function warp(uint256) external;
    function recordLogs() external;
    function getRecordedLogs() external returns (Log[] memory);
}

contract ThreatRegistryTest {
    Vm private constant VM = Vm(address(uint160(uint256(keccak256("hevm cheat code")))));
    uint256 private constant CIRCUIT = 1; // SAMPLE envelope, not an EZKL circuit identifier.
    bytes32 private constant HASH = keccak256("SAMPLE voiceprint fixture; no model output");
    bytes32 private constant PHONE = keccak256("+12025550123"); // Synthetic fixture, never contacted.
    bytes32 private constant EVIDENCE = keccak256("SAMPLE local number fixture v1");
    bytes private constant PROOF = bytes("MOCK_PROOF fixture v1");
    MockVerifier private verifier;
    VerifierAdapter private adapter;
    ThreatRegistry private registry;

    event ThreatRegistered(
        bytes32 indexed threatId, bytes32 indexed voiceprintHash, uint16 riskScore, uint64 registeredAt
    );
    event BlacklistPromoted(bytes32 indexed threatId, bytes32 indexed phoneKey);

    function setUp() public {
        VM.chainId(31337);
        verifier = new MockVerifier(address(this));
        adapter = new VerifierAdapter(address(verifier), CIRCUIT);
        registry = _newRegistry(7000);
    }

    function _newRegistry(uint16 threshold) private returns (ThreatRegistry) {
        return new ThreatRegistry(adapter, threshold, address(this), PHONE, EVIDENCE);
    }

    function _inputs(ThreatRegistry target, address sender, uint256 nonce, bytes32 hash, uint16 score)
        private
        view
        returns (uint256[] memory p)
    {
        p = new uint256[](8);
        p[0] = CIRCUIT;
        p[1] = score;
        p[2] = uint256(hash) >> 128;
        p[3] = uint128(uint256(hash));
        p[4] = block.chainid;
        p[5] = uint160(address(target));
        p[6] = uint160(sender);
        p[7] = nonce;
    }

    function _id(ThreatRegistry target, address sender, uint256 nonce) private view returns (bytes32) {
        return keccak256(abi.encode(block.chainid, address(target), sender, nonce));
    }

    function _register(uint256 nonce) private returns (bytes32) {
        uint256[] memory p = _inputs(registry, address(this), nonce, HASH, 9000);
        verifier.setFixture(PROOF, p, true);
        return registry.registerThreat(HASH, 9000, nonce, PROOF, p);
    }

    function _assertRejected(uint256[] memory p, bytes memory proof, bytes4 errorSelector) private {
        VM.recordLogs();
        VM.expectRevert(errorSelector);
        registry.registerThreat(HASH, 9000, 1, proof, p);
        require(VM.getRecordedLogs().length == 0, "failure emitted logs");
        bytes32 id = _id(registry, address(this), 1);
        require(!registry.exists(id) && !registry.usedSubmission(id), "failure persisted");
        require(!registry.isBlacklisted(PHONE), "failure promoted number");
    }

    function testRegistrationStoresFullRecordAndEmitsExactEvent() public {
        VM.warp(1_800_000_000);
        bytes32 expected = _id(registry, address(this), 1);
        uint256[] memory p = _inputs(registry, address(this), 1, HASH, 9000);
        verifier.setFixture(PROOF, p, true);
        VM.expectEmit(true, true, false, true, address(registry));
        emit ThreatRegistered(expected, HASH, 9000, 1_800_000_000);
        require(registry.registerThreat(HASH, 9000, 1, PROOF, p) == expected, "deterministic ID");
        ThreatRegistry.ThreatRecord memory r = registry.getThreat(expected);
        require(r.threatId == expected && r.voiceprintHash == HASH && r.riskScore == 9000, "fields");
        require(r.registeredAt == 1_800_000_000 && keccak256(r.zkProof) == keccak256(PROOF), "time/proof");
        require(registry.exists(expected) && registry.usedSubmission(expected), "indexes");
        require(!registry.isBlacklisted(PHONE), "registration must not promote");
    }

    function testLongProofStoredWithoutTruncation() public {
        bytes memory proof = new bytes(128); // MOCK_PROOF exercises multi-slot storage.
        for (uint256 i = 0; i < proof.length; ++i) {
            proof[i] = bytes1(uint8(i));
        }
        uint256[] memory p = _inputs(registry, address(this), 1, HASH, 9000);
        verifier.setFixture(proof, p, true);
        bytes32 id = registry.registerThreat(HASH, 9000, 1, proof, p);
        bytes memory stored = registry.getThreat(id).zkProof;
        require(stored.length == 128 && keccak256(stored) == keccak256(proof), "full proof storage");
    }

    function testUnknownAndTamperedProofsFailWithoutStateOrEvents() public {
        uint256[] memory p = _inputs(registry, address(this), 1, HASH, 9000);
        _assertRejected(p, PROOF, ThreatRegistry.InvalidProof.selector);
        verifier.setFixture(PROOF, p, true);
        _assertRejected(p, bytes("MOCK_PROOF tampered"), ThreatRegistry.InvalidProof.selector);
        _assertRejected(p, bytes(""), ThreatRegistry.InvalidProof.selector);
        _register(1); // A failed attempt did not consume the nonce.
    }

    function testChangedPublicInputInvalidatesFixture() public {
        uint256[] memory p = _inputs(registry, address(this), 1, HASH, 9000);
        verifier.setFixture(PROOF, p, true);
        p[1]++;
        _assertRejected(p, PROOF, ThreatRegistry.InvalidProof.selector);
    }

    function testEveryBindingMismatchRejectedEvenIfVerifierAcceptsFixture() public {
        for (uint256 i = 1; i < 8; ++i) {
            uint256[] memory p = _inputs(registry, address(this), 1, HASH, 9000);
            p[i]++;
            verifier.setFixture(PROOF, p, true);
            _assertRejected(p, PROOF, ThreatRegistry.PublicInputMismatch.selector);
        }
    }

    function testWrongCircuitAndWrongInputLengthRejected() public {
        uint256[] memory p = _inputs(registry, address(this), 1, HASH, 9000);
        p[0] = 2;
        verifier.setFixture(PROOF, p, true);
        _assertRejected(p, PROOF, ThreatRegistry.InvalidProof.selector);
        for (uint256 n = 0; n <= 9; ++n) {
            if (n == 8) continue;
            p = new uint256[](n);
            if (n > 0) p[0] = CIRCUIT;
            verifier.setFixture(PROOF, p, true);
            _assertRejected(p, PROOF, ThreatRegistry.InvalidProof.selector);
        }
    }

    function testDuplicateCannotOverwriteOriginal() public {
        bytes32 id = _register(1);
        uint256[] memory p = _inputs(registry, address(this), 1, bytes32(uint256(2)), 8000);
        verifier.setFixture(PROOF, p, true);
        VM.expectRevert(abi.encodeWithSelector(ThreatRegistry.DuplicateSubmission.selector, id));
        registry.registerThreat(bytes32(uint256(2)), 8000, 1, PROOF, p);
        require(registry.getThreat(id).voiceprintHash == HASH && registry.getThreat(id).riskScore == 9000, "overwrite");
    }

    function testSameVoiceprintCanHaveMultipleIncidents() public {
        bytes32 first = _register(1);
        bytes32 second = _register(2);
        require(first != second && registry.exists(first) && registry.exists(second), "multiple incidents");
    }

    function testCrossAccountReplayRejectedButIndependentSameNonceAllowed() public {
        _register(1);
        address other = address(0xBEEF);
        uint256[] memory p = _inputs(registry, address(this), 1, HASH, 9000);
        VM.expectRevert(ThreatRegistry.PublicInputMismatch.selector);
        VM.prank(other);
        registry.registerThreat(HASH, 9000, 1, PROOF, p);
        p = _inputs(registry, other, 1, HASH, 9000);
        verifier.setFixture(PROOF, p, true);
        VM.prank(other);
        bytes32 id = registry.registerThreat(HASH, 9000, 1, PROOF, p);
        require(id == _id(registry, other, 1), "account domain");
    }

    function testCrossRegistryReplayRejected() public {
        _register(1);
        ThreatRegistry other = _newRegistry(7000);
        uint256[] memory p = _inputs(registry, address(this), 1, HASH, 9000);
        VM.expectRevert(ThreatRegistry.PublicInputMismatch.selector);
        other.registerThreat(HASH, 9000, 1, PROOF, p);
        require(!other.exists(_id(other, address(this), 1)), "cross registry state");
    }

    function testCrossChainReplayFailsClosed() public {
        uint256[] memory p = _inputs(registry, address(this), 1, HASH, 9000);
        verifier.setFixture(PROOF, p, true);
        VM.chainId(31338);
        _assertRejected(p, PROOF, ThreatRegistry.InvalidProof.selector);
    }

    function testLowRiskRejectedAndBoundaryAccepted() public {
        uint256[] memory p = _inputs(registry, address(this), 1, HASH, 6999);
        verifier.setFixture(PROOF, p, true);
        VM.expectRevert(ThreatRegistry.BelowRiskThreshold.selector);
        registry.registerThreat(HASH, 6999, 1, PROOF, p);
        p[1] = 7000;
        verifier.setFixture(PROOF, p, true);
        registry.registerThreat(HASH, 7000, 1, PROOF, p);
    }

    function testFuzzRoundTrip(bytes32 hash, uint16 score, uint256 nonce) public {
        if (hash == bytes32(0)) hash = HASH;
        score = uint16(7000 + uint256(score) % 3001);
        uint256[] memory p = _inputs(registry, address(this), nonce, hash, score);
        verifier.setFixture(PROOF, p, true);
        bytes32 id = registry.registerThreat(hash, score, nonce, PROOF, p);
        ThreatRegistry.ThreatRecord memory r = registry.getThreat(id);
        require(
            r.threatId == _id(registry, address(this), nonce) && r.voiceprintHash == hash && r.riskScore == score,
            "round trip"
        );
    }

    function testFuzzOutOfRangeScoresRejected(uint16 seed) public {
        uint16 score = uint16(10001 + uint256(seed) % 55535);
        uint256[] memory p = _inputs(registry, address(this), 1, HASH, score);
        VM.expectRevert(ThreatRegistry.InvalidRiskScore.selector);
        registry.registerThreat(HASH, score, 1, PROOF, p);
    }

    function testZeroHashRejected() public {
        uint256[] memory p = _inputs(registry, address(this), 1, bytes32(0), 9000);
        VM.expectRevert(ThreatRegistry.InvalidVoiceprintHash.selector);
        registry.registerThreat(bytes32(0), 9000, 1, PROOF, p);
    }

    function testScoreZeroAndMaximumRepresentableWithConfiguredThreshold() public {
        registry = _newRegistry(0);
        uint256[] memory p = _inputs(registry, address(this), 0, HASH, 0);
        verifier.setFixture(PROOF, p, true);
        registry.registerThreat(HASH, 0, 0, PROOF, p);
        p = _inputs(registry, address(this), type(uint256).max, HASH, 10000);
        verifier.setFixture(PROOF, p, true);
        registry.registerThreat(HASH, 10000, type(uint256).max, PROOF, p);
    }

    function testTimestampOverflowRejected() public {
        VM.warp(uint256(type(uint64).max) + 1);
        uint256[] memory p = _inputs(registry, address(this), 1, HASH, 9000);
        verifier.setFixture(PROOF, p, true);
        _assertRejected(p, PROOF, ThreatRegistry.TimestampOverflow.selector);
    }

    function testMissingRecordRejected() public {
        VM.expectRevert(abi.encodeWithSelector(ThreatRegistry.ThreatNotFound.selector, bytes32(0)));
        registry.getThreat(bytes32(0));
    }

    function testSyntheticNumberPromotionRequiresSeparateAuthorizedTransaction() public {
        bytes32 id = _register(1);
        require(!registry.isBlacklisted(PHONE), "automatic promotion");
        require(registry.TEST_NUMBER_EVIDENCE() == EVIDENCE, "evidence");
        VM.expectEmit(true, true, false, true, address(registry));
        emit BlacklistPromoted(id, PHONE);
        registry.promoteToBlacklist(id, PHONE);
        require(registry.isBlacklisted(PHONE), "promotion");
        VM.expectRevert(ThreatRegistry.AlreadyBlacklisted.selector);
        registry.promoteToBlacklist(id, PHONE);
    }

    function testFuzzUnauthorizedPromotionRejected(address caller) public {
        if (caller == address(this)) return;
        bytes32 id = _register(1);
        VM.expectRevert(ThreatRegistry.Unauthorized.selector);
        VM.prank(caller);
        registry.promoteToBlacklist(id, PHONE);
        require(!registry.isBlacklisted(PHONE), "unauthorized promotion");
    }

    function testSpoofedOrUnapprovedNumberNeverPromoted() public {
        bytes32 id = _register(1);
        bytes32 unrelated = keccak256("SAMPLE unrelated spoofed number key");
        VM.recordLogs();
        VM.expectRevert(ThreatRegistry.UnapprovedTestNumber.selector);
        registry.promoteToBlacklist(id, unrelated);
        require(VM.getRecordedLogs().length == 0, "failed promotion logs");
        require(!registry.isBlacklisted(unrelated) && !registry.isBlacklisted(PHONE), "spoofed number");
    }

    function testPromotionRequiresExistingThreatAndLocalChain() public {
        VM.expectRevert(abi.encodeWithSelector(ThreatRegistry.ThreatNotFound.selector, bytes32(0)));
        registry.promoteToBlacklist(bytes32(0), PHONE);
        bytes32 id = _register(1);
        VM.chainId(84532);
        VM.expectRevert(ThreatRegistry.LocalPromotionOnly.selector);
        registry.promoteToBlacklist(id, PHONE);
    }

    function testInvalidRegistryConfigurationRejected() public {
        VM.expectRevert(ThreatRegistry.InvalidConfiguration.selector);
        new ThreatRegistry(VerifierAdapter(address(0)), 7000, address(this), PHONE, EVIDENCE);
        VM.expectRevert(ThreatRegistry.InvalidConfiguration.selector);
        _newRegistry(10001);
        VM.expectRevert(ThreatRegistry.InvalidConfiguration.selector);
        new ThreatRegistry(adapter, 7000, address(0), PHONE, EVIDENCE);
        VM.expectRevert(ThreatRegistry.InvalidConfiguration.selector);
        new ThreatRegistry(adapter, 7000, address(this), bytes32(0), EVIDENCE);
        VM.expectRevert(ThreatRegistry.InvalidConfiguration.selector);
        new ThreatRegistry(adapter, 7000, address(this), PHONE, bytes32(0));
    }
}
