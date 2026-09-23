package com.n0hana.echoes_server.cnpj.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CnpjRestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder(CnpjProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = (int) Duration.ofSeconds(props.getTimeoutSeconds()).toMillis();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return RestClient.builder().requestFactory(factory);
    }
}
