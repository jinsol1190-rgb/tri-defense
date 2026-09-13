// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {ThreatRegistry} from "../src/ThreatRegistry.sol";
import {MockVerifier} from "../src/mocks/MockVerifier.sol";
import {ScriptVm} from "./DeployLocal.s.sol";

/// @notice SAMPLE data / MOCK_PROOF local transaction smoke test; no audio or AI is executed.
contract SmokeLocal {
    ScriptVm private constant VM = ScriptVm(address(uint160(uint256(keccak256("hevm cheat code")))));

    function run(address verifierAddress, address registryAddress, address operator)
        external
        returns (bytes32 threatId)
    {
        require(block.chainid == 31337, "MOCK smoke test is local-only");
        MockVerifier verifier = MockVerifier(verifierAddress);
        ThreatRegistry registry = ThreatRegistry(registryAddress);
        bytes32 hash = keccak256("SAMPLE voiceprint fixture; no model output");
        bytes memory proof = bytes("MOCK_PROOF fixture v1");
        uint256[] memory inputs = new uint256[](8);
        inputs[0] = 1;
        inputs[1] = 9000;
        inputs[2] = uint256(hash) >> 128;
        inputs[3] = uint128(uint256(hash));
        inputs[4] = block.chainid;
        inputs[5] = uint160(registryAddress);
        inputs[6] = uint160(operator);
        inputs[7] = 1;
        VM.startBroadcast(operator);
        verifier.setFixture(proof, inputs, true);
        threatId = registry.registerThreat(hash, 9000, 1, proof, inputs);
        require(threatId == keccak256(abi.encode(block.chainid, registryAddress, operator, uint256(1))), "ID mismatch");
        ThreatRegistry.ThreatRecord memory record = registry.getThreat(threatId);
        require(record.voiceprintHash == hash && record.riskScore == 9000, "record mismatch");
        require(keccak256(record.zkProof) == keccak256(proof), "proof not stored");
        require(!registry.isBlacklisted(registry.TEST_PHONE_KEY()), "automatic promotion");
        registry.promoteToBlacklist(threatId, registry.TEST_PHONE_KEY());
        require(registry.isBlacklisted(registry.TEST_PHONE_KEY()), "promotion missing");
        VM.stopBroadcast();
    }
}
