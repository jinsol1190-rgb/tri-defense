# Contracts P0 development report

- Date: 2026-09-13.
- Scope: **requested contracts-only local P0 complete**; project-wide specification P0 remains **IN PROGRESS**.
- Branch: `codex/contracts-p0`; base commit `90ca088`. This report describes working-tree changes, not a published release.
- Read order: root `AGENTS.md`, `README.md`, `docs/implementation-specification.md`, `docs/architecture-review.md`.
- Authority: [Implementation Specification (Codex)](../../docs/implementation-specification.md), especially §§2, 4–6, 9–11, 13 and 15.
- Changes are confined to `contracts/`. Existing Android, README and root documentation changes were preserved. No backend, Android, dashboard, AI, Shared or proposal changes were made in this task.

## 1. What changed and why

The legacy Registry stored registrar claims using audioHash, an 8-bit score on a 0–100 scale, caller-supplied IDs/timestamps, and SUSPECTED status. It had no verifier and prohibited multiple incidents sharing a hash. That ABI did not satisfy the source specification.

Before editing, the migration was listed to the user: replace the data model, add fixed verification and bound submission context, separate number promotion, and provide explicitly local mock deployment evidence.

| Area | Current result | Classification |
| --- | --- | --- |
| ThreatRegistry | Deterministic IDs, VoiceprintHash, 0–10000 score, chain time, full proof, append-only storage, replay protection, reads/events | REAL contract mechanics |
| VerifierAdapter | Immutable normalized target and circuit ID, exact envelope validation, static call, fail-closed response handling | REAL adapter mechanics |
| MockVerifier | Administrator-controlled exact fixture admission/revocation; unknown fixtures fail | MOCK_PROOF; no cryptography |
| Number promotion | Separate authority transaction for existing incident and deployment-approved synthetic number/evidence | SAMPLE local policy |
| Deployment | Three contracts deployed in order; fixture admission, registration, promotion, receipts and live reads | SAMPLE / MOCK_PROOF |
| ABI | Four deterministic ABI JSON files plus drift-check command | REAL generated artifacts |

The old constructor, register function, record fields and event ABI are intentionally replaced. No compatibility adapter falsely treats old unverified claims as verified threats. Existing external deployments would require an explicit migration; none was performed. The unchanged backend DTO and historical root reports are still incompatible with the new ABI.

## 2. Updated module architecture

```text
submission (voiceprintHash, riskScore, nonce, proof, publicInputs)
    -> ThreatRegistry: bounds + submission deduplication
    -> immutable VerifierAdapter: envelope/circuit check
    -> immutable normalized IProofVerifier target
         local P0: MockVerifier exact fixture lookup
    -> ThreatRegistry: field/context binding + configured threshold
    -> append-only ThreatRecord + ThreatRegistered event

separate local promotion authority transaction
    + existing threat
    + constructor-approved synthetic phoneKey and evidence commitment
    -> blacklistedPhoneKeys + BlacklistPromoted event
```

Adapter code resides under `contracts/src/` so the existing root Foundry configuration compiles it. `contracts/verifier/` remains a documentation pointer. No runtime dependency on another repository module is introduced. Existing source/test/script build roots and Solidity configuration remain unchanged.

A submitting account is not a fixed registrar: any account may submit, but its own account/nonce context must match the verified public input. A future Smart Account should occupy that account field. Relayers cannot substitute an end-user identity without an explicitly compatible circuit/submission design.

## 3. Registry data and submission policy

The stored tuple is `(bytes32 threatId, bytes32 voiceprintHash, uint16 riskScore, uint64 registeredAt, bytes zkProof)`.

- ID: `keccak256(abi.encode(block.chainid, address(registry), msg.sender, nonce))`.
- Score range: 0–10000. Registration additionally requires the immutable configured minimum, inclusive. Local deployment uses **SAMPLE 7000**, not an approved/calibrated model threshold.
- Zero VoiceprintHash is rejected. This check does not establish how the hash was produced.
- Time: `block.timestamp`; overflow beyond uint64 rejects rather than truncates.
- Full supplied proof bytes are stored only after verification and binding checks. Tests cover short and 128-byte multi-slot proofs.
- One account/context nonce can create one record. Retry reverts with its deterministic existing ID; failed submissions do not consume it. Backend idempotent response handling remains separate future work.
- Multiple incidents may share a VoiceprintHash. There is no global hash-uniqueness restriction or record-update method.
- Registration does not contain a number or phoneKey and does not change the blacklist.
- Errors before final writes leave no committed record, consumed submission or contract event.

## 4. P0 normalized verifier envelope

This is an explicit **local development binding layout**, not an inferred EZKL ABI or an approved cross-module schema. The application interface is `verifyProof(bytes proof, uint256[] publicInputs) external view returns (bool)`.

| Index | Meaning | Required representation |
| --- | --- | --- |
| 0 | Circuit/envelope identifier | Equals immutable Adapter CIRCUIT_ID; local SAMPLE value 1 |
| 1 | RiskScore | Exact registered integer, 0–10000 |
| 2 | VoiceprintHash high limb | `uint256(voiceprintHash) >> 128` |
| 3 | VoiceprintHash low limb | `uint128(uint256(voiceprintHash))` |
| 4 | Chain domain | `block.chainid` |
| 5 | Registry domain | Zero-extended uint160 Registry address |
| 6 | Submitting account | Zero-extended uint160 `msg.sender` |
| 7 | Submission nonce | Exact uint256 nonce |

Exactly eight words and nonempty proof bytes are required. The adapter accepts only a successful static call returning precisely 32 bytes containing canonical boolean true. Revert, false, missing/extra return bytes, noncanonical true, unsupported circuit, wrong input count and attempted target state writes fail closed. Callers cannot choose the target or circuit per submission, and there are no target-upgrade setters.

Registry independently compares every registered field and domain after verification. Tests include a deliberately permissive **test-only response fixture** to show that field and cross-chain rejection do not rely solely on MockVerifier behavior. That response fixture is not included in deployment or exported ABIs.

MockVerifier stores `keccak256(abi.encode(proof, publicInputs)) -> accepted` under its immutable administrator. This is an exact fixture lookup, **not a proof system**. Its local chain guard is an additional guardrail, not an identity/security guarantee for arbitrary networks. The provided runner only starts and uses its own loopback node.

### Real verifier handoff still required

The actual generated verifier, model/settings/key manifest and public-output encoding do not exist in the repository. An approved artifact-specific wrapper must be implemented after inspecting them. It must cryptographically bind score, VoiceprintHash and submission context; merely passing this array beside a proof is insufficient. Circuit/field constraints, nonce encoding, output quantization and hash limb conversion must be validated against actual artifacts. The current uint256 envelope does not claim that every value fits a particular proof system's scalar field.

Out-of-circuit preprocessing or voiceprint generation is not proved by this P0 code. Neither a real computation proof nor a mock establishes crime, audio provenance, AI accuracy or phone ownership. Do not deploy an arbitrary proxy or caller-selected target and describe it as an approved fixed circuit.

## 5. Local synthetic number policy

`promoteToBlacklist(threatId, phoneKey)` preserves the specification's separate logical function. It requires:

1. Local chain ID 31337.
2. The immutable configured promotion authority.
3. An existing registered incident.
4. The single synthetic key approved at construction, with a nonzero evidence commitment also fixed at construction.
5. A key not already promoted.

The local deployment uses:

- Synthetic canonical E.164 text: `+12025550123`, never dialed or contacted.
- phoneKey: `keccak256` of that exact UTF-8 string.
- Evidence commitment: `keccak256("SAMPLE local number fixture v1")`.
- Evidence meaning: a **declared synthetic fixture**, not evidence about an actual subscriber or actual malicious call.
- Authority: local unlocked operator `0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266`; recorded in the deployment manifest.
- The separate signed promotion transaction is that test authority's attestation linking the approved fixture to the selected incident. The contract enforces the configured key/evidence presence and authority; it does not independently validate an evidence preimage or investigate number ownership.

All other phone keys, including a supplied spoofed/unapproved key, are rejected. General registration has no phone field. Constructor configuration is trusted test setup, not a general number-verification API. Real-number validation, multiple-number policy, correction/revocation and production authority remain unresolved and are not silently implemented. Local promotion is disabled on other chain IDs.

## 6. How to build, run and test

From repository root:

```sh
.tools/forge build
.tools/forge fmt --check
.tools/forge test -vv
python3 contracts/script/export_abi.py
python3 contracts/script/export_abi.py --check
python3 contracts/script/local_demo.py
```

Use `forge` on PATH if `.tools/forge` is absent. Required tools are Foundry 1.8.1 (forge/cast/anvil), Solidity 0.8.24, and Python 3.9+. No OpenZeppelin, forge-std, web3.py or other new package dependency is needed. The root Foundry config already fixes Paris EVM, optimizer enabled/200 runs, and 256 runs per fuzz test.

`local_demo.py` builds and exports ABI, owns a temporary loopback-only Anvil, broadcasts sequentially, validates live receipt/nonce/address/event associations, checks the resulting record and blacklist, writes evidence, and stops its node even on failure. It does not connect to any external RPC. Local script simulation is followed by broadcast and independent confirmed-chain reads.

## 7. Verification results

Executed on 2026-09-13 in this workspace:

| Check | Result |
| --- | --- |
| Solidity compilation and deployment script compilation | PASS, Solidity 0.8.24 |
| Foundry tests | **29 passed, 0 failed, 0 skipped** |
| Fuzz tests | 3 tests × 256 cases: record round-trip, out-of-range score, unauthorized promotion |
| Solidity formatting | PASS |
| ABI generation and drift check | PASS, 4 JSON files |
| Local deployment and transaction smoke test | PASS, all confirmed receipts successful |
| Live `getThreat` / `isBlacklisted` reads | PASS |
| Git whitespace check | PASS |

Coverage includes unknown/changed/empty proof; altered public input; all seven data/context mismatches even with fixture acceptance; unsupported circuit and input counts; duplicates preserving prior records; repeated voiceprints; cross-account, cross-registry and cross-chain reuse; score/threshold boundaries; missing/zero data; full proof preservation; timestamp overflow; exact registration/promotion events; failed-call no-state/no-log checks; unauthorized/unapproved/missing-incident/nonlocal promotions; adapter malformed/reverting/state-writing targets; mock configuration/revocation; and deployment-script public-network rejection.

The first event assertion failed because fixture configuration was placed after Foundry's next-call event expectation; moving fixture setup before the expectation fixed the test. Live deployment inspection also exposed mismatched transaction/receipt associations during concurrent unlocked broadcasts. The runner now uses `--slow` and verifies the chain's transaction nonce, deployment receipt address and event signatures; the repeated run passed. No failure was ignored.

Build lint emits advisory notes/warnings (including intentional static calls, explicit narrowing casts and test fixture patterns); successful compilation is not a claim of a warning-free security audit. This is a tested P0 implementation, not a third-party contract audit.

Runtime bytecode sizes: ThreatRegistry 3,725 bytes; VerifierAdapter 1,044 bytes; MockVerifier 1,086 bytes, all below the reported deployment size limit.

## 8. Local deployment evidence and cost

The reproducible [local manifest](../deployments/local.json) is authoritative for this run's timestamps, hashes, addresses, source/runtime checksums and raw events. [Generated ABIs](../abi/) are linked from each deployed contract entry.

| Contract | Local address | Deployment block | Deployment gas |
| --- | --- | --- | --- |
| MockVerifier | `0x5fbdb2315678afecb367f032d93f642f64180aa3` | 1 | 290,519 |
| VerifierAdapter | `0xe7f1725e7734ce288f8367e1bb143e90bb3f0512` | 2 | 283,641 |
| ThreatRegistry | `0x9fe46736679d2d9a65f0992f2272de9f3c7fa6e0` | 3 | 865,358 |

SAMPLE threatId: `0xa4aa218f504edf46cd26bb3762a226899464f2ef12471a80afee0fc96708e118`.

Registration of the **21-byte MOCK_PROOF** used **175,270 gas** at block 5. Separate synthetic promotion used **48,607 gas** at block 6. These are local measurements, not an estimate of full EZKL proof size/verification cost, L2 fees or KRW cost. The node was stopped; the manifest RPC is historical and has no explorer. No testnet address is claimed.

## 9. Remaining gaps and recommended implementation order

This task stops at contracts P0. It does not complete the specification's real zkML PoC or minimum cross-module ledger demonstration.

1. **Verification/Shared dependency handoff:** approve model, VoiceprintHash algorithm/version, score scale/threshold, circuit/key manifest and submission binding; generate a real small proof and exported verifier. No implementation was added to those modules here.
2. **Contracts real verifier integration:** inspect generated ABI; implement the artifact-specific normalized adapter/binding; run real proof success, tampering, wrong-circuit and cross-context failures; measure full proof storage and gas.
3. **Deployment decision:** select Base Sepolia or Arbitrum Sepolia with the team; define test authority/evidence policy; then add and validate the real-verifier deployment path and record chain ID/RPC/explorer/blocks/addresses/ABIs. No mock final-testnet deployment is supplied.
4. **Separately authorized integration:** align external DTOs and consumers with this ABI, connect submission/events to Room/dashboard, and implement ERC-4337. These are outside this contracts-only task.

Actual AI/model validity, generated proof verification, Android behavior, backend submission, dashboard synchronization, AA sponsorship and real-number policy remain unimplemented or unverified by this work. Root README and older architecture documents remain historical; only this module's README and report were updated to honor the module boundary.
