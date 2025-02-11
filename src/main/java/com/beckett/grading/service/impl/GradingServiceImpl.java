package com.beckett.grading.service.impl;

import com.beckett.certificate.entity.LookupBGS;
import com.beckett.certificate.repository.LookupBGSRepository;
import com.beckett.common.dto.EmailRequest;
import com.beckett.common.dto.PageDto;
import com.beckett.common.entity.UserInfoDetails;
import com.beckett.customer.entity.Customer;
import com.beckett.customer.repository.CustomerRepository;
import com.beckett.grading.service.CRMSyncService;
import com.beckett.grading.service.EmailTriggerService;
import com.beckett.common.exception.FormulaNotFoundException;
import com.beckett.common.exception.SubGradeSizeExceeded;
import com.beckett.grading.consants.Formulas;
import com.beckett.grading.dto.GradeFormulaDTO;
import com.beckett.grading.dto.GradeResult;
import com.beckett.grading.dto.RequestModel;
import com.beckett.grading.entity.GradeRoundNumber;
import com.beckett.grading.repository.GradeRoundNumberRepository;
import com.beckett.grading.repository.TakeOffReferenceRepository;
import com.beckett.grading.request.AssignGradersRequest;
import com.beckett.grading.request.ItemGrades;
import com.beckett.grading.response.GradingIssueCategories;
import com.beckett.grading.response.GradingQueue;
import com.beckett.grading.response.GradingWorkQueuesResponse;
import com.beckett.grading.entity.GradeFormula;
import com.beckett.grading.repository.GradeFormulaRepository;
import com.beckett.grading.utils.*;
import com.beckett.grading.utils.AsyncUtils;
import com.beckett.grading.utils.Constants;
import com.beckett.order.CategoryOfIssues;
import com.beckett.order.constant.OrderStatus;
import com.beckett.order.constant.SuborderStatus;
import com.beckett.order.entity.CardSuborderItemGrade;
import com.beckett.order.repository.*;
import com.beckett.grading.service.GradingService;
import com.beckett.location.entity.JobLocationMapping;
import com.beckett.location.entity.Location;
import com.beckett.location.repository.JobLocationMappingRepository;
import com.beckett.order.entity.*;
import com.beckett.order.repository.specification.CardSuborderJobSpecification;
import com.beckett.shdsvc.entity.*;
import com.beckett.shdsvc.enums.DealStageEnum;
import com.beckett.shdsvc.repository.*;
import com.beckett.user.entity.UserBusinessUnit;
import com.beckett.user.entity.UserBusinessUnitJobRole;
import com.beckett.user.entity.Users;
import com.beckett.user.repository.UsersRepository;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.beckett.common.util.Constants.*;
import static com.beckett.grading.utils.Constants.ITEM_NOT_FOUND;

@Transactional
@Service
@Slf4j
@RequiredArgsConstructor
public class GradingServiceImpl implements GradingService {

    private final GradingServiceTypeRepository gradingServiceTypeRepository;
    private final CardLabelWarehouseRepository cardLabelWarehouseRepository;
    private final OrderRepository orderRepository;
    private final CardSuborderRepository cardSuborderRepository;
    private final UsersRepository usersRepository;
    private final LookupBGSRepository lookupBGSRepository;
    private final CardSuborderJobRepository cardSuborderJobRepository;
    private final MasterCategoryRepository masterCategoryRepository;
    private final GradingRepository gradingRepository;
    private final ServiceLevelRepository serviceLevelRepository;
    private final JobLocationMappingRepository jobLocationMappingRepository;
    private final CardSuborderItemRepository cardSuborderItemRepository;
    private final CardSuborderItemGradeRepository cardSuborderItemGradeRepository;
    private final GradeFormulaRepository gradeFormulaRepository;
    private final GradeRoundNumberRepository gradeRoundNumberRepository;
    private final TakeOffReferenceRepository takeOffReferenceRepository;
    private final GradeFormulaMapper gradeFormulaMapper;
    private final GradingDescriptionMapper gradingDescriptionMapper;
    private final CRMSyncService crmSyncService;
    private final EmailTriggerService emailTriggerService;
    private final CustomerRepository customerRepository;
    private final SubGradeMapper subGradeMapper = new SubGradeMapper();
    private final SuborderRepository suborderRepository;

    @Override
    public GradingWorkQueuesResponse getMyGradingWorkQueues(Long loggedInUserId, String dueDate, Long serviceLevelId,
                                                            Long locationId,
                                                            Pageable pageable) {

        Specification<CardSuborderJob> condition1 = CardSuborderJobSpecification.hasSuborderPaymentDone();
        Specification<CardSuborderJob> condition2 = CardSuborderJobSpecification.hasSuborderStatusVerified();
        Specification<CardSuborderJob> combinedSpecification = condition1.or(condition2);

        Optional<Users> usersOptional = usersRepository.findById(loggedInUserId);

        if(usersOptional.isEmpty()) {
            log.warn("Logged User not found with id: {}", loggedInUserId);
            throw new IllegalArgumentException("Logged User not found with id: "+loggedInUserId);
        }

        return getGradingQueues(cardSuborderJobRepository.findAll(
                Specification.where(CardSuborderJobSpecification.hasGrader(loggedInUserId, usersOptional.get().getEmail()))
                    .and(CardSuborderJobSpecification.hasDueDate(dueDate))
                    .and(CardSuborderJobSpecification.hasServiceLevel(serviceLevelId))
                    .and(CardSuborderJobSpecification.hasLocation(locationId))
                    .and(combinedSpecification), pageable));
    }

    @Override
    public GradingWorkQueuesResponse getTotalGradingWorkQueues(Long userId, String dueDate, Long serviceLevelId,
                                                               Long locationId,
                                                               Pageable pageable) {
        Specification<CardSuborderJob> condition1 = CardSuborderJobSpecification.hasSuborderPaymentDone();
        Specification<CardSuborderJob> condition2 = CardSuborderJobSpecification.hasSuborderStatusVerified();
        Specification<CardSuborderJob> combinedSpecification = condition1.or(condition2);

        Optional<Users> usersOptional = Optional.empty();
        if(userId != null) {
            usersOptional = usersRepository.findById(userId);
            if(usersOptional.isEmpty()) {
                log.warn("Logged User not found with id: {}", userId);
                throw new IllegalArgumentException("Logged User not found with id: "+userId);
            }
        }

        return getGradingQueues(cardSuborderJobRepository.findAll(
                Specification.where(CardSuborderJobSpecification.hasServiceLevel(serviceLevelId))
                    .and(CardSuborderJobSpecification.hasDueDate(dueDate))
                    .and(CardSuborderJobSpecification.hasLocation(locationId))
                    .and(CardSuborderJobSpecification.hasGrader(userId, usersOptional.map(Users::getEmail).orElse(null)))
                    .and(combinedSpecification), pageable));
    }

    private GradingWorkQueuesResponse getGradingQueues(Page<CardSuborderJob> gradingWorkQueuePage) {
        GradingWorkQueuesResponse gradingWorkQueuesResponse = new GradingWorkQueuesResponse();
        PageDto pageDto = new PageDto();
        pageDto.setPageSize(gradingWorkQueuePage.getSize());
        pageDto.setPageStart(gradingWorkQueuePage.getNumber());
        pageDto.setTotalPages(gradingWorkQueuePage.getTotalPages());
        pageDto.setTotalRecords(gradingWorkQueuePage.getTotalElements());
        pageDto.setCurrentPageCount(gradingWorkQueuePage.getNumberOfElements());
        gradingWorkQueuesResponse.setPage(pageDto);

        if (!gradingWorkQueuePage.getContent().isEmpty()) {
            List<String> jobs = gradingWorkQueuePage.getContent().stream().map(CardSuborderJob::getJobNo).toList();
            List<JobLocationMapping> jobLocations = jobLocationMappingRepository.findByCardSubOrderJobNoAndCurrentLocationTrue(jobs);
            List<ServiceLevel> serviceLevelList = serviceLevelRepository.findAllById(gradingWorkQueuePage.getContent().stream().map(CardSuborderJob::getCardSuborder).mapToInt(subOrder -> subOrder.getServiceLevelId().intValue()).boxed().toList());
            gradingWorkQueuesResponse.setQueues(gradingWorkQueuePage
                    .getContent()
                    .stream()
                    .map(
                        cardSuborderJob -> {
                            GradingQueue gradingQueue = new GradingQueue();
                            CardSuborder cardSuborder = cardSuborderJob.getCardSuborder();
                            Order order = cardSuborderJob.getCardSuborder().getOrder();
                            gradingQueue.setOrderId(order.getOrderId());
                            gradingQueue.setSubOrderId(cardSuborder.getSuborderId());
                            gradingQueue.setCardSubOrderId(cardSuborder.getCardSuborderId());
                            gradingQueue.setCardSubOrderJobId(cardSuborderJob.getId());
                            gradingQueue.setOrderNo(order.getOrderNo());
                            gradingQueue.setSubOrderNo(cardSuborder.getSuborderNo());
                            gradingQueue.setJobNo(cardSuborderJob.getJobNo());
                            gradingQueue.setTotalNoOfItems(cardSuborderJob.getItemCount());
                            gradingQueue.setDueDate(cardSuborder.getDueDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
                            gradingQueue.setServiceLevel(populateServiceLevel(cardSuborder.getServiceLevelId().intValue(), serviceLevelList));
                            gradingQueue.setUserRole(cardSuborderJob.getGrader() != null ? populateUserRole(cardSuborderJob.getGrader()) : null);
                            gradingQueue.setAssignedTo(cardSuborderJob.getGrader() != null ? cardSuborderJob.getGrader().getUserId(): null);
                            gradingQueue.setLocation(populateLocation(cardSuborderJob.getId(), jobLocations));
                            gradingQueue.setGradingStatus(cardSuborderJob.getGradingStatus() != null ? cardSuborderJob.getGradingStatus().name() : CardSuborderJob.StatusEnum.READY_TO_GRADE.name());
                            gradingQueue.setOrderStatus(order.getOrderStatus().value());
                            return gradingQueue;
                        }
                    )
                    .toList());
        } else gradingWorkQueuesResponse.setQueues(new ArrayList<>());
        return gradingWorkQueuesResponse;
    }

    @Override
    public Void submitGrades(UserInfoDetails grader, List<ItemGrades> itemGrades) { //NOSONAR
        List<CardSuborderItemGrade> gradeList = new ArrayList<>();
        List<CardSuborderItem> items =  cardSuborderItemRepository.findAllById(itemGrades.stream().map(ItemGrades::getCardSuborderItemId).toList());
        Optional<ServiceLevel> serviceLevel = Optional.empty();
        Boolean withoutSubs = null;
        CardSuborderJob cardSuborderJob = null;
        for(ItemGrades grade : itemGrades) { //NOSONAR
            Optional<CardSuborderItem> cardSuborderItemOptional = items.stream().filter(item -> Objects.equals(grade.getCardSuborderItemId(), item.getCardSuborderItemId())).findFirst();
            if(cardSuborderItemOptional.isPresent()) {
                CardSuborderItem item = cardSuborderItemOptional.get();

                boolean isAlreadyGraded = item.getCardSuborderItemGrade() != null &&
                        (item.getCardSuborderItemGrade().getJrCenteringVal() != null
                                || item.getCardSuborderItemGrade().getJrFinalGradeVal() != null
                            || item.getCardSuborderItemGrade().getJrCornersVal() != null
                            || item.getCardSuborderItemGrade().getJrEdgesVal() != null
                            || item.getCardSuborderItemGrade().getJrSurfaceVal() != null
                            || item.getCardSuborderItemGrade().getJrAutoVal() != null
                        );

                // need to check whether the item is graded already by a grader or not
                if(isAlreadyGraded && !item.getCardSuborderItemGrade().getJrGrader().equalsIgnoreCase(grader.getUsername())) {
                    throw new IllegalArgumentException("Selected item is already graded by "+item.getCardSuborderItemGrade().getJrGrader()+" and hence can't be graded again.");
                }

                CardSuborderItemGrade cardSuborderItemGrade;

                if(item.getCardSuborderItemGrade() != null) {
                    cardSuborderItemGrade = item.getCardSuborderItemGrade();
                } else {
                    cardSuborderItemGrade = new CardSuborderItemGrade();
                    cardSuborderItemGrade.setCardSuborderItem(item);
                }

                cardSuborderItemGrade.setIsDeactivated(grade.getIsToDeactivate());
                cardSuborderItemGrade.setJrGraderNotes(grade.getTagImageAndGraderNotes().getGraderNotes());
                cardSuborderItemGrade.setJrBackImgTags(grade.getTagImageAndGraderNotes().getBackImgTags());
                cardSuborderItemGrade.setJrFrontImgTags(grade.getTagImageAndGraderNotes().getFrontImgTags());

                cardSuborderJob = Objects.isNull(cardSuborderJob) ? item.getCardSuborderJob() : cardSuborderJob;
                CardSuborder cardSuborder = cardSuborderJob.getCardSuborder();

                // cannot grade scenario
                if(Boolean.TRUE.equals(grade.getCannotGrade())) {
                    cardSuborderItemGrade.setIsCantGrade(Boolean.TRUE);
                    gradeList.add(cardSuborderItemGrade);
                    continue;
                }

                // service unavailable scenario
                if(Boolean.TRUE.equals(grade.getIsServiceUnavailable())) {
                    cardSuborderItemGrade.setIsSvcUnavailable(Boolean.TRUE);
                    cardSuborderItemGrade.setSvcUnavailableComments(grade.getServiceUnavailableComments());
                    gradeList.add(cardSuborderItemGrade);
                    continue;
                }

                if(serviceLevel.isEmpty()) {
                    serviceLevel = serviceLevelRepository.findById(cardSuborder.getServiceLevelId().intValue());
                    withoutSubs = serviceLevel.isPresent() && Boolean.FALSE.equals(serviceLevel.get().getSubGrade());
                }

                if(Boolean.FALSE.equals(withoutSubs)) {
                    validateGradeVal(item.getItemName(), grade);
                    cardSuborderItemGrade.setJrCenteringVal(grade.getCentering());
                    cardSuborderItemGrade.setJrCornersVal(grade.getCorners());
                    cardSuborderItemGrade.setJrEdgesVal(grade.getEdges());
                    cardSuborderItemGrade.setJrSurfaceVal(grade.getSurface());
                    cardSuborderItemGrade.setJrAutoVal(grade.getAuto());
                    cardSuborderItemGrade.setJrMinGrade(grade.getMinGrade());
                    cardSuborderItemGrade.setJrFinalGradeVal(grade.getFinalGrade());

                    if(cardSuborderItemGrade.getJrFinalGradeVal().intValue() == 10) {
                        cardSuborderItemGrade.setJrIsBlackLabel(Boolean.TRUE);
                    } else {
                        cardSuborderItemGrade.setJrIsBlackLabel(Boolean.FALSE);
                    }
                } else {
                    if(grade.getFinalGrade() != null && grade.getFinalGrade().intValue() == 10) {
                        validateGradeVal(item.getItemName(), grade);
                        cardSuborderItemGrade.setJrCenteringVal(grade.getCentering());
                        cardSuborderItemGrade.setJrCornersVal(grade.getCorners());
                        cardSuborderItemGrade.setJrEdgesVal(grade.getEdges());
                        cardSuborderItemGrade.setJrSurfaceVal(grade.getSurface());
                        cardSuborderItemGrade.setJrAutoVal(grade.getAuto());
                        cardSuborderItemGrade.setJrMinGrade(grade.getMinGrade());
                        cardSuborderItemGrade.setJrFinalGradeVal(grade.getFinalGrade());
                        cardSuborderItemGrade.setJrIsBlackLabel(Boolean.TRUE);
                    } else {
                        cardSuborderItemGrade.setJrIsBlackLabel(Boolean.FALSE);
                        cardSuborderItemGrade.setJrAutoVal(grade.getAuto());
                        cardSuborderItemGrade.setJrMinGrade(grade.getMinGrade());
                        cardSuborderItemGrade.setJrFinalGradeVal(grade.getFinalGrade());
                    }
                }
                cardSuborderItemGrade.setCardSuborderItem(item);
                cardSuborderItemGrade.setIsSecondReview(Boolean.TRUE);
                cardSuborderItemGrade.setJrGradedDate(LocalDateTime.now());
                cardSuborderItemGrade.setJrGrader(grader.getUsername());
                cardSuborderItemGrade.setJrGraderComment(grade.getComments());
                gradeList.add(cardSuborderItemGrade);
            } else log.warn(ITEM_NOT_FOUND, grade.getCardSuborderItemId());
        }
        Objects.requireNonNull(cardSuborderJob, "Card sub order job must not be null, this should not happen.");
        cardSuborderJob.setGradingStatus(CardSuborderJob.StatusEnum.READY_FOR_SENIOR_REVIEW);
        // MARKING ASSIGN OF THE JOB TO NULL SINCE THIS JOB NOW REQUIRES US TO REASSIGN IT TO THE NEXT LEVEL OF GRADING
        cardSuborderJob.setGrader(null);
        cardSuborderJob.setGradedBy(grader.getUsername());
        cardSuborderJob.setGradedOn(LocalDateTime.now());
        cardSuborderJobRepository.save(cardSuborderJob);
        cardSuborderItemGradeRepository.saveAll(gradeList);
        // need to send a communication email here for Level 1 grading done
        CardSuborderJob finalCardSuborderJob = cardSuborderJob;
        AsyncUtils.perform(() -> {
            // updating sub order and order level status to Grading
            if(finalCardSuborderJob.getCardSuborder() != null
                    && finalCardSuborderJob.getCardSuborder().getOrder() != null
                    && finalCardSuborderJob.getCardSuborder().getOrder().getSuborders() != null
                    && !finalCardSuborderJob.getCardSuborder().getOrder().getSuborders().isEmpty()) {
                CardSuborder cardSuborder = finalCardSuborderJob.getCardSuborder();
                cardSuborder.setSuborderStatus(SuborderStatus.GRADING);
                CardSuborderStatus cardSuborderStatus = cardSuborder.getCardSuborderStatus();
                if(cardSuborderStatus != null) cardSuborderStatus.setSuborderStatus(SuborderStatus.GRADING);
                cardSuborderRepository.save(cardSuborder);

                //HUBSPOT UPDATE
                crmSyncService.updateDeal(cardSuborder.getCrmDealId(), DealStageEnum.L1_GRADED);

                Order order = finalCardSuborderJob.getCardSuborder().getOrder();
                order.setOrderStatus(OrderStatus.GRADING);
                orderRepository.save(order);
                log.info("SubmitGrades::Order & Sub Order status moved to Grading ");
            } else {
                log.warn("SubmitGrades::CardSubOrder not found from Card Sub Order Job");
            }
        }, () -> {
            // E-mail Impl here
        });
        return null;
    }

    @Override
    public Void finalizeGrades(UserInfoDetails grader, List<ItemGrades> itemGrades) { //NOSONAR
        boolean isJuniorGrader = isGraderIsAtJuniorLevel(grader.getUserId());
        if(isJuniorGrader) {
            throw new IllegalArgumentException("Second level grading information can't be from a junior grader.");
        }
        List<LookupBGS> lookupBGSList = new ArrayList<>();
        List<CardSuborderItemGrade> gradeList = new ArrayList<>();
        List<Long> itemsIds = itemGrades.stream().map(ItemGrades::getCardSuborderItemId).toList();
        List<CardSuborderItem> items =  cardSuborderItemRepository.findAllById(itemsIds);
        Optional<ServiceLevel> serviceLevel = Optional.empty();
        Boolean withoutSubs = null;
        CardSuborderJob cardSuborderJob = null;
        CardSuborder cardSuborder = null;
        for(ItemGrades grade : itemGrades) { //NOSONAR
            Optional<CardSuborderItem> cardSuborderItemOptional = items.stream().filter(item -> Objects.equals(grade.getCardSuborderItemId(), item.getCardSuborderItemId())).findFirst();
            if(cardSuborderItemOptional.isPresent()) {

                CardSuborderItem item = cardSuborderItemOptional.get();
                cardSuborderJob = Objects.isNull(cardSuborderJob) ? item.getCardSuborderJob() : cardSuborderJob;
                cardSuborder = Objects.isNull(cardSuborder) ? cardSuborderJob.getCardSuborder() : cardSuborder;

                boolean isAlreadyGraded = item.getCardSuborderItemGrade() != null &&
                        (item.getCardSuborderItemGrade().getSrCenteringVal() != null
                                || item.getCardSuborderItemGrade().getSrFinalGradeVal() != null
                                || item.getCardSuborderItemGrade().getSrCornersVal() != null
                                || item.getCardSuborderItemGrade().getSrEdgesVal() != null
                                || item.getCardSuborderItemGrade().getSrSurfaceVal() != null
                                || item.getCardSuborderItemGrade().getSrAutoVal() != null
                        );

                boolean isL1GradingDone = item.getCardSuborderItemGrade() != null &&
                        (item.getCardSuborderItemGrade().getJrCenteringVal() != null
                                || item.getCardSuborderItemGrade().getJrFinalGradeVal() != null
                                || item.getCardSuborderItemGrade().getJrCornersVal() != null
                                || item.getCardSuborderItemGrade().getJrEdgesVal() != null
                                || item.getCardSuborderItemGrade().getJrSurfaceVal() != null
                                || item.getCardSuborderItemGrade().getJrAutoVal() != null
                        );

                // need to check whether the item is graded already by a grader or not
                if(isAlreadyGraded && !item.getCardSuborderItemGrade().getSrGrader().equalsIgnoreCase(grader.getUsername())) {
                    throw new IllegalArgumentException("Selected item is already graded by "+item.getCardSuborderItemGrade().getJrGrader()+" and hence can't be graded again.");
                }

                CardSuborderItemGrade cardSuborderItemGrade;

                if(item.getCardSuborderItemGrade() != null) {
                    cardSuborderItemGrade = item.getCardSuborderItemGrade();
                } else {
                    cardSuborderItemGrade = new CardSuborderItemGrade();
                    cardSuborderItemGrade.setCardSuborderItem(item);
                }

                cardSuborderItemGrade.setIsDeactivated(grade.getIsToDeactivate());
                cardSuborderItemGrade.setSrGraderNotes(grade.getTagImageAndGraderNotes().getGraderNotes());
                cardSuborderItemGrade.setSrBackImgTags(grade.getTagImageAndGraderNotes().getBackImgTags());
                cardSuborderItemGrade.setSrFrontImgTags(grade.getTagImageAndGraderNotes().getFrontImgTags());

                // cannot grade scenario
                if(Boolean.TRUE.equals(grade.getCannotGrade())) {
                    cardSuborderItemGrade.setIsCantGrade(Boolean.TRUE);
                    LookupBGS lookupBGS = addLookup(item, cardSuborderItemGrade);
                    lookupBGSList.add(lookupBGS);
                    cardSuborderItemGrade.setLookupBGS(lookupBGS);
                    gradeList.add(cardSuborderItemGrade);
                    continue;
                }

                // service unavailable scenario
                if(Boolean.TRUE.equals(grade.getIsServiceUnavailable())) {
                    cardSuborderItemGrade.setIsSvcUnavailable(Boolean.TRUE);
                    cardSuborderItemGrade.setSvcUnavailableComments(grade.getServiceUnavailableComments());
                    LookupBGS lookupBGS = addLookup(item, cardSuborderItemGrade);
                    lookupBGSList.add(lookupBGS);
                    cardSuborderItemGrade.setLookupBGS(lookupBGS);
                    gradeList.add(cardSuborderItemGrade);
                    continue;
                }

                if(serviceLevel.isEmpty()) {
                    serviceLevel = serviceLevelRepository.findById(cardSuborder.getServiceLevelId().intValue());
                    withoutSubs = serviceLevel.isPresent() && Boolean.FALSE.equals(serviceLevel.get().getSubGrade());
                }

                if(Boolean.FALSE.equals(withoutSubs)) {
                    validateGradeVal(item.getItemName(), grade);
                    cardSuborderItemGrade.setSrCenteringVal(grade.getCentering());
                    cardSuborderItemGrade.setSrCornersVal(grade.getCorners());
                    cardSuborderItemGrade.setSrEdgesVal(grade.getEdges());
                    cardSuborderItemGrade.setSrSurfaceVal(grade.getSurface());
                    cardSuborderItemGrade.setSrAutoVal(grade.getAuto());
                    cardSuborderItemGrade.setSrMinGrade(grade.getMinGrade());
                    cardSuborderItemGrade.setSrFinalGradeVal(grade.getFinalGrade());
                    if(cardSuborderItemGrade.getSrFinalGradeVal().intValue() == 10) {
                        if(!isL1GradingDone) {
                            throw new IllegalArgumentException("Since we are marking the final grade value to 10, we need a senior grader to review on top of this grading. Please consider submitting the grades first.");
                        }
                        cardSuborderItemGrade.setSrIsBlackLabel(Boolean.TRUE);
                    } else {
                        cardSuborderItemGrade.setSrIsBlackLabel(Boolean.FALSE);
                    }
                } else {
                    if(grade.getFinalGrade() != null && grade.getFinalGrade().intValue() == 10) {
                        if(!isL1GradingDone) {
                            throw new IllegalArgumentException("Since we are marking the final grade value to 10, we need a senior grader to review on top of this grading. Please consider submitting the grades first.");
                        }
                        validateGradeVal(item.getItemName(), grade);
                        cardSuborderItemGrade.setSrCenteringVal(grade.getCentering());
                        cardSuborderItemGrade.setSrCornersVal(grade.getCorners());
                        cardSuborderItemGrade.setSrEdgesVal(grade.getEdges());
                        cardSuborderItemGrade.setSrSurfaceVal(grade.getSurface());
                        cardSuborderItemGrade.setSrAutoVal(grade.getAuto());
                        cardSuborderItemGrade.setSrMinGrade(grade.getMinGrade());
                        cardSuborderItemGrade.setSrFinalGradeVal(grade.getFinalGrade());
                        cardSuborderItemGrade.setSrIsBlackLabel(Boolean.TRUE);
                    } else {
                        cardSuborderItemGrade.setSrAutoVal(grade.getAuto());
                        cardSuborderItemGrade.setSrMinGrade(grade.getMinGrade());
                        cardSuborderItemGrade.setSrFinalGradeVal(grade.getFinalGrade());
                        cardSuborderItemGrade.setSrIsBlackLabel(Boolean.FALSE);
                    }
                }
                cardSuborderItemGrade.setCardSuborderItem(item);
                cardSuborderItemGrade.setSrGradedDate(LocalDateTime.now());
                cardSuborderItemGrade.setSrGrader(grader.getUsername());
                cardSuborderItemGrade.setSrGraderComment(grade.getComments());
                cardSuborderItemGrade.setIsGraded(Boolean.TRUE);
                // setting up the grade type from grademaster table
                Optional<GradeMaster> gradeMaster = gradingRepository.findByGradeValue(cardSuborderItemGrade.getSrFinalGradeVal());
                gradeMaster.ifPresent(master -> cardSuborderItemGrade.setGradeType(master.getGradeName()));
                LookupBGS lookupBGS = addLookup(item, cardSuborderItemGrade);
                lookupBGSList.add(lookupBGS);
                cardSuborderItemGrade.setLookupBGS(lookupBGS);
                gradeList.add(cardSuborderItemGrade);
            } else log.warn(ITEM_NOT_FOUND, grade.getCardSuborderItemId());
        }
        Objects.requireNonNull(cardSuborderJob, "Card sub order job must not be null, this should not happen.");
        cardSuborderJob.setGradingStatus(CardSuborderJob.StatusEnum.GRADED);
        cardSuborderJob.setGradedOn(LocalDateTime.now());
        cardSuborderJob.setIsGraded(Boolean.TRUE);
        cardSuborderJob.setGradedBy(grader.getUsername());
        cardSuborderJobRepository.save(cardSuborderJob);
        cardSuborderItemGradeRepository.saveAll(gradeList);
        cardSuborderItemRepository.saveAll(items);
        lookupBGSRepository.saveAll(lookupBGSList);
     // start QC condition apply and make is_qc_applicable true
        Set<String> validServiceLevels = Set.of("Express", "Priority");
        for(CardSuborderItem cardItem : items) {
        	Suborder suborder = suborderRepository.findById(cardSuborder.getCardSuborderId()).orElseThrow();
            boolean isQcApplicable =
                    (cardItem.getDeclaredAmt() != null && cardItem.getDeclaredAmt().longValue() >= 5000) ||
                            (cardItem.getCurrentGrade() != null && cardItem.getCurrentGrade().longValue() == 10) ||
                            (cardItem.getCardSuborderItemGrade() != null && cardItem.getCardSuborderItemGrade().getSrFinalGradeVal() != null && cardItem.getCardSuborderItemGrade().getSrFinalGradeVal().longValue() == 10) ||
                            validServiceLevels.contains(serviceLevel.orElseThrow().getName()) ||
                            !suborder.getShipMethodId().getCountry().getCountryName().equalsIgnoreCase("United States of America");

            if (isQcApplicable) {
                cardSuborderJob.setIsQcApplicable(Boolean.TRUE);
                cardSuborderJob.setQcApplicableOn(LocalDateTime.now());
                cardSuborderJob.setQcApplicableBy(grader.getUsername());
                cardSuborderJobRepository.save(cardSuborderJob);
            }
        }
        //End QC condition
        // need to send a communication email here for Level 2 grading done
        CardSuborderJob finalCardSuborderJob = cardSuborderJob;
        AsyncUtils.perform(
                () -> {
                    // check if all the jobs are graded then update the sub order and order status to graded.
                    List<CardSuborderJob> jobs = cardSuborderJobRepository.findAllByCardSuborderIdHavingItems(finalCardSuborderJob.getCardSuborder().getCardSuborderId());
                    if(jobs != null && !jobs.isEmpty()) {
                        boolean isAllJobsGraded = jobs.stream().filter(job -> !job.getJobNo().equalsIgnoreCase(finalCardSuborderJob.getJobNo())
                        && job.getCardSuborder().getOrder() != null).allMatch(CardSuborderJob::getIsGraded);
                        if(isAllJobsGraded) {
                            // marking the sub order to Graded since all the jobs are graded now.
                            CardSuborder suborder = finalCardSuborderJob.getCardSuborder();
                            suborder.setSuborderStatus(SuborderStatus.GRADED);
                            suborder.getCardSuborderStatus().setSuborderStatus(SuborderStatus.GRADED);
                            suborder.getCardSuborderStatus().setIsGraded(Boolean.TRUE);
                            suborder.getCardSuborderStatus().setGradedOn(LocalDateTime.now());
                            cardSuborderRepository.save(suborder);

                            //HUBSPOT UPDATE
                            crmSyncService.updateDeal(suborder.getCrmDealId(), DealStageEnum.GRADED);
                            Optional<Customer> customer = customerRepository.findByEmail(suborder.getOrder().getCustomerEmail());
                            //Send Email post verification
                            customer.ifPresent(value -> triggerSubOrderGradedEmailToCustomer(
                                    suborder.getSuborderNo(),
                                    INVOICE_NO_PREFIX + suborder.getSuborderNo(),
                                    value.getEmail(),
                                    value.getFirstName()));

                            // going into order level now.
                            Optional<String> orderNo = jobs.stream().map(CardSuborderJob::getCardSuborder).map(CardSuborder::getOrder).map(Order::getOrderNo).findFirst();
                            if(orderNo.isPresent()) {
                                Optional<Order> orderOptional = orderRepository.findByOrderNo(orderNo.get());
                                if(orderOptional.isPresent() && orderOptional.get().getSuborders() != null && !orderOptional.get().getSuborders().isEmpty()) {
                                    List<CardSuborder> cardSuborderList = cardSuborderRepository.findAllById(orderOptional.get().getSuborders().stream().map(Suborder::getSuborderId).toList());
                                    if(!cardSuborderList.isEmpty()) {
                                        boolean isAllCardSubOrdersAreGraded = cardSuborderList.stream().allMatch(cso -> cso.getCardSuborderStatus() != null
                                                && !Objects.equals(cso.getCardSuborderId(), finalCardSuborderJob.getCardSuborder().getCardSuborderId())
                                                && cso.getCardSuborderStatus().getIsGraded()
                                                && cso.getCardSuborderStatus().getSuborderStatus().equals(SuborderStatus.GRADED)
                                                && cso.getSuborderStatus().equals(SuborderStatus.GRADED));
                                        if(isAllCardSubOrdersAreGraded) {
                                            // marking order to graded since all the card sub orders are graded now
                                            Order order = orderOptional.get();
                                            order.setOrderStatus(OrderStatus.GRADED);
                                            orderRepository.save(order);
                                        }
                                    }
                                }
                            }
                        } else {
                            log.warn("All the jobs are not graded yet hence skipping the root level updates.");
                        }
                    }
                },
                () -> {
                    // card label impl for all items in this job or bucket
                    if(!items.isEmpty()) {
                        log.info("Updating card label warehouse table with required line details");
                        items.stream().filter(item -> StringUtils.isNotBlank(item.getItemMasterId())).forEach(item -> {
                            // checking whether we have the data in this card label warehouse table for give item master id
                            log.info("current item master id: {}", item.getItemMasterId());
                            CardLabelWarehouse cardLabelWarehouse;
                            Optional<CardLabelWarehouse> cardLabelWarehouseOptional =
                                    cardLabelWarehouseRepository.findByItemMasterId(item.getItemMasterId());
                            cardLabelWarehouse = cardLabelWarehouseOptional.orElseGet(CardLabelWarehouse::new);
                            // generating lines
                            Map<String, String> linesMap = GradeUtils.prepareLines(item);
                            log.info("Lines: {} that are generated for item master id: {}", linesMap, item.getItemMasterId());
                            if(!linesMap.isEmpty()) {
                                cardLabelWarehouse.setBgsLine1(linesMap.get("line1"));
                                cardLabelWarehouse.setBgsLine2(linesMap.get("line2"));
                                cardLabelWarehouse.setBgsLine3(linesMap.get("line3"));
                                cardLabelWarehouse.setBgsLine4(linesMap.get("line4"));
                                cardLabelWarehouse.setItemMasterId(item.getItemMasterId());
                                cardLabelWarehouseRepository.save(cardLabelWarehouse);
                            }
                        });
                    }
                });
        return null;
    }

    private LookupBGS addLookup(CardSuborderItem item, CardSuborderItemGrade cardSuborderItemGrade) { // NOSONAR

        // creating a new record if in case master id is not present
        LookupBGS lookupBGS = new LookupBGS();
        if(StringUtils.isNotBlank(item.getItemMasterId())) {
            lookupBGS.setMasterItemId(Integer.parseInt(item.getItemMasterId()));
        }

        // defaulting the value to 1
        Optional<GradingServiceType> gradingServiceTypeOptional = gradingServiceTypeRepository.findById(1);
        gradingServiceTypeOptional.ifPresent(lookupBGS::setServiceTypeId);
        if(cardSuborderItemGrade.getSrCenteringVal() != null && cardSuborderItemGrade.getSrCenteringVal() > 0) {
            lookupBGS.setCenterGrade(BigDecimal.valueOf(cardSuborderItemGrade.getSrCenteringVal()));
        } else {
            lookupBGS.setCenterGrade(BigDecimal.ZERO);
        }

        if(cardSuborderItemGrade.getSrCornersVal() != null && cardSuborderItemGrade.getSrCornersVal() > 0) {
            lookupBGS.setCornerGrade(BigDecimal.valueOf(cardSuborderItemGrade.getSrCornersVal()));
        } else {
            lookupBGS.setCornerGrade(BigDecimal.ZERO);
        }
        if(cardSuborderItemGrade.getSrEdgesVal() != null && cardSuborderItemGrade.getSrEdgesVal() > 0) {
            lookupBGS.setEdgesGrade(BigDecimal.valueOf(cardSuborderItemGrade.getSrEdgesVal()));
        } else {
            lookupBGS.setEdgesGrade(BigDecimal.ZERO);
        }
        if(cardSuborderItemGrade.getSrSurfaceVal() != null && cardSuborderItemGrade.getSrSurfaceVal() > 0) {
            lookupBGS.setSurfaceGrade(BigDecimal.valueOf(cardSuborderItemGrade.getSrSurfaceVal()));
        } else {
            lookupBGS.setSurfaceGrade(BigDecimal.ZERO);
        }
        if(cardSuborderItemGrade.getSrAutoVal() != null && cardSuborderItemGrade.getSrAutoVal() > 0) {
            lookupBGS.setAutoGrade(BigDecimal.valueOf(cardSuborderItemGrade.getSrAutoVal()));
        } else {
            lookupBGS.setAutoGrade(BigDecimal.ZERO);
        }
        lookupBGS.setActive(Boolean.FALSE); // currently setting it to false since once the order is received then only we have to make it to true and accordingly the pop-report will get populated
        if(cardSuborderItemGrade.getSrFinalGradeVal() != null && cardSuborderItemGrade.getSrFinalGradeVal() > 0) {
            lookupBGS.setFinalGrade(BigDecimal.valueOf(cardSuborderItemGrade.getSrFinalGradeVal()));
        } else {
            lookupBGS.setFinalGrade(BigDecimal.ZERO);
        }

        lookupBGS.setDateGraded(LocalDateTime.now());
        lookupBGS.setNotes(cardSuborderItemGrade.getSrGraderComment());
        // need to fill up item attribute ids
        if(StringUtils.isNotBlank(item.getItemCategory())) {
            Optional<MasterCategory> category = masterCategoryRepository.findByNameIgnoreCase(item.getItemCategory());
            category.ifPresent(lookupBGS::setMasterCategory);
        }
        return lookupBGS;
    }

    @Override
    public Void assignGraders(List<AssignGradersRequest> assignGradersRequests) {
        assignGradersRequests.forEach(assignGradersRequest -> {
            Optional<Users> user = usersRepository.findById(assignGradersRequest.getGraderId());
            if(user.isEmpty() || Boolean.FALSE.equals(user.get().getActive()) || Boolean.TRUE.equals(user.get().getDeleted())) {
                throw new IllegalArgumentException("Selected Assignee either doesn't exists or it is deactivated, kindly requested to change the selection and try again.");
            }

            List<CardSuborderJob> jobs = cardSuborderJobRepository.findByJobNos(assignGradersRequest.getJobNos());
            if(jobs != null && !jobs.isEmpty()) {
                jobs.forEach(job -> {
                    job.setGrader(user.get());
                    job.setGradingStatus(CardSuborderJob.StatusEnum.GRADING);
                });
                cardSuborderJobRepository.saveAll(jobs);
            }
        });
        return null;
    }

    @Override
    public List<GradingIssueCategories> getGradingIssueCategoryList() {
        List<GradingIssueCategories> categories = new ArrayList<>();

        for(CategoryOfIssues category: CategoryOfIssues.values()) {
            GradingIssueCategories gradingIssueCategories = new GradingIssueCategories();
            gradingIssueCategories.setCategory(category.getCategory());
            gradingIssueCategories.setSubCategories(category.getSubCategories());
            categories.add(gradingIssueCategories);
        }

        return categories;
    }

    @Override
    public GradeResult calculateFinalGrades(String centering, String corners, String edges, String surface) {
        List<RequestModel> toGrade = new ArrayList<>();
        toGrade.add(new RequestModel(centering, "CENTER"));
        toGrade.add(new RequestModel(corners, "CORNERS"));
        toGrade.add(new RequestModel(edges, "EDGES"));
        toGrade.add(new RequestModel(surface, "SURFACE"));

        Set<SubGrade> subgradeSet = subGradeMapper.toSubGradeSet(toGrade);
        //step 2 Sorting subgrades in ascending order:
        List<SubGrade> sorted = getSorted(subgradeSet);
        //step 3 Calculation of Diff Value
        BigDecimal diffValue = calculateDiffValue(sorted);
        //step 4 Formula selection step
        Formulas formula = FormulaFactory.getFormula(diffValue, sorted);
        GradeFormulaDTO gradeFormulas = findGradeFormulas(formula.getValue(), diffValue);
        //step 7
        BigDecimal formulaSum = calculateFormulaSum(sorted, gradeFormulas);
        //step 8 + step 9
        BigDecimal roundNumber = getGradeNumberByFormulaSum(formulaSum);
        //step 10
        BigDecimal bump = roundNumber.subtract(sorted.getFirst().getGrade());
        //step 11
        int bumpCount = getBumpCount(bump);
        //step 12
        BigDecimal takeOffNumber = getTakeoffNumber(formula, bumpCount);
        //step 13
        BigDecimal grading = roundNumber.subtract(takeOffNumber);
        return new GradeResult(grading, gradingDescriptionMapper.getMapping(grading));
    }

    private boolean isGraderIsAtJuniorLevel(Long graderId) {//NOSONAR
        boolean isJunior = false;
        Optional<Users> grader = usersRepository.findById(graderId);
        if(grader.isPresent() && Boolean.TRUE.equals(grader.get().getActive()) && Boolean.FALSE.equals(grader.get().getDeleted())) {
            if (grader.get().getUserBusinessUnits() != null && !grader.get().getUserBusinessUnits().isEmpty()) {
                for (UserBusinessUnit userBusinessUnit : grader.get().getUserBusinessUnits()) {
                    if (userBusinessUnit.getUserBusinessUnitJobRoles() != null && !userBusinessUnit.getUserBusinessUnitJobRoles().isEmpty()) {
                        for (UserBusinessUnitJobRole userBusinessUnitJobRole : userBusinessUnit.getUserBusinessUnitJobRoles()) {
                            if (userBusinessUnitJobRole.getJobRole().getJobRoleName().toUpperCase().startsWith("GRAD")
                                && !Objects.isNull(userBusinessUnitJobRole.getJobRoleLevel())) {
                                return !userBusinessUnitJobRole.getJobRoleLevel().getJobRoleLevelValue().toUpperCase().startsWith("SR");
                            }
                        }
                    }
                }
            }
        } else throw new IllegalArgumentException("Logged in user is not active, please re-login with a diff user.");
        return isJunior;
    }

    private void validateGradeVal(String itemName, ItemGrades grade) {
        // validating the user input whether it's correctly entered or not
        boolean isValid = GradeUtils.isValidGradeVal(grade.getCentering());
        if(!isValid) {
            throw new IllegalArgumentException(String.format(Constants.FIELD_VALIDATION_MESSAGE, "Centering", itemName));
        }
        isValid = GradeUtils.isValidGradeVal(grade.getCorners());
        if(!isValid) {
            throw new IllegalArgumentException(String.format(Constants.FIELD_VALIDATION_MESSAGE, "Corners", itemName));
        }
        isValid = GradeUtils.isValidGradeVal(grade.getEdges());
        if(!isValid) {
            throw new IllegalArgumentException(String.format(Constants.FIELD_VALIDATION_MESSAGE, "Edges", itemName));
        }
        isValid = GradeUtils.isValidGradeVal(grade.getSurface());
        if(!isValid) {
            throw new IllegalArgumentException(String.format(Constants.FIELD_VALIDATION_MESSAGE, "Surface", itemName));
        }
        isValid = GradeUtils.isValidGradeVal(grade.getAuto());
        if(!isValid) {
            throw new IllegalArgumentException(String.format(Constants.FIELD_VALIDATION_MESSAGE, "Auto", itemName));
        }
        isValid = GradeUtils.isValidGradeVal(grade.getMinGrade());
        if(!isValid) {
            throw new IllegalArgumentException(String.format(Constants.FIELD_VALIDATION_MESSAGE, "Min Grade", itemName));
        }
    }

    private String populateLocation(Long cardSubOrderJobId, List<JobLocationMapping> jobLocations) {
        if(jobLocations != null && !jobLocations.isEmpty()) {
            Optional<JobLocationMapping> jobLocationMappingOptional = jobLocations.stream().filter(jobLocationMapping -> Objects.equals(jobLocationMapping.getCardSuborderJob().getId(), cardSubOrderJobId)).findFirst();
            if(jobLocationMappingOptional.isPresent()) {
                Location location = jobLocationMappingOptional.get().getLocation();
                return location.getName() + " - " + location.getLocationNumber();
            }
        }

        return null;
    }

    private String populateUserRole(Users grader) { // NOSONAR
        if(grader.getUserBusinessUnits() != null && !grader.getUserBusinessUnits().isEmpty()) {
            for (UserBusinessUnit userBusinessUnit : grader.getUserBusinessUnits()) {
                if(userBusinessUnit.getUserBusinessUnitJobRoles() != null && !userBusinessUnit.getUserBusinessUnitJobRoles().isEmpty()) {
                    for (UserBusinessUnitJobRole userBusinessUnitJobRole : userBusinessUnit.getUserBusinessUnitJobRoles()) {
                        if(userBusinessUnitJobRole.getJobRole().getJobRoleName().toUpperCase().startsWith("GRAD")) {
                            return userBusinessUnitJobRole.getJobRole().getJobRoleName() + " " + userBusinessUnitJobRole.getJobRoleLevel().getJobRoleLevelValue();
                        }
                    }
                }
            }
        }
        return null;
    }

    private String populateServiceLevel(Integer serviceLevelId, List<ServiceLevel> serviceLevelList) {
        if(serviceLevelList != null && !serviceLevelList.isEmpty()) {
            Optional<ServiceLevel> serviceLevelOptional = serviceLevelList.stream().filter(serviceLevel -> Objects.equals(serviceLevel.getServiceLevelId(), serviceLevelId)).findFirst();
            if(serviceLevelOptional.isPresent()) {
                return serviceLevelOptional.get().getName();
            }
        }
        return null;
    }

    private GradeFormulaDTO findGradeFormulas(String name, BigDecimal diffValue) {
        GradeFormula formula = gradeFormulaRepository
                .findFirstByGradeNameAndGradeBeginningLessThanEqualAndGradeEndingGreaterThanEqual(name, diffValue, diffValue)
                .orElseThrow(FormulaNotFoundException::new);
        return gradeFormulaMapper.map(formula);
    }

    private BigDecimal calculateFormulaSum(List<SubGrade> sortedSubGrades, GradeFormulaDTO gradeFormulaDTO) {
        if (sortedSubGrades.size() != 4) {
            throw new SubGradeSizeExceeded("Expected=4, got=" + sortedSubGrades.size());
        }


        return sortedSubGrades.get(0).getGrade().multiply(gradeFormulaDTO.getGrade1())
                .add(sortedSubGrades.get(1).getGrade().multiply(gradeFormulaDTO.getGrade2()))
                .add(sortedSubGrades.get(2).getGrade().multiply(gradeFormulaDTO.getGrade3()))
                .add(sortedSubGrades.get(3).getGrade().multiply(gradeFormulaDTO.getGrade4()));
    }
    private BigDecimal getGradeNumberByFormulaSum(BigDecimal formulaSum) {
        return gradeRoundNumberRepository
                .findFirstByNumberEndingGreaterThanEqualAndNumberBeginningLessThanEqual(formulaSum, formulaSum)
                .map(GradeRoundNumber::getNumber)
                .orElseGet(() -> new BigDecimal(10));
    }
    private int getBumpCount(BigDecimal bump) {
        return takeOffReferenceRepository.countByNumberLessThanEqual(bump);
    }

    private BigDecimal getTakeoffNumber(Formulas formulas, int bumpCount) {

        if (Objects.requireNonNull(formulas) == Formulas.CORNERS || formulas == Formulas.SURFACE_AND_EDGES) {
            if (bumpCount >= 2) {
                return new BigDecimal(bumpCount - 2)
                        .setScale(2, RoundingMode.HALF_EVEN)
                        .divide(BigDecimal.TWO, RoundingMode.HALF_EVEN);
            } else {
                return BigDecimal.ZERO;
            }
        } else if (formulas == Formulas.CENTERING) {
            if (bumpCount >= 4) {
                return new BigDecimal(bumpCount - 4)
                        .setScale(2, RoundingMode.HALF_EVEN)
                        .divide(BigDecimal.TWO, RoundingMode.HALF_EVEN);
            } else {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    private List<SubGrade> getSorted(Set<SubGrade> toGrade) {
        return toGrade.stream()
                .sorted(Comparator.comparing(SubGrade::getGrade))
                .toList();
    }

    private BigDecimal calculateDiffValue(List<SubGrade> sortedSubGrades) {
        return sortedSubGrades.get(1).getGrade().subtract(sortedSubGrades.get(0).getGrade());
    }

    private void triggerSubOrderGradedEmailToCustomer(
            String suborderNo, String invoiceNo, String customerEmail, String firstName) {
        EmailRequest emailRequest = new EmailRequest();
        emailRequest.setRecipients(Collections.singletonList(
                customerEmail
        ));
        emailRequest.setTemplateName(SUBORDER_GRADED_TEMPLATE_NAME);
        Map<String, String> templateData = new HashMap<>();
        templateData.put(SUBMISSION_ID, String.valueOf(suborderNo));
        templateData.put(FIRST_NAME, firstName);
        templateData.put(INVOICE_NO, invoiceNo);
        emailRequest.setTemplateData(templateData);

        emailTriggerService.sendEmail(emailRequest);
        log.info("Suborder graded email is been sent to the customer for suborder no: {}",
                suborderNo);
    }
}
