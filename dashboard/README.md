# Dashboard — React + Vite P0

Status: **REAL read-only Backend integration; explicit MOCK display fixtures**. Dashboard P0 is complete. A real API connection can still return `MOCK_PROOF`; the screen shows transport provenance and proof mode separately.

The [implementation specification](../docs/implementation-specification.md) is authoritative. See the [development report](docs/development-report.md) for decisions, test evidence and limits.

## Run

Requirements: Node **22.12+** (tested **24.19.0**) and pnpm **11.19.0**.

```sh
cd dashboard
pnpm install --frozen-lockfile
pnpm dev
```

Open `http://127.0.0.1:5173`. Backend mode is the default. For standalone UI exploration, explicitly select **Mock fixtures**. There is no automatic Mock fallback when the Backend is unavailable.

The Vite development/preview server proxies `/v1` to `http://127.0.0.1:8000`, so the existing Backend needs no CORS changes. To use another local Backend, copy `.env.example` to `.env.local`, set `DASHBOARD_BACKEND_URL`, and restart Vite. Only local HTTP targets are supported in this P0 configuration. `.env.local` is ignored.

From repository root, the existing disposable Backend environment can be started without modifying it:

```sh
backend/.venv/bin/python backend/scripts/local_demo.py --serve --port 8000
```

Follow Backend setup prerequisites first. It prints an approved SAMPLE/MOCK_PROOF submission body. Submit that body using the existing Backend API outside the Dashboard, then enter the returned submissionId in **Submission status**. Dashboard never creates a transaction. Registry entries appear when the corresponding registration event is read.

## Build and test

```sh
cd dashboard
pnpm test
pnpm build
pnpm preview
```

Expected: **17 passing tests**, build output in `dist/`. Dependencies are pinned in `package.json` and `pnpm-lock.yaml`. `pnpm-workspace.yaml` permits only the installed agent-browser package's setup script for browser QA.

A static deployment needs a same-origin `/v1` reverse proxy. Vite's development/preview proxy is not embedded in the static JavaScript bundle. No hosting or production deployment was added.

## Included views

- **Registry view:** registration events loaded in the current session; direct Threat ID lookup. This is not a total-count or list endpoint.
- **Event stream:** cursor-based polling every three seconds, deduplication by chainId/txHash/logIndex, expandable exact payloads.
- **Threat detail:** authoritative Backend record read, VoiceprintHash, score, chain timestamp, Registry address and full proof bytes.
- **Submission status:** supplied submissionId lookup and polling for SUBMITTED / REGISTERED / FAILED, txHash and errorCode.
- **Mock support:** isolated, static SAMPLE fixtures; sample-registered, sample-pending and sample-failed status IDs.
- **Provenance:** REAL Backend response versus MOCK fixtures, plus separately reported proof/transaction modes.

## Limits and next work

The Backend has no Registry list API, submission list API or WebSocket. This Dashboard uses only its existing GET routes. Data is session-local; reload/source switching clears it. Reorg signals clear events and selected details/status, then restart from the beginning. Outages mark retained events stale and clear unavailable detail/status responses.

No AI, Android, Backend or Contracts code was added or modified. No direct RPC, wallet, submission, proof generation, blacklist promotion, map or business logic is implemented. MOCK fixtures do not represent inference, real proofs or real transactions.

Next: verify future real-proof/API changes and separately authorize persistent synchronization or production deployment work. This task stops at Dashboard P0.

## Integration P0

The existing runtime is used unchanged by the [Android → Registry → Dashboard MOCK integration](../integration/README.md). See the [integration report](../docs/integration-p0-report.md) for actual device/browser verification and remaining blockers.
