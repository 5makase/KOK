package com.omakase.kok.waiting.domain.repository;

import com.omakase.kok.waiting.domain.entity.Waiting;
import com.omakase.kok.waiting.domain.enums.WaitingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface WaitingRepository extends JpaRepository<Waiting, UUID> {
    // 매장별 상태 조회
    List<Waiting> findByStoreIdAndStatusOrderByWaitingNumberAsc(UUID storeId, WaitingStatus status);

    // 매장별 상태 페이지 조회
    Page<Waiting> findByStoreIdAndStatus(UUID storeId, WaitingStatus status, Pageable pageable);

    // 매장별 전체 페이지 조회
    Page<Waiting> findByStoreId(UUID storeId, Pageable pageable);

    // 사용자별 상태 페이지 조회
    Page<Waiting> findByUserIdAndStatus(UUID userId, WaitingStatus status, Pageable pageable);

    // 사용자별 전체 페이지 조회
    Page<Waiting> findByUserId(UUID userId, Pageable pageable);

    // 사용자별 전체 조회
    List<Waiting> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // 자동 미입장 처리 대상 조회
    List<Waiting> findByStatusAndCallExpiresAtLessThanEqualOrderByCallExpiresAtAsc(
            WaitingStatus status,
            LocalDateTime now,
            Pageable pageable
    );

    // 중복 웨이팅 여부 확인
    boolean existsByStoreIdAndUserIdAndStatus(UUID storeId, UUID userId, WaitingStatus status);

    // 진행 중인 웨이팅 여부 확인
    boolean existsByStoreIdAndUserIdAndStatusIn(UUID storeId, UUID userId, List<WaitingStatus> statuses);
}
