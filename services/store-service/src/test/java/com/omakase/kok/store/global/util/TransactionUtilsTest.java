package com.omakase.kok.store.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TransactionUtils.runAfterCommit()이 실제 트랜잭션 커밋/롤백에 맞춰 동작하는지 검증.
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransactionUtilsTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("트랜잭션이 커밋되기 전에는 action이 실행되지 않고, 커밋된 후에 실행된다")
    void action_runs_only_after_commit() {
        AtomicBoolean ran = new AtomicBoolean(false);
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        template.execute(status -> {
            TransactionUtils.runAfterCommit(() -> ran.set(true));
            assertThat(ran.get()).isFalse(); // 커밋 전에는 아직 실행되지 않아야 함
            return null;
        });

        assertThat(ran.get()).isTrue(); // 트랜잭션 종료(커밋) 후에는 실행돼야 함
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 action은 실행되지 않는다 - Redis 불일치 방지 보장")
    void action_does_not_run_when_rolled_back() {
        AtomicBoolean ran = new AtomicBoolean(false);
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        template.execute(status -> {
            TransactionUtils.runAfterCommit(() -> ran.set(true));
            status.setRollbackOnly();
            return null;
        });

        assertThat(ran.get()).isFalse();
    }

    @Test
    @DisplayName("활성화된 트랜잭션이 없으면 대기 없이 즉시 실행된다")
    void action_runs_immediately_when_no_transaction_active() {
        AtomicBoolean ran = new AtomicBoolean(false);

        TransactionUtils.runAfterCommit(() -> ran.set(true));

        assertThat(ran.get()).isTrue();
    }
}
