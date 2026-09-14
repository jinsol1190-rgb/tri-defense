# Tri-Defense Android — P0 + MOCK Demo UI

Android-only preparation of the [implementation specification](../docs/implementation-specification.md), §§3–7. Integration P0 adds debug-only Backend communication and Room ingestion. No real AI, proof generation, direct chain access or complete threat-protection flow is implemented.

## Android Demo UI

The launcher **Tri-Defense Demo** is a **victim-facing submission wireframe** built with Kotlin / Jetpack Compose / Material 3. Answer the simulated unknown-number call with **받기**. A MOCK risk gauge starts changing at 0.2 seconds (inside the 0.5-second UI target), followed by an in-call precise warning and the end-call action. After ending the call, the same surface shows sharing pending and then **voice-feature sharing complete / phone number not registered**.

The scenario uses an unknown number. A phone number is not promoted just because it is unknown: promotion needs separate verification. Spoofed-contact numbers must likewise be excluded; that alternate scenario is documented but is not a second selectable demo. No phone-blocking success is claimed.

All scores and sharing states are **MOCK**. The 8-second precise warning and 12-second shared-result timings are illustrative pacing, not requirements or measured AI/chain latency. The first-stage gauge appears independently at 0.2 seconds. This is an app-rendered phone wireframe, not the system dialer; no actual audio, inference, proof, HTTP, Room write, chain registration or protection occurs.

Run `./gradlew assembleDebug installDebug`, then `adb shell am start -n org.tridefense.android/.ui.demo.DemoActivity`. Backend/Anvil are not needed. The previous `.ui.MainActivity` remains the explicit P0 role-testing screen. See [demo report and verification](docs/demo-ui-report.md) and [screenshots and demo video](docs/demo/README.md).

## Implementation status

| Component | Classification | Behavior |
| --- | --- | --- |
| Kotlin app/build | REAL | Standalone Android application and Gradle wrapper |
| CallScreeningService registration and role request | REAL | Protected system binding, user-driven role request, role availability/held state |
| Screening policy | SAMPLE placeholder | Immediately allows all incoming calls; never blocks because a caller is unknown. No protection claim |
| Room database | REAL storage scaffold | Version-2 schema with tested 1→2 migration; debug Backend ingestion into a separate MOCK database; no promotion |
| LiveAudioSource | SAMPLE placeholder | Explicitly unavailable; no recording or microphone permission |
| SampleAudioSource | SAMPLE adapter | Only returns caller-supplied sample input, otherwise unavailable; no bundled/generated audio |
| AI interfaces | SAMPLE placeholder implementation | All methods return Unsupported; no risk score, hash, match or HUMAN verdict produced |
| UI | MOCK presentation | Compose / Material 3 four-state victim-facing wireframe with a clearly labelled timed MOCK warning; original role-testing screen retained |
| DI | REAL skeleton | App-scoped manual constructor injection, lazy Room initialization |

The requested preparation and MOCK integration scope is complete: debug/release/app-test APK builds, 29 JVM/Robolectric tests and one actual API-35 device integration test passed; Lint has 0 errors. The broader real Android device protection PoC remains IN PROGRESS. A live-device screening/blocking demonstration is not provided by this allow-all skeleton.

## Build and test

Pinned build: JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2, Kotlin/kapt 2.1.20, Room 2.7.1, compile/target SDK 35 and Build Tools 35.0.0. Minimum Android API 29 is a P0 choice for the screening RoleManager flow, not a claim of device compatibility. No AI SDK or network library is included. Robolectric covers API 29 and 35; physical-device compatibility is not validated.

Install JDK 17 and the Android SDK through Android Studio or official command-line tools. Set `JAVA_HOME` to JDK 17 and `ANDROID_HOME` to your SDK, or create ignored `local.properties` with `sdk.dir`. Install `platforms;android-35`, `build-tools;35.0.0`, and `platform-tools`. SDK license acceptance is required. First builds download dependencies.

From this directory:

```sh
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
```

With an attached emulator/device (not needed for JVM tests):

```sh
./gradlew installDebug
adb shell am start -n org.tridefense.android/.ui.MainActivity
./gradlew connectedDebugAndroidTest
```

The application asks for the screening role only after tapping its button. Denial leaves the app unselected. Role availability and delivery of calls depend on the device. Even when the role is granted, this build allows every call.

The local build environment used for this task is under ignored `.tools/`. On this machine only, prefix Gradle commands with `JAVA_HOME="$PWD/.tools/jdk/Contents/Home"`; ignored `local.properties` points to `.tools/sdk`. Other machines should use their own SDK/JDK, not commit these paths or tool downloads.

The root Makefile/CI cover the original Python/Solidity modules only. Use this module's commands for Android and the root submission guide for cross-module verification.

## Structure and ownership

```text
app/src/main/
  AndroidManifest.xml
  java/org/tridefense/android/
    TriDefenseApplication.kt
    di/                         AppContainer and default bindings
    audio/                      input contracts and unavailable/sample adapters
    ai/                         logical results, interfaces, unsupported implementation
    screening/                  service and allow-all policy boundary
    registry/                   Room cache only, not Registry business rules
    ui/                         original role-testing activity
    ui/demo/                    isolated submission wireframe
  res/                          strings and layout
app/src/test/                   JVM/Robolectric behavior tests
app/src/debug/                  local MOCK HTTP / Room integration screen
app/src/testDebug/              integration and migration tests
app/src/androidTest/            wireframe UI, Room smoke and actual HTTP integration tests
app/schemas/                    exported Room versioned schema
build.gradle.kts, settings.gradle.kts, gradle.properties
gradle/wrapper/, gradlew, gradlew.bat
docs/development-report.md
```

Android-local data types mirror specification §7 while generated cross-language Shared DTOs remain unimplemented. They are not new backend APIs or a replacement source of truth. Later shared-contract adoption must reconcile these types explicitly. Input uses normalized finite PCM floats, interleaved channels, and epoch-millisecond `capturedAt` as a local adapter convention; no sample rate, minimum model window or risk threshold is selected. AI output score scale is 0..10000. `registeredAt` remains chain seconds; local Room block/time counters use signed Kotlin Long, and debug integration decodes the existing Backend JSON contract.

Room is only a cache. It starts empty and has no production writer/importer, phone promotion mutation, or phone-to-key hashing. The phone-key table is reserved for later verified promotion ingestion. Event identity is `(chainId, txHash, logIndex)`; multiple threats may share a voiceprint. Cursor storage is prepared, but synchronization/reorg recovery is not implemented. No destructive migration fallback or main-thread database queries are enabled. Proof strings in tests are explicitly SAMPLE storage fixtures, not proofs generated by the application.

## Limitations and next task

No microphone capture, real call recording, TFLite inference, fingerprinting, proof generation, blockchain logic, phone blacklist, or synthetic blocking demo is implemented. Debug builds alone include an API client and INTERNET permission. Release builds have no INTERNET permission; neither build requests RECORD_AUDIO, READ_CONTACTS or privileged call-capture permission. Real caller IDs are neither stored nor logged. Without contacts access, the platform generally excludes saved-contact calls from screening; private/unavailable numbers may not be delivered at all. Device-level behavior and the five-second system response deadline need real-device evidence.

Next Android task: validate role/input availability and incoming-call pass-through on an approved test device; implement any synthetic-number blocking PoC separately with clearly marked test-only data and a reviewed lookup policy. Foreground MOCK Room synchronization now uses the authoritative Backend/Registry path. Background synchronization and real protection remain separate tasks.

See [development report](docs/development-report.md) for verified results and remaining work.

## Integration P0 update

`app/src/debug/` contains the local-only HTTP client, fixture loader, foreground synchronizer and clearly marked MOCK control screen. Release builds exclude these classes and INTERNET permission. Room schema 2 adds deployment identity and an opaque Backend cursor; pages write records/events/cursor atomically. The debug database is separate from the default screening cache. Tests cover duplicate pages, mismatch rejection, outage/reorg behavior and migration. See [run instructions](../integration/README.md) and [integration report](../docs/integration-p0-report.md). Earlier development-report evidence describes the preceding standalone P0 milestone.

## User B protection concept screens (MOCK only)

`ProtectionDemoActivity` is a separate presentation entry point, without a launcher entry or changes to the user-A demo. It illustrates (1) an already registered malicious number's pre-ring blocking notification and sample record, or (2) a changed number's in-call voice-similarity warning. Both explicitly state that no actual blocking or voice matching occurs. The selector is a presenter-only intent extra, not a product setting or API.

```sh
adb shell am start -S -n org.tridefense.android/.ui.demo.ProtectionDemoActivity
adb shell am start -S -n org.tridefense.android/.ui.demo.ProtectionDemoActivity --es scenario changed
```

[User B screenshots and limitations](docs/protection-demo/README.md). Run `ProtectionDemoTest` with the same instrumentation command used for `DemoUiTest`. No call, permission, model, Backend, Room or contract is invoked. Registered-number lookup, actual screening, notifications and voice matching remain unimplemented.
