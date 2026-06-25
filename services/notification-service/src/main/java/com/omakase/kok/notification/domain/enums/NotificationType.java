package com.omakase.kok.notification.domain.enums;

import java.util.Map;

public enum NotificationType {

    // ── 웨이팅 ──────────────────────────────────────────
    WAITING_REGISTERED {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 웨이팅이 등록되었습니다. 대기번호 %s번, %s명입니다.",
                    params.get("storeName"), params.get("waitingNumber"), params.get("peopleCount"));
        }
    },
    WAITING_NEAR_TURN {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 곧 입장입니다. (앞에 %s팀 남음)",
                    params.get("storeName"), params.get("remainingTeams"));
        }
    },
    WAITING_CALLED {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 입장 순서가 되었습니다. %s분 내로 입장해주세요.",
                    params.get("storeName"), params.get("callTimeoutMinutes"));
        }
    },
    WAITING_ENTERED {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 입장이 완료되었습니다. 즐거운 시간 되세요!",
                    params.get("storeName"));
        }
    },
    WAITING_CANCELLED {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 웨이팅이 취소되었습니다. 사유: %s",
                    params.get("storeName"), params.get("cancelReason"));
        }
    },
    WAITING_NO_SHOW {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 호출 후 미입장으로 인해 웨이팅이 취소 처리되었습니다.",
                    params.get("storeName"));
        }
    },
    WAITING_AUTO_CANCEL_WARNING {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 곧 자동 취소됩니다. 빠르게 입장해주세요.",
                    params.get("storeName"));
        }
    },

    // ── 예약 ──────────────────────────────────────────
    RESERVATION_RECEIVED {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 예약이 접수되었습니다.",
                    params.get("storeName"));
        }
    },
    RESERVATION_CONFIRMED {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 예약이 확정되었습니다. (%s %s, %s명)",
                    params.get("storeName"), params.get("slotDate"),
                    params.get("slotTime"), params.get("reservationSize"));
        }
    },
    RESERVATION_CANCELLED {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 예약이 취소되었습니다.",
                    params.get("storeName"));
        }
    },
    RESERVATION_CHANGED {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 예약이 변경되었습니다. (%s %s, %s명)",
                    params.get("storeName"), params.get("slotDate"),
                    params.get("slotTime"), params.get("reservationSize"));
        }
    },
    RESERVATION_REMINDER_1DAY {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 내일 방문 예정입니다. (%s %s)",
                    params.get("storeName"), params.get("slotDate"), params.get("slotTime"));
        }
    },
    RESERVATION_REMINDER_1HOUR {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 1시간 후 방문 예정입니다. (%s)",
                    params.get("storeName"), params.get("slotTime"));
        }
    },
    RESERVATION_NO_SHOW {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 노쇼 처리되었습니다. 패널티가 부과됩니다.",
                    params.get("storeName"));
        }
    },
    RESERVATION_NEW_OWNER {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[새 예약] %s명 / %s %s",
                    params.get("reservationSize"), params.get("slotDate"), params.get("slotTime"));
        }
    },

    // ── 결제 ──────────────────────────────────────────
    PAYMENT_COMPLETED {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 예약금 결제가 완료되었습니다. (%s원)",
                    params.get("storeName"), params.get("amount"));
        }
    },
    PAYMENT_REFUNDED {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 예약금이 환불되었습니다. (%s원)",
                    params.get("storeName"), params.get("amount"));
        }
    },
    PAYMENT_EXPIRY_WARNING {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 결제 유효시간이 임박했습니다.",
                    params.get("storeName"));
        }
    },

    // ── 리뷰 ──────────────────────────────────────────
    REVIEW_REQUEST {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 방문하셨나요? 리뷰를 작성해주세요.",
                    params.get("storeName"));
        }
    },
    REVIEW_REPORT_RESULT {
        @Override
        public String render(Map<String, Object> params) {
            return "신고하신 리뷰가 처리되었습니다.";
        }
    },
    REVIEW_REPLY {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 사장님이 답글을 달았습니다.",
                    params.get("storeName"));
        }
    },

    // ── 계정 ──────────────────────────────────────────
    USER_WELCOME {
        @Override
        public String render(Map<String, Object> params) {
            return "환영합니다! 콕(Kok) 회원이 되셨습니다.";
        }
    },
    USER_OWNER_APPROVED {
        @Override
        public String render(Map<String, Object> params) {
            return "점주 가입이 승인되었습니다.";
        }
    },
    USER_OWNER_REJECTED {
        @Override
        public String render(Map<String, Object> params) {
            return "점주 가입이 거절되었습니다.";
        }
    },
    USER_PASSWORD_CHANGED {
        @Override
        public String render(Map<String, Object> params) {
            return "비밀번호가 변경되었습니다.";
        }
    };

    public abstract String render(Map<String, Object> params);
}
