package com.kok.review.global.exception;

import com.omakase.kok.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

    // ===== 404 NOT_FOUND : 대상 리소스 없음 =====
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW-404-001", "리뷰가 존재하지 않습니다."),
    REPLY_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW-404-002", "답글이 존재하지 않습니다."),
    ELIGIBILITY_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW-404-003", "리뷰 작성 권한 정보가 존재하지 않습니다."),
    RATING_SUMMARY_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW-404-004", "평점 집계 정보가 존재하지 않습니다."),

    // ===== 403 FORBIDDEN : 권한 없음 =====
    NOT_OWNER_ROLE(HttpStatus.FORBIDDEN, "REVIEW-403-001", "사장님 권한이 없습니다."),
    NOT_USER_ROLE(HttpStatus.FORBIDDEN, "REVIEW-403-002", "사용자 권한이 없습니다."),
    NOT_STORE_OWNER(HttpStatus.FORBIDDEN, "REVIEW-403-003", "해당 매장의 소유주가 아닙니다."),
    NOT_REVIEW_AUTHOR(HttpStatus.FORBIDDEN, "REVIEW-403-004", "본인이 작성한 리뷰가 아닙니다."),
    NOT_REPLY_AUTHOR(HttpStatus.FORBIDDEN, "REVIEW-403-005", "본인이 작성한 답글이 아닙니다."),

    // ===== 409 CONFLICT : 상태 충돌 / 중복 =====
    REPLY_ALREADY_EXISTS(HttpStatus.CONFLICT, "REVIEW-409-001", "이미 답글이 존재합니다."),
    DUPLICATE_REVIEW(HttpStatus.CONFLICT, "REVIEW-409-002", "이미 작성한 리뷰가 존재합니다."),

    // ===== 400 BAD_REQUEST : 요청 데이터 부정합 =====
    ELIGIBILITY_USER_MISMATCH(HttpStatus.BAD_REQUEST, "REVIEW-400-001", "리뷰 작성 권한의 사용자와 일치하지 않습니다."),
    ELIGIBILITY_STORE_MISMATCH(HttpStatus.BAD_REQUEST, "REVIEW-400-002", "리뷰 작성 권한의 매장과 일치하지 않습니다."),

    // ===== 500 INTERNAL_SERVER_ERROR : 시스템 오류 =====
    EVENT_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "REVIEW-500-001", "이벤트 직렬화에 실패했습니다."),

    // ===== 503 SERVICE_UNAVAILABLE : 외부 서비스 장애 =====
    STORE_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "REVIEW-503-001", "매장 서비스가 일시적으로 응답하지 않아 요청을 처리할 수 없습니다."),

    // 403
    NOT_ADMIN_ROLE(HttpStatus.FORBIDDEN, "REVIEW-403-007", "관리자 권한이 없습니다."),
    CANNOT_REPORT_OWN_REVIEW(HttpStatus.FORBIDDEN, "REVIEW-403-006", "본인이 작성한 리뷰는 신고할 수 없습니다."),

    // 404
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW-404-005", "신고 내역이 존재하지 않습니다."),

    // 409
    DUPLICATE_REPORT(HttpStatus.CONFLICT, "REVIEW-409-003", "이미 신고한 리뷰입니다."),
    REPORT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "REVIEW-409-004", "이미 처리된 신고입니다."),

    // 400
    REPORT_DETAIL_REQUIRED(HttpStatus.BAD_REQUEST, "REVIEW-400-003", "기타 사유는 상세 입력이 필요합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
