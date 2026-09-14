# Tri-Defense Architecture Consistency Review

- Review date: 2026-09-13
- Status: **review complete; project P0 IN PROGRESS; P1 completion blocked**.
- Read order: `AGENTS.md` → `README.md` → `docs/implementation-specification.md`.
- Authority: [Implementation Specification (Codex)](implementation-specification.md), especially §§2–13. This report is a derived implementation roadmap, not a replacement specification.
- Scope: all seven requested modules, plus the independently required Verification module and repository-wide tooling. No implementation, dependency installation, configuration change, or runtime refactor was performed.
- Output: `docs/architecture-review.md` (the backslash before the extension in the request is interpreted as punctuation escaping).

## 1. Executive assessment

The repository has a working legacy Python data model and a Foundry claim-storage Registry, surrounded by documentation placeholders. It does not yet implement the specification's two-stage inference, real zkML proof, verified threat registration, conditional number promotion, or cross-user protection.

The previous preparation task completed useful development scaffolding, **not specification P0**. Specification §11 requires Android feasibility evidence, actual AI baseline results, a real proof PoC, and a minimum fixture → registration → event → Room/dashboard connection. Calling that entire connection P1 would incorrectly defer a P0 acceptance requirement.

Folder names, function names, and field choices are development defaults (§§2, 5). Existing equivalents may be reused if differences are documented. A different directory name alone is not an architecture failure. Conversely, a README in a folder does not implement the folder's responsibility.

### Module readiness

| Module | Current implementation | P0 satisfied? | P1 dependency blocked |
| --- | --- | --- | --- |
| Android | SAMPLE documentation only | No | On-device detection, protection, APK |
| AI | SAMPLE component folders only | No | Real detection, reproducible voiceprints, proof inputs |
| Verification | Entire root module absent | No | Proof generation, generated Verifier, result binding |
| Backend | REAL legacy model; failing analysis placeholders | No | Submission, status, event delivery, ERC-4337 relay |
| Contracts | REAL legacy claim Registry and tests | No | Validated registration, promotion, authoritative events |
| Dashboard | SAMPLE documentation only | No | P0 event display and P1 final demonstration |
| Shared | SAMPLE folders, no contracts or fixtures | No | Cross-language compatibility and integration |
| Docs | Specification plus historical reports | Partial; not sufficient | Reproducibility and release acceptance; not all independent development |

“Blocks P1” means the dependent integration or final acceptance cannot pass. It does not mean every independent module task must stop. Open feasibility decisions must remain visible rather than being silently assumed solved (§15).

## 2. Evidence and verification boundary

Inspected every application source file, module README, test, documentation file, package/build configuration, and workflow in the repository. Generated outputs and local tooling are not treated as application modules.

Key evidence:

- [Python model](../backend/deepvoice/models.py), [verification placeholders](../backend/deepvoice/verification/), [Python tests](../tests/test_models.py).
- [Registry](../contracts/src/ThreatRegistry.sol), [Foundry tests](../contracts/test/ThreatRegistry.t.sol), [Foundry configuration](../foundry.toml).
- [Packaging](../pyproject.toml), [Makefile](../Makefile), [CI](../.github/workflows/tests.yml).
- [Legacy architecture](architecture.md), [previous implementation report](implementation-report.md), [P0 preparation report](p0-preparation.md).

Fresh read-only checks in this review:

| Check | Result |
| --- | --- |
| `PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=backend python3 -B -m unittest discover -s tests -v` | 11 passed |
| `.tools/forge fmt --check` | Passed |
| `.venv/bin/python -m pip check` | No broken requirements |
| Package metadata in repository `.venv` | `librosa`, `tensorflow`, `onnx`, `ezkl`, `fastapi`, `web3` absent |

Previous P0 preparation recorded wheel build, fresh Solidity 0.8.24 compilation, and 24 passing Foundry tests, including four 256-run fuzz tests. These are historical results, not fresh compilation/EVM execution in this review. The existing tests verify the legacy contract, not compliance with the new specification. Remote CI, real devices, proof execution, and testnet deployment are not verified here.

Dependency absence below means absent from repository configuration/artifacts, with `.venv` checks where stated. It does not claim software is absent from every location on the developer's machine. Backend/frontend frameworks are intentionally not selected by this review; §5 permits a later minimal choice with a documented reason.

Review baseline: branch `codex/p0-development-scaffold`, HEAD `90ca088`. README changes and the untracked implementation specification already existed at review start. This task preserves them and adds only this report.

## 3. Current repository architecture

```text
android/                         README only
ai/                              README plus five component READMEs
  preprocessing/ lightweight/ precise/ voiceprint/ evaluation/
backend/deepvoice/               Python package; no service
  models.py
  verification/                  three functions raising NotImplementedError
contracts/
  src/ThreatRegistry.sol         legacy claim registration
  test/ThreatRegistry.t.sol
  script/                        README only
  verifier/                      README only, outside Foundry source root
dashboard/                       README only
shared/
  schemas/ dto/ constants/ api/   READMEs only
docs/
  implementation-specification.md
  architecture.md
  implementation-report.md
  p0-preparation.md
  architecture-review.md         this review
.github/workflows/tests.yml      one Python/Foundry job
tests/test_models.py
Makefile, pyproject.toml, foundry.toml, README.md, AGENTS.md, .gitignore
```

There is no root `verification/`, Android build, dashboard build, backend executable, shared schema validation, or runtime link between Python and Solidity.

Required ownership is: Android owns device inputs/UI/cache/screening and calls the specified submission/proof integrations; AI owns model work and reference analysis; Verification owns proof tooling; Backend owns relay/status/Registry synchronization, never inference; Contracts enforce verified registration and number promotion; Dashboard displays real state; Shared defines cross-module contracts. Android's specified Smart Account/UserOperation integration does not authorize duplicating Registry business rules in the app.

## 4. Android review

Specification: §§1, 3–5, 7, 10–14.

| Requested dimension | Finding |
| --- | --- |
| 1. Current state | `android/README.md` only, labeled SAMPLE. No Kotlin, APK, input adapter, permission configuration, Room, or screening service. |
| 2. Missing folders | `android/app/src/main/`, `app/src/test/`, `app/src/androidTest/`; audio, ai, screening, registry, ui responsibilities under main. Exact Kotlin package namespace is not prescribed. |
| 3. Missing interfaces | `LiveAudioSource`, `SampleAudioSource`, shared `AudioInput`, lightweight/precise result handling, asynchronous proof job connection, submission/status client, Registry-event-to-Room mapping, screening lookup, and separate warning/protection states. No arbitrary new signatures should be invented. |
| 4. Missing build configuration | Gradle wrapper, settings/root/app build files, Kotlin/Android plugin versions, SDK levels, manifest and service/permission declarations, unit/device test targets, APK build and CI job. |
| 5. Missing dependencies | Android SDK/JDK/Gradle toolchain configuration; Kotlin, TensorFlow Lite, Room declarations; device-side precise-model/proof integration selected through feasibility work. `CallScreeningService` is an Android platform API, not a separate package to install. |
| 6. Missing documentation | Device/OS matrix, role/permission behavior, actual audio capture evidence, LIVE/SAMPLE labeling, saved/private-number cases, local lookup timing, installation/testing instructions, eventual Smart Account key and sponsorship behavior. |
| 7. Satisfies P0? | No. No device input/role/permission investigation, synthetic test-number screening evidence, or minimum Room ledger connection exists. |
| 8. Blocks P1? | Yes: on-device detection/proof integration and User B protection. Actual capture/proof/matching feasibility is unverified, not demonstrated impossible. |
| 9. Recommended next task | After common contracts and build preparation, establish the Kotlin app and run the specified device/input/role/synthetic-number screening PoC. Record unavailable signals; do not replace device analysis with a server architecture. |

P0 exit evidence: runnable device build, verified input route or explicit SAMPLE limitation, tested screening role/permission responses, synthetic-number blocking, and later the same threatId in Room as the chain/dashboard. P1 must distinguish current-call warnings from subsequent registered-number blocking; a new number cannot be blocked using audio not yet acquired.

## 5. AI review

Specification: §§1, 3–5, 7, 11, 13, 15.

| Requested dimension | Finding |
| --- | --- |
| 1. Current state | Five correctly named responsibility folders with SAMPLE READMEs. No model, dataset, inference, preprocessing, voiceprint, export, or evaluation. Backend analysis placeholders are not AI implementation. |
| 2. Missing folders | `ai/tests/`. Existing preprocessing/lightweight/precise/voiceprint/evaluation folders are reusable; they lack contents. Model/data artifact locations beyond the specified verification model directory must be chosen and documented, not fabricated as mandatory paths. |
| 3. Missing interfaces | `analyzeLightweight(AudioInput)`, `analyzePrecise(AudioInput)`, `matchVoiceprint(AudioInput, knownVoiceprints)` and their §7 outputs. Missing shared session/version conventions, 0..10000 scores, watermark status, input error handling, unsupported matching behavior, and model/preprocessing manifests. `generateProof` belongs to Verification integration, not backend inference. |
| 4. Missing build configuration | Isolated AI dependency environment, reference inference/evaluation commands, lightweight export and precise ONNX export, small fixture tests and separate CI. Root setuptools discovers only backend. |
| 5. Missing dependencies | Specified Librosa and ONNX tooling; training/export dependencies supporting CNN and TensorFlow Lite. Exact model architecture/framework versions remain evaluation decisions. Dataset/model files, licenses, checksums, and reproducible manifests are missing. No requirement to install multiple competing training frameworks. |
| 6. Missing documentation | Dataset separation, licenses, input lengths/rates, retention of high-frequency signal, model/threshold selection evidence, watermark limitations, voiceprint extraction/quantization/serialization version, accuracy/false-positive/false-negative and repeatability reports. |
| 7. Satisfies P0? | No. Both baseline inferences, normal/synthetic test separation, preprocessing and voiceprint reproducibility evidence are absent. |
| 8. Blocks P1? | Yes: real risk UI, real proof witness, changed-number matching, and meaningful evaluation. |
| 9. Recommended next task | Build the isolated reference/evaluation environment and versioned normal/synthetic fixtures; then implement preprocessing and both baseline models before claiming any detection or matching performance. |

P0 evidence must distinguish repeated-byte hash equality from speaker matching across utterances/codecs/noise. Watermark absence must not become a HUMAN verdict. Unconfirmed thresholds and matching methods remain explicit decisions. Model status metadata must distinguish SAMPLE / MOCK / REAL_MODEL separately from proof metadata.

## 6. Verification review — required additional module

Although absent from the requested seven names, Verification is an independent responsibility in §§3–5 and an explicit P0 gate in §11. Omitting it would make the project roadmap incomplete.

| Requested dimension | Finding |
| --- | --- |
| 1. Current state | No root Verification module. `backend/deepvoice/verification/` describes artifact/watermark/provenance analysis; `contracts/verifier/` is only a README. Neither provides zkML tooling. |
| 2. Missing folders | `verification/models/`, `settings/`, `scripts/`, `tests/`. |
| 3. Missing interfaces | `generateProof(PreciseResult, witnessInput)` returning proof/publicInputs/circuitVersion/elapsedMs; ONNX-to-EZKL compile/witness/prove/verify steps; exported generated Verifier ABI/artifact handoff to Contracts; versioned score/voiceprint/submission-context binding. |
| 4. Missing build configuration | Isolated EZKL setup, reproducible commands, supported-operator checks, circuit/key generation, artifact validation and bounded test target. Device proof execution is unverified. |
| 5. Missing dependencies | ONNX/EZKL declarations and compatible versions; approved model/settings/keys/Verifier artifacts with manifests and checksums. Generated ABI must be inspected rather than guessed. |
| 6. Missing documentation | Proven computation boundaries, out-of-circuit preprocessing/voiceprint exclusions, public input encoding, approved circuit/key versions, witness provenance, device memory/time measurements and negative verification cases. |
| 7. Satisfies P0? | No. No real small precise-model proof or invalid-output rejection evidence. |
| 8. Blocks P1? | Yes: real generated Verifier, result-bound registration and device proof integration. |
| 9. Recommended next task | After a small precise baseline and schema versions exist, export ONNX and reproduce real EZKL proof success plus altered-output failure locally. Record binding gaps explicitly before integrating Registry registration. |

A locally successful proof does not establish Android proof-generation feasibility. A mock result or adjacent JSON field does not establish cryptographic binding. Complete binding and cross-context replay rejection are prerequisites for the final verified P1 path.

## 7. Backend review

Specification: §§3, 5–8, 10–13.

| Requested dimension | Finding |
| --- | --- |
| 1. Current state | Installable `deepvoice` Python model package, enums, argument conversion, and three explicitly failing legacy analysis placeholders. No server, endpoints, persistence, RPC, submission, or synchronization. |
| 2. Missing folders | Literal `backend/src/` and `backend/tests/` are absent. `backend/deepvoice/` and root `tests/` already provide source/test equivalents and can be reused with a recorded mapping; no mandatory rename. Actual submit/status/registry-sync responsibilities are absent everywhere. |
| 3. Missing interfaces | All five §8 endpoints: POST `/v1/threat-submissions`; GET `/v1/threat-submissions/{id}`; GET `/v1/registry/events?cursor=...`; GET `/v1/registry/threats/{id}`; POST `/v1/blacklist-promotions`. Missing idempotency, nonce/context handling, publicInputs validation, status/error contract, UserOperation relay, receipt confirmation and event cursor semantics. |
| 4. Missing build configuration | Existing Python wheel/unittest configuration works for the legacy package. Missing service entry point/runtime configuration, chosen framework dependencies, service/API test target and separate CI. |
| 5. Missing dependencies | HTTP service and Ethereum RPC/transaction/AA client capabilities are not configured; state/cursor persistence mechanism is undecided. Legacy docs mention FastAPI/SQLite/web3.py, but these are not mandated by the specification. FastAPI/web3 are absent in `.venv`; SQLite is not an automatically required pip package. |
| 6. Missing documentation | API schemas/status/errors; idempotency and nonce policy; client/public-input trust boundary; RPC/receipt/drop/retry recovery; cursor/reorganization behavior; test promotion authentication/evidence; Bundler/Paymaster configuration and limits. |
| 7. Satisfies P0? | No. Package preparation is partial progress, but the minimum ledger relay/query path is absent. |
| 8. Blocks P1? | Yes: reliable submissions, shared events, final AA relay and protection synchronization. |
| 9. Recommended next task | Reconcile model/status contracts with Shared first; after the local verified Registry ABI exists, implement the minimal submission/status/event-query connection, followed by the full §8 behavior and AA relay in P1. |

The legacy model uses audioHash, 0..100 riskScore, submitted timestamp, and SUSPECTED status. It is not the §6 wire contract. Existing processing enums are not the specified `ANALYZED → PROVING → PROOF_READY → SUBMITTED → REGISTERED`, with FAILED/error/retry metadata. Neither enum currently implements transitions.

Do not flesh out AI analysis under backend. During a separately authorized migration, move AI ownership to `ai/` or remove obsolete placeholders with their tests deliberately updated. Do not relabel those analysis functions as the new root zkML module.

## 8. Contracts review

Specification: §§4–6, 9–11, 13–14.

| Requested dimension | Finding |
| --- | --- |
| 1. Current state | One compiled/tested legacy Registry: immutable registrar access, field bounds, append-only claims, duplicate ID/audioHash rejection, lookup and events. No proof verification. |
| 2. Missing folders | `contracts/deployments/`. `src/`, `test/`, `script/` exist; script has only a README. `contracts/verifier/` is outside configured `contracts/src`; future adapter source must be in the build source tree or an explicitly configured dependency path. |
| 3. Missing interfaces | §9 `registerThreat(voiceprintHash, riskScore, nonce, proof, publicInputs)` and return value; compatible `getThreat`; `isBlacklisted`, `promoteToBlacklist`, `verifyProof`; compliant `ThreatRegistered` and `BlacklistPromoted`. Existing register/get/event names do not imply compatible ABI. |
| 4. Missing build configuration | Foundry source/test/script configuration and compiler pin already exist. Missing generated Verifier inclusion/configuration, local/testnet deployment commands and reproducible chainId/address/ABI/deployment-block artifacts. |
| 5. Missing dependencies | Actual EZKL-generated Verifier, approved verification key/circuit/public-input artifacts. No current third-party Solidity library is required; adding OpenZeppelin or forge-std is not automatically necessary. Target chain and AA integration details remain unselected. |
| 6. Missing documentation | §6 model/ABI migration, cryptographic binding, deterministic submission ID/replay policy, risk threshold evidence, proof storage cost, fixed Verifier trust, synthetic-number promotion authority/evidence, deployment manifest and rejection tests. |
| 7. Satisfies P0? | No. Legacy tests pass, but there is no compliant registration-to-event connection or real Verifier path. Local mocks are permitted only with explicit labeling and do not satisfy the real zkML PoC. |
| 8. Blocks P1? | Yes: registered records/events are not the required proof-validated source of truth. |
| 9. Recommended next task | Agree shared types/context binding and generated Verifier ABI, then migrate Registry/adapter and tests together; preserve rejection guarantees and keep number promotion separate from generic registration. |

### Material contract/model differences

| Concern | Existing code | Specification baseline |
| --- | --- | --- |
| Identity | `audioHash`, exact-byte input hash claim | Versioned `voiceprintHash`, 32 bytes, bound to verified output/commitment |
| Score | `uint8`, 0..100 | `uint16`, 0..10000; UI divides by 100 for percent |
| Time | Submitted detection `timestamp` | `registeredAt = block.timestamp`; detection time logged separately |
| Proof | No input, storage or Verifier call | `zkProof`, `publicInputs`, fixed Verifier and checked result binding |
| ID | Caller-supplied threatId | Deterministic submission context; baseline chainId/address/account/nonce |
| Duplicate policy | Same audioHash rejected globally; retries revert | Idempotent submission handling without overwriting; multiple events may share a voiceprint |
| Replay protection | Registrar authorization and duplicate input ID | Proof-bound submission context, usedSubmission, cross-account/chain checks |
| Registration policy | DEEPVOICE enum and score bounds only | Valid proof, matching public values and versioned risk criterion |
| Number policy | Absent | Separate synthetic-number promotion, authorized evidence, phoneKey lookup/event |
| Event | Legacy seven-field record event | threatId/voiceprintHash/riskScore/registeredAt; separate promotion event |

Backend idempotent retry handling and contract replay rejection are compatible: repeated identical API submissions return existing status; they must not create a second chain record. Do not “fix” this by permitting proof replay.

## 9. Dashboard review

Specification: §§3, 5, 8, 11–15.

| Requested dimension | Finding |
| --- | --- |
| 1. Current state | `dashboard/README.md` only, SAMPLE; no visualization or client. |
| 2. Missing folders | Literal `frontend/` is absent. Reusing `dashboard/` is permitted by §§2/5; recommend recording that mapping instead of creating two dashboard roots. Actual source/test structure is missing; the specification does not prescribe its internal framework layout. |
| 3. Missing interfaces | Consumption of §8 status/events/threat queries; actual chainId/threatId/transaction identity, cursor synchronization and separate SAMPLE display. No independent threat decisions or business logic. |
| 4. Missing build configuration | Framework choice, manifest/lockfile, runnable build/dev commands, environment configuration and separate web CI job. |
| 5. Missing dependencies | Selected web runtime/UI/build tooling and service client configuration. React/Next.js are not required by the specification and are not selected here. |
| 6. Missing documentation | Stack decision, startup/configuration, event provenance, how the same threatId appears with Room, reconnect/error behavior, sample labeling, demo/reset steps. |
| 7. Satisfies P0? | No. §11 minimum ledger connection explicitly requires a dashboard displaying the same registered threatId. |
| 8. Blocks P1? | Yes for final event/transaction/state demonstration; does not block independent AI/device feasibility work. |
| 9. Recommended next task | After shared contracts and a local event-query path exist, build the minimum event/status display with a documented minimal framework choice. |

No map should be populated with invented geography. A map is conditional on trustworthy location data, not a P0 prerequisite or license to add a new data service.

## 10. Shared review

Specification: §§5–9, 11, 13.

| Requested dimension | Finding |
| --- | --- |
| 1. Current state | `schemas/`, `dto/`, `constants/`, `api/` contain SAMPLE READMEs only. No shared contracts or fixtures are used by runtime modules. |
| 2. Missing folders | `shared/fixtures/`. Additional existing folders are acceptable if ownership stays clear and schemas are not independently duplicated. |
| 3. Missing interfaces | §7 audio/results/proof/matching schemas; §6 ThreatRecord and score/timestamp rules; §8 submissions/status/errors/events/promotion requests; versioned serialization of big integers/bytes as strings; LIVE/SAMPLE, model and proof metadata; event identity/cursor; test-only number evidence. |
| 4. Missing build configuration | Schema validation and compatibility test commands/CI. Shared need not become an executable package; DTO generation is optional, not mandated. |
| 5. Missing dependencies | A validator compatible with the selected schema format, if required, and consumers' validation tools. No schema format or validator library has yet been selected. Reproducible synthetic-number/audio/test fixtures and version manifests are absent. |
| 6. Missing documentation | Canonical field/type/scale/encoding/version ownership, valid/invalid examples, API error semantics, event identity, artifact metadata, fixture licensing/provenance and privacy rules. |
| 7. Satisfies P0? | No. Minimum inter-module ledger data and reproducible fixtures do not exist. |
| 8. Blocks P1? | Yes: backend, contracts, Android, AI/proof output and dashboard cannot safely integrate against the legacy divergent formats. |
| 9. Recommended next task | Translate §§6–9 into one versioned contract set and synthetic fixtures; explicitly mark fields that still depend on AI/circuit feasibility, then add cross-consumer validation. |

Schemas must not prematurely claim a selected hash extraction algorithm or public-input packing. Those details are completed jointly with AI/Verification evidence. A synthetic fixture may demonstrate data flow but cannot masquerade as REAL_MODEL/REAL_PROOF. Number data stays out of ordinary threat registration, public inputs, and events for spoofed-known-number cases.

## 11. Docs review

Specification: §§2, 5, 10–15.

| Requested dimension | Finding |
| --- | --- |
| 1. Current state | Authoritative converted specification is available and README references it. Architecture/implementation/P0 preparation reports document the older PoC. This report adds a current comparison. |
| 2. Missing folders | `docs/` exists; no mandatory extra docs directory. Repository support `.github/ISSUE_TEMPLATE/` is absent. |
| 3. Missing interfaces | Human-facing API/data-model contracts, module-path decision record, acceptance/evidence matrix and documented artifact handoffs. Docs are not expected to expose a runtime API. |
| 4. Missing build configuration | No docs build is required. Missing module-specific verified setup/run/test instructions and CI coverage records. Link/content checks are useful but a documentation site is not required. |
| 5. Missing dependencies | No runtime dependency required for Markdown. Missing evidence depends on actual device/model/proof/chain tests, not installing a docs framework. |
| 6. Missing documentation | `api.md`, `data-model.md`, `decisions.md`, `limitations.md`, `demo.md`, `test-report.md`; `.github/pull_request_template.md`, issue templates, root `.env.example`. Missing release artifact/version/license/role/test evidence and up-to-date environment decisions. |
| 7. Satisfies P0? | Partial only. Specification and legacy checks exist; fresh-machine whole-project setup, device/AI/proof results and minimum ledger demo evidence do not. |
| 8. Blocks P1? | Missing evidence blocks declaring integration/release complete. It does not block independent work that §15 explicitly permits. |
| 9. Recommended next task | Reconcile current architecture and module path mappings with the specification, label historical claims, create the required documentation/evidence skeletons and templates, then update them with every module milestone. |

### Stale statements requiring future reconciliation

- `p0-preparation.md` says the specification is absent and model/proof frameworks are undecided. It should remain identifiable as a historical report; the specification now fixes TensorFlow Lite/Python-Librosa-CNN/ONNX-EZKL direction while leaving exact versions/models open.
- `shared/README.md` says no fields/API signatures are defined until a specification is available. §§6–9 now supply logical defaults.
- `dashboard/README.md` says framework/UI await the specification. The display responsibility is now specified; framework selection remains allowed under §5.
- `architecture.md` still centers a seven-field DB integrity PoC and describes zkML/automatic blocking as outside that old scope. Its disclaimer helps, but a complete current architecture and migration map are still missing.
- The old fixed mock detector → SQLite → DB tamper comparison recommendation is not the whole-project roadmap. Do not add Integrity Checker work merely because historical docs mention it.

The source specification and proposal must not be edited to make the legacy implementation appear compliant. This review does not alter either document or README.

## 12. Repository-wide consistency and proposed architecture adjustments

These are recommendations only; none were applied during this review.

1. Add the independent root `verification/` tree. This is the missing ONNX/EZKL responsibility, separate from AI analysis and on-chain verification.
2. Record `dashboard/` as the existing equivalent of specification `frontend/`; avoid duplicate modules. Record `backend/deepvoice/` and root `tests/` as existing source/test equivalents if retained. Directory migration is optional; responsibility and build discovery are not.
3. Place future Solidity Verifier adapter source under `contracts/src/` or explicitly configured dependencies. The existing `contracts/verifier/README.md` is not a compiled adapter.
4. Add Android source/unit/device-test trees, `ai/tests/`, `shared/fixtures/`, and `contracts/deployments/`; fill required docs and GitHub templates. Add `.env.example` using only actually chosen configuration names, no secrets or invented credentials.
5. Establish Shared as contract authority. Migrate the legacy model/ABI deliberately, with tests covering both changed behavior and rejection cases; do not silently reinterpret audioHash as voiceprintHash or multiply old scores and claim model calibration.
6. Keep analysis out of Backend. Future cleanup of legacy verification placeholders must preserve test/build integrity and avoid importing AI/proof dependencies into the API process.
7. Extend isolated build and CI targets per module as real modules arrive. Do not add a “passing” empty Android/AI/dashboard target or make the aggregate check imply unimplemented modules were built.
8. Record one testnet choice and AA provider/configuration responsibilities. Direct local transactions can prove connection only; they are not ERC-4337 completion.

Current root tooling correctly isolates the existing Python package and Solidity source/test trees. It does not configure Android, AI, Verification, web, or Shared checks. CI has one combined job, while §12 calls for separated contract/backend/AI/web/Android checks. Add expensive proof/device evidence separately. Versions, manifests, checksums, licenses and reproducible artifact retrieval remain required as artifacts are introduced.

## 13. Decisions and gates — do not guess

| Decision/evidence | Required before | Permitted progress meanwhile |
| --- | --- | --- |
| Target Android device/OS, input route and screening role | Claiming real-call/device support | Shared/build scaffolding and explicitly SAMPLE input work |
| Model/data selection, rates, thresholds, voiceprint algorithm/version | Claiming detection or speaker matching quality | Dataset tooling and measured baseline experiments |
| Supported ONNX ops, circuit/key, public-input layout, binding coverage | Treating a registered field as zkML-verified | Small-model proof feasibility and negative tests |
| Device precise inference/proof latency/memory | Claiming on-device final path | Local reference proof with explicit execution location |
| Real-number promotion evidence/authority/correction policy | Real-number automatic promotion | Synthetic test numbers with explicit test authority/evidence |
| Testnet and Smart Account/Bundler/Paymaster provider | Testnet deployment and AA acceptance | Local transaction connectivity, clearly labeled |
| Backend/frontend framework and schema format | Their real build/implementation | Choose minimal dependencies under §5 and document rationale; this review does not select them |

0.5-second detection, 0.1-second synchronization and “gas under one won” are not achieved facts. Record stage-level measurements, gasUsed and submission/confirmation latency. P0 may finish with honestly recorded feasibility gaps where the specification permits; final on-device claims cannot bypass those gaps. An unresolved decision is not permission to substitute server inference or pretend mock proofs are real.

## 14. Review deliverable and use

- What changed: only this architecture review was added; existing runtime, README, specification and configuration were preserved.
- How to use: use numbered steps as module work items; attach specification sections, dependencies and acceptance evidence to each issue/PR.
- How to run: no new executable exists. Existing environment setup is `make setup`; full legacy checks are `make check`. These do not build or validate missing modules.
- How to test: this review reran only the read-only checks listed in §2; validate each future step using its acceptance evidence and specification §13.
- Current limitations: no device, inference, proof, API, testnet, dashboard or end-to-end verification occurred here. The report distinguishes fresh checks, historical evidence and proposed work.
- Recommended next task: step 01. Whole-project P0 remains IN PROGRESS until the specification's P0 evidence is produced.

## 15. Exact recommended implementation order

The following is the default sequential project plan. Steps may be investigated independently once their inputs exist, but the listed acceptance gates must be met before dependent completion is claimed. Every step includes its documentation and tests; no step authorizes implementation during this review.

### P0 — feasibility and minimum connection

| Order | Owner(s) | Task | Prerequisite and acceptance evidence |
| --- | --- | --- | --- |
| 01 | Docs + repository | Reconcile architecture, map equivalent paths, add missing responsibility/document/template/environment scaffolds and build coverage plan | Specification read; no invented APIs; decisions recorded, existing checks remain green |
| 02 | Shared | Establish §§6–9 logical schema versions, valid/invalid synthetic fixtures, model/proof metadata, score/encoding rules | 01; contract validation and explicit unresolved algorithm/circuit fields |
| 03 | Android | Establish pinned Kotlin/Gradle build; device audio/role/permission and synthetic-number screening PoC | 01–02; APK runs, input capability evidence, LIVE/SAMPLE distinction and screening results |
| 04 | AI | Isolated Librosa/model/export environment, dataset separation, preprocessing, lightweight then precise baseline, voiceprint reproducibility evaluation | 02–03 input findings; measured normal/synthetic outputs, failures/unsupported conditions, versioned artifacts |
| 05 | Verification + AI + Shared | Small precise ONNX → EZKL witness/proof/local verify; establish binding/public input proposal | 04; real proof verifies and changed output fails; artifacts, versions and proof scope recorded |
| 06 | Contracts + Verification + Shared | Migrate Registry data/ABI and fixed adapter using generated ABI; test rejection and local deployment | 02/05; verified field binding, no state/events on invalid submission, no global voiceprint uniqueness mistake; synthetic fixture registration/event reproducible |
| 07 | Backend | Minimal local submission/status/Registry-event query connection using shared contract; no inference | 06 ABI/events; receipt-backed state, identical retry does not create a second record; local transaction mode explicitly labeled |
| 08 | Android | Minimum event ingestion into Room; retain local screening behavior | 03/07; chain record and Room share threatId; protection cache does not become authority |
| 09 | Dashboard | Minimal event/status/transaction display in existing dashboard path | 07; real local event shares threatId with 06/08, SAMPLE mode distinctly labeled |
| 10 | Docs + all modules | Reproduce minimum fixture → registration → event → Room/dashboard connection; record P0 evidence and one testnet choice | 03–09; setup/run commands verified, model/proof/transaction modes recorded; unresolved capture/device-proof/matching conditions listed individually |

P0 step 06 starts the real verification integration needed by later steps; full policy, replay and final testnet acceptance are completed and revalidated in P1. Local MockVerifier tests may supplement development, but never replace step 05's real proof gate or satisfy the final verified path.

### P1 — complete required integration and final demo

| Order | Owner(s) | Task | Acceptance evidence |
| --- | --- | --- | --- |
| 11 | AI + Android + Shared | Integrate real lightweight/precise outputs, risk UI, session/version metadata and matching | Warnings do not wait for proof/chain; invalid/unsupported inputs are not normal verdicts; specified quality evidence recorded |
| 12 | Verification + Android | Connect asynchronous real proof generation to actual precise computation on target device | Timing/memory and execution-location evidence; failure states visible; if infeasible, record team decision requirement, not silent server substitution |
| 13 | Contracts + Verification | Complete proof-context replay defenses, fixed circuit/key policy, separate authorized synthetic-number promotion; deploy real Verifier/Registry to chosen testnet | Tampered proof/score/hash/other circuit and cross-account/chain replay rejected; valid event identity and deployment manifest; spoofed number excluded |
| 14 | Backend + Shared | Complete all §8 endpoints, errors/auth, idempotency/state persistence and RPC recovery; robust event cursor/backfill/reorg handling | 202 vs REGISTERED distinct; collisions/invalid proofs/dependency failures tested; only chain-valid records distributed |
| 15 | Android + Backend + Contracts | Integrate Smart Account → UserOperation → Bundler → EntryPoint → Registry and Paymaster sponsorship | Keys/account creation/sponsorship limits/retries documented; provider failure UI tested; no direct-transaction shortcut labeled ERC-4337 |
| 16 | Android + Backend | Complete Room deduplication/reconnect/reorg/offline handling, promoted-number screening, changed-number audio matching warnings | Event identity chainId/txHash/logIndex; last block saved; missing/removed logs handled; local screening within specified deadline; no spoofed-known-number blocking |
| 17 | Dashboard + Backend | Complete actual transaction/event/synchronization and failure-state display | Same threat identity and valid status as chain/Room, reconnect and sample boundaries verified |
| 18 | Docs + all modules | Run §14 scenarios A/B/C; assemble reproducible release evidence | Runnable APK, real testnet Verifier/Registry addresses and ABI, dashboard, commit/model/circuit versions, test report/demo/reset commands; incomplete on-device/live capabilities explicitly disclosed |

### P2 — measured optimization after P1 gates

| Order | Owner(s) | Task | Acceptance evidence |
| --- | --- | --- | --- |
| 19 | AI + Verification + Android | Optimize measured inference/proof/memory/battery bottlenecks | Before/after p50/p95 and correctness/regression results; no removal of real inference/proof requirements |
| 20 | Backend + Android + Contracts | Improve measured event recovery/synchronization and transaction/proof-storage costs | Recovery/gas/latency measurements; storage-policy changes require a decision record |
| 21 | Dashboard + Android + Docs | Improve readability, blocking reports and presentation/demo documentation | Verified UI/demo evidence; location map only with trustworthy source data |
| 22 | Shared + Docs + all modules | Revalidate compatibility and publish the final evidence set as authorized | Schemas/fixtures/versions match artifacts; limitations and license/role/release records current |

**Per-module traversal:** Docs 01→10→18→21→22 (updated at every step); Shared 02→05→06→11→14→22; Android 03→08→11→12→15→16→19→20→21; AI 04→05→11→19; Verification 05→06→12→13→19; Contracts 06→13→15→20; Backend 07→14→15→16→17→20; Dashboard 09→17→21. All modules participate in gates 10, 18 and 22 where relevant.

**Immediate next task:** step 01, a structure/documentation alignment task against the now-available specification, followed by Shared contracts. Do not start by expanding the legacy backend detector or DB tamper demo.

