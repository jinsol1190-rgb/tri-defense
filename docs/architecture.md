> P0 준비 업데이트: 전체 Tri-Defense 목표와 모듈 경계는 AGENTS.md를 따른다.
> 아래 내용은 기존 claim-record PoC의 데이터 계약이다. 아래의 zkML/자동 차단 제외는
> 기존 PoC 범위 설명이며 Tri-Defense 요구사항을 제거하지 않는다.
> 새 개발용 폴더는 문서 placeholder이고 실행 경로에 연결되지 않는다.
> backend/verification은 기존 미구현 확장점으로 보존하며, 향후 AI 구현은 ai/에 둔다.
> 환경 및 현재 제약: [P0 준비 보고서](p0-preparation.md).

# 범위와 데이터 계약

현재 구현: 저장소 scaffold, Python 데이터 모델, Solidity Registry, 단위/퍼즈 테스트.
FastAPI, SQLite 저장/상태 전이, web3.py 트랜잭션 처리, Integrity Checker,
음성 전처리 및 AI/Mock Detector 실행은 후속 작업이다. UI와 통화 시뮬레이션은 없다.

## 데이터 표현

| 필드 | Python / Solidity | 규칙 |
| --- | --- | --- |
| threatId | bytes / bytes32 | 32바이트, zero 금지; 사건 ID (향후 UUID 등을 32바이트로 인코딩) |
| audioHash | bytes / bytes32 | 원본 테스트 입력 바이트의 SHA-256, zero 금지 |
| aiResult | AIResult / uint8 | HUMAN=0, DEEPVOICE=1; Registry는 1만 등록 |
| riskScore | int / uint8 | 0~100 정수; 확률 보장 아님 |
| modelVersion | str / string | UTF-8 1~128바이트, ASCII 공백만인 값 금지 |
| timestamp | int / uint64 | 탐지 시각 Unix seconds, 1~2^64-1; 블록 시각 아님 |
| status | ThreatStatus / enum | SUSPECTED=0, VERIFIED=1, DISPUTED=2; 등록은 0만 |

audioHash는 화자/공격자 ID가 아니다. 동일 바이트를 재확인하는 값이며,
재인코딩/다른 발화의 유사성 탐지는 제외한다. 원본 음성은 온체인에 저장하지 않는다.
SHA-256 생성은 향후 Audio Processing이 수행한다. 모델은 해시의 형식만 검사한다.
형식 검사, 점수 검사, 재해시는 2단계 검증이 아니다.
Python은 modelVersion을 UTF-8로 인코딩한다. Solidity는 바이트 길이와 ASCII 공백 여부만
검사하며 UTF-8 문법 자체를 검증하지 않는다. 등록자는 정상 인코딩 문자열을 제공해야 한다.

## Registry 정책

- 생성자에서 정한 nonzero registrar만 쓰기 가능. 계정 변경/수정/삭제 함수 없음.
- 동일 threatId 또는 동일 audioHash 재등록은 revert. 모델이 달라도 같은 음성은
  기존 사건 ID를 조회해야 한다. 재시도 시 기존 7개 필드 비교는 후속 백엔드의 책임이다.
- 없는 threatId 조회는 ThreatNotFound, 없는 audioHash 조회는 bytes32(0).
- timestamp와 AI 결과는 등록자가 제출한 주장이다. 높은 점수를 강제하지 않는다.
  탐지 임계값은 향후 서비스 정책이며 모델/컨트랙트의 범위 검증과 구별한다.
- 최초 등록 status는 항상 SUSPECTED. VERIFIED/DISPUTED 전환은 미구현.
- 블록체인은 등록 이후 기록 비교의 기준이며 AI 판단의 정확성, 음성 출처,
  등록 전 DB의 진실성을 보장하지 않는다. 등록 계정 침해 시 허위 신규 기록은 가능하다.

## 후속 구현 경계

1. Audio Processing: 테스트 WAV 로드/전처리, 원본 바이트 SHA-256.
2. Detector: `predict(audio) -> {aiResult, riskScore, modelVersion}`.
   Mock 도입 시 고정 출력과 `MOCK` modelVersion으로 실제 AI와 명시적으로 구분.
3. SQLite 사건 상태: CREATED → AI_ANALYZED → CHAIN_PENDING →
   CHAIN_CONFIRMED 또는 CHAIN_FAILED. 온체인 status와 별개.
4. web3.py 등록/receipt 확인 후 DB 갱신.
5. Integrity Checker: DB와 체인의 7개 필드 직접 비교.
   MATCH / MISMATCH / NOT_REGISTERED / RPC_UNAVAILABLE 구별.
   RPC 실패는 MISMATCH가 아니다. DB 변조 데모는 이 단계에서 구현한다.
6. `verification/`은 NotImplementedError로 미구현을 알리는 2단계 확장 지점.
   실제 Artifact/Watermark/Provenance, zkML, 자동 차단은 현재 범위 밖이다.
