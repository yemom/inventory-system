package com.inventory.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class BackendApplicationTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> postgres =
			new PostgreSQLContainer<>("postgres:16-alpine")
					.withDatabaseName("inventory_test_db")
					.withUsername("stockflow")
					.withPassword("stockflowpassword");

	@Test
	void contextLoads() {
	}

}
