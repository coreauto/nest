package com.beckett.grading.service;

import com.beckett.common.entity.UserInfoDetails;
import com.beckett.grading.dto.GradeResult;
import com.beckett.grading.request.AssignGradersRequest;
import com.beckett.grading.request.ItemGrades;
import com.beckett.grading.response.GradingIssueCategories;
import com.beckett.grading.response.GradingWorkQueuesResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface GradingService {
    List<GradingIssueCategories> getGradingIssueCategoryList();
    GradeResult calculateFinalGrades(String centering,
                                     String corners,
                                     String edges,
                                     String surface);
    GradingWorkQueuesResponse getMyGradingWorkQueues(Long loggedInUserId, String dueDate, Long serviceLevelId, Long locationId, Pageable pageable);
    GradingWorkQueuesResponse getTotalGradingWorkQueues(Long userId, String dueDate, Long serviceLevelId, Long locationId, Pageable pageable);
    Void submitGrades(UserInfoDetails grader, List<ItemGrades> itemGrades);
    Void finalizeGrades(UserInfoDetails grader, List<ItemGrades> itemGrades);
    Void assignGraders(List<AssignGradersRequest> assignGradersRequests);
}
