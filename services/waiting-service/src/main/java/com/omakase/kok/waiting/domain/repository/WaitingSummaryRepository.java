package com.omakase.kok.waiting.domain.repository;

import com.omakase.kok.waiting.domain.entity.WaitingSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WaitingSummaryRepository extends JpaRepository<WaitingSummary, UUID> {
    // 매장별 요약 조회
    Optional<WaitingSummary> findByStoreId(UUID storeId);
}
