package com.flowboard.card_service.controller;

import com.flowboard.card_service.dto.AddCardCommentRequest;
import com.flowboard.card_service.dto.AssignCardRequest;
import com.flowboard.card_service.dto.BoardStatsResponse;
import com.flowboard.card_service.dto.CardActivityResponse;
import com.flowboard.card_service.dto.CardResponse;
import com.flowboard.card_service.dto.CreateCardRequest;
import com.flowboard.card_service.dto.MoveCardRequest;
import com.flowboard.card_service.dto.PagedResponse;
import com.flowboard.card_service.dto.ReorderCardRequest;
import com.flowboard.card_service.dto.SetPriorityRequest;
import com.flowboard.card_service.dto.SetStatusRequest;
import com.flowboard.card_service.dto.UpdateCardCommentRequest;
import com.flowboard.card_service.dto.UpdateCardRequest;
import com.flowboard.card_service.enums.CardStatus;
import com.flowboard.card_service.enums.Priority;
import com.flowboard.card_service.exception.CustomException;
import com.flowboard.card_service.service.CardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardController unit tests")
class CardControllerTest {

    @Mock private CardService cardService;
    @InjectMocks private CardController controller;

    private final Long userId = 1L;

    @Test
    void createRequiresHeader() {
        assertThatThrownBy(() -> controller.create(new CreateCardRequest(), null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createReturnsCreated() {
        CreateCardRequest request = new CreateCardRequest();
        CardResponse response = CardResponse.builder().id(1L).listId(10L).build();
        when(cardService.createCard(request, userId)).thenReturn(response);

        ResponseEntity<CardResponse> entity = controller.create(request, userId);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(entity.getBody()).isEqualTo(response);
    }

    @Test
    void getEndpointsReturnOk() {
        CardResponse card = CardResponse.builder().id(1L).listId(10L).build();
        when(cardService.getCardById(1L)).thenReturn(card);
        when(cardService.getCardByList(10L)).thenReturn(List.of(card));
        when(cardService.getCardByBoard(100L)).thenReturn(List.of(card));
        when(cardService.getCardByAssignee(5L)).thenReturn(List.of(card));
        when(cardService.getArchivedCardsByBoard(100L)).thenReturn(List.of(card));
        when(cardService.getArchivedCardsByList(10L)).thenReturn(List.of(card));
        when(cardService.getCardsByStatus(100L, CardStatus.TO_DO)).thenReturn(List.of(card));
        when(cardService.getCardsByPriority(100L, Priority.HIGH)).thenReturn(List.of(card));
        when(cardService.getOverdueCardsByBoard(100L)).thenReturn(List.of(card));
        when(cardService.getAllOverdueCards()).thenReturn(List.of(card));
        when(cardService.searchCards(100L, "abc")).thenReturn(List.of(card));
        when(cardService.searchByTitleOrAssignee("abc", 5L)).thenReturn(List.of(card));

        assertThat(controller.getById(1L).getBody()).isEqualTo(card);
        assertThat(controller.getByList(10L).getBody()).hasSize(1);
        assertThat(controller.getByBoard(100L).getBody()).hasSize(1);
        assertThat(controller.getByAssignee(5L).getBody()).hasSize(1);
        assertThat(controller.getArchivedByBoard(100L).getBody()).hasSize(1);
        assertThat(controller.getArchivedByList(10L).getBody()).hasSize(1);
        assertThat(controller.getByStatus(100L, CardStatus.TO_DO).getBody()).hasSize(1);
        assertThat(controller.getByPriority(100L, Priority.HIGH).getBody()).hasSize(1);
        assertThat(controller.getOverdueByBoard(100L).getBody()).hasSize(1);
        assertThat(controller.getAllOverdue().getBody()).hasSize(1);
        assertThat(controller.search(100L, "abc").getBody()).hasSize(1);
        assertThat(controller.searchGlobal("abc", 5L).getBody()).hasSize(1);
    }

    @Test
    void updateDeleteMoveAndReorderReturnExpectedResponses() {
        UpdateCardRequest update = new UpdateCardRequest();
        MoveCardRequest move = new MoveCardRequest();
        ReorderCardRequest reorder = new ReorderCardRequest();
        CardResponse card = CardResponse.builder().id(1L).listId(10L).build();
        when(cardService.updateCard(1L, update, userId)).thenReturn(card);
        when(cardService.moveCard(1L, move, userId)).thenReturn(card);
        when(cardService.reorderCards(reorder, userId)).thenReturn(List.of(card));

        assertThat(controller.update(1L, update, userId).getBody()).isEqualTo(card);
        assertThat(controller.move(1L, move, userId).getBody()).isEqualTo(card);
        assertThat(controller.reorder(reorder, userId).getBody()).hasSize(1);
        assertThat(controller.delete(1L, userId).getBody()).isEqualTo("Card deleted successfully");
        verify(cardService).deleteCard(1L, userId);
    }

    @Test
    void archiveAssignmentPriorityAndStatusEndpointsReturnOk() {
        CardResponse card = CardResponse.builder().id(1L).listId(10L).build();
        AssignCardRequest assign = new AssignCardRequest();
        SetPriorityRequest priority = new SetPriorityRequest();
        SetStatusRequest status = new SetStatusRequest();

        when(cardService.archiveCard(1L, userId)).thenReturn(card);
        when(cardService.unarchiveCard(1L, userId)).thenReturn(card);
        when(cardService.setAssignee(1L, assign, userId)).thenReturn(card);
        when(cardService.setPriority(1L, priority, userId)).thenReturn(card);
        when(cardService.setStatus(1L, status, userId)).thenReturn(card);

        assertThat(controller.archive(1L, userId).getBody()).isEqualTo(card);
        assertThat(controller.unarchive(1L, userId).getBody()).isEqualTo(card);
        assertThat(controller.setAssignment(1L, assign, userId).getBody()).isEqualTo(card);
        assertThat(controller.setPriority(1L, priority, userId).getBody()).isEqualTo(card);
        assertThat(controller.setStatus(1L, status, userId).getBody()).isEqualTo(card);
    }

    @Test
    void commentEndpointsReturnExpectedResponses() {
        CardActivityResponse activity = CardActivityResponse.builder().id(9L).cardId(1L).description("hello").build();
        AddCardCommentRequest add = new AddCardCommentRequest();
        UpdateCardCommentRequest update = new UpdateCardCommentRequest();

        when(cardService.getCardActivity(1L)).thenReturn(List.of(activity));
        when(cardService.addComment(1L, add, userId)).thenReturn(activity);
        when(cardService.updateComment(1L, 9L, update, userId)).thenReturn(activity);
        when(cardService.getCardActivityPaged(1L, 0, 20)).thenReturn(PagedResponse.of(org.springframework.data.domain.Page.empty()));

        assertThat(controller.getActivity(1L).getBody()).hasSize(1);
        assertThat(controller.addComment(1L, add, userId).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.updateComment(1L, 9L, update, userId).getBody()).isEqualTo(activity);
        assertThat(controller.deleteComment(1L, 9L, userId).getBody()).isEqualTo("Comment deleted successfully");
        assertThat(controller.getActivityPaged(1L, 0, 20).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(cardService).deleteComment(1L, 9L, userId);
    }

    @Test
    void attachmentEndpointsReturnExpectedResponses() {
        CardResponse card = CardResponse.builder().id(1L).listId(10L).build();
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        CardService.AttachmentDownload download = new CardService.AttachmentDownload(
                new ByteArrayResource("hello".getBytes()), "a.txt", "text/plain");

        when(cardService.uploadAttachment(1L, file, userId)).thenReturn(card);
        when(cardService.deleteAttachment(1L, "att-1", userId)).thenReturn(card);
        when(cardService.getAttachment(1L, "att-1", userId)).thenReturn(download);

        assertThat(controller.uploadAttachment(1L, file, userId).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.deleteAttachment(1L, "att-1", userId).getBody()).isEqualTo(card);
        ResponseEntity<ByteArrayResource> response = controller.downloadAttachment(1L, "att-1", userId);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("a.txt");
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void statsAndCopyEndpointsReturnExpectedResponses() {
        BoardStatsResponse stats = BoardStatsResponse.builder().boardId(100L).totalCards(3L).build();
        CardResponse original = CardResponse.builder().id(1L).listId(10L).build();
        CardResponse copy = CardResponse.builder().id(2L).listId(10L).build();

        when(cardService.getBoardStats(100L)).thenReturn(stats);
        when(cardService.getCardById(1L)).thenReturn(original);
        when(cardService.copyCard(1L, 10L, userId)).thenReturn(copy);
        when(cardService.copyCard(1L, 99L, userId)).thenReturn(copy);

        assertThat(controller.getBoardStats(100L).getBody()).isEqualTo(stats);
        assertThat(controller.copyCard(1L, null, userId).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.copyCard(1L, 99L, userId).getBody()).isEqualTo(copy);
    }
}
