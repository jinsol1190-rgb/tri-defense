# Tri-Defense — 초기 PoC 제출 소스코드

> 한 명의 딥보이스 탐지를 다른 사용자의 방어로 연결하는 3단계 보안 네트워크

이 저장소는 해커톤 **초기 구현(P0)** 결과입니다. Android·Backend·스마트 컨트랙트·Dashboard의 **로컬 MOCK 통합**과 발표용 **Android 사용자 경험 데모**를 포함합니다. 완성된 보안 서비스가 아닙니다.

**실제 AI 추론, zkML 증명 생성·암호학적 검증, ERC-4337, 실통화 차단, 운영 배포는 미구현입니다.** 화면의 점수·음성 지문·Proof·보호 결과는 실제 탐지 성능이나 실제 보호를 입증하지 않습니다.

[구현 명세 — Source of Truth](docs/implementation-specification.md) · [제출 안내·검증 결과](docs/submission-readiness.md) · [로컬 통합 보고서](docs/integration-p0-report.md)

## 심사위원용 빠른 안내

| 확인할 내용 | 위치 |
| --- | --- |
| 사용자 A: 통화 중 게이지 → 경고 → 공유 결과 | [화면 4장](android/docs/demo/README.md) · [전체 시연 영상](android/docs/demo/tri-defense-ui-demo.mp4) |
| 사용자 B: 등록 번호 차단 알림 / 변경 번호 경고 | [MOCK 콘셉트 화면 2장](android/docs/protection-demo/README.md) |
| Android UI 소스 | [ui/demo/](android/app/src/main/java/org/tridefense/android/ui/demo/) |
| 실제 로컬 API·EVM·이벤트·Room 통합 재현 | [integration/README.md](integration/README.md) |
| 모듈별 소스·실행 방법 | [Android](android/README.md) · [Contracts](contracts/README.md) · [Backend](backend/README.md) · [Dashboard](dashboard/README.md) |

Android 데모는 앱 안에서 실행되는 **독립적인 MOCK 와이어프레임**입니다. 실제 시스템 전화 앱이 아니며, 아래의 로컬 Backend/체인 통합 경로를 호출하지 않습니다. 발표용 시연과 실제 연동 검증을 구분합니다.

## 목표로 하는 3단계 흐름

1. **경량 탐지:** 사용 가능한 음성 입력으로 위험 점수를 표시합니다. 0.5초는 실제 모델에서 검증할 목표이며, 현재 데모는 0.2초부터 MOCK 게이지를 표시합니다.
2. **정밀 분석:** 음성 특징과 Voiceprint Hash를 분석하고 사용자에게 경고합니다. 경고는 증명·체인 확인을 기다리지 않습니다.
3. **검증 후 공유:** 지정 연산의 증명을 검증한 위협 정보를 ThreatRegistry에 등록하고 다른 사용자에게 동기화합니다. 도용된 지인의 번호는 제외하고, 번호 차단 목록 승격에는 별도 검증이 필요합니다.

실제 지문 대조, 번호 승격 정책, 실통화 입력·차단, 지연시간은 검증 전 완료로 주장하지 않습니다. zkML은 범죄 여부나 음성 출처를 확정하는 기술이 아닙니다.

## 현재 구현 범위

| 모듈 | 구현된 범위 | 제한 |
| --- | --- | --- |
| Android | Kotlin·Compose·Material 3 데모, Room 캐시, debug API 클라이언트, CallScreeningService 구조 | 실제 정책은 모든 전화 허용. AI·음성 대조·실제 보호 없음 |
| Backend | 제출·상태·Registry·이벤트 REST API, SQLite 저장, 생성 ABI 사용 | 로컬 체인·MOCK_PROOF 경로. 추론·실제 zkML·운영용 번호 승격 API 없음 |
| Contracts | ThreatRegistry, VerifierAdapter, MockVerifier, 테스트용 번호 승격, 배포·ABI 생성 | 로컬 EVM의 실제 실행 + 명시적 MOCK 검증. 실제 zkML Verifier 없음 |
| Dashboard | Registry·이벤트·상세·제출 상태, Backend 연동과 MOCK fixture 모드 | 모니터링 UI이며 실제 탐지 모델 없음 |
| Integration | Android 제출 → Backend → 로컬 Registry → 이벤트 → Dashboard/Android Room | 합성 테스트 데이터. UI 와이어프레임과 별도 진입점. 실제 통화 차단까지 연결되지 않음 |
| AI / Shared | AI 인터페이스·모듈 구조, 공유 fixture·스키마/DTO 준비 문서 | 실제 모델·지문 추출·완성된 공통 코드 생성 미구현 |

`REAL`은 해당 구간의 HTTP·EVM·저장소 동작을 의미합니다. `MOCK_PROOF`는 암호학적 증명이 아닙니다. 기존 Python 특징 처리 코드는 `backend/deepvoice/`에 보존되어 있지만, 실제 DeepVoice 탐지 파이프라인의 완성을 뜻하지 않습니다.

## 가장 간단하게 실행하기 — Android UI 데모

JDK 17과 Android SDK 35를 준비하고 `JAVA_HOME` 및 SDK 경로를 설정합니다. 기기/에뮬레이터는 API 29 이상이 필요합니다. 자세한 설정은 [Android README](android/README.md)를 참고하세요.

```sh
# 저장소 루트
./android/gradlew -p android assembleDebug installDebug
adb shell am start -n org.tridefense.android/.ui.demo.DemoActivity
```

**받기 → 게이지 변화 → 정밀 경고 → 통화 종료 → 공유 결과**를 시연합니다. Backend·블록체인·마이크 권한은 필요 없습니다. 음성은 재생하거나 녹음하지 않습니다.

사용자 B 화면은 별도 실행합니다(`-S`는 이 앱의 기존 실행을 종료합니다).

```sh
# 이미 등록된 악성 번호의 차단 알림 콘셉트
adb shell am start -S -n org.tridefense.android/.ui.demo.ProtectionDemoActivity
# 변경된 번호의 통화 중 경고 콘셉트
adb shell am start -S -n org.tridefense.android/.ui.demo.ProtectionDemoActivity --es scenario changed
```

## 로컬 통합과 테스트

통합 재현은 [전용 안내](integration/README.md)를 따릅니다. Python 3.11+, Foundry 1.8.1, Node 22.12+, pnpm 11.19.0 및 테스트용 Android 에뮬레이터가 필요합니다. 통합 실행기는 지정한 에뮬레이터의 **이 앱 데이터만 초기화**합니다.

각 모듈 README의 의존성 설치 후:

```sh
./android/gradlew -p android assembleDebug assembleDebugAndroidTest assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug
backend/.venv/bin/python -m unittest discover -s backend/tests
forge test
pnpm --dir dashboard test
pnpm --dir dashboard build
```

`make check`와 기존 GitHub Actions는 초기 Python/Contracts 검사입니다. Android·Backend·Dashboard 전체 검증을 대신하지 않습니다. 최신 로컬 결과와 과거 E2E 결과는 [제출 검증 기록](docs/submission-readiness.md)에 구분해 기록합니다.

`.tools`, SDK/JDK, 가상환경, `node_modules`, 빌드 산출물, 로컬 DB와 `.env`는 제출 소스에 포함하지 않습니다. 샘플 번호·공개 로컬 Anvil 계정은 테스트 전용입니다.

## 다음 구현 순서

[구현 명세](docs/implementation-specification.md)의 우선순위를 따릅니다. 실제 입력·권한 및 AI/지문 PoC → 실제 zkML/Verifier → 번호 승격 근거와 동기화·차단 연결 → ERC-4337/테스트넷 통합 → 측정·최적화 순으로 검증합니다. 성능 수치와 운영 보호 효과는 측정 전 보장하지 않습니다.
