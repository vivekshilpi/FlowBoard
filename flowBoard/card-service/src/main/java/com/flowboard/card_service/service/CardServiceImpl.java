package com.flowboard.card_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.card_service.config.RabbitMQConfig;
import com.flowboard.card_service.client.NotificationClient;
import com.flowboard.card_service.client.dto.SendNotificationRequest;
import com.flowboard.card_service.dto.*;
import com.flowboard.card_service.entity.Card;
import com.flowboard.card_service.entity.CardActivity;
import com.flowboard.card_service.enums.CardStatus;
import com.flowboard.card_service.enums.Priority;
import com.flowboard.card_service.event.BoardChangeEvent;
import com.flowboard.card_service.event.CardAssignedEvent;
import com.flowboard.card_service.exception.CustomException;
import com.flowboard.card_service.repository.CardActivityRepository;
import com.flowboard.card_service.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardServiceImpl implements CardService{

    private final CardRepository cardRepository;
    private final CardActivityRepository activityRepository;
    private final RabbitTemplate rabbitTemplate;
    private final NotificationClient notificationClient;
    private final CardAttachmentStorageService attachmentStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public CardResponse createCard(CreateCardRequest request, Long userId) {
        int position;

        if(request.getPosition()!=null){
            cardRepository.shiftPositionsRight(
                    request.getListId(), request.getPosition());
            position = request.getPosition();
        }
        else{
            position= cardRepository.findMaxPositionByListId(request.getListId())
                    .map(max->max+1)
                    .orElse(0);
        }

        Card card = Card.builder()
                .listId(request.getListId())
                .boardId(request.getBoardId())
                .title(request.getTitle())
                .description(request.getDescription())
                .position(position)
                .priority(request.getPriority()!=null ?
                        request.getPriority() : Priority.MEDIUM)
                .status(CardStatus.TO_DO)
                .dueDate(request.getDueDate())
                .startDate(request.getStartDate())
                .assigneeId(request.getAssigneeId())
                .createdById(userId)
                .coverColor(request.getCoverColor())
                .labelsData(writeJson(request.getLabels()))
                .checklistData(writeJson(normalizeChecklistItems(request.getChecklistItems())))
                .attachmentsData(writeJson(List.of()))
                .isArchived(false)
                .createdAt(LocalDateTime.now())
                .build();

        cardRepository.save(card);

        logActivity(card.getId(), userId, "CREATE",
                "created card '"+card.getTitle()+"'",
                null, card.getTitle());

        if(card.getAssigneeId()!=null){
            logActivity(card.getId(), userId, "ASSIGNMENT",
                    "assigned card to userId="+card.getAssigneeId(),
                    null, String.valueOf(card.getAssigneeId()));
            publishAssignmentEvent(card, card.getAssigneeId(), userId);
        }

        log.info("Card created: id={} title={} listId={} boardId={}",
                card.getId(), card.getTitle(), card.getListId(), card.getBoardId());
        publishBoardChange(card.getBoardId(), "CARD", "CREATED", card.getId(), userId);
        return toResponse(card);
    }

    @Override
    public CardResponse getCardById(Long cardId) {
        return toResponse(findCard(cardId));
    }

    @Override
    public List<CardResponse> getCardByList(Long listId) {
        return cardRepository.findByListIdAndIsArchivedFalseOrderByPosition(listId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<CardResponse> getCardByBoard(Long boardId) {
        return cardRepository.findByBoardIdAndIsArchivedFalse(boardId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<CardResponse> getCardByAssignee(Long assigneeId) {
        return cardRepository.findByAssigneeIdAndIsArchivedFalse(assigneeId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public CardResponse updateCard(Long cardId, UpdateCardRequest request, Long userId) {
        Card card = findCard(cardId);

        if(card.isArchived()){
            throw new CustomException("Cannot updtade an archived card - unarchive it first",
                    HttpStatus.BAD_REQUEST);
        }

        if(request.getStatus()!=null &&
                request.getStatus()!=card.getStatus()){
            logActivity(cardId, userId, "STATUS_CHANGE", "changed status from "+card.getStatus()
            +" to "+request.getStatus(),
                    card.getStatus().name(), request.getStatus().name());
        }

        if(request.getPriority()!=null
            && request .getPriority()!=card.getPriority()){
            logActivity(cardId, userId, "PRIORITY_CHANGE",
                    "changed priority from "+ card.getPriority()
            + " to "+request.getPriority(),
                    card.getPriority().name(),
                    request.getPriority().name());
        }

        if(request.getDueDate()!=null
            && !request.getDueDate().equals(card.getDueDate())){
            logActivity(cardId, userId, "DUE_DATE_CHANGE",
                    "changed due date to "+request.getDueDate(),
                    card.getDueDate()!=null?card.getDueDate().toString(): null,
                    request.getDueDate().toString());
        }
        card.setTitle(request.getTitle());
        if (request.getDescription() != null)
            card.setDescription(request.getDescription());
        if (request.getPriority() != null)
            card.setPriority(request.getPriority());
        if (request.getStatus() != null)
            card.setStatus(request.getStatus());
        if (request.getDueDate() != null)
            card.setDueDate(request.getDueDate());
        if (request.getStartDate() != null)
            card.setStartDate(request.getStartDate());
        if (request.getCoverColor() != null)
            card.setCoverColor(request.getCoverColor());
        if (request.getLabels() != null)
            card.setLabelsData(writeJson(request.getLabels()));
        if (request.getChecklistItems() != null)
            card.setChecklistData(writeJson(normalizeChecklistItems(request.getChecklistItems())));

        card.setUpdatedAt(LocalDateTime.now());
        cardRepository.save(card);

        log.info("Card updated: id={}", cardId);
        publishBoardChange(card.getBoardId(), "CARD", "UPDATED", card.getId(), userId);
        return toResponse(card);
    }

    @Override
    @Transactional
    public void deleteCard(Long cardId, Long userId) {
            Card card = findCard(cardId);

            if(!card.isArchived()){
                cardRepository.shiftPositionsLeft(card.getListId(), card.getPosition());
            }

            cardRepository.delete(card);
            log.info("Card deleted: id={} by userId={}", cardId, userId);
            publishBoardChange(card.getBoardId(), "CARD", "DELETED", cardId, userId);
    }

    @Override
    @Transactional
    public CardResponse moveCard(Long cardId,
                                 MoveCardRequest request,
                                 Long userId) {
        Card card = findCard(cardId);

        Long sourceListId = card.getListId();
        Long targetListId = request.getTargetListId();
        boolean movedAcrossLists = !Objects.equals(sourceListId, targetListId);

        String oldLocation = "listId=" + sourceListId;
        String newLocation = "listId=" + targetListId;

        // Step 1: close gap in source list
        cardRepository.shiftPositionsLeft(sourceListId, card.getPosition());

        // Step 2: make room in target list
        int targetPosition;
        if (request.getTargetPosition() != null) {
            cardRepository.shiftPositionsRight(
                    targetListId, request.getTargetPosition());
            targetPosition = request.getTargetPosition();
        } else {
            targetPosition = cardRepository
                    .findMaxPositionByListId(targetListId)
                    .map(max -> max + 1)
                    .orElse(0);
        }

        // Step 3: update card
        card.setListId(targetListId);
        card.setBoardId(request.getTargetBoardId());
        card.setPosition(targetPosition);
        card.setUpdatedAt(LocalDateTime.now());
        cardRepository.save(card);

        logActivity(cardId, userId, "MOVE",
                "moved card from " + oldLocation + " to " + newLocation,
                oldLocation, newLocation);

        log.info("Card moved: id={} from listId={} to listId={}",
                cardId, sourceListId, targetListId);
        publishBoardChange(card.getBoardId(), "CARD", "MOVED", card.getId(), userId);
        if (movedAcrossLists) {
            notifyCardMovement(card, userId, "Card moved",
                    "Card '" + card.getTitle() + "' was moved.");
        }

        return toResponse(card);
    }

    @Override
    @Transactional
    public List<CardResponse> reorderCards(ReorderCardRequest request,
                                           Long userId) {
        List<Long> orderedIds = request.getOrderedCardIds();

        List<Card> listCards = cardRepository
                .findByListIdAndIsArchivedFalseOrderByPosition(request.getListId());

        List<Long> existingIds = listCards.stream()
                .map(Card::getId).toList();

        for (Long id : orderedIds) {
            if (!existingIds.contains(id)) {
                throw new CustomException(
                        "Card id=" + id + " does not belong to list id="
                                + request.getListId(),
                        HttpStatus.BAD_REQUEST);
            }
        }

        AtomicInteger pos = new AtomicInteger(0);
        orderedIds.forEach(id -> {
            Card card = listCards.stream()
                    .filter(c -> c.getId().equals(id))
                    .findFirst().orElseThrow();
            card.setPosition(pos.getAndIncrement());
            card.setUpdatedAt(LocalDateTime.now());
            cardRepository.save(card);
        });

        log.info("Cards reordered in listId={}", request.getListId());
        Long boardId = listCards.isEmpty() ? null : listCards.get(0).getBoardId();
        if (boardId != null) {
            publishBoardChange(boardId, "CARD", "REORDERED", null, userId);
        }

        return cardRepository
                .findByListIdAndIsArchivedFalseOrderByPosition(request.getListId())
                .stream().map(this::toResponse).toList();
    }

    // ── Archive ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CardResponse archiveCard(Long cardId, Long userId) {
        Card card = findCard(cardId);

        if (card.isArchived()) {
            throw new CustomException(
                    "Card is already archived", HttpStatus.BAD_REQUEST);
        }

        cardRepository.shiftPositionsLeft(card.getListId(), card.getPosition());

        card.setArchived(true);
        card.setUpdatedAt(LocalDateTime.now());
        cardRepository.save(card);

        logActivity(cardId, userId, "ARCHIVE",
                "archived card", null, "archived");

        log.info("Card archived: id={}", cardId);
        publishBoardChange(card.getBoardId(), "CARD", "ARCHIVED", card.getId(), userId);
        return toResponse(card);
    }

    @Override
    @Transactional
    public CardResponse unarchiveCard(Long cardId, Long userId) {
        Card card = findCard(cardId);

        if (!card.isArchived()) {
            throw new CustomException(
                    "Card is not archived", HttpStatus.BAD_REQUEST);
        }

        int newPosition = cardRepository
                .findMaxPositionByListId(card.getListId())
                .map(max -> max + 1)
                .orElse(0);

        card.setArchived(false);
        card.setPosition(newPosition);
        card.setUpdatedAt(LocalDateTime.now());
        cardRepository.save(card);

        logActivity(cardId, userId, "UNARCHIVE",
                "unarchived card", "archived", null);

        log.info("Card unarchived: id={} newPosition={}", cardId, newPosition);
        publishBoardChange(card.getBoardId(), "CARD", "UNARCHIVED", card.getId(), userId);
        return toResponse(card);
    }

    @Override
    public List<CardResponse> getArchivedCardsByBoard(Long boardId) {
        return cardRepository.findByBoardIdAndIsArchivedTrue(boardId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<CardResponse> getArchivedCardsByList(Long listId) {
        return cardRepository.findByListIdAndIsArchivedTrue(listId)
                .stream().map(this::toResponse).toList();
    }

    // ── Assignment ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CardResponse setAssignee(Long cardId,
                                    AssignCardRequest request,
                                    Long userId) {
        Card card = findCard(cardId);
        card.setAssigneeId(request.getAssigneeId());
        card.setUpdatedAt(LocalDateTime.now());
        cardRepository.save(card);

        logActivity(cardId, userId, "ASSIGNMENT",
                request.getAssigneeId() != null
                        ? "assigned card to userId=" + request.getAssigneeId()
                        : "unassigned card",
                null, String.valueOf(request.getAssigneeId()));

        // Publish assignment event to RabbitMQ — notification-service listens
        if (request.getAssigneeId() != null) {
            publishAssignmentEvent(card, request.getAssigneeId(), userId);
        }

        publishBoardChange(card.getBoardId(), "CARD", "ASSIGNEE_UPDATED", card.getId(), userId);

        return toResponse(card);
    }

    // ── Priority and Status ───────────────────────────────────────────────────

    @Override
    @Transactional
    public CardResponse setPriority(Long cardId,
                                    SetPriorityRequest request,
                                    Long userId) {
        Card card = findCard(cardId);
        String old = card.getPriority().name();

        card.setPriority(request.getPriority());
        card.setUpdatedAt(LocalDateTime.now());
        cardRepository.save(card);

        logActivity(cardId, userId, "PRIORITY_CHANGE",
                "changed priority from " + old
                        + " to " + request.getPriority(),
                old, request.getPriority().name());
        publishBoardChange(card.getBoardId(), "CARD", "PRIORITY_UPDATED", card.getId(), userId);

        return toResponse(card);
    }

    @Override
    @Transactional
    public CardResponse setStatus(Long cardId,
                                  SetStatusRequest request,
                                  Long userId) {
        Card card = findCard(cardId);
        CardStatus previousStatus = card.getStatus();
        String old = previousStatus.name();

        card.setStatus(request.getStatus());
        card.setUpdatedAt(LocalDateTime.now());
        cardRepository.save(card);

        logActivity(cardId, userId, "STATUS_CHANGE",
                "changed status from " + old
                        + " to " + request.getStatus(),
                old, request.getStatus().name());
        publishBoardChange(card.getBoardId(), "CARD", "STATUS_UPDATED", card.getId(), userId);
        if (previousStatus != CardStatus.DONE && request.getStatus() == CardStatus.DONE) {
            notifyCardMovement(card, userId, "Card moved to Done",
                    "Card '" + card.getTitle() + "' has been marked as Done.");
        }

        return toResponse(card);
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    @Override
    public List<CardResponse> getCardsByStatus(Long boardId, CardStatus status) {
        return cardRepository
                .findByBoardIdAndStatusAndIsArchivedFalse(boardId, status)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<CardResponse> getCardsByPriority(Long boardId, Priority priority) {
        return cardRepository
                .findByBoardIdAndPriorityAndIsArchivedFalse(boardId, priority)
                .stream().map(this::toResponse).toList();
    }

    // ── Overdue ───────────────────────────────────────────────────────────────

    @Override
    public List<CardResponse> getOverdueCardsByBoard(Long boardId) {
        return cardRepository
                .findOverdueByBoardId(boardId, LocalDate.now())
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<CardResponse> getAllOverdueCards() {
        return cardRepository
                .findAllOverdue(LocalDate.now())
                .stream().map(this::toResponse).toList();
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Override
    public List<CardResponse> searchCards(Long boardId, String keyword) {
        return cardRepository
                .searchByTitle(boardId, keyword)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<CardResponse> searchByTitleOrAssignee(String keyword,
                                                      Long assigneeId) {
        return cardRepository
                .searchByTitleOrAssignee(keyword, assigneeId)
                .stream().map(this::toResponse).toList();
    }

    // ── Activity log ──────────────────────────────────────────────────────────

    @Override
    public List<CardActivityResponse> getCardActivity(Long cardId) {
        findCard(cardId); // validate card exists
        return activityRepository
                .findByCardIdOrderByCreatedAtDesc(cardId)
                .stream()
                .map(this::toActivityResponse)
                .toList();
    }

    @Override
    @Transactional
    public CardActivityResponse addComment(Long cardId, AddCardCommentRequest request, Long userId) {
        Card card = findCard(cardId);
        CardActivity parent = null;

        if (request.getParentActivityId() != null) {
            parent = activityRepository.findById(request.getParentActivityId())
                    .orElseThrow(() -> new CustomException("Parent comment not found", HttpStatus.NOT_FOUND));
            if (!Objects.equals(parent.getCardId(), cardId)) {
                throw new CustomException("Parent comment does not belong to this card", HttpStatus.BAD_REQUEST);
            }
        }

        CardActivity activity = CardActivity.builder()
                .cardId(cardId)
                .actorId(userId)
                .actionType("COMMENT")
                .parentActivityId(request.getParentActivityId())
                .description(request.getContent().trim())
                .createdAt(LocalDateTime.now())
                .build();

        activityRepository.save(activity);
        notifyCommentRecipients(card, parent, request.getMentionedUserIds(), userId);
        publishBoardChange(card.getBoardId(), "CARD", "COMMENTED", card.getId(), userId);
        return toActivityResponse(activity);
    }

    @Override
    @Transactional
    public CardActivityResponse updateComment(Long cardId,
                                              Long commentId,
                                              UpdateCardCommentRequest request,
                                              Long userId) {
        findCard(cardId);
        CardActivity comment = findComment(cardId, commentId);
        requireCommentOwner(comment, userId);

        if ("COMMENT_DELETED".equals(comment.getActionType())) {
            throw new CustomException("Deleted comments cannot be edited", HttpStatus.BAD_REQUEST);
        }

        String oldDescription = comment.getDescription();
        String newDescription = request.getContent().trim();
        comment.setDescription(newDescription);
        comment.setOldValue(oldDescription);
        comment.setNewValue(newDescription);
        activityRepository.save(comment);
        return toActivityResponse(comment);
    }

    @Override
    @Transactional
    public void deleteComment(Long cardId, Long commentId, Long userId) {
        findCard(cardId);
        CardActivity comment = findComment(cardId, commentId);
        requireCommentOwner(comment, userId);

        comment.setActionType("COMMENT_DELETED");
        comment.setOldValue(comment.getDescription());
        comment.setNewValue(null);
        comment.setDescription("Comment deleted");
        activityRepository.save(comment);
    }

    @Override
    @Transactional
    public CardResponse uploadAttachment(Long cardId, MultipartFile file, Long userId) {
        Card card = findCard(cardId);
        CardAttachmentStorageService.StoredAttachment storedAttachment = attachmentStorageService.store(cardId, file);

        List<CardAttachmentDto> attachments = getAttachments(card);
        String attachmentId = UUID.randomUUID().toString();
        attachments.add(CardAttachmentDto.builder()
                .id(attachmentId)
                .fileName(storedAttachment.originalName())
                .contentType(storedAttachment.contentType())
                .size(storedAttachment.size())
                .storedPath(storedAttachment.storedPath())
                .downloadUrl(buildAttachmentDownloadUrl(cardId, attachmentId))
                .uploadedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build());

        card.setAttachmentsData(writeJson(attachments));
        card.setUpdatedAt(LocalDateTime.now());
        cardRepository.save(card);
        publishBoardChange(card.getBoardId(), "CARD", "ATTACHMENT_UPLOADED", card.getId(), userId);
        return toResponse(card);
    }

    @Override
    @Transactional
    public CardResponse deleteAttachment(Long cardId, String attachmentId, Long userId) {
        Card card = findCard(cardId);
        List<CardAttachmentDto> attachments = new ArrayList<>(getAttachments(card));
        CardAttachmentDto attachment = attachments.stream()
                .filter(existing -> Objects.equals(existing.getId(), attachmentId))
                .findFirst()
                .orElseThrow(() -> new CustomException("Attachment not found", HttpStatus.NOT_FOUND));

        attachmentStorageService.deleteQuietly(attachment.getStoredPath());
        attachments.removeIf(existing -> Objects.equals(existing.getId(), attachmentId));
        card.setAttachmentsData(writeJson(attachments));
        card.setUpdatedAt(LocalDateTime.now());
        cardRepository.save(card);
        publishBoardChange(card.getBoardId(), "CARD", "ATTACHMENT_DELETED", card.getId(), userId);
        return toResponse(card);
    }

    @Override
    public AttachmentDownload getAttachment(Long cardId, String attachmentId, Long userId) {
        Card card = findCard(cardId);
        CardAttachmentDto attachment = getAttachments(card).stream()
                .filter(existing -> Objects.equals(existing.getId(), attachmentId))
                .findFirst()
                .orElseThrow(() -> new CustomException("Attachment not found", HttpStatus.NOT_FOUND));

        byte[] fileBytes = attachmentStorageService.read(attachment.getStoredPath());
        return new AttachmentDownload(
                new ByteArrayResource(fileBytes),
                attachment.getFileName(),
                attachment.getContentType() == null ? "application/octet-stream" : attachment.getContentType()
        );
    }

    private Card findCard(Long cardId){
        return cardRepository.findById(cardId)
                .orElseThrow(()-> new CustomException("Card not found", HttpStatus.NOT_FOUND));
    }
    private void logActivity(Long cardId, Long actorId,
                             String actionType, String description,
                             String oldValue, String newValue){
        CardActivity activity = CardActivity.builder()
                .cardId(cardId)
                .actorId(actorId)
                .actionType(actionType)
                .parentActivityId(null)
                .description(description)
                .oldValue(oldValue)
                .newValue(newValue)
                .createdAt(LocalDateTime.now())
                .build();

        activityRepository.save(activity);
    }

    private CardResponse toResponse(Card card){
        card.computeOverdue();
        return CardResponse.builder()
                .id(card.getId())
                .listId(card.getListId())
                .boardId(card.getBoardId())
                .title(card.getTitle())
                .description(card.getDescription())
                .position(card.getPosition())
                .priority(card.getPriority())
                .status(card.getStatus())
                .dueDate(card.getDueDate())
                .startDate(card.getStartDate())
                .assigneeId(card.getAssigneeId())
                .createdById(card.getCreatedById())
                .isArchived(card.isArchived())
                .isOverdue(card.isOverdue())
                .coverColor(card.getCoverColor())
                .labels(readStringList(card.getLabelsData()))
                .checklistItems(readChecklist(card.getChecklistData()))
                .attachments(publicAttachments(card.getId(), readAttachments(card.getAttachmentsData())))
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .build();
    }

    private CardActivityResponse toActivityResponse(CardActivity a){
        return CardActivityResponse.builder()
                .id(a.getId())
                .cardId(a.getCardId())
                .actorId(a.getActorId())
                .actionType(a.getActionType())
                .parentActivityId(a.getParentActivityId())
                .description(a.getDescription())
                .oldValue(a.getOldValue())
                .newValue(a.getNewValue())
                .createdAt(a.getCreatedAt())
                .build();
    }

    @Override
    public PagedResponse<CardActivityResponse> getCardActivityPaged(
            Long cardId, int page, int size) {
        findCard(cardId);
        var pageRequest = PageRequest.of(page, size);
        var activityPage = activityRepository
                .findByCardIdOrderByCreatedAtDesc(cardId, pageRequest);
        return PagedResponse.of(activityPage.map(this::toActivityResponse));
    }

    @Override
    public BoardStatsResponse getBoardStats(Long boardId) {
        List<Card> active   = cardRepository.findByBoardIdAndIsArchivedFalse(boardId);
        List<Card> archived = cardRepository.findByBoardIdAndIsArchivedTrue(boardId);
        List<Card> overdue  = cardRepository.findOverdueByBoardId(boardId, LocalDate.now());

        long total     = active.size();
        long completed = active.stream()
                .filter(c -> c.getStatus() == CardStatus.DONE).count();

        Map<String, Long> byStatus = active.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        c -> c.getStatus().name(),
                        java.util.stream.Collectors.counting()));

        Map<String, Long> byPriority = active.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        c -> c.getPriority().name(),
                        java.util.stream.Collectors.counting()));

        Map<Long, Long> byAssignee = active.stream()
                .filter(c -> c.getAssigneeId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        Card::getAssigneeId,
                        java.util.stream.Collectors.counting()));

        return BoardStatsResponse.builder()
                .boardId(boardId)
                .totalCards(total)
                .completedCards(completed)
                .overdueCards(overdue.size())
                .archivedCards(archived.size())
                .completionRate(total == 0 ? 0 :
                        Math.round((double) completed / total * 1000.0) / 10.0)
                .overdueRate(total == 0 ? 0 :
                        Math.round((double) overdue.size() / total * 1000.0) / 10.0)
                .cardsByStatus(byStatus)
                .cardsByPriority(byPriority)
                .cardsByAssignee(byAssignee)
                .build();
    }

    @Override
    @Transactional
    public CardResponse copyCard(Long cardId, Long targetListId, Long userId) {
        Card original = findCard(cardId);

        int position = cardRepository
                .findMaxPositionByListId(targetListId)
                .map(max -> max + 1)
                .orElse(0);

        Card copy = Card.builder()
                .listId(targetListId)
                .boardId(original.getBoardId())
                .title("Copy of " + original.getTitle())
                .description(original.getDescription())
                .position(position)
                .priority(original.getPriority())
                .status(CardStatus.TO_DO)
                .dueDate(original.getDueDate())
                .startDate(original.getStartDate())
                .coverColor(original.getCoverColor())
                .assigneeId(original.getAssigneeId())
                .createdById(userId)
                .isArchived(false)
                .createdAt(LocalDateTime.now())
                .build();

        cardRepository.save(copy);

        logActivity(copy.getId(), userId, "CREATE",
                "copied from card #" + cardId, null, copy.getTitle());

        log.info("Card copied: original={} copy={} targetList={}",
                cardId, copy.getId(), targetListId);
        publishBoardChange(copy.getBoardId(), "CARD", "COPIED", copy.getId(), userId);

        return toResponse(copy);
    }

    private void publishAssignmentEvent(Card card, Long assigneeId, Long userId) {
        CardAssignedEvent event = new CardAssignedEvent(
                card.getId(),
                card.getBoardId(),
                card.getTitle(),
                assigneeId,
                userId,
                null,
                null
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.FLOWBOARD_EXCHANGE,
                RabbitMQConfig.ASSIGNMENT_KEY,
                event
        );
        log.info("CardAssignedEvent published for cardId={}", card.getId());
    }

    private void notifyCardMovement(Card card, Long actorId, String title, String message) {
        if (card.getAssigneeId() == null || Objects.equals(card.getAssigneeId(), actorId)) {
            return;
        }

        SendNotificationRequest request = new SendNotificationRequest();
        request.setRecipientId(card.getAssigneeId());
        request.setActorId(actorId);
        request.setType("MOVE");
        request.setTitle(title);
        request.setMessage(message);
        request.setRelatedId(card.getId());
        request.setRelatedType("CARD");
        request.setDeepLinkUrl(cardDeepLink(card));
        request.setSendEmail(true);
        notificationClient.send(request);
    }

    private void notifyCommentRecipients(Card card,
                                         CardActivity parent,
                                         List<Long> mentionedUserIds,
                                         Long actorId) {
        LinkedHashSet<Long> mentions = new LinkedHashSet<>();
        if (mentionedUserIds != null) {
            mentions.addAll(mentionedUserIds);
        }

        for (Long recipientId : mentions) {
            if (recipientId == null || Objects.equals(recipientId, actorId)) {
                continue;
            }

            SendNotificationRequest request = new SendNotificationRequest();
            request.setRecipientId(recipientId);
            request.setActorId(actorId);
            request.setType("MENTION");
            request.setTitle("You were mentioned in a comment");
            request.setMessage("Someone mentioned you on card '" + card.getTitle() + "'.");
            request.setRelatedId(card.getId());
            request.setRelatedType("CARD");
            request.setDeepLinkUrl(cardDeepLink(card));
            request.setSendEmail(true);
            notificationClient.send(request);
        }

        if (parent != null && !Objects.equals(parent.getActorId(), actorId)) {
            SendNotificationRequest request = new SendNotificationRequest();
            request.setRecipientId(parent.getActorId());
            request.setActorId(actorId);
            request.setType("COMMENT");
            request.setTitle("New reply on your comment");
            request.setMessage("Someone replied on card '" + card.getTitle() + "'.");
            request.setRelatedId(card.getId());
            request.setRelatedType("CARD");
            request.setDeepLinkUrl(cardDeepLink(card));
            notificationClient.send(request);
        }
    }

    private String cardDeepLink(Card card) {
        return "/board/" + card.getBoardId() + "?cardId=" + card.getId();
    }

    private CardActivity findComment(Long cardId, Long commentId) {
        CardActivity comment = activityRepository.findById(commentId)
                .orElseThrow(() -> new CustomException("Comment not found", HttpStatus.NOT_FOUND));
        if (!Objects.equals(comment.getCardId(), cardId)) {
            throw new CustomException("Comment does not belong to this card", HttpStatus.BAD_REQUEST);
        }
        return comment;
    }

    private void requireCommentOwner(CardActivity comment, Long userId) {
        if (!Objects.equals(comment.getActorId(), userId)) {
            throw new CustomException("You can only modify your own comments", HttpStatus.FORBIDDEN);
        }
    }

    private List<ChecklistItemDto> normalizeChecklistItems(List<ChecklistItemDto> checklistItems) {
        if (checklistItems == null) {
            return List.of();
        }

        return checklistItems.stream()
                .filter(item -> item != null && item.getText() != null && !item.getText().trim().isBlank())
                .map(item -> ChecklistItemDto.builder()
                        .id(item.getId() == null || item.getId().isBlank() ? UUID.randomUUID().toString() : item.getId())
                        .text(item.getText().trim())
                        .completed(item.isCompleted())
                        .build())
                .toList();
    }

    private List<String> readStringList(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(source, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<ChecklistItemDto> readChecklist(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(source, new TypeReference<List<ChecklistItemDto>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<CardAttachmentDto> getAttachments(Card card) {
        return readAttachments(card.getAttachmentsData());
    }

    private List<CardAttachmentDto> readAttachments(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(source, new TypeReference<List<CardAttachmentDto>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<CardAttachmentDto> publicAttachments(Long cardId, List<CardAttachmentDto> attachments) {
        return attachments.stream()
                .map(attachment -> CardAttachmentDto.builder()
                        .id(attachment.getId())
                        .fileName(attachment.getFileName())
                        .contentType(attachment.getContentType())
                        .size(attachment.getSize())
                        .storedPath(null)
                        .downloadUrl(buildAttachmentDownloadUrl(cardId, attachment.getId()))
                        .uploadedAt(attachment.getUploadedAt())
                        .build())
                .toList();
    }

    private String buildAttachmentDownloadUrl(Long cardId, String attachmentId) {
        return "/api/v1/cards/" + cardId + "/attachments/" + attachmentId + "/download";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CustomException("Failed to serialize card data", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void publishBoardChange(Long boardId,
                                    String entityType,
                                    String action,
                                    Long entityId,
                                    Long actorUserId) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.FLOWBOARD_EXCHANGE,
                RabbitMQConfig.BOARD_CHANGE_KEY,
                BoardChangeEvent.builder()
                        .boardId(boardId)
                        .entityType(entityType)
                        .action(action)
                        .entityId(entityId)
                        .actorUserId(actorUserId)
                        .occurredAt(LocalDateTime.now())
                        .build()
        );
    }
}
