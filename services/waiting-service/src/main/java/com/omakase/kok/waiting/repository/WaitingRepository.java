package com.omakase.kok.waiting.repository;

import com.omakase.kok.waiting.domain.entity.Waiting;
import com.omakase.kok.waiting.domain.enums.WaitingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WaitingRepository extends JpaRepository<Waiting, UUID> {
    // 매장별 상태 조회
    List<Waiting> findByStoreIdAndStatusOrderByWaitingNumberAsc(UUID storeId, WaitingStatus status);

    // 사용자별 전체 조회
    List<Waiting> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // 중복 웨이팅 여부 확인
    boolean existsByStoreIdAndUserIdAndStatus(UUID storeId, UUID userId, WaitingStatus status);
}
