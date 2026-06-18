package com.omakase.kok.user.domain.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CurrentTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

import com.omakase.kok.user.domain.user.enums.ApprovalStatus;

@Entity
@Table(name = "p_owner_approvals")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OwnerApproval {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "approval_id", nullable = false, updatable = false)
    private UUID approvalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalStatus status;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();

        if (this.status == null) {
            this.status = ApprovalStatus.PENDING;
        }
    }

    public void approve(UUID processedBy) {
        this.status = ApprovalStatus.APPROVED;
        this.processedAt = LocalDateTime.now();
        this.processedBy = processedBy;
    }

    public void reject(UUID processedBy, String rejectReason) {
        this.status = ApprovalStatus.REJECTED;
        this.rejectReason = rejectReason;
        this.processedAt = LocalDateTime.now();
        this.processedBy = processedBy;
    }
}
