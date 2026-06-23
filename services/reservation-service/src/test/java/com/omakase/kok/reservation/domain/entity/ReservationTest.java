package com.omakase.kok.reservation.domain.entity;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import com.omakase.kok.reservation.domain.exception.ReservationErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Reservation 도메인 테스트")
class ReservationTest {

    private Reservation reservation;

    @BeforeEach
    void setUp() {
        reservation = Reservation.builder()
                .slotId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .bookerName("홍길동")
                .bookerPhone("010-1234-5678")
                .reservationSize(2)
                .requestMessage("창가 자리 부탁드립니다")
                .build();
    }

    @Test
    @DisplayName("생성 직후 상태는 PAYMENT_PENDING이다")
    void initialStatus_isPaymentPending() {
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
    }

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        @Test
        @DisplayName("PAYMENT_PENDING 상태에서 confirm() 시 CONFIRMED로 전이된다")
        void success() {
            reservation.confirm();

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        @DisplayName("CONFIRMED 상태에서 confirm() 시 RESERVATION_NOT_CONFIRMABLE 예외가 발생한다")
        void fail_alreadyConfirmed() {
            reservation.confirm();

            assertThatThrownBy(() -> reservation.confirm())
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_CONFIRMABLE);
        }

        @Test
        @DisplayName("CANCELLED 상태에서 confirm() 시 RESERVATION_NOT_CONFIRMABLE 예외가 발생한다")
        void fail_fromCancelled() {
            reservation.cancel("USER", "단순 변심");

            assertThatThrownBy(() -> reservation.confirm())
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_CONFIRMABLE);
        }
    }

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("PAYMENT_PENDING 상태에서 취소 시 CANCELLED로 전이되고 취소 정보가 기록된다")
        void success_fromPending() {
            reservation.cancel("USER", "단순 변심");

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(reservation.getCancelledBy()).isEqualTo("USER");
            assertThat(reservation.getCancelReason()).isEqualTo("단순 변심");
            assertThat(reservation.getCancelledAt()).isNotNull();
        }

        @Test
        @DisplayName("CONFIRMED 상태에서 취소 시 CANCELLED로 전이된다")
        void success_fromConfirmed() {
            reservation.confirm();

            reservation.cancel("OWNER", "매장 사정");

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(reservation.getCancelledBy()).isEqualTo("OWNER");
        }

        @Test
        @DisplayName("VISITED 상태에서 취소 시 RESERVATION_NOT_CANCELLABLE 예외가 발생한다")
        void fail_fromVisited() {
            reservation.confirm();
            reservation.visit();

            assertThatThrownBy(() -> reservation.cancel("USER", "취소"))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_CANCELLABLE);
        }

        @Test
        @DisplayName("NO_SHOW 상태에서 취소 시 RESERVATION_NOT_CANCELLABLE 예외가 발생한다")
        void fail_fromNoShow() {
            reservation.confirm();
            reservation.noShow();

            assertThatThrownBy(() -> reservation.cancel("USER", "취소"))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_CANCELLABLE);
        }

        @Test
        @DisplayName("CANCELLED 상태에서 재취소 시 RESERVATION_NOT_CANCELLABLE 예외가 발생한다")
        void fail_fromCancelled() {
            reservation.cancel("USER", "취소");

            assertThatThrownBy(() -> reservation.cancel("USER", "재취소"))
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_CANCELLABLE);
        }
    }

    @Nested
    @DisplayName("visit()")
    class Visit {

        @Test
        @DisplayName("CONFIRMED 상태에서 visit() 시 VISITED로 전이되고 visitedAt이 기록된다")
        void success() {
            reservation.confirm();

            reservation.visit();

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.VISITED);
            assertThat(reservation.getVisitedAt()).isNotNull();
        }

        @Test
        @DisplayName("PAYMENT_PENDING 상태에서 visit() 시 RESERVATION_NOT_VISITABLE 예외가 발생한다")
        void fail_fromPending() {
            assertThatThrownBy(() -> reservation.visit())
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_VISITABLE);
        }

        @Test
        @DisplayName("CANCELLED 상태에서 visit() 시 RESERVATION_NOT_VISITABLE 예외가 발생한다")
        void fail_fromCancelled() {
            reservation.cancel("USER", "취소");

            assertThatThrownBy(() -> reservation.visit())
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_VISITABLE);
        }
    }

    @Nested
    @DisplayName("noShow()")
    class NoShow {

        @Test
        @DisplayName("CONFIRMED 상태에서 noShow() 시 NO_SHOW로 전이된다")
        void success() {
            reservation.confirm();

            reservation.noShow();

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        }

        @Test
        @DisplayName("PAYMENT_PENDING 상태에서 noShow() 시 RESERVATION_NOT_VISITABLE 예외가 발생한다")
        void fail_fromPending() {
            assertThatThrownBy(() -> reservation.noShow())
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_VISITABLE);
        }

        @Test
        @DisplayName("VISITED 상태에서 noShow() 시 RESERVATION_NOT_VISITABLE 예외가 발생한다")
        void fail_fromVisited() {
            reservation.confirm();
            reservation.visit();

            assertThatThrownBy(() -> reservation.noShow())
                    .isInstanceOf(BaseException.class)
                    .extracting(e -> ((BaseException) e).getErrorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_VISITABLE);
        }
    }

    @Nested
    @DisplayName("isCancellable()")
    class IsCancellable {

        @Test
        @DisplayName("PAYMENT_PENDING 상태는 취소 가능하다")
        void pendingIsCancellable() {
            assertThat(reservation.isCancellable()).isTrue();
        }

        @Test
        @DisplayName("CONFIRMED 상태는 취소 가능하다")
        void confirmedIsCancellable() {
            reservation.confirm();
            assertThat(reservation.isCancellable()).isTrue();
        }

        @Test
        @DisplayName("VISITED 상태는 취소 불가능하다")
        void visitedIsNotCancellable() {
            reservation.confirm();
            reservation.visit();
            assertThat(reservation.isCancellable()).isFalse();
        }

        @Test
        @DisplayName("NO_SHOW 상태는 취소 불가능하다")
        void noShowIsNotCancellable() {
            reservation.confirm();
            reservation.noShow();
            assertThat(reservation.isCancellable()).isFalse();
        }

        @Test
        @DisplayName("CANCELLED 상태는 취소 불가능하다")
        void cancelledIsNotCancellable() {
            reservation.cancel("USER", "취소");
            assertThat(reservation.isCancellable()).isFalse();
        }
    }
}
