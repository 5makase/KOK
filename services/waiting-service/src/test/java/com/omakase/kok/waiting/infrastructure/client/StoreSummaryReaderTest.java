package com.omakase.kok.waiting.infrastructure.client;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.waiting.global.exception.WaitingErrorCode;
import com.omakase.kok.waiting.global.exception.WaitingException;
import com.omakase.kok.waiting.infrastructure.client.dto.StoreSummaryResponse;
import com.omakase.kok.waiting.infrastructure.redis.StoreSummaryCacheRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class StoreSummaryReaderTest {
    @Mock
    private StoreFeignClient storeFeignClient;

    @Mock
    private StoreSummaryCacheRepository storeSummaryCacheRepository;

    @InjectMocks
    private StoreSummaryReader storeSummaryReader;

    @Test
    @DisplayName("매장 요약 응답이 비어 있으면 도메인 예외를 던진다")
    void getStoreSummary_throwsWhenPayloadIsEmpty() {
        UUID storeId = UUID.randomUUID();
        given(storeSummaryCacheRepository.get(storeId)).willReturn(java.util.Optional.empty());
        given(storeFeignClient.getStoreSummary(storeId)).willReturn(ApiResponse.success((StoreSummaryResponse) null));

        assertThatThrownBy(() -> storeSummaryReader.getStoreSummary(storeId))
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_STORE_SUMMARY_UNAVAILABLE));
    }

    @Test
    @DisplayName("매장 요약 응답이 완전하면 데이터를 반환한다")
    void getStoreSummary_returnsSummary() {
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        StoreSummaryResponse expected = StoreSummaryResponse.of(storeId, "테스트 매장", ownerId);
        given(storeSummaryCacheRepository.get(storeId)).willReturn(java.util.Optional.empty());
        given(storeFeignClient.getStoreSummary(storeId)).willReturn(ApiResponse.success(expected));

        StoreSummaryResponse actual = storeSummaryReader.getStoreSummary(storeId);

        assertThat(actual).isSameAs(expected);
        then(storeSummaryCacheRepository).should().set(storeId, expected);
    }

    @Test
    @DisplayName("매장 요약 캐시가 있으면 store-service를 호출하지 않는다")
    void getStoreSummary_cacheHitSkipsStoreService() {
        UUID storeId = UUID.randomUUID();
        StoreSummaryResponse cached = StoreSummaryResponse.of(storeId, "캐시 매장", UUID.randomUUID());
        given(storeSummaryCacheRepository.get(storeId)).willReturn(java.util.Optional.of(cached));

        StoreSummaryResponse actual = storeSummaryReader.getStoreSummary(storeId);

        assertThat(actual).isSameAs(cached);
        then(storeFeignClient).should(never()).getStoreSummary(storeId);
    }
}
