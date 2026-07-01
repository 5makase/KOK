package com.omakase.kok.store.infrastructure.client;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.store.global.exception.StoreErrorCode;
import com.omakase.kok.store.infrastructure.client.dto.OwnerApprovalResponse;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerApprovalAdapterTest {

    @Mock OwnerApprovalClient ownerApprovalClient;
    @InjectMocks OwnerApprovalAdapter adapter;

    private final UUID ownerId = UUID.randomUUID();

    // isApproved

    @Test
    @DisplayName("승인된 OWNER → true 반환")
    void isApproved_returns_true_when_approved() {
        OwnerApprovalResponse data = mock(OwnerApprovalResponse.class);
        when(data.getApproved()).thenReturn(true);
        when(ownerApprovalClient.getApprovalStatus(ownerId)).thenReturn(ApiResponse.success(data));

        assertThat(adapter.isApproved(ownerId)).isTrue();
    }

    @Test
    @DisplayName("미승인 OWNER → false 반환")
    void isApproved_returns_false_when_not_approved() {
        OwnerApprovalResponse data = mock(OwnerApprovalResponse.class);
        when(data.getApproved()).thenReturn(false);
        when(ownerApprovalClient.getApprovalStatus(ownerId)).thenReturn(ApiResponse.success(data));

        assertThat(adapter.isApproved(ownerId)).isFalse();
    }

    @Test
    @DisplayName("응답 data가 null → OWNER_APPROVAL_CHECK_FAILED")
    void isApproved_throws_when_data_is_null() {
        when(ownerApprovalClient.getApprovalStatus(ownerId))
            .thenReturn(ApiResponse.success(null));

        assertThatThrownBy(() -> adapter.isApproved(ownerId))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(StoreErrorCode.OWNER_APPROVAL_CHECK_FAILED);
    }

    @Test
    @DisplayName("approved 필드가 null → OWNER_APPROVAL_CHECK_FAILED")
    void isApproved_throws_when_approved_field_is_null() {
        // @NoArgsConstructor로 생성 시 Boolean approved = null
        when(ownerApprovalClient.getApprovalStatus(ownerId))
            .thenReturn(ApiResponse.success(new OwnerApprovalResponse()));

        assertThatThrownBy(() -> adapter.isApproved(ownerId))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(StoreErrorCode.OWNER_APPROVAL_CHECK_FAILED);
    }

    @Test
    @DisplayName("FeignException 발생 → OWNER_APPROVAL_CHECK_FAILED")
    void isApproved_throws_when_feign_exception() {
        when(ownerApprovalClient.getApprovalStatus(ownerId))
            .thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> adapter.isApproved(ownerId))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(StoreErrorCode.OWNER_APPROVAL_CHECK_FAILED);
    }

    // fallback (CircuitBreaker가 열리거나 타임아웃 시 호출 - reflection으로 직접 검증)

    @Test
    @DisplayName("fallback - BaseException은 그대로 재전파")
    void fallback_rethrows_base_exception() {
        BaseException original = new BaseException(StoreErrorCode.OWNER_APPROVAL_CHECK_FAILED);

        assertThatThrownBy(() -> invokeFallback(ownerId, original))
            .isSameAs(original);
    }

    @Test
    @DisplayName("fallback - 인프라 장애(non-BaseException) → OWNER_APPROVAL_CHECK_FAILED")
    void fallback_wraps_infrastructure_exception() {
        RuntimeException cause = new RuntimeException("circuit open");

        assertThatThrownBy(() -> invokeFallback(ownerId, cause))
            .isInstanceOf(BaseException.class)
            .extracting(e -> ((BaseException) e).getErrorCode())
            .isEqualTo(StoreErrorCode.OWNER_APPROVAL_CHECK_FAILED);
    }

    // helper

    private void invokeFallback(UUID ownerId, Throwable t) throws Throwable {
        try {
            Method method = OwnerApprovalAdapter.class
                .getDeclaredMethod("isApprovedFallback", UUID.class, Throwable.class);
            method.setAccessible(true);
            method.invoke(adapter, ownerId, t);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
