package com.kok.review.domain.entity;

import com.kok.review.presentation.DTO1.request.ReviewReplyRequestDto;
import com.omakase.kok.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_review_replies",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_reply_review_id",
                columnNames = "review_id"))
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReply extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reply_id", updatable = false)
    private UUID replyId;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;            // 작성한 소유주 userId

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder
    private ReviewReply(UUID reviewId, UUID storeId, UUID ownerId, String content) {
        this.reviewId = reviewId;
        this.storeId = storeId;
        this.ownerId = ownerId;
        this.content = content;
    }

    public static ReviewReply create(UUID reviewId, UUID storeId, UUID ownerId, String content) {
        return ReviewReply.builder()
                .reviewId(reviewId)
                .storeId(storeId)
                .ownerId(ownerId)
                .content(content)
                .build();
    }

    // 답글 수정
    public void update(ReviewReplyRequestDto dto) {
        this.content = dto.getContent();
    }
}
