package com.n0hana.echoes_server.cnpj.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "cnpj")
@Getter
@Setter
public class CnpjProperties {

    private Provider brasilApi = new Provider();
    private int timeoutSeconds = 10;
    private int cacheTtlHours = 24;
    private int cacheMaxSize = 500;

    @Getter
    @Setter
    public static class Provider {
        private String url;
    }
}
