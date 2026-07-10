# k6 Load Test

서비스별 k6 부하 테스트 모음. 폴더 구조는 `jmeter/`와 같은 컨벤션을 따른다.

```text
k6/
  <service>/
    README.md          # 그 서비스의 테스트 목록·실행 방법
    <endpoint>-load-test-<vu|rps>.js
```

## 서비스별 테스트

| 서비스 | 문서 |
|---|---|
| store-service | [k6/store/README.md](store/README.md) |

## 새 서비스 테스트 추가 시

1. `k6/<service>/` 폴더를 새로 만든다 (예: `k6/waiting/`, `k6/reservation/`).
2. 그 폴더 안에 스크립트와 함께 `README.md`를 작성 권장  
   - [테스트 목록 표 / 실행 모델 / 실행 전 준비 / 실행 / 부하 프로파일 / 임계값 / 새 테스트 추가 시] 등 구조를 참고해서 그 서비스에 맞게 작성한다.
3. 이 파일의 "서비스별 테스트" 표에 한 줄 추가한다.
