package com.beckett.grading.service.impl;

import com.amazonaws.services.secretsmanager.model.InvalidRequestException;
import com.beckett.common.util.Constants;
import com.beckett.order.dto.request.DealInfo;
import com.beckett.order.dto.response.CRMDealCreateResponseDTO;
import com.beckett.shdsvc.entity.DealStage;
import com.beckett.shdsvc.enums.DealStageEnum;
import com.beckett.shdsvc.repository.DealStageRepository;
import com.beckett.grading.service.CRMSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CRMSyncServiceImpl implements CRMSyncService {

    @Value("${fixed.bearer.token}")
    private String apiKey;

    @Value("${crm.api.url}")
    private String crmApiUrl;

    @Autowired
    private RestClient restClient;

    private final DealStageRepository dealStageRepository;
    public static final String API_V1_CRM_CREATE_DEAL = "/api/v1/crm/deal/contact";
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER = "Bearer ";

    @Override
    public void updateDeal(String dealId, DealStageEnum dealStageEnum) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(AUTHORIZATION, BEARER + apiKey);

            DealInfo dealInfo = formUpdateDealRequest(dealStageEnum);

            // Build the URL with query parameters
            String urlWithQueryParams = UriComponentsBuilder.fromHttpUrl(crmApiUrl +
                            API_V1_CRM_CREATE_DEAL)
                    .queryParam("dealId", dealId)
                    .toUriString();

            restClient.put().uri(urlWithQueryParams)
                    .headers(httpheaders -> httpheaders.addAll(headers))
                    .body(dealInfo)
                    .retrieve()
                    .toEntity(CRMDealCreateResponseDTO.class);

            log.info("Deal is updated successfully.");
        } catch (Exception e) {
            log.error("Deal update failed, the error message is: { }", e);
        }
    }

    private DealInfo formUpdateDealRequest(DealStageEnum dealStageEnum) {
        Optional<DealStage> dealStage = dealStageRepository.findByDealStageName(dealStageEnum.getStageName());
        if(dealStage.isEmpty()){
            throw new InvalidRequestException(Constants.DEAL_STAGE_NOT_FOUND);
        }

        DealInfo dealInfo = new DealInfo();
        dealInfo.setPipeline(Constants.DEFAULT_DEAL_PIPELINE);
        dealInfo.setDealStage(dealStage.get().getDealStageCode());
        dealInfo.setTypeformServiceLevel(Constants.DEAL_TYPE_FORM_SERVICE_LEVEL);

        return dealInfo;
    }
}
