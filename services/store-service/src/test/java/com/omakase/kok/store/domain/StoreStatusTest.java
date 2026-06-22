package com.omakase.kok.store.domain;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.domain.enums.StoreStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreStatusTest {

    @Test
    @DisplayName("PREPARING에서 OPEN으로 전이 가능")
    void preparing_to_open() {
        assertThatCode(() -> StoreStatus.PREPARING.validateTransitionTo(StoreStatus.OPEN))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = StoreStatus.class, names = {"CLOSED", "PERMANENTLY_CLOSED"})
    @DisplayName("PREPARING에서 OPEN 외 전이 불가")
    void preparing_to_invalid(StoreStatus next) {
        assertThatThrownBy(() -> StoreStatus.PREPARING.validateTransitionTo(next))
                .isInstanceOf(BaseException.class);
    }

    @ParameterizedTest
    @EnumSource(value = StoreStatus.class, names = {"CLOSED", "PERMANENTLY_CLOSED"})
    @DisplayName("OPEN에서 CLOSED, PERMANENTLY_CLOSED로 전이 가능")
    void open_to_valid(StoreStatus next) {
        assertThatCode(() -> StoreStatus.OPEN.validateTransitionTo(next))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = StoreStatus.class, names = {"PREPARING", "OPEN"})
    @DisplayName("OPEN에서 PREPARING, OPEN으로 전이 불가")
    void open_to_invalid(StoreStatus next) {
        assertThatThrownBy(() -> StoreStatus.OPEN.validateTransitionTo(next))
                .isInstanceOf(BaseException.class);
    }

    @Test
    @DisplayName("CLOSED에서 OPEN으로 전이 가능")
    void closed_to_open() {
        assertThatCode(() -> StoreStatus.CLOSED.validateTransitionTo(StoreStatus.OPEN))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = StoreStatus.class, names = {"PREPARING", "CLOSED", "PERMANENTLY_CLOSED"})
    @DisplayName("CLOSED에서 OPEN 외 전이 불가")
    void closed_to_invalid(StoreStatus next) {
        assertThatThrownBy(() -> StoreStatus.CLOSED.validateTransitionTo(next))
                .isInstanceOf(BaseException.class);
    }

    @ParameterizedTest
    @EnumSource(StoreStatus.class)
    @DisplayName("PERMANENTLY_CLOSED에서 어떤 상태로도 전이 불가")
    void permanently_closed_to_any(StoreStatus next) {
        assertThatThrownBy(() -> StoreStatus.PERMANENTLY_CLOSED.validateTransitionTo(next))
                .isInstanceOf(BaseException.class);
    }
}
