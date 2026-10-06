package com.inventory.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Normalises {@code spring.datasource.url} so the JDBC driver can actually open
 * a connection, whatever shape the platform hands the connection string in.
 *
 * <p><b>Why this exists.</b> Render (and most managed-PaaS providers) expose
 * PostgreSQL as a <em>URI</em>, not a JDBC URL —
 * {@code postgres://user:pw@host:5432/db}. Spring Boot performs no translation,
 * so mapping that value straight onto {@code spring.datasource.url} produces a
 * URL with no {@code jdbc:} prefix. No driver accepts it, the connection fails,
 * Hibernate silently ends up with <em>null</em> JDBC metadata, and the app dies
 * at boot with {@code "Unable to determine Dialect without JDBC metadata (please
 * set 'jakarta.persistence.jdbc.url' ...)"} — a message that blames the JDBC
 * URL setting even though the operator demonstrably configured one.
 *
 * <p><b>Why a {@code BeanFactoryPostProcessor} and not an
 * {@code EnvironmentPostProcessor}.</b> The idiomatic registration for the latter
 * is a {@code META-INF/spring/…EnvironmentPostProcessor.imports} resource — which
 * does not work in this project's shipped artifact. {@code spring-boot:repackage}
 * relocates {@code META-INF/**} to the <em>archive root</em> of the executable
 * jar while application classes and resources live under {@code BOOT-INF/classes/}.
 * The Boot class loader only exposes {@code BOOT-INF/classes/} and
 * {@code BOOT-INF/lib/*.jar}, so the registration file is invisible to
 * {@code SpringFactoriesLoader} and the post-processor silently never runs. A
 * component-scanned bean has no such packaging hazard, so it works identically
 * from {@code java -jar}, in a container, from the IDE, and under
 * {@code @SpringBootTest}.
 *
 * <p><b>What it does, in order:</b>
 * <ol>
 *   <li>Prefixes {@code jdbc:} when the value is a {@code postgres://} /
 *       {@code postgresql://} URI.</li>
 *   <li>Lifts any {@code user:password@} component out of the URI into
 *       {@code spring.datasource.username} / {@code spring.datasource.password},
 *       percent-decoding it. Credentials embedded in a connection string are
 *       authoritative for that string, and a mismatch with separately configured
 *       credentials is exactly what produces "password authentication failed".</li>
 *   <li>Rewrites the URL without the userinfo component — the driver reads
 *       credentials from the pool properties, not from the URL.</li>
 * </ol>
 *
 * <p><b>Deliberately not done:</b> nothing is invented. A URL in an unrecognised
 * shape, or one that cannot be parsed, is left completely untouched so the
 * DataSource still fails loudly with its own diagnostics rather than being handed
 * a silently rewritten value. This includes the Testcontainers URL
 * ({@code jdbc:tc:postgresql:15-alpine:///db}) used by the test profile.
 *
 * <p>Values that already work are not touched either: if the normalised URL is
 * identical to the configured one and no credentials were embedded, no property
 * source is registered at all, so Docker Compose behaviour is unchanged.
 *
 * <p>Runs as a {@code BeanFactoryPostProcessor}, i.e. after the environment and
 * config data are prepared but before any bean — including {@code dataSource} and
 * the JPA {@code EntityManagerFactory} — is instantiated.
 */
@Component
public class DataSourceUrlNormalizer implements BeanFactoryPostProcessor, Ordered {

    /** Name of the property source inserted at the front of the environment. */
    static final String PROPERTY_SOURCE_NAME = "stockflow-datasource-url";

    private static final Logger log = LoggerFactory.getLogger(DataSourceUrlNormalizer.class);

    private static final String JDBC_PREFIX = "jdbc:";

    /**
     * A {@code BeanFactoryPostProcessor} is instantiated <em>before</em> the
     * autowiring {@code BeanPostProcessor}s are registered, so constructor
     * injection is unavailable and Spring requires the no-arg constructor. The
     * {@code Environment} singleton is registered by
     * {@code AbstractApplicationContext.prepareBeanFactory} — i.e. before this
     * runs — so it is resolved from the bean factory instead.
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        apply(beanFactory.getBean(ConfigurableEnvironment.class));
    }

    static void apply(ConfigurableEnvironment environment) {
        String configured = environment.getProperty("spring.datasource.url");
        if (configured == null || configured.isBlank()) {
            return;
        }

        NormalizedDataSource normalized = normalize(configured);
        if (normalized == null) {
            // Unrecognised/unsupported shape: leave it alone and let the
            // DataSource report the real problem.
            return;
        }

        String currentUsername = environment.getProperty("spring.datasource.username");
        String currentPassword = environment.getProperty("spring.datasource.password");

        Map<String, Object> overrides = new LinkedHashMap<>();

        if (!configured.trim().equals(normalized.jdbcUrl())) {
            overrides.put("spring.datasource.url", normalized.jdbcUrl());
            // Never log the raw value: it may embed credentials.
            log.info("Normalised spring.datasource.url into a JDBC URL the PostgreSQL driver accepts");
        }

        if (normalized.username() != null && !normalized.username().equals(currentUsername)) {
            if (currentUsername != null && !currentUsername.isBlank()) {
                log.warn("spring.datasource.username is being overridden by the credentials embedded in the "
                        + "datasource URL, because both must belong to the same database.");
            }
            overrides.put("spring.datasource.username", normalized.username());
        }

        if (normalized.password() != null && !normalized.password().equals(currentPassword)) {
            overrides.put("spring.datasource.password", normalized.password());
        }

        if (overrides.isEmpty()) {
            return;
        }

        MutablePropertySources sources = environment.getPropertySources();
        // Guard against re-registration.
        sources.remove(PROPERTY_SOURCE_NAME);
        sources.addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
    }

    /**
     * Pure, side-effect-free normalisation. Returns {@code null} when the input
     * is not a shape this normaliser is willing to rewrite.
     */
    static NormalizedDataSource normalize(String raw) {
        String trimmed = raw.trim();

        final String candidate;
        if (startsWithIgnoreCase(trimmed, JDBC_PREFIX)) {
            candidate = trimmed.substring(JDBC_PREFIX.length());
        } else if (startsWithIgnoreCase(trimmed, "postgres://")
                || startsWithIgnoreCase(trimmed, "postgresql://")) {
            // PaaS URI form — needs the jdbc: prefix the driver requires.
            candidate = trimmed;
        } else {
            return null;
        }

        // Match the "://" separator, not the first "//". For "postgresql://…"
        // indexOf("//") points at the slashes, so substring(0, index) would
        // capture the scheme as "postgresql:" (colon included) and every rewrite
        // would be rejected.
        int schemeEnd = candidate.indexOf("://");
        if (schemeEnd < 0) {
            // No authority component, e.g. the Testcontainers URL
            // "jdbc:tc:postgresql:15-alpine:///db". Leave it untouched.
            return null;
        }

        String scheme = candidate.substring(0, schemeEnd);
        if (!"postgresql".equalsIgnoreCase(scheme) && !"postgres".equalsIgnoreCase(scheme)) {
            // Some other driver's URL; not ours to rewrite.
            return null;
        }

        URI uri;
        try {
            uri = URI.create(scheme + "://" + candidate.substring(schemeEnd + 3));
        } catch (IllegalArgumentException ex) {
            log.warn("spring.datasource.url could not be parsed as a PostgreSQL URI; leaving it unchanged.");
            return null;
        }

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return null;
        }

        String username = null;
        String password = null;
        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            int colon = userInfo.indexOf(':');
            username = decodeUriComponent(colon < 0 ? userInfo : userInfo.substring(0, colon));
            password = colon < 0 ? null : decodeUriComponent(userInfo.substring(colon + 1));
        }

        StringBuilder jdbcUrl = new StringBuilder(JDBC_PREFIX).append("postgresql://").append(host);
        int port = uri.getPort();
        if (port > 0) {
            jdbcUrl.append(':').append(port);
        }
        String path = uri.getPath();
        jdbcUrl.append(path == null || path.isEmpty() ? "/" : path);
        if (uri.getQuery() != null) {
            jdbcUrl.append('?').append(uri.getQuery());
        }

        return new NormalizedDataSource(jdbcUrl.toString(), username, password);
    }

    /**
     * Percent-decodes a URI component. A literal {@code +} must survive as a
     * plus sign — {@link URLDecoder} would otherwise turn it into a space,
     * silently corrupting any password containing one.
     */
    private static String decodeUriComponent(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            // Not valid percent-encoding; use it verbatim rather than guessing.
            return value;
        }
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /**
     * @param jdbcUrl   the URL rewritten for the JDBC driver
     * @param username  credentials lifted out of the URI, or {@code null}
     * @param password  credentials lifted out of the URI, or {@code null}
     */
    record NormalizedDataSource(String jdbcUrl, String username, String password) {
    }

    /**
     * Runs last, i.e. after {@code ConfigurationClassPostProcessor} has
     * registered the bean definitions, but still before any bean — including
     * {@code dataSource} — is instantiated and bound.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}