// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

/// @notice Normalized application interface, NOT an assumed EZKL generated ABI.
interface IProofVerifier {
    function verifyProof(bytes calldata proof, uint256[] calldata publicInputs) external view returns (bool);
}
