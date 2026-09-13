# 1~4단계 구현 보고 (2026-09-13)

## 1. Repository scaffold

- 생성한 파일: `.gitignore`, `pyproject.toml`, `foundry.toml`, `README.md`,
  `docs/architecture.md`, `backend/deepvoice/__init__.py`,
  `backend/deepvoice/verification/__init__.py`, `artifact_detector.py`,
  `watermark_checker.py`, `provenance_checker.py`.
- 구현한 내용: Git 초기화, Python/Solidity 디렉터리, 데이터 계약 및 범위 문서.
  2단계 placeholder는 호출 시 NotImplementedError를 발생시킨다.
- 테스트 결과: `python3 -m compileall -q backend` 통과.
- 실패한 테스트/문제: 시작 시 Git/Foundry가 없었음. Git 초기화 및 공식 GitHub
  release의 Foundry v1.8.1 macOS arm64 바이너리를 `.tools/`에 로컬 설치해 해결.
- 다음 단계 제안: Python 데이터 모델 구현 (완료).

## 2. Python ThreatRecord

- 생성한 파일: `backend/deepvoice/models.py`, `tests/test_models.py`.
- 구현한 내용: 7개 필드, 불변 dataclass, AIResult/ThreatStatus/ProcessingState,
  필드 범위 검사 및 등록 인자 변환. 범위 검사는 2단계 분석이 아니다.
- 테스트 결과: `PYTHONPATH=backend python3 -m unittest discover -s tests -v`:
  **11 passed, 0 failed**. 길이/점수/타임스탬프/enum, 등록 상태 제한,
  bool·문자열 암묵 변환 거부, placeholder의 명시적 실패를 확인.
- 실패한 테스트/문제: 없음. `MOCK-fixed-v1`은 테스트 fixture 문자열이며
  실제 AI 또는 Mock Detector 실행 결과가 아니다.
- 다음 단계 제안: Solidity Registry 구현 (완료).

## 3. Solidity ThreatRegistry

- 생성한 파일: `contracts/src/ThreatRegistry.sol`.
- 구현한 내용: registerThreat/getThreat/threatExists/getThreatIdByAudioHash,
  immutable 등록 계정 제한, 중복 ID/hash 거부, 필수 필드 검증,
  SUSPECTED 고정, ThreatRegistered 이벤트. 수정/삭제/계정 변경 함수 없음.
- 테스트 결과: `.tools/forge build`, Solidity 0.8.24 컴파일 성공.
  공개 ABI 검사에서 유일한 쓰기 함수가 registerThreat이며 조회 결과의
  필드 순서가 요구한 7개 필드와 일치함을 확인.
- 실패한 테스트/문제: 초기 lint 초기화/스타일 지적 수정. Registry 소스는
  수정 후 빌드에서 lint 지적 없음. 이는 보안 감사 또는 AI 정확성 검증이 아니다.
- 다음 단계 제안: Foundry 동작 테스트 (완료).

## 4. Foundry 테스트 및 CI 설정

- 생성한 파일: `contracts/test/ThreatRegistry.t.sol`, `.github/workflows/tests.yml`,
  `docs/implementation-report.md`.
- 구현한 내용: 등록/7개 필드 조회/이벤트/공개 조회/등록 계정/중복 처리/입력 경계/
  기존 기록 보존/퍼즈 테스트. Foundry 내장 cheatcode interface를 직접 선언해
  forge-std 등 외부 테스트 라이브러리 의존성을 없앴다.
- 테스트 결과: `.tools/forge test -vv`: **24 passed, 0 failed, 0 skipped**.
  퍼즈 4개 각각 256회, 총 1,024회. `.tools/forge fmt --check` 통과.
  Python과 합계 **35개 테스트 통과**.
- 실패한 테스트/문제: 테스트 실패 없음. 마지막 전체 `forge build`는 성공했지만,
  테스트 소스에는 lint 스타일 note와 `reentrancy-events`, `unused-return` 경고가 있다.
  각각 expectEmit cheatcode 뒤의 기대 이벤트 선언, expectRevert 뒤의 실패할 조회 호출을
  지적한다. 실제 Registry의 외부 호출/재진입 경고가 아니며 테스트 패턴으로 유지했다.
  GitHub Actions는 설정만 작성했고 원격 실행하지 않았다.
- 다음 단계 제안: 테스트 음성 SHA-256 + 명시적 고정 Mock Detector → SQLite →
  Anvil/web3.py receipt 처리 → DB 변조/체인 직접 비교 데모 순서로 구현.
  이번 작업에서는 진행하지 않았다.

## 검증 범위의 한계

현재 테스트는 Python 모델 및 Foundry EVM 내 스마트 컨트랙트 실행을 검증한다.
FastAPI, DB, Anvil RPC, web3.py, 실제 AI, Integrity Checker, 테스트넷 배포,
GitHub 업로드는 수행하지 않았다. 데이터 모델의 ProcessingState는 상태 값 정의이며
SQLite 저장소나 상태 전이 엔진 구현이 아니다.

블록체인은 등록 이후 탐지 기록 비교의 기준이다. AI 판단의 정확성 또는
등록 전 데이터의 진실성을 보장하지 않는다. 원본 음성은 온체인에 저장하지 않으며
audioHash는 화자/공격자 식별이나 유사 음성 탐지에 사용하지 않는다.
