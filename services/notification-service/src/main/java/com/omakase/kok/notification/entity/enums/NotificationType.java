package com.omakase.kok.notification.entity.enums;

import java.util.Map;

public enum NotificationType {

    // ── 웨이팅 ──────────────────────────────────────────
    WAITING_REGISTERED {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 현재 %s번째 대기 중입니다.",
                    params.get("storeName"), params.get("waitingNumber"));
        }
    },
    WAITING_CALLED {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 곧 입장입니다. (앞에 %s팀 남음)",
                    params.get("storeName"), params.get("remainingTeams"));
        }
    },
    WAITING_ENTERED {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("⭐ [%s] 지금 입장해주세요! (대기번호 %s번)",
                    params.get("storeName"), params.get("waitingNumber"));
        }
    },

    WAITING_NO_SHOW {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 노쇼 처리되었습니다.",
                    params.get("storeName"));
        }
    },

    WAITING_CANCELLED {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[%s] 웨이팅이 취소되었습니다.",
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
            return String.format("[%s] 노쇼 처리되었습니다.",
                    params.get("storeName"));
        }
    },
    RESERVATION_NEW_OWNER {
        @Override
        public String render(Map<String, Object> params) {
            return String.format("[새 예약] %s명 / %s %s",
                    params.get("reservationSize"), params.get("slotDate"), params.get("slotTime"));
        }
    };

    public abstract String render(Map<String, Object> params);
}
