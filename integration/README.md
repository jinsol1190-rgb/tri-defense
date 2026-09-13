# Local Integration P0 — SAMPLE / MOCK_PROOF

This harness reuses Backend's `LocalChain` test setup and the existing generated ABIs. Android sends the POST; the host does not substitute a POST for Android. No new API or contract behavior is introduced.

## Prerequisites

Follow the Android, Backend, Contracts and Dashboard module READMEs for dependencies. The runner expects `backend/.venv`, Foundry in `.tools` or PATH, and adb in `android/.tools/sdk/platform-tools`. Supply JDK 17 via JAVA_HOME, SDK path via `android/local.properties`, Node 22.12+ and pnpm 11.19 on PATH. A disposable emulator must be booted and listed by adb. Tested: API 35 Google APIs arm64-v8a, Pixel 7 profile.

For the repository-local SDK, a test emulator can be prepared with:

```sh
android/.tools/sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root="$PWD/android/.tools/sdk" 'emulator' 'system-images;android-35;google_apis;arm64-v8a'
mkdir -p android/.tools/avd
ANDROID_AVD_HOME="$PWD/android/.tools/avd" android/.tools/sdk/cmdline-tools/latest/bin/avdmanager create avd --name tridefense-p0 --package 'system-images;android-35;google_apis;arm64-v8a' --device pixel_7
ANDROID_AVD_HOME="$PWD/android/.tools/avd" android/.tools/sdk/emulator/emulator -avd tridefense-p0 -no-snapshot
```

Choose the matching system image on non-arm64 hosts. Do not recreate an AVD containing useful data.

## Run and verify

From repository root:

```sh
android/gradlew -p android assembleDebug assembleDebugAndroidTest
android/.tools/sdk/platform-tools/adb devices
backend/.venv/bin/python integration/run.py --device emulator-5554 --serve
```

The explicit serial identifies the disposable test device. The runner installs the two debug APKs and **clears `org.tridefense.android` app data** for an isolated run. It owns an ephemeral Anvil on a free loopback port and Backend on port 8000. An occupied port fails; it never terminates another server.

The setup admits exactly one synthetic proof/input tuple through MockVerifier's existing administrator function. Android receives the generated configuration via a debug Activity intent; its button sends the request. The device test checks the actual Room record after receipt-backed registration. Host checks compare the receipt, API status, Registry record, emitted event, and Android evidence. `--serve` keeps services alive for Dashboard; omitting it stops services after the Android/API assertions.

In a second terminal:

```sh
pnpm --dir dashboard install --frozen-lockfile
pnpm --dir dashboard exec agent-browser install
pnpm --dir dashboard dev
```

In a third terminal:

```sh
backend/.venv/bin/python integration/verify_dashboard.py
```

This checks the real browser in Backend API mode: event, Registry detail, submitted ID, transaction hash and REAL / MOCK_PROOF labels, with no browser errors. It saves a screenshot and assertions. A PASS from `run.py` alone does **not** include Dashboard verification.

`integration/out/` contains ignored ephemeral fixture JSON, device evidence, API/event results, instrumentation output, browser text and screenshot. Regenerate them for each deployment. Ctrl-C the runner and Vite when finished; the runner stops its own chain, closes the API/store and removes its adb reverse mapping. Stop the emulator separately.

For manual Android use while the runner is serving, open **Tri-Defense MOCK Integration**, paste `out/fixture.json`, tap **Load SAMPLE fixture**, then **Submit MOCK threat** or **Refresh Registry cache**. The original placeholder app screen and CallScreeningService remain separate. Repeated submission uses the same idempotency key; to create a different scenario restart the test harness with fresh app data.

## Limits

Local chain 31337 only, ordinary local transactions, explicit mock verification, no inference/proof generation/AA. HTTP cleartext is debug-only and restricted to loopback/emulator-host names. `adb reverse` connects Android loopback to Backend. Room synchronization is foreground/manual with bounded catch-up; no background worker or automatic call blocking. A reorg invalidates cache; the next refresh rebuilds it. See the [full report](../docs/integration-p0-report.md).
