package com.beckett.grading.controller;

import com.beckett.common.dto.ResponseDTO;
import com.beckett.common.dto.SearchPaginatedDTO;
import com.beckett.common.entity.UserInfoDetails;
import com.beckett.common.exception.UnauthorizedAccessException;
import com.beckett.grading.dto.GradeResult;
import com.beckett.grading.request.AssignGradersRequest;
import com.beckett.grading.request.ItemGrades;
import com.beckett.grading.response.GradingIssueCategories;
import com.beckett.grading.response.GradingWorkQueuesResponse;
import com.beckett.grading.service.GradingService;
import io.micrometer.common.util.StringUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/grading")
@Tag(name = "Grading API", description = "APIs for Grading operations")
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST,
                RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class GradingController {
    private final GradingService gradingService;

    @Autowired
    public GradingController(GradingService gradingService) {
        this.gradingService = gradingService;
    }

    @Operation(summary = "Get my grading queues based on user")
    @PostMapping(value = "/my-work-queues", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<GradingWorkQueuesResponse>> myWorkQueues(@RequestBody @Valid SearchPaginatedDTO searchPaginatedDTO) {
        return buildAndReturnResponse(gradingService.getMyGradingWorkQueues(
                getLoggedInUserDetails().getUserId(),
                getByKeyField("dueDate", searchPaginatedDTO, String.class),
                getByKeyField("serviceLevelId", searchPaginatedDTO, Long.class),
                getByKeyField("locationId", searchPaginatedDTO, Long.class),
                generatePageRequest(searchPaginatedDTO)));
    }

    @Operation(summary = "Get total grading queues based on user")
    @PostMapping(value = "/total-work-queues", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<GradingWorkQueuesResponse>> getGradingWorkQueues(@RequestBody @Valid SearchPaginatedDTO searchPaginatedDTO) {
        return buildAndReturnResponse(gradingService.getTotalGradingWorkQueues(
                getByKeyField("userId", searchPaginatedDTO, Long.class),
                getByKeyField("dueDate", searchPaginatedDTO, String.class),
                getByKeyField("serviceLevelId", searchPaginatedDTO, Long.class),
                getByKeyField("locationId", searchPaginatedDTO, Long.class),
                generatePageRequest(searchPaginatedDTO)));
    }

    private ResponseEntity<ResponseDTO<GradingWorkQueuesResponse>> buildAndReturnResponse(GradingWorkQueuesResponse gradingWorkQueuesResponse) {
        return ResponseEntity.ok(ResponseDTO.<GradingWorkQueuesResponse>builder()
                .status(HttpStatus.OK.getReasonPhrase())
                .message("Grading work queues fetched successfully.")
                .data(gradingWorkQueuesResponse)
                .build());
    }

    private <T> T getByKeyField(String key, SearchPaginatedDTO searchPaginatedDTO, Class<T> returnType) {
        if (Objects.nonNull(searchPaginatedDTO)
                && Objects.nonNull(searchPaginatedDTO.getSearch())
                && Objects.nonNull(searchPaginatedDTO.getSearch().getFilters())
                && !searchPaginatedDTO.getSearch().getFilters().isEmpty()) {

            return searchPaginatedDTO.getSearch()
                    .getFilters()
                    .stream()
                    .filter(filter -> StringUtils.isNotBlank(String.valueOf(filter.getValue())) && filter.getField().equalsIgnoreCase(key))
                    .map(filter -> convertValue(filter.getValue(), returnType)) // Convert value to the desired type
                    .findFirst()
                    .orElse(null); // Return null if no match is found
        }
        return null;
    }

    private <T> T convertValue(String value, Class<T> returnType) {
        if (returnType == String.class) {
            return returnType.cast(value); // Return as String
        } else if (returnType == Long.class) {
            return returnType.cast(Long.parseLong(value)); // Convert to Long
        } else if (returnType == Integer.class) {
            return returnType.cast(Integer.parseInt(value)); // Convert to Integer
        } else if (returnType == Double.class) {
            return returnType.cast(Double.parseDouble(value)); // Convert to Double
        } else if (returnType == Boolean.class) {
            return returnType.cast(Boolean.parseBoolean(value)); // Convert to Boolean
        }
        throw new IllegalArgumentException("Unsupported return type: " + returnType.getName());
    }

    private Pageable generatePageRequest(SearchPaginatedDTO searchPaginatedDTO) {
        if(!Objects.isNull(searchPaginatedDTO)) {
            List<Sort.Order> orders = new ArrayList<>();
            if(!Objects.isNull(searchPaginatedDTO.getSorting()) && StringUtils.isNotBlank(searchPaginatedDTO.getSorting().getType())) {
                if (searchPaginatedDTO.getSorting().getType().equalsIgnoreCase("asc")) {
                    searchPaginatedDTO.getSorting().getFields().forEach(field -> orders.add(Sort.Order.asc(field)));
                } else {
                    searchPaginatedDTO.getSorting().getFields().forEach(field -> orders.add(Sort.Order.desc(field)));
                }
            }
            return PageRequest.of(searchPaginatedDTO.getPagination().getPageStart(), searchPaginatedDTO.getPagination().getPageLimit(), Sort.by(orders));
        } else {
            return PageRequest.of(0, 50);
        }
    }

    @Operation(summary = "Save grading information submit by the jr/sr grader")
    @PostMapping(value = "/submit", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<Void>> submitGrades(@RequestBody List<ItemGrades> itemGrades) {
        return ResponseEntity.ok(ResponseDTO.<Void>builder()
                .status(HttpStatus.OK.getReasonPhrase())
                .message("Grading info submitted successfully.")
                .data(gradingService.submitGrades(getLoggedInUserDetails(), itemGrades))
                .build());
    }


    @Operation(summary = "Save grading information finalized by the sr grader")
    @PostMapping(value = "/finalize", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<Void>> finalizeGrades(@RequestBody List<ItemGrades> itemGrades) {
        return ResponseEntity.ok(ResponseDTO.<Void>builder()
                .status(HttpStatus.OK.getReasonPhrase())
                .message("Grading info finalized successfully.")
                .data(gradingService.finalizeGrades(getLoggedInUserDetails(), itemGrades))
                .build());
    }

    @Operation(summary = "Assign graders to selected Jobs from Queue")
    @PutMapping(value = "/assign", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<Void>> assignGraders(@RequestBody @Size(min = 1, message = "Please select at least Job and a grader, in order to do the assignment") List<AssignGradersRequest> assignGradersRequests) {
        // need to check whether the logged in user is admin or a senior grader
        return ResponseEntity.ok(ResponseDTO.<Void>builder()
                .status(HttpStatus.OK.getReasonPhrase())
                .message("Graders assigned successfully.")
                .data(gradingService.assignGraders(assignGradersRequests))
                .build());
    }

    @Operation(summary = "Calculate the final grade based on the 4 sub grading params provided")
    @GetMapping(value = "/calc", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<GradeResult>> calculateFinalGrade(@RequestParam(required = false) String centering,
                                                                        @RequestParam(required = false) String corners,
                                                                        @RequestParam(required = false) String edges,
                                                                        @RequestParam(required = false) String surface) {
        return ResponseEntity.ok(ResponseDTO.<GradeResult>builder()
                .status(HttpStatus.OK.getReasonPhrase())
                .message("Final grades calculated successfully.")
                .data(gradingService.calculateFinalGrades(centering, corners, edges, surface))
                .build());
    }

    @Operation(summary = "Get the Category & Sub Category List to tag item with required grade level issues")
    @GetMapping(value = "/categories", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<List<GradingIssueCategories>>> getGradingIssueCategoryList() {
        return ResponseEntity.ok(ResponseDTO.<List<GradingIssueCategories>>builder()
                .status(HttpStatus.OK.getReasonPhrase())
                .message("Grading Issue Categories fetched successfully.")
                .data(gradingService.getGradingIssueCategoryList())
                .build());
    }

    private UserInfoDetails getLoggedInUserDetails() {
        Optional<Object> optionalPrincipal = Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal);
        if(optionalPrincipal.isPresent() && !optionalPrincipal.get().toString().equalsIgnoreCase("anonymousUser")) {
            Optional<UserInfoDetails> loggedInUserDetailsOptional = optionalPrincipal
                    .map(UserInfoDetails.class::cast);
            return loggedInUserDetailsOptional.get(); // NOSONAR
        } else {
            throw new UnauthorizedAccessException("Can't access API resources without authorization");
        }
    }



}
