# Store Service

**매장 관리**
- 매장 등록/수정/상태 변경/조회, 랭킹 조회, 검색
- 메뉴 등록/수정/삭제/품절 처리, 편의시설·영업시간(요일별 일괄 등록)·이미지 관리
- 카테고리(대/소분류) 관리 및 관리자 전용 매장 강제 삭제

**검색/필터링 — QueryDSL 기반**
- 카테고리, 지역, 키워드, 편의시설 등 복합 조건 동적 쿼리
- 위도/경도/반경 기반 Haversine 거리 계산 및 거리순 정렬
- 생성일/평점/리뷰수/거리 기준 정렬 지원

**캐싱 전략**
- 검색 목록·랭킹 응답 Redis 캐싱(랭킹은 Redis ZSet으로 실시간 순위 관리, TTL 5분)
- Redis 장애 시 DB(QueryDSL) fallback, DB 커밋 후 캐시 무효화

**Kafka 연동**
- Outbox 패턴으로 `STORE_CREATED` 이벤트를 `store.events.v1`에 발행
- `review.events.v1` 구독 → 리뷰 평점/리뷰수 변경 시 매장 평점 갱신

**외부 연동**
- Feign + Resilience4j CircuitBreaker로 user-service의 사장님 승인 여부 조회
