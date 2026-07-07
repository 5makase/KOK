package com.omakase.kok.user.domain.user.repository;

import com.omakase.kok.user.domain.user.entity.OwnerApproval;
import com.omakase.kok.user.domain.user.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OwnerApprovalRepository extends JpaRepository<OwnerApproval, UUID> {

    // 승인 요청별 목록 가져오기
    List<OwnerApproval> findByStatus(ApprovalStatus status);

    // 여러번 승인 요청을 할 수 있으므로, 중복 승인 요청 방지용
    boolean existsByUser_UserIdAndStatus(UUID userId, ApprovalStatus status);

    // 재신청이 가능해 유저당 여러 건이 존재할 수 있으므로, 최신 승인 이력 1건만 조회
    // 고도화: (OWNER 승인 상태 조회 내부 API에서 사용)
    Optional<OwnerApproval> findFirstByUser_UserIdOrderByCreatedAtDesc(UUID userId);
}
