package com.omakase.kok.waiting.infrastructure.redis;

import com.omakase.kok.waiting.domain.entity.Waiting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class WaitingQueueRedisStoreTest {
    @Mock
    private StringRedisTemplate redisTemplate;

    private WaitingQueueRedisStore waitingQueueRedisStore;

    @BeforeEach
    void setUp() {
        waitingQueueRedisStore = new WaitingQueueRedisStore(redisTemplate);
    }

    @Test
    @DisplayName("DB 기준 대기열 복구는 Lua script로 Redis 키를 복원한다")
    void restoreQueue_restoresQueueWithLuaScript() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID waitingId = UUID.randomUUID();
        LocalDate waitingDate = LocalDate.of(2026, 7, 1);
        Waiting waiting = Waiting.builder()
                .id(waitingId)
                .storeId(storeId)
                .storeName("테스트 매장")
                .userId(userId)
                .waitingNumber(7L)
                .peopleCount(2)
                .build();
        setField(waiting, "createdAt", waitingDate.atTime(12, 0));
        String queueKey = "waiting:store:" + storeId + ":queue:20260701";
        String sequenceKey = "waiting:store:" + storeId + ":sequence:20260701";
        String activeUserKeyPattern = "waiting:store:" + storeId + ":user:*:20260701:active";
        String activeUserKey = "waiting:store:" + storeId + ":user:" + userId + ":20260701:active";

        waitingQueueRedisStore.restoreQueue(storeId, waitingDate, List.of(waiting), 11L);

        ArgumentCaptor<DefaultRedisScript> scriptCaptor = ArgumentCaptor.forClass(DefaultRedisScript.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(List.of(queueKey, sequenceKey)),
                aryEq(new Object[]{"259200", "11", activeUserKeyPattern, waitingId.toString(), activeUserKey, "7"})
        );
        assertThat(scriptCaptor.getValue().getScriptAsString())
                .contains("SCAN", "MATCH", "activeUserKeyPattern");
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }
}
