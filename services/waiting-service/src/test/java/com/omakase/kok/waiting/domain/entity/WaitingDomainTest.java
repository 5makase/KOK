package com.omakase.kok.waiting.domain.entity;

import com.omakase.kok.waiting.domain.enums.WaitingEventType;
import com.omakase.kok.waiting.global.exception.WaitingErrorCode;
import com.omakase.kok.waiting.global.exception.WaitingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaitingDomainTest {

    @Test
    @DisplayName("웨이팅은 WAITING 상태에서만 호출할 수 있다")
    void call_onlyWaitingStatus() {
        Waiting waiting = waiting();
        waiting.call();

        assertThat(waiting.getCalledAt()).isNotNull();
        assertThatThrownBy(waiting::call)
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_CALL_NOT_ALLOWED));
    }

    @Test
    @DisplayName("웨이팅은 CALLED 상태에서만 입장 완료 또는 미입장 처리할 수 있다")
    void enterAndNoShow_onlyCalledStatus() {
        Waiting waiting = waiting();

        assertThatThrownBy(waiting::enter)
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_ENTER_NOT_ALLOWED));
        assertThatThrownBy(() -> waiting.noShow("미방문"))
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_NO_SHOW_NOT_ALLOWED));
    }

    @Test
    @DisplayName("웨이팅은 종료 상태에서 취소할 수 없다")
    void cancel_notAllowedAfterEntered() {
        Waiting waiting = waiting();
        waiting.call();
        waiting.enter();

        assertThatThrownBy(() -> waiting.cancel("취소 요청"))
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_CANCEL_NOT_ALLOWED));
    }

    @Test
    @DisplayName("아웃박스 이벤트는 허용된 상태 전이만 처리한다")
    void outboxStatusTransitionValidation() {
        WaitingOutboxEvent event = WaitingOutboxEvent.builder()
                .waiting(waiting())
                .eventType(WaitingEventType.WAITING_REGISTERED)
                .payload("{}")
                .build();

        assertThatThrownBy(event::retry)
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_OUTBOX_STATUS_NOT_ALLOWED));

        event.publish();

        assertThatThrownBy(() -> event.fail("publish 이후 실패 처리"))
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_OUTBOX_STATUS_NOT_ALLOWED));
    }

    @Test
    @DisplayName("실패한 아웃박스 이벤트는 재시도 대기 상태로 되돌릴 수 있다")
    void outboxRetryAfterFailure() {
        WaitingOutboxEvent event = WaitingOutboxEvent.builder()
                .waiting(waiting())
                .eventType(WaitingEventType.WAITING_REGISTERED)
                .payload("{}")
                .build();

        event.fail("kafka down");
        event.retry();

        assertThat(event.getStatus()).isEqualTo(com.omakase.kok.waiting.domain.enums.OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getFailedReason()).isNull();
    }

    @Test
    @DisplayName("웨이팅 설정 수정값은 null을 유지하고 숫자 값만 양수를 검증한다")
    void waitingSettingUpdateValidation() {
        WaitingSetting setting = WaitingSetting.create(UUID.randomUUID(), true, 10, 5, true, 10);

        assertThatThrownBy(() -> setting.update(true, 0, 5, true, 10))
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_SETTING_INVALID));

        setting.update(null, 20, null, null, null);

        assertThat(setting.getWaitingEnabled()).isTrue();
        assertThat(setting.getMaxWaitingCount()).isEqualTo(20);
        assertThat(setting.getCallTimeoutMinutes()).isEqualTo(5);
        assertThat(setting.getAllowUserCancel()).isTrue();
    }

    @Test
    @DisplayName("웨이팅 설정 수정값은 평균 대기 시간을 0 이상으로만 허용한다")
    void waitingSettingUpdateAverageWaitingMinutes_nonNegative() {
        WaitingSetting setting = WaitingSetting.create(UUID.randomUUID(), true, 10, 5, true, 10);

        assertThatThrownBy(() -> setting.update(null, null, null, null, -1))
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_SETTING_INVALID));
    }

    private Waiting waiting() {
        return Waiting.builder()
                .storeId(UUID.randomUUID())
                .storeName("UNKNOWN")
                .userId(UUID.randomUUID())
                .visitorName("UNKNOWN")
                .waitingNumber(1L)
                .peopleCount(2)
                .requestMessage(null)
                .build();
    }
}
