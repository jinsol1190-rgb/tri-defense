# P0 development preparation — 2026-09-13

## Scope and implementation

This task prepares repository structure and build tooling only. It does not complete the broader P0 Android/AI/zkML PoCs or implement P1/P2.

Added isolated documentation placeholders for Android, AI preprocessing/lightweight/precise/voiceprint/evaluation, dashboard, shared DTOs/schemas/constants/API contracts, Verifier Adapter, and deployment scripts. All placeholders are SAMPLE and contain no executable feature, public API, inference, Proof, or VoiceprintHash.

Existing backend models, failing verification placeholders, Registry source, and tests are unchanged. The existing implementation is REAL claim-storage code; synthetic test metadata is MOCK, not inference. No new module imports another module. Python package discovery remains restricted to backend; Foundry source/test/script paths remain inside contracts. Existing backend verification placeholders are legacy, not authorization to put AI inference in backend.

The root Makefile builds the existing Python distribution and Solidity code independently. CI uses the same setup/build/test commands. Build outputs, virtual environments, and local tools are ignored. Python runtime dependencies remain empty; pip resolves the declared setuptools build requirement in isolation. Foundry remains version 1.8.1 with Solidity 0.8.24. Dependency versions beyond existing toolchain requirements are not claimed to be locked.

## Decisions and limitations

- Specification and proposal are absent. No proposal was edited. Android SDK/Gradle versions, frontend stack, model framework, proof framework and API schemas are intentionally undecided.
- Android and dashboard are not compilable application projects yet; AI/shared are non-executable placeholders. Build success applies only to existing Python/Solidity components.
- No audio capture or permission capability has been verified.
- The existing Registry accepts registrar claims without a Verifier. It must not be presented as proof-verified threat registration. Fixing it requires a separately authorized implementation task.
- No deployment, backend API, inference, synchronization, cache or cross-user protection exists.
- Existing Foundry test-source lint notes and reentrancy-events/unused-return warnings are retained. No contract logic was changed to suppress them.
- GitHub Actions is configured but remote execution is not verified.

## Run and test

Run `make setup`, then `make check` from the repository root. Re-run setup after Python source edits to refresh the installed package. `make build` and `make test` are separate entry points. There is no application server to start.

## Validation

- `make setup`: package built and installed in `.venv`; `pip check` reports no broken requirements.
- `make check`: passed; Python wheel built, Solidity build completed, 11 Python tests and 24 Foundry tests passed, formatting passed. Four fuzz tests each ran 256 cases.
- `.tools/forge build --force --no-lint`: fresh compilation of both Solidity files passed with solc 0.8.24. Normal `make check` retained lint output; this additional command confirms compilation without relying on cached artifacts.
- Wheel contents contain only the existing deepvoice package and distribution metadata; tests import the installed package from `.venv`, not the source tree.
- `git diff --check`: passed. Existing Python/Solidity runtime sources and tests have no changes relative to the baseline commit.
- Verified locally with Python 3.9.6 and Foundry 1.8.1. No Android/dashboard build or end-to-end MVP success is claimed.

Development-scaffold checks pass. Overall Tri-Defense P0/MVP status remains IN PROGRESS because the functional PoCs are outside this task.

## Remaining work

Next: provide and review the implementation specification/proposal, then approve module-specific toolchains and acceptance criteria. Continue the broader P0 sequence (Android PoC, AI PoC, zkML PoC, verified Registry) only in separately authorized work. P1/P2 remain out of scope.
