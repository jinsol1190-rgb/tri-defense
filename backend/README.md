# Backend — local P0

Status: **REAL API/relay mechanics; local transactions with MOCK_PROOF**. The requested Backend P0 is implemented. The project-wide real AI/zkML/AA integration is incomplete.

Source of truth: [implementation specification](../docs/implementation-specification.md), especially §§6–9. The [development report](docs/development-report.md) documents architecture, API behavior, tests and remaining work.

## Implemented API

| Method | Route | Behavior |
| --- | --- | --- |
| POST | `/v1/threat-submissions` | Validate transport fields, enforce durable idempotency, preflight and submit using the generated Registry ABI; return 202 |
| GET | `/v1/threat-submissions/{submissionId}` | Check actual receipt, registration event and record; return SUBMITTED / REGISTERED / FAILED |
| GET | `/v1/registry/threats/{threatId}` | Read the authoritative on-chain ThreatRecord |
| GET | `/v1/registry/events?cursor=...` | Read and decode Registry events with block-hash-anchored pagination |

Every response identifies `MOCK_PROOF` and `LOCAL_TRANSACTION`. Only localhost RPC on chain 31337 is supported. The backend never admits Mock Verifier fixtures, generates proof/AI outputs, promotes numbers or deploys contracts. Test/demo helpers prepare explicitly SAMPLE fixtures using existing contract artifacts.

## Setup and build

Use Python **3.11+** with OpenSSL (verified: **3.12.14**), and Foundry **1.8.1** for local integration tests/demo. Run from repository root:

```sh
python3.12 -m venv backend/.venv
backend/.venv/bin/python -m pip install -r backend/requirements.lock
backend/.venv/bin/python -m pip install -e backend
backend/.venv/bin/python -m pip check
backend/.venv/bin/python -m pip wheel --no-deps --wheel-dir backend/dist backend/
```

The backend has an isolated `pyproject.toml`; `web3==7.13.0` consumes the existing generated JSON ABI. `requirements.lock` pins the resolved dependency versions tested on Python 3.12/macOS arm64. HTTP serving and SQLite use Python's standard library. Initial dependency and Solidity compiler installation may require a network connection.

The system Apple Python 3.9/LibreSSL environment is not the backend environment. No change to root packaging or another module's environment is required.

## Run the complete local API demo

```sh
backend/.venv/bin/python backend/scripts/local_demo.py
```

The command starts its own loopback Anvil and API server, deploys **existing** contract artifacts as test setup, explicitly admits one SAMPLE/MOCK_PROOF fixture, performs all four API calls over HTTP, verifies their common threatId, and stops its processes. It changes no Solidity source or contracts deployment manifest.

To keep a disposable API and local chain available for manual requests:

```sh
backend/.venv/bin/python backend/scripts/local_demo.py --serve --port 8000
```

It prints an approved SAMPLE request body for `POST /v1/threat-submissions`. Stop with Ctrl-C. The demo database and chain are temporary and discarded on exit.

For an already running local deployment, use actual values from that live chain:

```sh
backend/.venv/bin/python -m tridefense_backend \
  --rpc-url "$LOCAL_RPC_URL" \
  --registry "$LOCAL_REGISTRY_ADDRESS" \
  --sender "$LOCAL_UNLOCKED_SENDER" \
  --deployment-block "$LOCAL_REGISTRY_DEPLOYMENT_BLOCK" \
  --abi-dir contracts/abi \
  --database backend/submissions.sqlite3
```

These shell variables are deployment-specific inputs, not bundled credentials. Do not reuse the stopped ephemeral RPC from `contracts/deployments/local.json`. This mode preserves SQLite submissions across process restarts, provided chain/Registry/sender identity matches. The API always binds to `127.0.0.1`; the unlocked sender is a trusted local development account, not a user-selected request field.

## Test

```sh
backend/.venv/bin/python -m unittest discover -s backend/tests -v
```

Expected: **21 passing Backend tests**, with actual Anvil/contract calls and an HTTP listener test. Missing Foundry is a test failure, not a silent skip. Tests include proof rejection, idempotency/restart, pending and reverted transactions, receipt/event integrity, RPC failure, pagination and reorganization detection.

## Structure and limitations

```text
backend/
  tridefense_backend/    API, transport schema, ABI client, SQLite submission service, CLI
  tests/                real local-chain integration tests and SAMPLE setup
  scripts/local_demo.py HTTP demo / disposable development server
  docs/development-report.md
  pyproject.toml
  requirements.lock
  deepvoice/            preserved legacy PoC models and unimplemented analysis stubs
```

The new service never imports legacy `deepvoice/` analysis stubs or its obsolete wire model. Legacy root tests remain historical checks, not evidence of current ABI compatibility.

P0 is single-process, local-only and unauthenticated; it is not a public service. There is no AI, Android, dashboard, new contract, proof generation, ERC-4337, promotion API or real-testnet support. Ambiguous broadcasts remain SUBMITTED with `BROADCAST_UNCERTAIN` and are never automatically resent. Full retry/recovery and production authentication remain later work.

Next: integrate the separately approved real verifier/submission contract and recovery/authentication design before expanding beyond this local P0 boundary.

## Integration P0

The existing runtime is used unchanged by the [Android → Registry → Dashboard MOCK integration](../integration/README.md). See the [integration report](../docs/integration-p0-report.md) for actual device/browser verification and remaining blockers.
