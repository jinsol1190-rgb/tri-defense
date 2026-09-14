# Android 1–3 stage victim UI report

## Changes

Restored the first-stage gauge within the ongoing call. The deterministic timer updates every 50 ms, reveals the gauge at 200 ms, and changes its mock score continuously toward 90/100. This separates the <=0.5-second UI response target from the later precise warning. The score is explicitly marked MOCK, not a measured probability.

The second-stage warning appears on the same call surface with an end-call action and guidance to verify using a known contact. No separate analysis wizard exists. After the user ends that warned call, the third-stage surface shows sharing pending followed by voice-feature sharing complete, explicitly retaining **phone number not registered**. No registration button, technical hash/proof display or record filters are present.

Four capture points: incoming call, first-stage gauge, second-stage warning, third-stage sharing result. The continuous video additionally includes gauge movement and the pending-sharing state.

## Scope and interpretation

The selected scenario is an unknown-number call. Voiceprint sharing and phone-number blacklist promotion are distinct: being unknown is not enough for promotion. The demo never claims the number was registered or blocked. A spoofed-contact scenario would similarly exclude the contact number, but no additional scenario-selection UI was added. Separate verified phone promotion is not simulated because no evidence for it exists in this fixture.

`DemoApp` state/timers and `CallRiskGauge` Canvas rendering are presentation only. The app container, audio, models, Telecom, Backend, Room and chain are not invoked. Real AI, zkML and blockchain implementation remain out of scope. The call screen imitates familiar Android phone controls; it is not the actual dialer or an overlay.

Timing: gauge starts at 0.2 seconds; the 8-second precise warning and 12-second sharing completion are illustrative demo pacing, not specified latency, proof verification or blockchain finality. Only the first-stage UI response is tied to the <=0.5-second presentation requirement. This does not demonstrate real AI performance.

Ending early or rejecting the call cancels the pending warning and produces no sharing result. Ending after a warning permits the simulated asynchronous sharing to finish. Caller number remains visible and unregistered. Activity recreation retains presentation state. Mute/speaker are visual toggles, not audio controls.

## Run and test

From `android/`, with JDK 17 and SDK 35:

```sh
./gradlew assembleDebug assembleRelease assembleDebugAndroidTest testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am start -n org.tridefense.android/.ui.demo.DemoActivity
adb shell am instrument -w -r -e class org.tridefense.android.ui.demo.DemoUiTest org.tridefense.android.test/androidx.test.runner.AndroidJUnitRunner
```

Tap **받기**, observe the gauge and warning, then **통화 종료**. Sharing advances without user action. Backend and Anvil are not required.

Capture from the repository root:

```sh
python3 android/scripts/capture_demo.py --serial emulator-5554
```

The capture test uses 50 ms clock steps and wall-clock pauses so gauge movement is visible. Capture dwells and emulator/test overhead mean recording duration is not a latency benchmark. Video is silent. Final files and logs are under `docs/demo/`.

## Remaining work and next step

Use the four screenshots or video in the proposal with the MOCK caption. Actual phone capture/overlay permissions, model calibration and performance, valid proof verification, and real registration remain separate work. No other module or proposal document was modified. The UI implementation stage did not publish changes; the subsequent submission preparation is recorded in the root submission guide.

## Verification — 2026-09-14

Debug/release/test APK builds passed. 29 JVM tests and 4 API-35 UI tests passed (UI suite: 6.414 seconds). The flow test checks that the first-stage gauge is visible at approximately 0.45 seconds of controlled UI time, followed by the later warning, sharing pending, and voice-feature sharing complete with the number excluded. Early rejection/hang-up and recreation are covered. Lint: 0 errors, 20 warnings. `git diff --check` passed. This verifies presentation logic, not real inference latency.

The continuous capture-flow test also passed. Four native 1080×2400 screenshots and a silent 720×1600 full-flow MP4 were regenerated and bundled in `demo/victim-ui-assets.zip`. Gauge and sharing layouts were visually inspected.
