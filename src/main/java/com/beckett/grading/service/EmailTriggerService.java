package com.beckett.grading.service;

import com.beckett.common.dto.EmailRequest;

public interface EmailTriggerService {

    void sendEmail(EmailRequest emailRequest);
}
