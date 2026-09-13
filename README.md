# Tri-Defense — Integration P0

Source of Truth: [구현 명세 (Codex)](docs/implementation-specification.md). 제안서와 README가 충돌하면 구현 명세를 우선한다.

**로컬 MOCK 통합 완료:** Android 디버그 앱 → Backend API → VerifierAdapter / MockVerifier → ThreatRegistry → 이벤트 → Dashboard 및 Android Room.
Android 에뮬레이터가 실제 HTTP 요청을 보내고, 로컬 EVM에서 발생한 이벤트를 두 클라이언트가 조회하는 경로를 검증했다.
AI·zkML·ERC-4337·실제 통화 보호는 구현하지 않았다. 전체 명세의 완료를 의미하지 않는다.

[통합 보고서 · 시퀀스 다이어그램 · 남은 차단 요인](docs/integration-p0-report.md) · [실행 및 테스트](integration/README.md)

## 모듈 상태

| 모듈 | 구분 | 현재 상태 |
| --- | --- | --- |
| [android/](android/README.md) | REAL 연결 / SAMPLE 입력 | Kotlin·Room·CallScreeningService scaffold, debug 전용 MOCK 제출·Registry 동기화. 통화 정책은 allow-all |
| [backend/](backend/README.md) | REAL API / MOCK_PROOF | 제출·상태·Registry·이벤트 API, 생성 ABI 사용, 로컬 트랜잭션 |
| [contracts/](contracts/README.md) | REAL 로컬 EVM / MOCK 검증 | ThreatRegistry, VerifierAdapter, 정확한 fixture만 허용하는 MockVerifier, 배포·ABI |
| [dashboard/](dashboard/README.md) | REAL 조회 / MOCK_PROOF 표시 | React + Vite, Registry·이벤트·상세·제출 상태, 별도 MOCK 표시 fixture |
| ai/ | SAMPLE scaffold | 전처리·경량/정밀 모델·voiceprint·평가 역할 문서만 존재 |
| [shared/](shared/README.md) | SAMPLE scaffold / 통합 fixture 계약 | 기존 DTO·스키마 폴더와 [로컬 fixture 규약](shared/fixtures/README.md) |
| docs/ | REAL 문서 | 구현 명세, 과거 architecture review, 모듈별 개발 보고서, 최신 통합 보고서 |

REAL은 HTTP·EVM·저장소 동작을 뜻한다. `MOCK_PROOF`는 암호학적 zkML 증명이 아니다. fixture의 VoiceprintHash와 RiskScore는 합성 테스트 데이터다.

## 실행

환경: JDK 17, Android SDK 35/adb 및 API 29 이상 테스트 기기, Python 3.11+, Foundry 1.8.1/Solidity 0.8.24, Node 22.12+, pnpm 11.19.0.
각 모듈 README에 설치 방법이 있다. 테스트 전용 에뮬레이터를 사용한다. 통합 실행기는 해당 디버그 앱 데이터를 초기화한다.

```sh
# 저장소 루트에서; JAVA_HOME/Android SDK를 먼저 설정
android/gradlew -p android assembleDebug assembleDebugAndroidTest
backend/.venv/bin/python integration/run.py --device emulator-5554 --serve
```

다른 터미널:

```sh
pnpm --dir dashboard install --frozen-lockfile
pnpm --dir dashboard dev
```

세 번째 터미널에서 실제 Android 제출 결과와 Dashboard 화면을 비교한다:

```sh
backend/.venv/bin/python integration/verify_dashboard.py
```

Dashboard는 `http://127.0.0.1:5173`의 **Backend API** 모드를 사용한다. Mock fixtures 모드는 이 통합 테스트의 데이터 소스가 아니다. 종료할 때 각 서버 터미널에서 Ctrl-C.

## 테스트 및 빌드

```sh
android/gradlew -p android assembleDebug assembleDebugAndroidTest assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug
backend/.venv/bin/python -m unittest discover -s backend/tests
.tools/forge test
pnpm --dir dashboard test
pnpm --dir dashboard build
```

검증 결과: Android JVM debug 29 / release 25, Android 에뮬레이터 통합 1, Backend 21, Contracts 29, Dashboard 17개 테스트 통과. 브라우저 통합 검증 통과. Android debug/release 및 Dashboard build 성공.
Root `make check`는 초기 Python 모델·Contracts 검사이며 전체 모듈 통합 검사를 대신하지 않는다. 기존 Python 모델은 `backend/deepvoice/`, 기존 모델 테스트는 `tests/`에 보존되어 있다.

## 남은 범위

실제 AI 및 zkML PoC, 실제 Verifier, ERC-4337/테스트넷, 장기 백그라운드 동기화, 기기별 통화 보호 검증은 별도 작업이다. 현재 Room의 MOCK 캐시는 기본 통화 차단 캐시와 분리되어 있다. 다음 작업은 [통합 보고서의 권장 순서](docs/integration-p0-report.md#remaining-blockers-and-recommended-order)를 검토한 뒤 정한다.
