package com.omakase.kok.waiting.infrastructure.redis;

import com.omakase.kok.waiting.global.exception.WaitingErrorCode;
import com.omakase.kok.waiting.global.exception.WaitingException;
import com.omakase.kok.waiting.domain.entity.Waiting;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WaitingQueueRedisStore {
    private final StringRedisTemplate redisTemplate;

    private static final String WAITING_QUEUE_KEY_PREFIX = "waiting:store:";
    private static final String WAITING_QUEUE_KEY_MIDDLE = ":queue:";
    private static final String WAITING_SEQUENCE_KEY_MIDDLE = ":sequence:";
    private static final String WAITING_ACTIVE_USER_KEY_MIDDLE = ":user:";
    private static final String WAITING_ACTIVE_USER_KEY_SUFFIX = ":active";
    private static final String WAITING_STORE_CACHE_KEY_SUFFIX = ":cache";
    private static final Duration WAITING_KEY_TTL = Duration.ofDays(3);
    private static final long WAITING_KEY_TTL_SECONDS = WAITING_KEY_TTL.toSeconds();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DefaultRedisScript<List> REGISTER_WAITING_SCRIPT = new DefaultRedisScript<>("""
            local queueKey = KEYS[1]
            local sequenceKey = KEYS[2]
            local activeUserKey = KEYS[3]
            local waitingId = ARGV[1]
            local maxWaitingCount = tonumber(ARGV[2])
            local ttlSeconds = tonumber(ARGV[3])
            local currentCount = redis.call('ZCARD', queueKey)

            if redis.call('EXISTS', activeUserKey) == 1 then
                return {-2, currentCount}
            end

            if currentCount >= maxWaitingCount then
                return {-1, currentCount}
            end

            local waitingNumber = redis.call('INCR', sequenceKey)
            redis.call('ZADD', queueKey, waitingNumber, waitingId)
            redis.call('SET', activeUserKey, waitingId, 'EX', ttlSeconds)
            
            redis.call('EXPIRE', queueKey, ttlSeconds)
            if redis.call('TTL', sequenceKey) == -1 then
                redis.call('EXPIRE', sequenceKey, ttlSeconds)
            end

            local rank = redis.call('ZRANK', queueKey, waitingId)
            return {waitingNumber, rank + 1}
            """, List.class);
    private static final DefaultRedisScript<Long> REMOVE_WAITING_SCRIPT = new DefaultRedisScript<>("""
            local queueKey = KEYS[1]
            local activeUserKey = KEYS[2]
            local waitingId = ARGV[1]

            local removed = redis.call('ZREM', queueKey, waitingId)
            if redis.call('GET', activeUserKey) == waitingId then
                redis.call('DEL', activeUserKey)
            end

            return removed
            """, Long.class);
    private static final DefaultRedisScript<Long> RESTORE_WAITING_QUEUE_SCRIPT = new DefaultRedisScript<>("""
            local queueKey = KEYS[1]
            local sequenceKey = KEYS[2]
            local ttlSeconds = tonumber(ARGV[1])
            local maxIssuedWaitingNumber = tonumber(ARGV[2])
            local activeUserKeyPattern = ARGV[3]
            local index = 4

            redis.call('DEL', queueKey)

            local cursor = '0'
            repeat
                local scanResult = redis.call('SCAN', cursor, 'MATCH', activeUserKeyPattern, 'COUNT', 100)
                cursor = scanResult[1]
                local activeUserKeys = scanResult[2]
                for keyIndex = 1, #activeUserKeys do
                    redis.call('DEL', activeUserKeys[keyIndex])
                end
            until cursor == '0'

            while index <= #ARGV do
                local waitingId = ARGV[index]
                local activeUserKey = ARGV[index + 1]
                local waitingNumber = tonumber(ARGV[index + 2])

                redis.call('ZADD', queueKey, waitingNumber, waitingId)
                redis.call('SET', activeUserKey, waitingId, 'EX', ttlSeconds)

                index = index + 3
            end

            redis.call('SET', sequenceKey, maxIssuedWaitingNumber, 'EX', ttlSeconds)
            if maxIssuedWaitingNumber > 0 then
                redis.call('EXPIRE', queueKey, ttlSeconds)
            end

            return maxIssuedWaitingNumber
            """, Long.class);

    private String formatDate(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }

    // 대기열 키
    private String queueKey(UUID storeId, LocalDate date) {
        return WAITING_QUEUE_KEY_PREFIX + storeId + WAITING_QUEUE_KEY_MIDDLE + formatDate(date);
    }

    // 웨이팅 번호 키
    private String sequenceKey(UUID storeId, LocalDate date) {
        return WAITING_QUEUE_KEY_PREFIX + storeId + WAITING_SEQUENCE_KEY_MIDDLE + formatDate(date);
    }

    // 사용자별 진행 중 웨이팅 키
    private String activeUserKey(UUID storeId, UUID userId, LocalDate date) {
        return WAITING_QUEUE_KEY_PREFIX + storeId + WAITING_ACTIVE_USER_KEY_MIDDLE
                + userId + ":" + formatDate(date) + WAITING_ACTIVE_USER_KEY_SUFFIX;
    }

    // 사용자별 진행 중 웨이팅 키 패턴
    private String activeUserKeyPattern(UUID storeId, LocalDate date) {
        return WAITING_QUEUE_KEY_PREFIX + storeId + WAITING_ACTIVE_USER_KEY_MIDDLE
                + "*:" + formatDate(date) + WAITING_ACTIVE_USER_KEY_SUFFIX;
    }

    // 매장 캐시 키
    private String storeCacheKey(UUID storeId) {
        return WAITING_QUEUE_KEY_PREFIX + storeId + WAITING_STORE_CACHE_KEY_SUFFIX;
    }

    // 웨이팅 등록
    public Optional<WaitingRegistration> register(
            UUID storeId,
            UUID userId,
            UUID waitingId,
            Integer maxWaitingCount,
            LocalDate waitingDate
    ) {
        List<Long> result = executeRegisterScript(storeId, userId, waitingId, maxWaitingCount, waitingDate);
        if (result.size() < 2) {
            throw new WaitingException(WaitingErrorCode.WAITING_REGISTER_FAILED);
        }
        long waitingNumber = ((Number) result.get(0)).longValue();
        Long currentRank = result.get(1);

        if (waitingNumber == -2) {
            throw new WaitingException(WaitingErrorCode.WAITING_ALREADY_EXISTS);
        }

        if (waitingNumber < 0) {
            return Optional.empty();
        }

        return Optional.of(new WaitingRegistration(waitingNumber, currentRank));
    }

    // 현재 순번 조회
    public Long getRank(UUID storeId, UUID waitingId, LocalDate waitingDate) {
        Long rank = redisTemplate.opsForZSet().rank(queueKey(storeId, waitingDate), waitingId.toString());
        if (rank == null) {
            return null;
        }
        return rank + 1;
    }

    // 대기열/사용자 중복 방지 키 제거
    public void remove(UUID storeId, UUID userId, UUID waitingId, LocalDate waitingDate) {
        redisTemplate.execute(
                REMOVE_WAITING_SCRIPT,
                List.of(queueKey(storeId, waitingDate), activeUserKey(storeId, userId, waitingDate)),
                waitingId.toString()
        );
    }

    // 다음 호출 대상 조회
    public Optional<UUID> findFirst(UUID storeId) {
        Set<String> waitingIds = redisTemplate.opsForZSet().range(queueKey(storeId, LocalDate.now()), 0, 0);
        if (waitingIds == null || waitingIds.isEmpty()) {
            return Optional.empty();
        }
        return waitingIds.stream()
                .findFirst()
                .map(UUID::fromString);
    }

    // 순번 임박 대상 조회
    public List<UUID> findNearTurn(UUID storeId, int threshold) {
        // 최대 상한선 가드문 배치 (과도한 량 조회 방지)
        if (threshold <= 0 || threshold > 50) {
            return List.of();
        }
        Set<String> waitingIds = redisTemplate.opsForZSet().range(queueKey(storeId, LocalDate.now()), 0, threshold - 1L);
        if (waitingIds == null || waitingIds.isEmpty()) {
            return List.of();
        }
        return waitingIds.stream()
                .map(UUID::fromString)
                .toList();
    }

    // 현재 대기 팀 수 조회
    public Long count(UUID storeId) {
        Long count = redisTemplate.opsForZSet().zCard(queueKey(storeId, LocalDate.now()));
        if (count == null) {
            return 0L;
        }
        return count;
    }

    // DB 기준 대기열 복원
    public void restoreQueue(UUID storeId, LocalDate waitingDate, List<Waiting> waitings, long maxIssuedWaitingNumber) {
        List<String> keys = List.of(queueKey(storeId, waitingDate), sequenceKey(storeId, waitingDate));
        List<String> args = new java.util.ArrayList<>();
        args.add(String.valueOf(WAITING_KEY_TTL_SECONDS));
        args.add(String.valueOf(maxIssuedWaitingNumber));
        args.add(activeUserKeyPattern(storeId, waitingDate));
        for (Waiting waiting : waitings) {
            args.add(waiting.getId().toString());
            args.add(activeUserKey(storeId, waiting.getUserId(), waitingDate));
            args.add(waiting.getWaitingNumber().toString());
        }
        redisTemplate.execute(RESTORE_WAITING_QUEUE_SCRIPT, keys, args.toArray());
    }

    // 매장 웨이팅 기준값 캐싱
    public void cacheStoreValues(
            UUID storeId,
            Boolean waitingEnabled,
            Integer maxWaitingCount,
            Integer callTimeoutMinutes,
            Boolean allowUserCancel,
            Integer averageWaitingMinutes
    ) {
        redisTemplate.opsForHash().putAll(
                storeCacheKey(storeId),
                Map.of(
                        "waitingEnabled", waitingEnabled.toString(),
                        "maxWaitingCount", maxWaitingCount.toString(),
                        "callTimeoutMinutes", callTimeoutMinutes.toString(),
                        "allowUserCancel", allowUserCancel.toString(),
                        "averageWaitingMinutes", averageWaitingMinutes.toString()
                )
        );
    }

    // 매장 웨이팅 기준값 조회
    public Optional<StoreWaitingValues> getStoreValues(UUID storeId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(storeCacheKey(storeId));
        if (values.isEmpty()) {
            return Optional.empty();
        }
        // 불완전한 캐시는 미스로 처리
        if (!hasAllStoreValueFields(values)) {
            return Optional.empty();
        }

        return Optional.of(new StoreWaitingValues(
                Boolean.parseBoolean(requiredValue(values, "waitingEnabled")),
                Integer.parseInt(requiredValue(values, "maxWaitingCount")),
                Integer.parseInt(requiredValue(values, "callTimeoutMinutes")),
                Boolean.parseBoolean(requiredValue(values, "allowUserCancel")),
                Integer.parseInt(requiredValue(values, "averageWaitingMinutes"))
        ));
    }

    // 필수 캐시 필드 확인
    private boolean hasAllStoreValueFields(Map<Object, Object> values) {
        return values.containsKey("waitingEnabled")
                && values.containsKey("maxWaitingCount")
                && values.containsKey("callTimeoutMinutes")
                && values.containsKey("allowUserCancel")
                && values.containsKey("averageWaitingMinutes");
    }

    // 캐시 필드 추출
    private String requiredValue(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        if (value == null) {
            throw new IllegalStateException("Missing waiting store cache field: " + field);
        }
        return value.toString();
    }

    // 웨이팅 등록 스크립트 실행
    @SuppressWarnings("unchecked")
    private List<Long> executeRegisterScript(
            UUID storeId,
            UUID userId,
            UUID waitingId,
            Integer maxWaitingCount,
            LocalDate waitingDate
    ) {
        return (List<Long>) redisTemplate.execute(
                REGISTER_WAITING_SCRIPT,
                List.of(
                        queueKey(storeId, waitingDate),
                        sequenceKey(storeId, waitingDate),
                        activeUserKey(storeId, userId, waitingDate)
                ),
                waitingId.toString(),
                maxWaitingCount.toString(),
                String.valueOf(WAITING_KEY_TTL.toSeconds())
        );
    }

    // 웨이팅 등록 결과
    public record WaitingRegistration(
            Long waitingNumber,
            Long currentRank
    ) {
    }

    // 매장 웨이팅 기준값
    public record StoreWaitingValues(
            Boolean waitingEnabled,
            Integer maxWaitingCount,
            Integer callTimeoutMinutes,
            Boolean allowUserCancel,
            Integer averageWaitingMinutes
    ) {
    }
}
