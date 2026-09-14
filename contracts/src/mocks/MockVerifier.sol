// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {IProofVerifier} from "../interfaces/IProofVerifier.sol";

/// @notice MOCK_PROOF: local fixture lookup only. Performs NO cryptographic verification.
/// @dev Denies unconfigured fixtures. Never deploy on a public network.
contract MockVerifier is IProofVerifier {
    uint256 public constant LOCAL_CHAIN_ID = 31337;
    address public immutable FIXTURE_ADMIN;
    mapping(bytes32 => bool) private acceptedFixtures;

    error LocalOnly();
    error Unauthorized();
    error InvalidFixture();

    constructor(address fixtureAdmin) {
        if (block.chainid != LOCAL_CHAIN_ID) revert LocalOnly();
        if (fixtureAdmin == address(0)) revert InvalidFixture();
        FIXTURE_ADMIN = fixtureAdmin;
    }

    function setFixture(bytes calldata proof, uint256[] calldata publicInputs, bool accepted) external {
        if (block.chainid != LOCAL_CHAIN_ID) revert LocalOnly();
        if (msg.sender != FIXTURE_ADMIN) revert Unauthorized();
        if (proof.length == 0) revert InvalidFixture();
        acceptedFixtures[keccak256(abi.encode(proof, publicInputs))] = accepted;
    }

    function verifyProof(bytes calldata proof, uint256[] calldata publicInputs) external view returns (bool) {
        return block.chainid == LOCAL_CHAIN_ID && acceptedFixtures[keccak256(abi.encode(proof, publicInputs))];
    }
}
