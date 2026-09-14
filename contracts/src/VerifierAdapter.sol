// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {IProofVerifier} from "./interfaces/IProofVerifier.sol";

/// @notice REAL fixed-target, fail-closed adapter using the documented P0 input envelope.
/// @dev A real generated verifier needs a separately reviewed ABI/binding wrapper.
contract VerifierAdapter is IProofVerifier {
    uint256 public constant PUBLIC_INPUT_COUNT = 8;
    address public immutable VERIFIER;
    uint256 public immutable CIRCUIT_ID;

    error InvalidConfiguration();

    constructor(address verifier, uint256 circuitId) {
        if (verifier.code.length == 0 || circuitId == 0) revert InvalidConfiguration();
        VERIFIER = verifier;
        CIRCUIT_ID = circuitId;
    }

    function verifyProof(bytes calldata proof, uint256[] calldata publicInputs) external view returns (bool) {
        if (proof.length == 0 || publicInputs.length != PUBLIC_INPUT_COUNT || publicInputs[0] != CIRCUIT_ID) {
            return false;
        }
        (bool success, bytes memory result) =
            VERIFIER.staticcall(abi.encodeCall(IProofVerifier.verifyProof, (proof, publicInputs)));
        if (!success || result.length != 32) return false;
        // Only canonical ABI true is accepted; malformed responses fail closed.
        return abi.decode(result, (uint256)) == 1;
    }
}
