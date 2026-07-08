package com.omakase.kok.user.user.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.user.domain.user.entity.OwnerApproval;
import com.omakase.kok.user.domain.user.entity.User;
import com.omakase.kok.user.domain.user.enums.ApprovalStatus;
import com.omakase.kok.user.domain.user.enums.Role;
import com.omakase.kok.user.domain.user.repository.OwnerApprovalRepository;
import com.omakase.kok.user.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OWNER 승인/거절 통합 테스트")
class OwnerApprovalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    // ────────────────────────────────────────────────────
    // 승인
    // ────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/v1/admin/owners/approvals/{id}/approve - 승인")
    class Approve {

        @Test
        @Transactional
        @DisplayName("성공 - MASTER가 승인 → status APPROVED, processedAt/processedBy 기록")
        void success() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/owners/approvals/{id}/approve",
                            pendingApproval.getApprovalId())
                            .header("X-User-Id", masterUserId.toString())
                            .header("X-Role", "MASTER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"))
                    .andExpect(jsonPath("$.data.processedBy").value(masterUserId.toString()))
                    .andExpect(jsonPath("$.data.processedAt").isNotEmpty());

            OwnerApproval updated = ownerApprovalRepository.findById(pendingApproval.getApprovalId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
            assertThat(updated.getProcessedBy()).isEqualTo(masterUserId);
            assertThat(updated.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("실패 - USER role로 승인 요청 → 403")
        void fail_userRole() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/owners/approvals/{id}/approve",
                            pendingApproval.getApprovalId())
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-Role", "USER"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패 - OWNER role로 승인 요청 → 403")
        void fail_ownerRole() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/owners/approvals/{id}/approve",
                            pendingApproval.getApprovalId())
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-Role", "OWNER"))
                    .andExpect(status().isForbidden());
        }
    }

    // ────────────────────────────────────────────────────
    // 거절
    // ────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/v1/admin/owners/approvals/{id}/reject - 거절")
    class Reject {

        @Test
        @Transactional
        @DisplayName("성공 - MASTER가 거절 → status REJECTED, rejectReason 저장")
        void success() throws Exception {
            String body = objectMapper.writeValueAsString(
                    java.util.Map.of("rejectReason", "허위 매장 정보 기재")
            );

            mockMvc.perform(patch("/api/v1/admin/owners/approvals/{id}/reject",
                            pendingApproval.getApprovalId())
                            .header("X-User-Id", masterUserId.toString())
                            .header("X-Role", "MASTER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"))
                    .andExpect(jsonPath("$.data.rejectReason").value("허위 매장 정보 기재"))
                    .andExpect(jsonPath("$.data.processedBy").value(masterUserId.toString()));

            OwnerApproval updated = ownerApprovalRepository.findById(pendingApproval.getApprovalId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
            assertThat(updated.getRejectReason()).isEqualTo("허위 매장 정보 기재");
        }

        @Test
        @DisplayName("실패 - USER role로 거절 요청 → 403")
        void fail_userRole() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/owners/approvals/{id}/reject",
                            pendingApproval.getApprovalId())
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rejectReason\":\"test\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    // ────────────────────────────────────────────────────
    // 동시성 테스트 - @Version Optimistic Lock
    // ────────────────────────────────────────────────────

    @Nested
    @DisplayName("동시성 테스트 - Optimistic Lock")
    class ConcurrencyTest {

        @Test
        @Disabled("동시성 테스트는 PostgreSQL 환경에서만 유효. H2는 격리 수준 차이로 정확한 검증 불가")
        @DisplayName("동시 승인 요청 2개 → 한 건만 성공, 나머지 409 또는 500 (낙관적 잠금)")
        void concurrentApproveRequests() throws Exception {
            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        int status = mockMvc.perform(
                                        patch("/api/v1/admin/owners/approvals/{id}/approve",
                                                pendingApproval.getApprovalId())
                                                .header("X-User-Id", masterUserId.toString())
                                                .header("X-Role", "MASTER"))
                                .andReturn()
                                .getResponse()
                                .getStatus();

                        if (status == 200) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(1);

            OwnerApproval result = ownerApprovalRepository.findById(pendingApproval.getApprovalId()).orElseThrow();
            assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        }
    }
}