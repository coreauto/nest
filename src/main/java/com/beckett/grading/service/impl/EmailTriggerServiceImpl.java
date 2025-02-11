package com.beckett.grading.service.impl;

import com.beckett.common.dto.EmailRequest;
import com.beckett.grading.service.EmailTriggerService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTriggerServiceImpl implements EmailTriggerService {

    @Value("${fixed.bearer.token}")
    private String apiKey;

    @Value("${notification.api.url}")
    private String notificationApiUrl;

    private final RestClient restClient;

    public static final String API_V1_NOTIFICATION_SEND_EMAIL = "/api/v1/notification/send-email";
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER = "Bearer ";

    @Async
    @Override
    public void sendEmail(EmailRequest emailRequest) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(AUTHORIZATION, BEARER + apiKey);

            restClient.post()
                    .uri(notificationApiUrl + API_V1_NOTIFICATION_SEND_EMAIL)
                    .headers(httpheaders -> httpheaders.addAll(headers))
                    .body(emailRequest)
                    .retrieve()
                    .toEntity(JsonNode.class);

            log.info("Email Triggered successfully");
        } catch (Exception e) {
            log.error("Email Triggered failed { }", e);
        }
    }
}
