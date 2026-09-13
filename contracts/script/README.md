# Contract deployment and ABI scripts

Status: **SAMPLE / MOCK_PROOF; local P0 only**.

From repository root:

```sh
python3 contracts/script/local_demo.py
```

This invokes `DeployLocal.s.sol` and `SmokeLocal.s.sol` through Foundry on its own ephemeral loopback Anvil (chain 31337). Deployments and fixture transactions use an unlocked local test account without persisting private keys. `--slow` waits for each receipt before the next transaction; the runner checks transaction nonces, receipt addresses and event signatures against live JSON-RPC. It always stops its own node on exit.

`DeployLocal.run(address operator)` deploys MockVerifier → immutable VerifierAdapter → ThreatRegistry. `SmokeLocal.run(address verifier, address registry, address operator)` explicitly admits one mock fixture, registers it, reads it, and separately promotes the configured synthetic number. These scripts reject other chain IDs. A chain ID guard does not make a malicious public chain safe; use only the owned loopback runner for mocks.

Build before exporting ABI:

```sh
.tools/forge build
python3 contracts/script/export_abi.py
python3 contracts/script/export_abi.py --check
```

The exporter writes only the four public contract/interface ABIs, not script or test ABIs. Foundry build/broadcast outputs remain in existing ignored root directories. The runner writes reviewable local evidence to `contracts/deployments/local.json`.

There is no public-testnet deployment script pretending to support an absent EZKL verifier. A later real deployment must select the network, inspect the actual generated ABI, pin approved artifacts and binding, and replace the local number policy explicitly. See [the development report](../docs/development-report.md).
