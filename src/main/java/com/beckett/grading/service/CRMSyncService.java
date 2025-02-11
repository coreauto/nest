package com.beckett.grading.service;


import com.beckett.shdsvc.enums.DealStageEnum;

public interface CRMSyncService {
    void updateDeal(String dealId, DealStageEnum dealStageEnum);
}
