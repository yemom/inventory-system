package com.inventory.backend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC configuration for pagination and request handling.
 * <p>
 * Enforces a maximum page size of 100 to prevent clients from
 * requesting thousands of records in a single call.
 * <p>
 * Also configures the Pageable argument resolver to use 0-based
 * page indices and a default page size of 20.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Maximum page size allowed for any paginated endpoint.
     * Clients requesting more than this will be clamped to 100.
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Default page size when the client doesn't specify one.
     */
    public static final int DEFAULT_PAGE_SIZE = 20;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        PageableHandlerMethodArgumentResolver resolver = new PageableHandlerMethodArgumentResolver();
        resolver.setOneIndexedParameters(false); // 0-based page indices
        resolver.setMaxPageSize(MAX_PAGE_SIZE);
        resolver.setFallbackPageable(org.springframework.data.domain.PageRequest.of(0, DEFAULT_PAGE_SIZE));
        resolvers.add(resolver);
    }
}
