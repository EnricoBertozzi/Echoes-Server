package com.n0hana.echoes_server.cnpj.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.n0hana.echoes_server.cnpj.CnpjDTO;

@Configuration
public class CnpjConfig {

    @Bean
    public Cache<String, CnpjDTO> cnpjCache(CnpjProperties properties) {
        return Caffeine.newBuilder()
            .maximumSize(properties.getCacheMaxSize())
            .expireAfterWrite(Duration.ofHours(properties.getCacheTtlHours()))
            .recordStats()
            .build();
    }
}
