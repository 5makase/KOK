package com.omakase.kok.user.user.presentation.controller;

import com.omakase.kok.user.domain.user.entity.OwnerApproval;
import com.omakase.kok.user.domain.user.entity.User;
import com.omakase.kok.user.domain.user.enums.ApprovalStatus;
import com.omakase.kok.user.domain.user.enums.Role;
import com.omakase.kok.user.domain.user.repository.OwnerApprovalRepository;
import com.omakase.kok.user.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("OWNER 승인 동시성 테스트 - PostgreSQL")
class OwnerApprovalConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("kok-test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OwnerApprovalRepository ownerApprovalRepository;

    private UUID masterUserId;
    private OwnerApproval pendingApproval;

    @BeforeEach
    void setUp() {
        ownerApprovalRepository.deleteAll();
        userRepository.deleteAll();

        masterUserId = UUID.randomUUID();

        User ownerUser = userRepository.save(User.builder()
                .username("owneruser")
                .email("owner@example.com")
                .password("encodedPw")
                .name("점주")
                .phone("010-1111-2222")
                .role(Role.OWNER)
                .build());

        pendingApproval = ownerApprovalRepository.save(OwnerApproval.builder()
                .user(ownerUser)
                .status(ApprovalStatus.PENDING)
                .build());
    }

    @Test
    @DisplayName("동시 승인 요청 2개 → 한 건만 성공, 나머지 409 또는 500 (낙관적 잠금)")
    void concurrentApproveRequests() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await(); // 모든 스레드가 준비될 때까지 대기 후 동시 출발

                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-User-Id", masterUserId.toString());
                    headers.set("X-Role", "MASTER");

                    ResponseEntity<String> response = restTemplate.exchange(
                            "/api/v1/admin/owners/approvals/{id}/approve",
                            HttpMethod.PATCH,
                            new HttpEntity<>(headers),
                            String.class,
                            pendingApproval.getApprovalId()
                    );

                    if (response.getStatusCode().is2xxSuccessful()) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();   // 모든 스레드 준비 완료 대기
        start.countDown(); // 동시 출발 신호
        done.await();      // 모든 스레드 완료 대기
        executor.shutdown();

        // 정확히 한 건만 성공해야 함
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        // DB 최종 상태 확인
        OwnerApproval result = ownerApprovalRepository.findById(pendingApproval.getApprovalId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }
}