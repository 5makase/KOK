package com.kok.review.domain.repository;

import com.kok.review.domain.entity.ReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReviewReplyRepository extends JpaRepository<ReviewReply, UUID> {
    boolean existsByReviewId(UUID ReviewId);
    ReviewReply findByReviewId(UUID ReviewId);
}
