# Android P0 development report

Date: 2026-09-13. Branch: `codex/android-p0`.

## Scope and changes

Read AGENTS.md, root README, implementation specification and architecture review before editing. The latest user request limits this work to Android preparation. All source/config/documentation changes are inside `android/`. Other modules and the source specification are unchanged. Existing uncommitted root documentation remains untouched.

Created a standalone Kotlin Android project, manifest, protected CallScreeningService, RoleManager request/status UI, empty Room cache/DAOs, audio abstractions, typed AI interfaces with unsupported responses, manual DI and clearly labeled placeholder screen. The UI is explicitly requested as a placeholder, not a redesign of the proposed product.

No inference/proof/chain/HTTP implementation or dependency exists. No default score/hash or bundled fake output is returned. Screening immediately allows calls; the blocking response tested in JVM tests uses an explicit MOCK policy only, never a production binding.

## Architecture decisions

- API 29 minimum makes the public screening role flow available without legacy permission workarounds. Compile/target API 35 is paired with the pinned AGP 8.9.2 / Gradle 8.11.1 / JDK 17 toolchain. This is a build choice, not a confirmed target phone.
- Native Activity/XML keeps placeholder UI dependencies small. Manual app-scoped constructor injection provides replaceable AI/audio/screening dependencies without a DI code-generation framework. Room uses kapt and exports schema v1.
- Room opens lazily and is not touched by the main-thread screening callback. The default policy has no blocking I/O. No future asynchronous lookup deadline is claimed implemented.
- A default allow-all policy is intentional while verified number mapping is unavailable. Merely requesting the screening role does not enable protection. No caller identity is logged.
- Cache structures mirror section 6: threat data includes voiceprintHash, 0..10000 score convention, registeredAt and proof string; phone-key cache is separate; events have chain/transaction/log identity and block metadata; cursor holds last block. They are local storage scaffolds, not a trusted ingestion/validation pipeline.
- AI/audio types remain Android-local because Shared cannot be changed in this task. A future migration must reconcile schema/version/encoding conventions. Valid normalized PCM is an adapter convention; model duration/rate/threshold decisions are intentionally open.
- README and report live inside Android under the strict module-isolation instruction. Root Makefile, root README, CI and architecture-review history are not rewritten.

## Validation

**Requested Android preparation scope: complete. Broader specification P0: IN PROGRESS.**

| Check | Result |
| --- | --- |
| `./gradlew assembleDebug` | PASS — debug APK generated |
| `./gradlew assembleDebugAndroidTest` | PASS — device-test APK compiled |
| `./gradlew testDebugUnitTest` | PASS — 25 tests, 0 failures/errors/skipped |
| `./gradlew lintDebug` | PASS — 0 errors, 10 warnings |
| Room schema export | PASS — `app/schemas/org.tridefense.android.registry.ThreatDatabase/1.json` |
| APK metadata/permissions (`aapt`) | Package `org.tridefense.android`, min 29/target 35; no uses-permission entries |
| `adb devices` | No attached device; connected instrumentation not executed |
| Scope/whitespace checks | Only Android work added in this task; no Android whitespace errors |

The final combined Gradle invocation completed successfully. Tests: audio adapters/input validation 5; unsupported AI and score contract 2; Room empty cache/duplicate preservation/event identity/cursor 4; screening response/manifest 8 (API 29 and 35); UI role states 6 (API 29 and 35). Unit-test Android behavior is Robolectric, not a real device. No 5-second real-call latency claim is made.

An initial build failed because test coroutine 1.10.1 conflicted with the app's resolved 1.7.3 under Android consistent dependency resolution. Test dependencies were aligned to 1.7.3; the final build passed. No dependency conflict remains in the executed build.

Nonblocking Lint warnings: seven newer-dependency notices, one Android 12+ data-extraction configuration recommendation, one kapt-to-KSP recommendation, and one missing launcher icon. The app is a development placeholder; a production icon/backup policy is not claimed. Compiler warnings concern legacy insets APIs needed by the current API-29-compatible UI, unused annotation-processor options in test compilation, and statically known unsupported placeholder results in tests. None is suppressed as a build failure workaround.

JDK 17, SDK 35/Build Tools 35.0.0, Gradle 8.11.1, Kotlin 2.1.20 and Room 2.7.1 were exercised locally on macOS arm64. Tool downloads are ignored under `.tools/`; normal Gradle caches remain outside version control. The Gradle distribution checksum is pinned in wrapper properties.

## Remaining limitations

The task's preparation scope and the specification's full P0 device PoC are different. Full P0 remains IN PROGRESS: no attached device, real-call access evidence, actual incoming-call block test, AI/proof execution, shared schema consumption, chain event ingestion, Room synchronization or A→B demonstration. Android supports service registration and pass-through only at this stage.

Robolectric tests are simulated Android JVM tests; they do not prove carrier audio availability or a real five-second response deadline. Device instrumentation is a Room smoke test, not a phone-call simulation. The sample adapter takes caller-provided PCM only and never records. Unsupported AI is not a HUMAN verdict.

## Run and test

Use the commands in [Android README](../README.md). Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. JVM results: `app/build/reports/tests/testDebugUnitTest/`. Lint: `app/build/reports/lint-results-debug.html`. Install and connected-test commands require a selected development device.

## Official references checked

- [AGP 8.9 compatibility](https://developer.android.com/build/releases/agp-8-9-0-release-notes): Gradle/JDK/SDK compatibility.
- [CallScreeningService](https://developer.android.com/reference/android/telecom/CallScreeningService): system binding, role selection, incoming/outgoing handling and timing constraints.
- [Room](https://developer.android.com/training/data-storage/room): entities, DAOs and database setup.

## Next suggested task

Stop implementation here. Next Android-only task should obtain approved device/OS evidence for screening role, incoming-call pass-through and possible input routes, then explicitly scope a synthetic test-number blocking PoC. No P1 or other-module implementation is part of this delivery.
