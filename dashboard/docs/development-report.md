# Dashboard P0 development report

Date: 2026-09-13. Branch: `codex/dashboard-p0`; base commit `90ca088`. Describes working-tree changes, not a published release.

**Status: Dashboard-only P0 complete.** All changes are inside `dashboard/`. Existing Backend, Contracts, Android, root README and architecture documents were preserved. No implementation beyond Dashboard P0 was performed.

## Documents and API inspected

Read root AGENTS.md, README.md, `docs/implementation-specification.md`, `docs/architecture-review.md`, Backend README and actual API/client/submission source. The implementation specification takes priority over historical scaffold descriptions.

Backend exposes GET event pages, GET a threat by ID, GET a submission by ID, and POST submission. Dashboard consumes only the three GET routes. The current Backend returns local chain data with `transactionMode: LOCAL_TRANSACTION` and `proofMode: MOCK_PROOF`.

The absence of a Registry list endpoint or WebSocket was identified before implementation. Registry view is therefore a session projection of loaded ThreatRegistered events, and “stream” means cursor-based polling, not push delivery. No new Backend endpoint or CORS change was introduced.

## What changed

| Responsibility | Implementation | Classification |
| --- | --- | --- |
| Registry view | Loaded registration rows, chain block, score, selection, direct Threat ID lookup | REAL view mechanics |
| Event stream | Three-second cursor polling, exact event identity, deduplication, chronological ordering and expanded payload | REAL Backend reads / explicit MOCK fixture alternative |
| Threat detail | GET existing record; full identifiers, VoiceprintHash, score, registeredAt, Registry address and expandable proof | REAL Backend response or labeled SAMPLE fixture |
| Submission status | User-entered existing submissionId, periodic status/txHash/error read | REAL Backend response or labeled SAMPLE fixture |
| Source controls | Explicit Backend/Mock selector; changing source clears all previous state | REAL UI behavior |
| Proof assurance | Backend transport and proof mode shown independently | No real-proof claim inferred from API connectivity |
| Failure handling | Timeout, malformed response, missing data, outage and reorg states | REAL client behavior |

No write method exists in the Dashboard API client. The UI does not submit threats, admit verifier fixtures, promote numbers, infer risk, generate proofs or connect directly to a blockchain.

## Architecture and folder mapping

The existing `dashboard/` is retained as the specification's frontend equivalent. No duplicate frontend module was created.

```text
React views
  -> read-only client interface
       Backend mode: same-origin GET /v1 through Vite local proxy
       Mock mode: explicit in-memory SAMPLE fixtures
  -> polling hooks with request cancellation and cleanup
  -> session event projection / selected threat / submission status
```

```text
dashboard/
  src/App.jsx          Registry, events, detail, status and source controls
  src/api.js           Existing route calls, response validation, identity/score formatting
  src/hooks.js         Sequential polling, cancellation, source/reorg reset behavior
  src/mock.js          Explicit SAMPLE fixtures only
  src/main.jsx         React entry
  src/styles.css       Responsive styles; no external image/font dependency
  tests/               Vitest + Testing Library tests
  docs/                Development report and browser evidence
  index.html
  vite.config.js       Local Backend proxy, React and test configuration
  .env.example
  package.json
  pnpm-lock.yaml
  pnpm-workspace.yaml
```

The client validates required response fields, preserves large block/log/time identifiers as decimal strings, and uses BigInt only for ordering. Threat/submission responses must match the requested ID. The full proof is displayed as bytes supplied by the Backend, never interpreted as successful cryptographic verification.

React review followed the available React best-practices skill: stable client instances, independent lookup/feed requests, derived lists rather than duplicate business state, effect cleanup, AbortController cancellation, stale-result protection, stable event keys, labeled inputs, semantic regions/tables, keyboard controls and responsive overflow handling. No state library or blockchain SDK is needed.

## Provenance policy

- Backend mode starts as **Connecting**, not a claimed successful connection.
- Successful reads show **REAL Backend response** and the separate Backend-reported proof/transaction modes.
- The current local Backend remains visibly **MOCK_PROOF**, including when it reports REGISTERED.
- Mock mode shows **MOCK dataset**, **MOCK · Sample fixtures**, **MOCK_PROOF**, and a SAMPLE disclaimer covering records, hashes, proof bytes and statuses.
- Backend failure never switches data sources or invents records.
- RiskScore is rendered on the specified 0–100 scale from the 0–10000 integer. The detail view explicitly says this is not a verified crime probability.
- No invented attacker geography, protection totals, detection accuracy or proof performance metrics are shown.

Fixture IDs and bytes are illustrative strings for display testing only. The three Mock submission IDs exercise registered, pending and failed presentation without simulating actual inference or chain execution.

## Polling and recovery

Requests time out after ten seconds. Each polling cycle schedules the next request after completion, avoiding overlapping requests within that cycle. Feed, detail and submission lookups are independent. Detail/status polling continues after a success so later failures or state changes are visible.

Events are deduplicated by chainId/txHash/logIndex. The Backend's cursor is opaque and URL-encoded unchanged. The displayed “through block” is the last loaded Backend range, and the count is explicitly loaded registrations, not an authoritative chain total. Each poll fetches one Backend page; deep histories may take multiple cycles to catch up.

A Backend CURSOR_REORG, CHAIN_CHANGED or INVALID_CURSOR response clears event state, detail selection and tracked submission UI, then starts again without a cursor. A changed chain/Registry namespace also resets the projection. Ordinary outages retain old events with a prominent stale warning; detail/status read failures remove the prior successful response. Pending requests from an old ID/source cannot overwrite the current view.

There is no persistent browser cache, full reconciliation database, websocket or finality policy. Session memory grows with loaded events; large-history pagination/virtualization is later work, not a hidden P0 completeness claim.

## Build and dependencies

Verified toolchain:

- Node 24.19.0; declared Node requirement 22.12+.
- pnpm 11.19.0.
- React / React DOM 19.3.0.
- Vite 8.3.0; React plugin 6.1.1.
- Vitest 5.0.0; Testing Library React 16.3.3; jsdom 30.0.1.
- agent-browser 0.37.1 with Chrome 153.0.8010.36 for browser QA.

Versions and resolved dependencies are pinned. Setup initially required approving the agent-browser package's installation script; the scoped permission is recorded in dashboard/pnpm-workspace.yaml. No user approval was needed for the authorized local tooling setup.

Vite's documented [proxy configuration](https://vite.dev/config/server-options.html#server-proxy) allows same-origin local API requests while preserving the Backend. [Vite build documentation](https://vite.dev/guide/build.html) describes the static build boundary; a deployed static bundle still needs a separately configured API reverse proxy.

Commands from dashboard/:

```sh
pnpm install --frozen-lockfile
pnpm test
pnpm build
pnpm dev
```

See the [module README](../README.md) for existing Backend startup, custom local proxy target and `pnpm preview`. There are no runtime secrets or browser RPC keys.

## Test evidence

| Check | Result |
| --- | --- |
| Vitest / Testing Library | **17 passed**, 0 failed |
| Production build | PASS, 19 modules transformed |
| JS bundle | 234.80 kB; gzip 73.45 kB |
| CSS bundle | 6.01 kB; gzip 2.07 kB |
| Browser initial load | PASS, meaningful content, no Vite overlay or unexpected browser errors |
| Actual Backend integration | PASS through the Vite proxy |
| Explicit Mock mode | PASS; no real-proof claim |
| Mobile 390 × 844 | PASS; document width equals viewport width after correction |

Automated tests cover existing route paths and cursor encoding; read-only methods; Backend errors; malformed response rejection; response-ID mismatch; unknown/prototype-named Mock IDs; transport failure/invalid JSON; timeout; event deduplication and large-number ordering; cursor continuation, outage retention and reorg clearing; timer cleanup; obsolete lookup-response rejection; removal of unavailable status; no automatic Mock fallback; independent REAL transport/MOCK proof labels; fixture selection/detail/proof view; pending status; source reset; invalid/missing threat IDs.

The initial detail test matched a hidden duplicate hash in an expandable event payload; scoping the assertion to the Threat detail region corrected the selector. Mobile QA found a four-pixel overflow caused by grid item minimum sizing; allowing the columns to shrink resolved it. Final tests and build passed after corrections.

## Actual Backend/browser verification

Started the existing `backend/scripts/local_demo.py --serve --port 8009` as test setup without changing its source. It deployed existing contracts on its own Anvil and printed an explicitly approved SAMPLE/MOCK_PROOF request. Submitted that request through the existing Backend API outside the Dashboard. Then opened Vite through agent-browser and verified:

1. Registry/event counts advanced from zero to one through polling.
2. Selecting the registration fetched its Backend detail, full VoiceprintHash and stored 29-byte Mock proof.
3. Entering the actual submissionId displayed Backend-reported REGISTERED, transaction hash and **MOCK_PROOF** together.
4. Registry, event, detail and status shared threatId `0xa4aa218f504edf46cd26bb3762a226899464f2ef12471a80afee0fc96708e118`.
5. Switching to Mock fixtures cleared previous selections and displayed the SAMPLE warning.
6. Mobile layout was inspected; no horizontal document overflow remained.
7. Stopping the owned test chain produced DEPENDENCY_UNAVAILABLE and a visible stale-event warning, without switching to Mock data.

Owned test Backend, Anvil, browser and Vite processes were stopped after verification.

Evidence: [Backend desktop view](backend-view.png), [Mock mobile view](mock-mobile.png). These screenshots contain local synthetic test data only; they are not public-testnet or real-AI evidence.

## Remaining gaps and next suggested work

The Backend has no collection endpoint for submissions/records and no WebSocket, so users enter an existing submissionId and Registry rows derive from loaded events. No Backend extensions were made to hide these limitations.

A real API connection does not establish real AI or zkML verification. Current Backend mode is local-only and mock-proof-backed. No APK, Android integration, wallet, direct chain client, proof generator, transaction submission, promotion workflow, hosting or production authentication was implemented.

Next recommended task: validate the approved real-proof Backend response contract when available, then separately authorize production proxy/authentication and robust large-history synchronization work. Stop point is **Dashboard P0**.
