package com.inventory.backend;

import com.inventory.backend.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Smoke test — verifies that the Spring application context starts successfully.
 *
 * <p>Uses the shared {@link TestcontainersConfiguration#POSTGRES} container and
 * registers it via {@code @DynamicPropertySource} so this test shares the same
 * datasource URL as all {@code BaseIntegrationTest} subclasses — allowing Spring
 * Boot's context cache to reuse a single context and a single HikariCP pool
 * across the entire test suite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BackendApplicationTests {

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		TestcontainersConfiguration.registerProperties(registry);
	}

	@Test
	void contextLoads() {
	}

}
