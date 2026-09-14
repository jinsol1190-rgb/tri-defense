// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {ThreatRegistry} from "../src/ThreatRegistry.sol";
import {VerifierAdapter} from "../src/VerifierAdapter.sol";
import {MockVerifier} from "../src/mocks/MockVerifier.sol";

interface ScriptVm {
    function startBroadcast(address signer) external;
    function stopBroadcast() external;
}

/// @notice SAMPLE local deployment only. No private keys or public-network deployment path.
contract DeployLocal {
    ScriptVm private constant VM = ScriptVm(address(uint160(uint256(keccak256("hevm cheat code")))));

    function run(address operator)
        external
        returns (MockVerifier verifier, VerifierAdapter adapter, ThreatRegistry registry)
    {
        require(block.chainid == 31337, "MOCK deployment is local-only");
        require(operator != address(0), "operator required");
        VM.startBroadcast(operator);
        verifier = new MockVerifier(operator);
        adapter = new VerifierAdapter(address(verifier), 1); // SAMPLE circuit envelope v1.
        registry = new ThreatRegistry(
            adapter,
            7000, // SAMPLE threshold, NOT a calibrated model policy.
            operator,
            keccak256("+12025550123"), // Synthetic fixture only; never dialed.
            keccak256("SAMPLE local number fixture v1")
        );
        VM.stopBroadcast();
    }
}
