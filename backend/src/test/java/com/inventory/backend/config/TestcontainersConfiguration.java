package com.inventory.backend.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * JVM-wide shared PostgreSQL Testcontainers singleton for the entire test suite.
 *
 * <p>A single static {@link PostgreSQLContainer} is started once when this class
 * is loaded and lives until the JVM exits (managed by Testcontainers / Ryuk).
 * All test classes that call {@link #registerProperties(DynamicPropertyRegistry)}
 * via {@code @DynamicPropertySource} share this same container and the same
 * database port, which prevents HikariCP from losing its connection when a second
 * Spring context starts.
 *
 * <p>This approach is preferred over {@code @Bean @ServiceConnection} in a
 * {@code @TestConfiguration} class, because each Spring context would call the
 * {@code @Bean} factory method independently, creating a <em>new</em> container
 * per context — reproducing the very problem we are solving.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * class MyIntegrationTest {
 *
 *     @DynamicPropertySource
 *     static void props(DynamicPropertyRegistry r) {
 *         TestcontainersConfiguration.registerProperties(r);
 *     }
 * }
 * }</pre>
 *
 * <p>Or — as done in this project — call it from the single
 * {@code BaseIntegrationTest} base class so all subclasses inherit it
 * automatically.
 */
public final class TestcontainersConfiguration {

    /** The one and only PostgreSQL container for the whole test JVM. */
    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("inventory_test_db")
                    .withUsername("stockflow")
                    .withPassword("stockflowpassword");

    static {
        POSTGRES.start();
    }

    private TestcontainersConfiguration() {}

    /**
     * Register the running container's JDBC coordinates into the Spring
     * {@link DynamicPropertyRegistry}.  Call this from a
     * {@code @DynamicPropertySource} static method in every test class
     * (or once in a shared base class).
     */
    public static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
