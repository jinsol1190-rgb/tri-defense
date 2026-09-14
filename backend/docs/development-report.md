# Backend P0 development report

Date: 2026-09-13. Branch: `codex/backend-p0`, base commit `90ca088`; this report describes working-tree changes, not a released commit.

**Status: requested Backend-only local P0 complete.** Project-wide real proof and end-to-end P0 remain incomplete. Changes are confined to `backend/`; previous Android, contracts, root README and document changes are preserved.

Read: `AGENTS.md` → root `README.md` → `docs/implementation-specification.md` → `docs/architecture-review.md`; then generated ThreatRegistry, VerifierAdapter, MockVerifier and IProofVerifier ABIs. The [implementation specification](../../docs/implementation-specification.md) is authoritative. Historical root README/review statements describe the earlier scaffold.

## What changed

Implemented four specified APIs: submission, submission status, authoritative Registry read and Registry event read. Added strict request encoding, generated-ABI RPC calls, durable SQLite idempotency, local CLI, dependency configuration, tests and a reproducible HTTP demo.

No Solidity, AI inference, proof generation, Android, dashboard or other module implementation was added. The specification's separate promotion endpoint is outside this request's four responsibilities and remains absent. The Event API can decode a BlacklistPromoted event emitted by the existing contract after a separate authorized action.

| Component | Classification | Responsibility |
| --- | --- | --- |
| `api.py` | REAL | Four HTTP routes, strict JSON handling, safe error responses |
| `schema.py` | REAL | Transport validation only; no inference or proof approval |
| `registry.py` | REAL client mechanics / MOCK_PROOF configuration | Existing ABI encode/decode, contract preflight/submit, receipt/event/read checks |
| `service.py` | REAL | SQLite request identity, nonce reservations, receipt-backed status |
| `__main__.py` | REAL local development runner | Explicit live deployment configuration; loopback HTTP |
| `tests/support.py`, `scripts/local_demo.py` | SAMPLE / MOCK_PROOF | Owned Anvil, existing bytecode deployment and explicit fixture admission for testing only |

## Architecture and decisions

```text
HTTP request
  -> strict transport schema
  -> SQLite idempotency and nonce reservation
  -> generated ThreatRegistry ABI client
  -> existing registerThreat contract path / fixed Adapter / MockVerifier
  -> transaction hash (SUBMITTED)

status request
  -> canonical receipt + exact Registry event + on-chain stored fields/proof
  -> REGISTERED or FAILED; missing receipt stays SUBMITTED

Registry/Event requests
  -> live generated-ABI contract reads and event queries
  -> JSON with explicit local/mock metadata
```

The old `backend/deepvoice/` package has no API server and uses obsolete audioHash/0–100 score fields. The new package `backend/tridefense_backend/` isolates the current service without silently changing old semantics. It does not import legacy analysis placeholders. Root package/build configuration remains untouched.

The specification permits a minimal backend stack selection (§5). Python's WSGI development server and SQLite avoid an additional HTTP framework; web3.py handles the generated ABI and Ethereum RPC instead of handwritten Solidity ABI encoding or Registry rules. Python 3.11+ is required, and Python 3.12.14/OpenSSL 3.5.8 was used. `web3==7.13.0` is the declared runtime dependency, with tested transitive versions in `requirements.lock`. No AI/ONNX/EZKL dependency is imported.

Official references used for the client integration: [web3.py contract ABI/functions/events](https://web3py.readthedocs.io/en/v7.13.0/web3.contract.html), [transaction receipts and block queries](https://web3py.readthedocs.io/en/v7.13.0/web3.eth.html). Deployment code in tests consumes existing build output; it is not a new blockchain implementation.

## API contract

### POST /v1/threat-submissions

Requires `application/json` and exactly these fields:

| Field | Encoding |
| --- | --- |
| idempotencyKey | 1–128 ASCII letters/digits or `._:-` |
| nonce | Canonical decimal string, uint256 |
| voiceprintHash | Nonzero 32-byte `0x` hexadecimal string |
| riskScore | JSON integer 0–10000; booleans/floats/strings rejected |
| proof | Nonempty even-length `0x` bytes, at most 65,536 bytes for local transport |
| publicInputs | Exactly eight canonical decimal uint256 strings, as specified by the current Contracts P0 envelope |

Phone numbers/phoneKey, a caller-selected sender or verifier, extra fields and duplicate JSON keys are rejected. Request bodies are limited to 150,000 bytes. Hex letter case is normalized for idempotency; leading-zero decimal variants are rejected.

The backend does not invent public inputs, voiceprints or proofs. Inputs are passed to the existing contract without rewriting them. Preflight calls `registerThreat` using `eth_call` from the configured local sender. The contract checks actual target verification, field binding, threshold and reuse. The transaction then executes that same contract path; preflight is not a verification bypass.

A new successful submission returns HTTP **202** with `submissionId`, `status: SUBMITTED`, `txHash`, deterministic `threatId`, `errorCode: null`, `chainId`, `registryAddress`, `transactionMode: LOCAL_TRANSACTION`, and `proofMode: MOCK_PROOF`. The deterministic ID comes from the existing contract's preflight return value. Receiving an ID or a transaction hash does not prove registration.

### GET /v1/threat-submissions/{submissionId}

Returns HTTP 200 and the same status fields. Status is reconciled on each call, including after previously reporting REGISTERED:

- No receipt: SUBMITTED.
- Canonical receipt status 0: FAILED / TRANSACTION_REVERTED.
- Canonical successful receipt, exactly one matching ThreatRegistered event from the configured Registry, and matching stored voiceprint/score/full proof: REGISTERED.
- Successful unrelated receipt or mismatched event/record: FAILED with a specific integrity error.
- Receipt removed/reorganized: SUBMITTED, never a permanently cached registration claim.
- RPC unavailable: HTTP 503; no fabricated status success.

Local confirmation policy is inclusion in the currently canonical block. This is not a finality guarantee or a public-network confirmation policy. Backend starts at submission; it does not fake ANALYZED/PROVING/PROOF_READY states for work it never performs.

### GET /v1/registry/threats/{threatId}

Queries `exists` and `getThreat` through the generated ABI. Returns threatId, voiceprintHash, integer riskScore, decimal-string registeredAt, hex zkProof and explicit local/mock metadata. Missing records return 404. SQLite is not used as a substitute threat registry.

### GET /v1/registry/events?cursor=...

Decodes ThreatRegistered and BlacklistPromoted from the configured Registry. Each result contains chainId, decimal-string blockNumber/logIndex, blockHash, txHash, event name and ABI-decoded args. Consumers identify events by chainId/txHash/logIndex. Results sort by block/log position.

Pages span at most 100 complete blocks starting at the configured deployment block. The opaque base64url cursor contains version, chain/Registry namespace, next block and previous block hash. Whole-block pagination does not split a block's events. Empty polling returns an anchored continuation cursor. The response also includes throughBlock and local/mock metadata.

A changed anchor or rolled-back head returns **409 CURSOR_REORG** with restart guidance. Clients must discard/rebuild their derived chain-event cache and restart without a cursor. A detected reorganization during a query returns 503. This is bounded local detection/rebuild signaling, not a full persisted reorg reconciliation service. The cursor is a validated position token, not an authentication token or a signed authorization grant.

### HTTP errors

| Status | Examples |
| --- | --- |
| 400 | Malformed JSON, duplicate keys, unexpected fields/query, invalid score/bytes/uint/cursor |
| 404 | Missing submission/threat or unimplemented route |
| 409 | Same idempotency key with different payload, reserved nonce, changed event cursor anchor |
| 413 / 415 | Body size / unsupported content type |
| 422 | Existing Registry rejects proof, field binding, threshold, circuit or reuse; error decoded from generated ABI when available |
| 503 | RPC/storage dependency unavailable, changed chain, ambiguous broadcast |

Raw exception details/provider URLs/proof contents are not returned in error responses. No new promotion/authentication API is exposed by this task.

## Idempotency, persistence and recovery boundary

SQLite stores only submission tracking: key, nonce, normalized payload, expected threatId, txHash, state and error. Database identity includes chain/Registry/sender, deployment block hash and Registry runtime code hash; startup rejects reuse for another deployment/context. A reset local chain should use a fresh database.

Same key/same canonical payload returns the existing submission and refreshed status, without sending again. Same key/different payload returns 409. A different key using an already reserved contract nonce also returns 409. Per-process locking and SQLite uniqueness serialize local submissions.

The reservation is committed **before** sending. An RPC timeout or crash between send and txHash persistence can make broadcast outcome ambiguous. The row remains SUBMITTED with BROADCAST_UNCERTAIN; a repeat request does not resend. Initial ambiguous send returns 503 with submissionId, and subsequent lookup/retry exposes the retained state. P0 has no background recovery worker, signed-transaction journal or automatic rebroadcast. This conservative limitation prevents claiming exact-once delivery where it is not guaranteed.

A definitively failed tracked submission retains its key/nonce; P0 does not automatically retry it. A new independently authorized submission requires fresh context/proof. Operator reconciliation and safe retry semantics belong to subsequent work. SQLite is local unencrypted development storage; it contains supplied proof/payload but no raw audio or phone data from the submission API.

## Mock Verifier and scope boundary

The client follows Registry → Adapter → target addresses using the generated ABIs and confirms the configured target exposes the local Mock Verifier interface. It accepts only loopback HTTP RPC and chain 31337, and always labels results MOCK_PROOF. This is a local configuration guard, not an audit of arbitrary deployed bytecode.

The running API does **not** call setFixture, generate proof, directly write Registry state, promote a number or change on-chain policy. Unknown Mock fixtures are rejected by the existing contract. Only explicit SAMPLE test/demo setup admits the exact fixture before HTTP submission. Tests verify that absence of fixture admission returns 422 and emits no registration event.

One configured unlocked local account submits ordinary transactions. Clients must supply public inputs bound to that account, current Registry and chain. This is not per-user account authentication, a signed relay protocol, ERC-4337, a Bundler or Paymaster. The server binds to 127.0.0.1 and is a single-process development service, not a public or production HTTP deployment.

## How to run and test

From repository root with Python 3.12 and Foundry 1.8.1 available:

```sh
python3.12 -m venv backend/.venv
backend/.venv/bin/python -m pip install -r backend/requirements.lock
backend/.venv/bin/python -m pip install -e backend
backend/.venv/bin/python -m pip check
backend/.venv/bin/python -m pip wheel --no-deps --wheel-dir backend/dist backend/
backend/.venv/bin/python -m unittest discover -s backend/tests -v
backend/.venv/bin/python backend/scripts/local_demo.py
```

`--serve --port 8000` keeps the disposable demo running and prints its approved SAMPLE request. The [Backend README](../README.md) also documents persistent service startup against an existing live local deployment. The demo's chain/database are temporary; Ctrl-C stops owned processes. No stopped contracts manifest RPC is treated as a live deployment.

## Verification evidence

| Check | Result |
| --- | --- |
| Backend wheel build | PASS: tridefense_backend-0.1.0-py3-none-any.whl |
| Dependency check | PASS: no broken requirements |
| Python bytecode compilation | PASS |
| Backend unit/local-chain/API tests | **21 passed**, no skipped tests |
| Preserved legacy model tests | **11 passed**; historical model checks, not current ABI compatibility proof |
| Actual HTTP demo | PASS: POST → receipt-backed status → record → event share one threatId |
| Whitespace check | PASS |

Tests execute existing contracts on an owned Anvil. Cases cover API encoding/JSON/media/body errors; exact ABI field/event reads; rejected/tampered mock proofs; idempotency, nonce conflict and restart; pending versus confirmed state; mined revert and unrelated successful receipt; ambiguous broadcast without resend; RPC outage; missing data/routes; external promotion event decoding; event pagination; invalid cursor; same-height reorganization and head rollback; local RPC restriction; mismatched database context; and a real HTTP listener.

The initial system Python 3.9 attempt exposed old-pip editable-install limitations and a LibreSSL compatibility warning. The verified backend environment uses Python 3.12.14/OpenSSL 3.5.8 instead. During repeat tests, immediate-mining assumptions caused transient SUBMITTED results and nonce-count races; test setup now waits for actual receipts, and the HTTP demo polls status. The API correctly retains asynchronous semantics. All 21 tests passed after those fixes.

Test/demo fixture data is explicitly SAMPLE / MOCK_PROOF. No real model, zkML verification, public network, Android or dashboard behavior was tested. Build/test tooling writes ignored generated output; no contract source or other module was changed.

## Remaining gaps and exact next recommended work

1. Obtain the approved real verifier/public-input and account submission design from the separately authorized Verification/Shared/Contracts work. The current eight-word envelope and Mock proof are local development conventions.
2. In a subsequent Backend task, add durable signed-transaction recovery, explicit retry/drop policy and authentication/account binding. Do not widen the current local unlocked-account mode into a public service.
3. Add real-network confirmation/finality policy, persisted event recovery and reorg reconciliation, then the separately authorized promotion request and ERC-4337 relay paths.
4. Integrate external consumers only in their own tasks. No AI inference should be moved into Backend.

Stop point: **Backend P0 only**. Root README/architecture review and old `deepvoice` models remain historical; the new module README/report describe the current service without rewriting other modules or proposal documents.
