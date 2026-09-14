# 초기 PoC 제출 안내 및 검증 기록

이 문서는 2026-09-14 제출 정리 작업의 결과를 기록합니다. 완성 서비스·전체 구현 명세 충족을 주장하지 않습니다.

## 제출 범위

- 모듈별 P0 소스와 로컬 MOCK 통합 harness.
- 사용자 A의 1–3차 흐름을 보여주는 Compose 와이어프레임과 네이티브 캡처/영상.
- 사용자 B의 등록 번호 차단 알림·변경 번호 경고 MOCK 화면.
- 구현 명세, 실행 절차, 테스트와 제한 문서.

UI의 MOCK 점수와 지문/Proof·등록/차단 표시는 시나리오 표현입니다. 실통화·실제 AI·암호학적 검증·실제 보호 결과가 아닙니다. 실제 로컬 HTTP/EVM 통합과 UI 데모는 별도 진입점이며, UI 데모를 실행해도 Backend/체인을 호출하지 않습니다.

## 실행 경로

1. 먼저 루트 README의 화면·영상 링크로 사용자 경험 확인.
2. Android README에 따라 JDK/SDK 준비 후 UI APK 빌드·실행.
3. 기술 연동을 확인하려면 integration/README.md에 따라 별도 로컬 통합 실행.

SDK/JDK, Python 가상환경, Node 의존성은 각자 설치합니다. 개발자의 절대 경로나 `.tools/` 설치물은 소스에 포함하지 않습니다. release APK는 서명되지 않은 개발 산출물이며 스토어 배포용이 아닙니다.

## 검증 기록

2026-09-14 제출 정리 중 아래 항목을 재실행했습니다.

| 항목 | 결과 |
| --- | --- |
| Android debug/release/test APK | 빌드 성공 |
| Android JVM debug / release | 29 / 25 통과 |
| Android 사용자 A/B Compose UI | 6개 통과 |
| Android Lint | 오류 0, 경고 20 |
| Backend | 21개 통과 |
| Contracts | 29개 통과, format/build 통과 |
| Dashboard | 17개 통과, production build 성공 |
| 루트 `make check` | 기존 Python/Contracts build·test 통과 |
| Android → HTTP → VerifierAdapter → MockVerifier → Registry 이벤트 → Room | 실제 로컬 통합 재실행 통과 |
| 변경 파일 공백 검사 | `git diff --check` 통과 |

Dashboard의 Backend API 브라우저 E2E는 이전 Integration P0에서 통과한 기록이 있으며, 이번 제출 정리에서는 Dashboard 단위 테스트와 production build를 재실행했습니다. 위 Android 통합 재실행을 Dashboard 브라우저 재검증으로 계산하지 않습니다.

기존 GitHub Actions는 Python/Contracts 검사만 실행합니다. 나머지는 위 로컬 검증이며, CI 통과를 전체 모듈 자동 검증으로 표현하지 않습니다.

## 제출 파일과 공개 범위

SDK/JDK, `.env`, local.properties, 로컬 DB, 의존성 설치물, 원시 로컬 빌드 로그와 수동으로 압축 해제한 중복 자료는 제출 커밋에서 제외합니다. 필요한 샘플 PNG/MP4/ZIP, UI 테스트와 재현 문서는 포함합니다.

소스 및 접근 가능한 커밋 변경 기록에 대해 주요 토큰·개인 키 패턴과 민감 설정 파일명을 검사했고 검출은 없었습니다. 이는 패턴 검사이며 무결점 보안 감사를 보장하지 않습니다. 로컬 Anvil 테스트 계정은 공개된 테스트용 fixture이며 실제 자산용으로 사용하지 않습니다.

공개 여부와 제출 SHA는 GitHub 저장소의 현재 상태 및 최종 제출 메시지를 기준으로 확인합니다. 공개 저장소는 소스와 커밋 기록을 누구나 열람·복제할 수 있습니다.

## 미구현 및 다음 작업

실제 음성 접근·TFLite 추론·지문 대조, 실제 zkML/Verifier, 운영 번호 승격 정책, 백그라운드 동기화·실통화 차단, ERC-4337 및 운영 배포는 미구현입니다. CallScreeningService는 현재 모든 전화를 허용합니다. 실제 모델로 0.5초 탐지나 0.1초 전파를 달성했다는 주장은 하지 않습니다.

제안서와 함께 제출할 설명:

> Tri-Defense 초기 PoC 소스코드입니다. Android·Backend·스마트 컨트랙트·Dashboard의 로컬 MOCK 통합과 사용자 경험 시연을 포함합니다. 실제 AI 추론, zkML 증명, ERC-4337 및 실통화 보호는 후속 구현 범위입니다.
