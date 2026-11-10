package io.flagship.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import io.flagship.cache.CacheProperties;
import io.flagship.ratelimit.RateLimitProperties;

@Configuration
@EnableConfigurationProperties({CacheProperties.class, RateLimitProperties.class})
public class FlagshipPropertiesConfig {
}
