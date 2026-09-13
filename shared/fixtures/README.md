# SAMPLE integration fixture configuration

`integration/run.py` generates `integration/out/fixture.json`. This is local test configuration, **not an additional Backend API** and not an AI output.

| Field | Meaning |
| --- | --- |
| proofMode | Always `MOCK_PROOF` |
| chainId | String `31337` |
| backendUrl | Local HTTP origin; Android uses `adb reverse` for `127.0.0.1:8000` |
| registryAddress | Actual local deployment address |
| submission | Existing POST `/v1/threat-submissions` payload, unchanged |

Submission fields remain `idempotencyKey`, `nonce`, `voiceprintHash`, `riskScore`, `proof`, `publicInputs`. Integer/byte representation follows [Backend API documentation](../../backend/README.md). Public inputs bind circuit, score, hash halves, chain, registry, sender and nonce under the existing [contract interface](../../contracts/README.md).

The helper labels synthetic transport values, then explicitly admits the exact tuple using MockVerifier. Android cannot admit fixtures or invoke RPC. The payload is deployment-dependent; do not copy it to another chain or treat it as a reusable real proof. The generated fixture is ignored rather than committed with stale addresses.
