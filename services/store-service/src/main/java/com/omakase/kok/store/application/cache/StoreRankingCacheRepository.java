package com.omakase.kok.store.application.cache;

import com.omakase.kok.store.application.result.StoreRankingResult;

import java.util.List;
import java.util.Optional;

// 랭킹 응답 전체 캐싱. 응답 JSON을 통째로 캐싱해 두 번째 요청부터 ZSet/DB 조회를 생략
// 키: store:ranking:response:{size}, TTL 5분, 평점 변경 이벤트 afterCommit 시 무효화
public interface StoreRankingCacheRepository {

    // Redis 장애 시 Optional.empty() 반환 → DB 조회로 fallback
    Optional<List<StoreRankingResult>> get(int size);

    void set(int size, List<StoreRankingResult> results);

    // 평점 변경 시 size 무관하게 전체 삭제 - 랭킹 순서 자체가 바뀔 수 있으므로 부분 무효화 불가
    void evictAll();

}
