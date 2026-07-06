package com.omakase.kok.store.application.validator;

import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.entity.StoreCategory;
import com.omakase.kok.store.domain.vo.Address;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreOwnerValidatorTest {

    private final StoreOwnerValidator validator = new StoreOwnerValidator();

    private Store store;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        StoreCategory category = StoreCategory.create("한식", 1, null);
        store = Store.create(ownerId, category, "테스트 매장", null,
                new Address("서울", "강남구", null, null, null, null), null, null);
    }

    @Test
    @DisplayName("MASTER는 소유권과 무관하게 항상 통과")
    void master_bypasses_ownership_check() {
        assertThatCode(() ->
                validator.validate(store, UUID.randomUUID(), AuthConstants.MASTER, StoreErrorCode.STORE_ACCESS_DENIED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OWNER + 소유자 본인이면 통과")
    void owner_with_matching_id_passes() {
        assertThatCode(() ->
                validator.validate(store, ownerId, AuthConstants.OWNER, StoreErrorCode.STORE_ACCESS_DENIED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OWNER지만 소유자가 아니면 거부")
    void owner_with_mismatched_id_is_denied() {
        assertThatThrownBy(() ->
                validator.validate(store, UUID.randomUUID(), AuthConstants.OWNER, StoreErrorCode.STORE_ACCESS_DENIED))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_ACCESS_DENIED);
    }

    @Test
    @DisplayName("MASTER/OWNER가 아닌 역할은 소유권 확인 없이 즉시 거부 - 컨트롤러 우회 방어")
    void role_other_than_master_or_owner_is_denied_immediately() {
        // 소유자 본인 ID로 호출해도, 역할 자체가 허용되지 않으면 거부돼야 한다
        assertThatThrownBy(() ->
                validator.validate(store, ownerId, AuthConstants.USER, StoreErrorCode.STORE_ACCESS_DENIED))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_ACCESS_DENIED);
    }

    @Test
    @DisplayName("errorCode는 호출부가 전달한 값 그대로 사용된다")
    void thrown_exception_uses_caller_provided_error_code() {
        assertThatThrownBy(() ->
                validator.validate(store, UUID.randomUUID(), AuthConstants.USER, StoreErrorCode.STORE_HOURS_REQUIRED_FOR_OPEN))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.STORE_HOURS_REQUIRED_FOR_OPEN);
    }
}
