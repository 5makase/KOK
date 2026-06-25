package com.omakase.kok.notification.domain.enums;

public enum NotificationSendStatus {
    PENDING,  // 발송 대기
    SENT,     // 발송 성공
    FAILED,   // 발송 실패 (재시도 대상)
    SKIPPED   // slack_id 없음 (재시도 불필요)
}
