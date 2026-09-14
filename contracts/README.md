# Contracts — local P0

Status: **requested contracts-only P0 complete using MOCK_PROOF**. Actual zkML verification and the specification's project-wide P0 remain incomplete.

The [implementation specification](../docs/implementation-specification.md), especially §§4–6 and 9, is authoritative. This module replaces the old unverified claim Registry ABI. It does not change other application modules.

| Component | Status | Behavior |
| --- | --- | --- |
| ThreatRegistry | REAL contract mechanics | Append-only records, score bounds, fixed verifier call, public-input binding, replay rejection, full proof storage, events and reads |
| VerifierAdapter | REAL adapter mechanics | Immutable normalized verifier target/circuit ID; rejects failure, malformed return data and unexpected input envelope |
| MockVerifier | MOCK_PROOF, local only | Administrator explicitly accepts exact proof/input fixtures; unknown fixtures fail; no cryptography or inference |
| Number promotion | SAMPLE policy, local only | Separate authority transaction; existing incident and constructor-approved synthetic number/evidence required |
| Deployment/demo | SAMPLE / MOCK_PROOF | Owned ephemeral Anvil, ordinary transactions, receipt and live-read checks |

## Build and test

From repository root, use Foundry **1.8.1** (`forge`, `cast`, `anvil`), Solidity **0.8.24** (selected by the existing `foundry.toml`), and Python **3.9+**. Solidity sources have no third-party dependencies; Python deployment/export scripts use only the standard library. Initial compiler/tool installation may require a network connection.

```sh
.tools/forge build
.tools/forge fmt --check
.tools/forge test -vv
python3 contracts/script/export_abi.py
python3 contracts/script/export_abi.py --check
python3 contracts/script/local_demo.py
```

If Foundry is installed on PATH, replace `.tools/forge` with `forge`. Python scripts discover either installation automatically. The demo builds first, exports ABIs, starts its own loopback-only Anvil, deploys, submits a labeled fixture, separately promotes the synthetic number, checks confirmed receipts and live reads, then stops its own node. No key, token, external RPC, or running server is required.

Generated ABIs are in [abi/](abi/). `--check` detects drift against current build artifacts; run the build first. [deployments/local.json](deployments/local.json) records addresses, blocks, transaction hashes, bytecode/source checksums, ABI paths, events and gas measurements. Its RPC and addresses refer to a stopped ephemeral chain, not a live service. Rerunning the demo regenerates this evidence on a fresh chain.

## Structure

```text
contracts/
  src/ThreatRegistry.sol
  src/VerifierAdapter.sol
  src/interfaces/IProofVerifier.sol
  src/mocks/MockVerifier.sol
  test/ThreatRegistry.t.sol
  test/VerifierAdapter.t.sol
  script/DeployLocal.s.sol
  script/SmokeLocal.s.sol
  script/local_demo.py
  script/export_abi.py
  abi/                           four generated JSON ABIs
  deployments/local.json         local run evidence
  docs/development-report.md     architecture, encoding, evidence, limitations
  verifier/README.md             pointer to compiled adapter location
```

## Current limitations and next task

Read the [development report](docs/development-report.md) before integrating. The eight-word input envelope, circuit ID `1`, risk threshold `7000`, voiceprint fixture and number evidence are **local development fixtures**, not approved model/circuit/number policies. A mock-backed record is not a cryptographically verified threat. No real proof, EZKL-generated ABI, ERC-4337, public testnet deployment, backend communication, Android synchronization or dashboard is implemented here.

Next: obtain the real generated verifier, verification-key/model manifest and approved public-input binding from the separate Verification work; inspect its actual ABI before connecting it. Public-network deployment remains pending those artifacts, target-network selection and number policy. Root README/architecture reviews and backend DTOs still describe historical states; this task updates only contracts documentation and intentionally does not migrate those modules.
