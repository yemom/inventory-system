package com.inventory.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration.
 * <p>
 * Spring Boot auto-configures the {@link RedisConnectionFactory} and the
 * {@code StringRedisTemplate} from {@code spring.data.redis.*} properties.
 * <p>
 * We additionally provide a JSON-serialized {@code RedisTemplate<String, Object>}
 * (bean name {@code redisTemplate}) for the cache-aside cache service. The
 * auto-configured JDK-serialized template backs off because a bean named
 * {@code redisTemplate} already exists.
 * <p>
 * The distributed rate limiter uses the auto-configured {@code StringRedisTemplate}
 * so that Lua-script arguments are serialized as plain strings (not JSON), which
 * the token-bucket Lua script expects.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
