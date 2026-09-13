# Tri-Defense — P0 development scaffold

현재 작업 범위는 개발용 저장소 구조·빌드 설정·placeholder 준비다.
Android/AI/zkML PoC 및 P1/P2 통합 기능은 구현하지 않았다.
구현 명세(Codex)와 제안서는 현재 저장소에 없으므로 플랫폼·모델·API를 임의로 선택하지 않았다.

## 상태

| 모듈 | 구분 | 현재 상태 |
| --- | --- | --- |
| android/ | SAMPLE | 역할 문서만 존재; 앱/Gradle 빌드 없음 |
| ai/ | SAMPLE | 전처리·경량/정밀 모델·voiceprint·평가 폴더만 존재 |
| backend/ | REAL / SAMPLE | 기존 데이터 모델 / 미구현 placeholder; 서비스 없음 |
| contracts/ | REAL / SAMPLE | 기존 claim Registry / Verifier·배포 placeholder |
| dashboard/ | SAMPLE | 시각화 역할 문서만 존재; 앱 빌드 없음 |
| shared/ | SAMPLE | DTO·스키마·상수·API 계약용 폴더; 계약 정의 없음 |
| docs/ | REAL | 구조·환경·제약·검증 기록 |

SAMPLE은 구조 예시이며 실행 가능한 기능이나 AI 결과를 뜻하지 않는다.
기존 Registry는 Proof를 검증하지 않는다. Tri-Defense의 검증된 위협 Registry 요건을 충족하지 않는다.
기존 코드는 보존했으며 AI·블록체인·백엔드 로직을 추가하지 않았다.

## 개발 환경 및 빌드

필수: Python 3.9 이상(`venv`, `pip` 포함), Make, Foundry 1.8.1.
Solidity 0.8.24는 Foundry 설정으로 선택한다. 최초 설치/컴파일에는 네트워크가 필요할 수 있다.
Python 패키지 빌드의 `setuptools>=61`은 pip의 격리 빌드 환경에 설치하므로 시스템 Python을 변경하지 않는다.
런타임 외부 Python 의존성은 없다.

```sh
make setup
make check
```

- `make setup`: `.venv` 생성, 기존 Python 패키지 설치, 의존성 검사.
- `make build`: Python wheel(`dist/`) 및 기존 Solidity 코드(`out/`) 빌드.
- `make test`: 설치된 Python 패키지 테스트, Solidity 포맷 검사와 Foundry 테스트.
- 소스를 변경하면 `make setup`으로 설치본을 갱신한 뒤 검사한다.
- `.tools/forge`가 있으면 자동 사용한다. 그 외에는 PATH의 `forge`를 사용한다.
  다른 위치는 `make check FORGE=/absolute/path/to/forge`로 지정한다.
- Android·AI·dashboard·shared는 문서 placeholder이며 빌드 성공 대상으로 계산하지 않는다.
- 실행할 전체 앱/서버는 아직 없다.

환경 검증과 남은 작업은 [P0 준비 보고서](docs/p0-preparation.md)를 참고한다.
아래는 보존된 기존 PoC 설명이며, 후속 작업 제안은 최신 AGENTS.md와 구현 명세 검토를 우선한다.

---

# Deepvoice Threat Registry PoC

‘Web3 시대의 블록체인 AI 융합 해커톤’ 트랙1: AI + 블록체인 융합 서비스.

통화 중 딥보이스 탐지 결과의 핵심 데이터를 블록체인에 기록하고,
등록 이후 중앙 DB 기록의 변조 여부를 비교할 수 있도록 하는 프로젝트다.
블록체인은 AI 판단의 정확성을 보장하지 않는다.

## 현재 구현 범위 (1~4단계)

- Repository scaffold 및 2단계 검증 placeholder
- Python ThreatRecord 모델
- Solidity ThreatRegistry (SUSPECTED 등록 및 조회)
- Python 모델 테스트 및 Foundry 테스트

실제 AI, Mock Detector, 통화 연동, FastAPI, SQLite, web3.py,
DB 변조 탐지 데모와 테스트넷 배포는 **아직 구현하지 않았다**.
검증 placeholder는 성공 결과를 반환하지 않고 NotImplementedError를 발생시킨다.
WAV는 향후 통화 음성을 대신하는 테스트 입력이며 파일 업로드 서비스가 아니다.

## 실행

Python 3.9 이상, Foundry v1.8.1, Solidity 0.8.24.
Python 모델 테스트에는 외부 패키지가 필요 없다.

```sh
PYTHONPATH=backend python3 -m unittest discover -s tests -v
forge fmt --check
forge test -vv
```

이 작업 환경에서 Foundry 바이너리는 gitignore 처리된 `.tools/`에 설치했다.
해당 환경은 `forge` 대신 `.tools/forge`를 사용하면 된다.
다른 환경에서는 [Foundry 공식 설치 안내](https://getfoundry.sh/getting-started/installation)를 따른다.
최초 컴파일에는 Solidity 컴파일러 다운로드를 위한 네트워크가 필요하다.

## 구조

```text
backend/deepvoice/models.py        Python 데이터 계약
backend/deepvoice/verification/    미구현 2단계 분석 확장 지점
contracts/src/ThreatRegistry.sol  변경/삭제 없는 Registry
contracts/test/                   Foundry 단위/퍼즈 테스트
tests/                            Python 모델 테스트
docs/architecture.md              필드 표현, 중복 정책, 후속 범위
```

온체인에는 원본 음성을 저장하지 않는다. audioHash는 SHA-256이며 동일 바이트
재확인 용도로만 사용한다. 동일 화자, 동일 공격자, 재인코딩 음성 식별은 지원하지 않는다.
자세한 정책은 [데이터 계약](docs/architecture.md)을 참고한다.
