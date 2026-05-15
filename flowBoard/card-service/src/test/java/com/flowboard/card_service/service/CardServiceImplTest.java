package com.flowboard.card_service.service;

import com.flowboard.card_service.config.RabbitMQConfig;
import com.flowboard.card_service.client.NotificationClient;
import com.flowboard.card_service.client.dto.SendNotificationRequest;
import com.flowboard.card_service.dto.*;
import com.flowboard.card_service.entity.Card;
import com.flowboard.card_service.entity.CardActivity;
import com.flowboard.card_service.enums.CardStatus;
import com.flowboard.card_service.enums.Priority;
import com.flowboard.card_service.exception.CustomException;
import com.flowboard.card_service.repository.CardActivityRepository;
import com.flowboard.card_service.repository.CardRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.ByteArrayResource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardServiceImpl – full coverage suite")
class CardServiceImplTest {

    @Mock CardRepository         cardRepository;
    @Mock CardActivityRepository activityRepository;
    @Mock org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    @Mock NotificationClient     notificationClient;
    @Mock CardAttachmentStorageService attachmentStorageService;

    @InjectMocks CardServiceImpl cardService;

    private Card card;

    @BeforeEach
    void setUp() {
        card = Card.builder()
                .id(1L).listId(10L).boardId(100L)
                .title("Design login page")
                .position(4).priority(Priority.MEDIUM)
                .status(CardStatus.TO_DO).isArchived(false)
                .createdById(1L).createdAt(LocalDateTime.now()).build();
    }

    // ── createCard ─────────────────────────────────────────────────────────────

    @Nested @DisplayName("createCard()")
    class CreateCardTests {

        @Test @DisplayName("appends at end when position null")
        void createCard_appendsAtEnd() {
            CreateCardRequest req = new CreateCardRequest();
            req.setListId(10L); req.setBoardId(100L); req.setTitle("New"); req.setPosition(null);

            when(cardRepository.findMaxPositionByListId(anyLong())).thenReturn(Optional.of(2));
            when(cardRepository.save(any())).thenReturn(card);
            when(activityRepository.save(any())).thenReturn(new CardActivity());

            CardResponse r = cardService.createCard(req, 1L);
            assertThat(r).isNotNull();
            verify(cardRepository, never()).shiftPositionsRight(anyLong(), anyInt());
        }

        @Test @DisplayName("inserts at position and shifts siblings right")
        void createCard_insertsAtPosition() {
            CreateCardRequest req = new CreateCardRequest();
            req.setListId(10L); req.setBoardId(100L); req.setTitle("Insert"); req.setPosition(1);

            when(cardRepository.save(any())).thenReturn(card);
            when(activityRepository.save(any())).thenReturn(new CardActivity());

            cardService.createCard(req, 1L);
            verify(cardRepository).shiftPositionsRight(10L, 1);
        }

        @Test @DisplayName("position 0 when list is empty")
        void createCard_emptyList_position0() {
            CreateCardRequest req = new CreateCardRequest();
            req.setListId(10L); req.setBoardId(100L); req.setTitle("First");

            when(cardRepository.findMaxPositionByListId(anyLong())).thenReturn(Optional.empty());
            when(cardRepository.save(any())).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                assertThat(c.getPosition()).isEqualTo(0);
                return c;
            });
            when(activityRepository.save(any())).thenReturn(new CardActivity());

            cardService.createCard(req, 1L);
        }

        @Test @DisplayName("default priority is MEDIUM when not set")
        void createCard_defaultPriority() {
            CreateCardRequest req = new CreateCardRequest();
            req.setListId(10L); req.setBoardId(100L); req.setTitle("T"); req.setPriority(null);

            when(cardRepository.findMaxPositionByListId(anyLong())).thenReturn(Optional.empty());
            when(cardRepository.save(any())).thenReturn(card);
            when(activityRepository.save(any())).thenReturn(new CardActivity());

            cardService.createCard(req, 1L);
            verify(cardRepository).save(argThat(c -> c.getPriority() == Priority.MEDIUM));
        }
    }

    // ── getCardById ────────────────────────────────────────────────────────────

    @Test @DisplayName("getCardById – throws 404 when not found")
    void getCardById_notFound() {
        when(cardRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> cardService.getCardById(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException)e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test @DisplayName("getCardById – returns card when found")
    void getCardById_success() {
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        CardResponse r = cardService.getCardById(1L);
        assertThat(r.getId()).isEqualTo(1L);
    }

    // ── getCardByList / getCardByBoard / getCardByAssignee ────────────────────

    @Test @DisplayName("getCardByList – returns active cards sorted by position")
    void getCardByList_success() {
        when(cardRepository.findByListIdAndIsArchivedFalseOrderByPosition(10L))
                .thenReturn(List.of(card));
        assertThat(cardService.getCardByList(10L)).hasSize(1);
    }

    @Test @DisplayName("getCardByBoard – returns non-archived board cards")
    void getCardByBoard_success() {
        when(cardRepository.findByBoardIdAndIsArchivedFalse(100L)).thenReturn(List.of(card));
        assertThat(cardService.getCardByBoard(100L)).hasSize(1);
    }

    @Test @DisplayName("getCardByAssignee – returns cards for given user")
    void getCardByAssignee_success() {
        when(cardRepository.findByAssigneeIdAndIsArchivedFalse(5L)).thenReturn(List.of(card));
        assertThat(cardService.getCardByAssignee(5L)).hasSize(1);
    }

    // ── updateCard ────────────────────────────────────────────────────────────

    @Test @DisplayName("updateCard – updates fields and logs activity")
    void updateCard_success() {
        UpdateCardRequest req = new UpdateCardRequest();
        req.setTitle("Updated title"); req.setStatus(CardStatus.IN_PROGRESS);
        req.setPriority(Priority.HIGH);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenReturn(card);
        when(activityRepository.save(any())).thenReturn(new CardActivity());

        CardResponse r = cardService.updateCard(1L, req, 1L);
        assertThat(r).isNotNull();
        verify(activityRepository, atLeastOnce()).save(any());
    }

    @Test @DisplayName("updateCard – throws 400 on archived card")
    void updateCard_archived_throws() {
        card.setArchived(true);
        UpdateCardRequest req = new UpdateCardRequest(); req.setTitle("X");

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> cardService.updateCard(1L, req, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException)e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── deleteCard ────────────────────────────────────────────────────────────

    @Test @DisplayName("deleteCard – shifts left and deletes")
    void deleteCard_success() {
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        cardService.deleteCard(1L, 1L);
        verify(cardRepository).shiftPositionsLeft(card.getListId(), card.getPosition());
        verify(cardRepository).delete(card);
    }

    // ── moveCard ──────────────────────────────────────────────────────────────

    @Test @DisplayName("moveCard – closes gap in source and opens in target")
    void moveCard_success() {
        MoveCardRequest req = new MoveCardRequest();
        req.setTargetListId(20L); req.setTargetBoardId(100L); req.setTargetPosition(null);
        card.setAssigneeId(5L);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.findMaxPositionByListId(20L)).thenReturn(Optional.of(3));
        when(cardRepository.save(any())).thenReturn(card);
        when(activityRepository.save(any())).thenReturn(new CardActivity());

        cardService.moveCard(1L, req, 1L);

        verify(cardRepository).shiftPositionsLeft(10L, 4);
        assertThat(card.getListId()).isEqualTo(20L);
        assertThat(card.getPosition()).isEqualTo(4);
        verify(notificationClient).send(argThat(reqPayload ->
                reqPayload.getRecipientId().equals(5L)
                        && reqPayload.getActorId().equals(1L)
                        && reqPayload.getType().equals("MOVE")
                        && reqPayload.getTitle().equals("Card moved")
                        && reqPayload.getRelatedId().equals(1L)
                        && reqPayload.getDeepLinkUrl().equals("/board/100?cardId=1")
                        && reqPayload.isSendEmail()));
    }

    @Test @DisplayName("moveCard – with targetPosition inserts at specific slot")
    void moveCard_withPosition() {
        MoveCardRequest req = new MoveCardRequest();
        req.setTargetListId(20L); req.setTargetBoardId(100L); req.setTargetPosition(2);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenReturn(card);
        when(activityRepository.save(any())).thenReturn(new CardActivity());

        cardService.moveCard(1L, req, 1L);
        verify(cardRepository).shiftPositionsRight(20L, 2);
        assertThat(card.getPosition()).isEqualTo(2);
    }

    @Test @DisplayName("moveCard – does not notify when only reordered inside same list")
    void moveCard_sameList_doesNotNotify() {
        MoveCardRequest req = new MoveCardRequest();
        req.setTargetListId(10L); req.setTargetBoardId(100L); req.setTargetPosition(1);
        card.setAssigneeId(5L);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenReturn(card);
        when(activityRepository.save(any())).thenReturn(new CardActivity());

        cardService.moveCard(1L, req, 1L);

        verify(notificationClient, never()).send(any(SendNotificationRequest.class));
    }

    // ── archiveCard / unarchiveCard ───────────────────────────────────────────

    @Nested @DisplayName("archiveCard()")
    class ArchiveTests {

        @Test @DisplayName("archives card and shifts siblings left")
        void archiveCard_success() {
            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenReturn(card);
            when(activityRepository.save(any())).thenReturn(new CardActivity());

            CardResponse r = cardService.archiveCard(1L, 1L);
            assertThat(r).isNotNull();
            verify(cardRepository).shiftPositionsLeft(card.getListId(), card.getPosition());
            assertThat(card.isArchived()).isTrue();
        }

        @Test @DisplayName("throws 400 when already archived")
        void archiveCard_alreadyArchived() {
            card.setArchived(true);
            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            assertThatThrownBy(() -> cardService.archiveCard(1L, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException)e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test @DisplayName("unarchiveCard – restores and appends at end")
    void unarchiveCard_success() {
        card.setArchived(true);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.findMaxPositionByListId(anyLong())).thenReturn(Optional.of(5));
        when(cardRepository.save(any())).thenReturn(card);
        when(activityRepository.save(any())).thenReturn(new CardActivity());

        cardService.unarchiveCard(1L, 1L);
        assertThat(card.isArchived()).isFalse();
        assertThat(card.getPosition()).isEqualTo(6);
    }

    // ── setPriority / setStatus / setAssignee ─────────────────────────────────

    @Test @DisplayName("setPriority – updates priority and logs PRIORITY_CHANGE")
    void setPriority_success() {
        SetPriorityRequest req = new SetPriorityRequest(); req.setPriority(Priority.HIGH);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenReturn(card);
        when(activityRepository.save(any())).thenReturn(new CardActivity());

        cardService.setPriority(1L, req, 1L);
        assertThat(card.getPriority()).isEqualTo(Priority.HIGH);
        verify(activityRepository).save(argThat(a -> a.getActionType().equals("PRIORITY_CHANGE")));
    }

    @Test @DisplayName("setStatus – updates status and logs STATUS_CHANGE")
    void setStatus_success() {
        SetStatusRequest req = new SetStatusRequest(); req.setStatus(CardStatus.IN_PROGRESS);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenReturn(card);
        when(activityRepository.save(any())).thenReturn(new CardActivity());

        cardService.setStatus(1L, req, 1L);
        assertThat(card.getStatus()).isEqualTo(CardStatus.IN_PROGRESS);
        verify(activityRepository).save(argThat(a ->
                a.getActionType().equals("STATUS_CHANGE") &&
                        a.getOldValue().equals("TO_DO") &&
                        a.getNewValue().equals("IN_PROGRESS")));
        verify(notificationClient, never()).send(any(SendNotificationRequest.class));
    }

    @Test @DisplayName("setStatus – notifies assignee when moved to done by another user")
    void setStatus_done_notifiesAssignee() {
        SetStatusRequest req = new SetStatusRequest(); req.setStatus(CardStatus.DONE);
        card.setAssigneeId(5L);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenReturn(card);
        when(activityRepository.save(any())).thenReturn(new CardActivity());

        cardService.setStatus(1L, req, 1L);

        verify(notificationClient).send(argThat(reqPayload ->
                reqPayload.getRecipientId().equals(5L)
                        && reqPayload.getActorId().equals(1L)
                        && reqPayload.getType().equals("MOVE")
                        && reqPayload.getTitle().equals("Card moved to Done")
                        && reqPayload.getMessage().contains("marked as Done")
                        && reqPayload.getRelatedId().equals(1L)
                        && reqPayload.getDeepLinkUrl().equals("/board/100?cardId=1")
                        && reqPayload.isSendEmail()));
    }

    @Test @DisplayName("setStatus – does not notify when card was already done")
    void setStatus_alreadyDone_doesNotNotify() {
        SetStatusRequest req = new SetStatusRequest(); req.setStatus(CardStatus.DONE);
        card.setStatus(CardStatus.DONE);
        card.setAssigneeId(5L);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenReturn(card);
        when(activityRepository.save(any())).thenReturn(new CardActivity());

        cardService.setStatus(1L, req, 1L);

        verify(notificationClient, never()).send(any(SendNotificationRequest.class));
    }

    @Test @DisplayName("setStatus – does not notify when actor is the assignee")
    void setStatus_done_byAssignee_doesNotNotify() {
        SetStatusRequest req = new SetStatusRequest(); req.setStatus(CardStatus.DONE);
        card.setAssigneeId(1L);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenReturn(card);
        when(activityRepository.save(any())).thenReturn(new CardActivity());

        cardService.setStatus(1L, req, 1L);

        verify(notificationClient, never()).send(any(SendNotificationRequest.class));
    }

    @Test @DisplayName("setAssignee – assigns user and publishes RabbitMQ event")
    void setAssignee_publishesEvent() {
        AssignCardRequest req = new AssignCardRequest(); req.setAssigneeId(5L);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenReturn(card);
        when(activityRepository.save(any())).thenReturn(new CardActivity());

        cardService.setAssignee(1L, req, 1L);
        assertThat(card.getAssigneeId()).isEqualTo(5L);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.FLOWBOARD_EXCHANGE),
                eq(RabbitMQConfig.ASSIGNMENT_KEY),
                any(Object.class));
    }

    @Test @DisplayName("setAssignee – removes assignee when null, no rabbit event")
    void setAssignee_unassign() {
        AssignCardRequest req = new AssignCardRequest(); req.setAssigneeId(null);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenReturn(card);
        when(activityRepository.save(any())).thenReturn(new CardActivity());

        cardService.setAssignee(1L, req, 1L);
        verify(rabbitTemplate, never()).convertAndSend(
                eq(RabbitMQConfig.FLOWBOARD_EXCHANGE),
                eq(RabbitMQConfig.ASSIGNMENT_KEY),
                any(Object.class));
    }

    // ── overdue / search / stats ──────────────────────────────────────────────

    @Test @DisplayName("getOverdueCardsByBoard – returns overdue cards")
    void getOverdueByBoard_success() {
        when(cardRepository.findOverdueByBoardId(100L, LocalDate.now())).thenReturn(List.of(card));
        assertThat(cardService.getOverdueCardsByBoard(100L)).hasSize(1);
    }

    @Test @DisplayName("getAllOverdueCards – platform-wide overdue")
    void getAllOverdue_success() {
        when(cardRepository.findAllOverdue(any())).thenReturn(List.of(card));
        assertThat(cardService.getAllOverdueCards()).hasSize(1);
    }

    @Test @DisplayName("searchCards – filters by board and keyword")
    void searchCards_success() {
        when(cardRepository.searchByTitle(100L, "login")).thenReturn(List.of(card));
        assertThat(cardService.searchCards(100L, "login")).hasSize(1);
    }

    @Test @DisplayName("getBoardStats – returns correct counts and rates")
    void getBoardStats_success() {
        Card doneCard = Card.builder().id(2L).boardId(100L).status(CardStatus.DONE)
                .priority(Priority.HIGH).isArchived(false).createdAt(LocalDateTime.now()).build();

        when(cardRepository.findByBoardIdAndIsArchivedFalse(100L)).thenReturn(List.of(card, doneCard));
        when(cardRepository.findByBoardIdAndIsArchivedTrue(100L)).thenReturn(List.of());
        when(cardRepository.findOverdueByBoardId(eq(100L), any())).thenReturn(List.of());

        BoardStatsResponse stats = cardService.getBoardStats(100L);
        assertThat(stats.getTotalCards()).isEqualTo(2);
        assertThat(stats.getCompletedCards()).isEqualTo(1);
        assertThat(stats.getCompletionRate()).isEqualTo(50.0);
    }

    // ── copyCard ──────────────────────────────────────────────────────────────

    @Test @DisplayName("copyCard – creates new card in same list with 'Copy of' prefix")
    void copyCard_success() {
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.findMaxPositionByListId(anyLong())).thenReturn(Optional.of(3));
        when(cardRepository.save(any())).thenReturn(card);
        when(activityRepository.save(any())).thenReturn(new CardActivity());

        CardResponse r = cardService.copyCard(1L, 10L, 1L);
        verify(cardRepository).save(argThat(c -> c.getTitle().startsWith("Copy of")));
    }

    // ── activity paged ────────────────────────────────────────────────────────

    @Test @DisplayName("getCardActivityPaged – returns page of activity logs")
    void getCardActivityPaged_success() {
        CardActivity act = CardActivity.builder().id(1L).cardId(1L).actorId(1L)
                .actionType("CREATE").createdAt(LocalDateTime.now()).build();

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(activityRepository.findByCardIdOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(act)));

        PagedResponse<CardActivityResponse> page = cardService.getCardActivityPaged(1L, 0, 10);
        assertThat(page.getContent()).hasSize(1);
    }

    // ── getArchivedCards ──────────────────────────────────────────────────────

    @Test @DisplayName("getArchivedCardsByBoard – returns archived cards")
    void getArchivedByBoard() {
        card.setArchived(true);
        when(cardRepository.findByBoardIdAndIsArchivedTrue(100L)).thenReturn(List.of(card));
        assertThat(cardService.getArchivedCardsByBoard(100L)).hasSize(1);
    }

    @Test @DisplayName("getArchivedCardsByList – returns archived cards in list")
    void getArchivedByList() {
        card.setArchived(true);
        when(cardRepository.findByListIdAndIsArchivedTrue(10L)).thenReturn(List.of(card));
        assertThat(cardService.getArchivedCardsByList(10L)).hasSize(1);
    }

    // ── getCardsByStatus / getCardsByPriority ─────────────────────────────────

    @Test @DisplayName("getCardsByStatus – filters by status in board")
    void getCardsByStatus() {
        when(cardRepository.findByBoardIdAndStatusAndIsArchivedFalse(100L, CardStatus.TO_DO))
                .thenReturn(List.of(card));
        assertThat(cardService.getCardsByStatus(100L, CardStatus.TO_DO)).hasSize(1);
    }

    @Test @DisplayName("getCardsByPriority – filters by priority in board")
    void getCardsByPriority() {
        when(cardRepository.findByBoardIdAndPriorityAndIsArchivedFalse(100L, Priority.MEDIUM))
                .thenReturn(List.of(card));
        assertThat(cardService.getCardsByPriority(100L, Priority.MEDIUM)).hasSize(1);
    }

    @Test @DisplayName("reorderCards – updates positions and returns reordered cards")
    void reorderCards_success() {
        ReorderCardRequest req = new ReorderCardRequest();
        req.setListId(10L);
        req.setOrderedCardIds(List.of(2L, 1L));

        Card second = Card.builder().id(2L).listId(10L).boardId(100L).title("Second")
                .position(0).priority(Priority.MEDIUM).status(CardStatus.TO_DO)
                .createdById(1L).createdAt(LocalDateTime.now()).build();

        when(cardRepository.findByListIdAndIsArchivedFalseOrderByPosition(10L))
                .thenReturn(List.of(card, second))
                .thenReturn(List.of(second, card));
        when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<CardResponse> result = cardService.reorderCards(req, 1L);

        assertThat(result).hasSize(2);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.FLOWBOARD_EXCHANGE),
                eq(RabbitMQConfig.BOARD_CHANGE_KEY), ArgumentMatchers.<Object>any());
    }

    @Test @DisplayName("reorderCards – rejects cards from another list")
    void reorderCards_rejectsInvalidIds() {
        ReorderCardRequest req = new ReorderCardRequest();
        req.setListId(10L);
        req.setOrderedCardIds(List.of(99L));

        when(cardRepository.findByListIdAndIsArchivedFalseOrderByPosition(10L)).thenReturn(List.of(card));

        assertThatThrownBy(() -> cardService.reorderCards(req, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test @DisplayName("getCardActivity – validates card exists and maps activities")
    void getCardActivity_success() {
        CardActivity activity = CardActivity.builder().id(5L).cardId(1L).actorId(1L)
                .actionType("COMMENT").description("hello").createdAt(LocalDateTime.now()).build();
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(activityRepository.findByCardIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(activity));

        List<CardActivityResponse> result = cardService.getCardActivity(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDescription()).isEqualTo("hello");
    }

    @Test @DisplayName("addComment – sends mention and reply notifications")
    void addComment_sendsNotifications() {
        AddCardCommentRequest request = new AddCardCommentRequest();
        request.setContent("  Replying here  ");
        request.setParentActivityId(10L);
        request.setMentionedUserIds(List.of(7L, 8L, 1L, 7L));

        CardActivity parent = CardActivity.builder().id(10L).cardId(1L).actorId(9L)
                .actionType("COMMENT").description("parent").createdAt(LocalDateTime.now()).build();
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(activityRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardActivityResponse response = cardService.addComment(1L, request, 1L);

        assertThat(response.getDescription()).isEqualTo("Replying here");
        verify(notificationClient, times(3)).send(any(SendNotificationRequest.class));
    }

    @Test @DisplayName("addComment – rejects parent from another card")
    void addComment_rejectsParentFromAnotherCard() {
        AddCardCommentRequest request = new AddCardCommentRequest();
        request.setContent("reply");
        request.setParentActivityId(10L);

        CardActivity parent = CardActivity.builder().id(10L).cardId(2L).actorId(9L).build();
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(activityRepository.findById(10L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> cardService.addComment(1L, request, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test @DisplayName("updateComment – edits own comment")
    void updateComment_success() {
        UpdateCardCommentRequest request = new UpdateCardCommentRequest();
        request.setContent(" updated ");
        CardActivity comment = CardActivity.builder().id(11L).cardId(1L).actorId(1L)
                .actionType("COMMENT").description("old").createdAt(LocalDateTime.now()).build();

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(activityRepository.findById(11L)).thenReturn(Optional.of(comment));
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardActivityResponse response = cardService.updateComment(1L, 11L, request, 1L);

        assertThat(response.getDescription()).isEqualTo("updated");
        assertThat(comment.getOldValue()).isEqualTo("old");
    }

    @Test @DisplayName("updateComment – rejects non-owner")
    void updateComment_rejectsNonOwner() {
        UpdateCardCommentRequest request = new UpdateCardCommentRequest();
        request.setContent("updated");
        CardActivity comment = CardActivity.builder().id(11L).cardId(1L).actorId(2L)
                .actionType("COMMENT").description("old").build();

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(activityRepository.findById(11L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> cardService.updateComment(1L, 11L, request, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test @DisplayName("deleteComment – marks comment as deleted")
    void deleteComment_success() {
        CardActivity comment = CardActivity.builder().id(11L).cardId(1L).actorId(1L)
                .actionType("COMMENT").description("old").build();
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(activityRepository.findById(11L)).thenReturn(Optional.of(comment));

        cardService.deleteComment(1L, 11L, 1L);

        assertThat(comment.getActionType()).isEqualTo("COMMENT_DELETED");
        assertThat(comment.getDescription()).isEqualTo("Comment deleted");
        verify(activityRepository).save(comment);
    }

    @Test @DisplayName("uploadAttachment – stores file and exposes public download URL")
    void uploadAttachment_success() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        CardAttachmentStorageService.StoredAttachment stored = new CardAttachmentStorageService.StoredAttachment(
                "/tmp/file", "a.txt", "text/plain", 5L);
        card.setAttachmentsData("[]");

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(attachmentStorageService.store(1L, file)).thenReturn(stored);
        when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardResponse response = cardService.uploadAttachment(1L, file, 1L);

        assertThat(response.getAttachments()).hasSize(1);
        assertThat(response.getAttachments().get(0).getDownloadUrl())
                .isEqualTo("/api/v1/cards/1/attachments/" + response.getAttachments().get(0).getId() + "/download");
    }

    @Test @DisplayName("deleteAttachment – removes stored file metadata")
    void deleteAttachment_success() {
        card.setAttachmentsData("[{\"id\":\"att-1\",\"fileName\":\"a.txt\",\"contentType\":\"text/plain\",\"size\":5,\"storedPath\":\"/tmp/file\",\"uploadedAt\":\"2026-05-15T00:00:00\"}]");
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardResponse response = cardService.deleteAttachment(1L, "att-1", 1L);

        verify(attachmentStorageService).deleteQuietly("/tmp/file");
        assertThat(response.getAttachments()).isEmpty();
    }

    @Test @DisplayName("getAttachment – loads bytes and returns resource")
    void getAttachment_success() {
        card.setAttachmentsData("[{\"id\":\"att-1\",\"fileName\":\"a.txt\",\"contentType\":\"text/plain\",\"size\":5,\"storedPath\":\"/tmp/file\",\"uploadedAt\":\"2026-05-15T00:00:00\"}]");
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(attachmentStorageService.read("/tmp/file")).thenReturn("hello".getBytes());

        CardService.AttachmentDownload download = cardService.getAttachment(1L, "att-1", 1L);

        assertThat(download.fileName()).isEqualTo("a.txt");
        assertThat(download.resource()).isInstanceOf(ByteArrayResource.class);
    }
}
