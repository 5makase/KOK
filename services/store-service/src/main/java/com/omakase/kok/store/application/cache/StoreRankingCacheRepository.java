package com.omakase.kok.store.application.cache;

import com.omakase.kok.store.application.result.StoreRankingResult;

import java.util.List;
import java.util.Optional;

// 랭킹 응답 전체 캐싱. 응답 JSON을 통째로 캐싱해 두 번째 요청부터 ZSet/DB 조회를 생략
// 키: store:ranking:response:{size}, TTL 5분, 평점 변경 이벤트 afterCommit 시 무효화
public interface StoreRankingCacheRepository {

    // Redis 장애 시 Optional.empty() 반환 → 호출부(StoreRatingService)에서 ZSet 기반 조회로 fallback
    // 주의: ZSet도 Redis 의존이라 Redis 전체 장애 시엔 함께 비어짐. DB 직접 조회 fallback은 미구현(TODO)
    Optional<List<StoreRankingResult>> get(int size);

    void set(int size, List<StoreRankingResult> results);

    // 평점 변경 시 size 무관하게 전체 삭제 - 랭킹 순서 자체가 바뀔 수 있으므로 부분 무효화 불가
    void evictAll();

}
