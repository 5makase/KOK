package com.kok.review.domain.repository;

import com.kok.review.domain.entity.ReviewRatingSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReviewRatingSummaryRepository extends JpaRepository<ReviewRatingSummary, UUID> {
}
