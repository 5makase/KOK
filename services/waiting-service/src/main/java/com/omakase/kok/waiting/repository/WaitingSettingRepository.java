package com.omakase.kok.waiting.repository;

import com.omakase.kok.waiting.domain.entity.WaitingSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WaitingSettingRepository extends JpaRepository<WaitingSetting, UUID> {
    // 매장별 설정 조회
    Optional<WaitingSetting> findByStoreId(UUID storeId);
}
