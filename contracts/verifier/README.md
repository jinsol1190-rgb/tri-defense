# Verifier location

The compiled **REAL adapter mechanics** are in [`src/VerifierAdapter.sol`](../src/VerifierAdapter.sol), with the normalized [`IProofVerifier`](../src/interfaces/IProofVerifier.sol) interface. This directory remains a documentation pointer; Foundry compiles `contracts/src/`.

[`MockVerifier`](../src/mocks/MockVerifier.sol) is **MOCK_PROOF**, local-only fixture lookup, not cryptographic verification. The actual EZKL-generated verifier, verification key, manifest and ABI are still missing. See the [binding and limitations report](../docs/development-report.md).
