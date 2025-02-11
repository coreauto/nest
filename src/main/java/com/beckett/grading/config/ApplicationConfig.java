package com.beckett.grading.config;

import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class ApplicationConfig {
    @Bean
    public RestClient restClient() {
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory =
                new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(Duration.ofSeconds(10));
        clientHttpRequestFactory.setConnectionRequestTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
                .requestFactory(clientHttpRequestFactory)
                .build();
    }
}
