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
