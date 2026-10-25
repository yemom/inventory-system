package com.inventory.backend.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.config.TestcontainersConfiguration;
import com.inventory.backend.dto.AuthRequest;
import com.inventory.backend.dto.AuthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for all Spring Boot integration tests.
 *
 * <p>Uses a JVM-wide shared PostgreSQL container from
 * {@link TestcontainersConfiguration} injected via {@code @DynamicPropertySource}.
 * All subclasses share the same application context and the same live container,
 * so HikariCP never tries to reconnect to a dead container port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    /**
     * Wire the shared container's JDBC URL into every Spring context that
     * inherits from this base class. Because the container is a static
     * singleton, all contexts receive the same URL and therefore share the
     * same HikariCP pool (via context caching).
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        TestcontainersConfiguration.registerProperties(registry);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String login(String identifier, String password) throws Exception {
        AuthRequest req = new AuthRequest(identifier, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return resp.getToken();
    }

    protected String loginAsSuperAdmin() throws Exception {
        return login("superadmin", "TestsOnly-7f3a91c4-SuperAdmin");
    }

    protected HttpHeaders authHeader(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}