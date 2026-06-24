package com.kok.review.presentation.DTO1.response;

import com.kok.review.domain.entity.Review;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ReviewGetResponseDto {
    private UUID reviewId;
    private UUID storeId;
    private UUID userId;
    private BigDecimal rating;
    private String content;
    private List<String> imageUrls;
    private int likeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String userName;
    private String storeName;

    // --- 나중에 채움 (다른 서비스 / 미구현 기능) ---
//    private boolean isBlinded;
//    private Boolean isLiked;               // 좋아요 기능 후
//    private OwnerCommentDto ownerComment;  // 사장님 댓글 기능 후

    public static ReviewGetResponseDto from(Review review, List<String> imageUrls, String usernName, String storeName){
        return ReviewGetResponseDto.builder()
                .reviewId(review.getReviewId())
                .storeId(review.getStoreId())
                .userId(review.getUserId())
                .rating(review.getRating())
                .content(review.getContent())
                .imageUrls(imageUrls)
                .likeCount(review.getLikeCount())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .userName(usernName)
                .storeName(storeName)
                .build();
    }

    //목록 조회용 - 임시
    public static ReviewGetResponseDto from2(Review review, List<String>imageUrls){
        return ReviewGetResponseDto.builder()
                .reviewId(review.getReviewId())
                .storeId(review.getStoreId())
                .userId(review.getUserId())
                .likeCount(review.getLikeCount())
                .content(review.getContent())
                .imageUrls(imageUrls)
                .rating(review.getRating())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
