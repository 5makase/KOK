package com.omakase.kok.notification.integration;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.notification.infrastructure.client.SlackClient;
import com.omakase.kok.notification.infrastructure.client.UserServiceClient;
import com.omakase.kok.notification.infrastructure.client.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("notification_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @MockitoBean
    protected UserServiceClient userServiceClient;

    @MockitoBean
    protected SlackClient slackClient;

    @BeforeEach
    void setUpExternalMocks() {
        // slackId = null → SlackSendService가 SKIPPED 처리 후 정상 종료
        // (NPE 방지: mock 기본값 null이면 getData()에서 NPE 발생)
        UserResponse userWithNoSlack = new UserResponse();
        when(userServiceClient.getUser(any(), anyString()))
                .thenReturn(ApiResponse.success(userWithNoSlack));
    }
}
