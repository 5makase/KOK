package com.omakase.kok.store.global.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// afterCommit 공통화 - StoreService, StoreRatingService에서 중복 사용되던 패턴을 단일 진입점으로 통일
public class TransactionUtils {

    private TransactionUtils() {}

    /**
     * 트랜잭션 활성 시: DB 커밋 완료 후 action 실행 (롤백 시 실행되지 않아 Redis 불일치 방지)
     * 트랜잭션 없을 시: 대기 없이 즉시 실행 - @Transactional 없이 직접 호출되는 경우(단위 테스트 등)
     */
    public static void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
