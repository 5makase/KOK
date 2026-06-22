package com.kok.review.infrastructure.persistence;

import com.kok.review.domain.entity.ReviewEligibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReviewEligibilityRepository extends JpaRepository<ReviewEligibility, UUID> {
    Boolean existsByEventId(UUID eventId);
}
