package com.flowBoard.list_service.service;

import com.flowBoard.list_service.dto.*;
import com.flowBoard.list_service.entity.TaskList;
import com.flowBoard.list_service.exception.CustomException;
import com.flowBoard.list_service.repository.ListRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.amqp.AmqpException;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListServiceImpl Unit Tests")
class ListServiceImplTest {

    @Mock ListRepository listRepository;
    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks ListServiceImpl listService;

    private TaskList sampleList;

    @BeforeEach
    void setUp() {
        sampleList = TaskList.builder()
                .id(1L).boardId(10L).name("To Do")
                .position(0).isArchived(false)
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("createList should append at end when position is null")
    void createList_appendsAtEnd() {
        CreateListRequest req = new CreateListRequest();
        req.setBoardId(10L);
        req.setName("To Do");
        req.setPosition(null);

        // FIX: anyLong() instead of any() for Long parameter — avoids NPE on unboxing
        when(listRepository.findMaxPositionByBoardId(anyLong()))
                .thenReturn(Optional.of(2));
        when(listRepository.save(any())).thenReturn(sampleList);

        ListResponse response = listService.createList(req, 1L);

        assertThat(response).isNotNull();
        // FIX: anyLong(), anyInt() instead of any(), any()
        verify(listRepository, never()).shiftPositionsRight(anyLong(), anyInt());
    }

    @Test
    @DisplayName("createList should shift right and insert at given position")
    void createList_insertsAtPosition() {
        CreateListRequest req = new CreateListRequest();
        req.setBoardId(10L);
        req.setName("Review");
        req.setPosition(1);

        when(listRepository.save(any())).thenReturn(sampleList);

        listService.createList(req, 1L);

        verify(listRepository).shiftPositionsRight(10L, 1);
    }

    @Test
    @DisplayName("archiveList should shift siblings left and set isArchived=true")
    void archiveList_success() {
        when(listRepository.findById(1L)).thenReturn(Optional.of(sampleList));
        when(listRepository.save(any())).thenReturn(sampleList);

        listService.archiveList(1L, 1L);

        assertThat(sampleList.isArchived()).isTrue();
        verify(listRepository).shiftPositionsLeft(sampleList.getBoardId(),
                sampleList.getPosition());
    }

    @Test
    @DisplayName("archiveList should throw 400 when already archived")
    void archiveList_alreadyArchived_throws() {
        sampleList.setArchived(true);
        when(listRepository.findById(1L)).thenReturn(Optional.of(sampleList));

        assertThatThrownBy(() -> listService.archiveList(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("deleteList should shift siblings left and delete")
    void deleteList_success() {
        when(listRepository.findById(1L)).thenReturn(Optional.of(sampleList));

        listService.deleteList(1L, 1L);

        verify(listRepository).shiftPositionsLeft(
                sampleList.getBoardId(), sampleList.getPosition());
        verify(listRepository).delete(sampleList);
    }

    @Test
    @DisplayName("getListById should throw 404 when not found")
    void getListById_notFound_throws() {
        when(listRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listService.getListById(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("unarchiveList should restore and append at end of active lists")
    void unarchiveList_success() {
        sampleList.setArchived(true);
        sampleList.setPosition(0);

        when(listRepository.findById(1L)).thenReturn(Optional.of(sampleList));
        when(listRepository.findMaxPositionByBoardId(anyLong()))
                .thenReturn(Optional.of(3));
        when(listRepository.save(any())).thenReturn(sampleList);

        listService.unarchiveList(1L, 1L);

        assertThat(sampleList.isArchived()).isFalse();
        assertThat(sampleList.getPosition()).isEqualTo(4);
    }

    @Test
    @DisplayName("getListsByBoard should map active lists")
    void getListsByBoard_success() {
        when(listRepository.findByBoardIdAndIsArchivedFalseOrderByPosition(10L))
                .thenReturn(List.of(sampleList));

        assertThat(listService.getListsByBoard(10L)).hasSize(1);
    }

    @Test
    @DisplayName("updateList should update mutable fields")
    void updateList_success() {
        UpdateListRequest request = new UpdateListRequest();
        request.setName("Doing");
        request.setColor("#ffffff");
        when(listRepository.findById(1L)).thenReturn(Optional.of(sampleList));
        when(listRepository.save(any())).thenReturn(sampleList);

        ListResponse response = listService.updateList(1L, request, 9L);

        assertThat(response.getName()).isEqualTo("Doing");
        assertThat(sampleList.getColor()).isEqualTo("#ffffff");
    }

    @Test
    @DisplayName("updateList should reject archived list")
    void updateList_archivedList_throws() {
        UpdateListRequest request = new UpdateListRequest();
        request.setName("Doing");
        sampleList.setArchived(true);
        when(listRepository.findById(1L)).thenReturn(Optional.of(sampleList));

        assertThatThrownBy(() -> listService.updateList(1L, request, 9L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("reorderLists should save new positions in request order")
    void reorderLists_success() {
        TaskList second = TaskList.builder()
                .id(2L).boardId(10L).name("Review").position(1).isArchived(false)
                .createdAt(LocalDateTime.now()).build();
        ReorderListRequest request = new ReorderListRequest();
        request.setBoardId(10L);
        request.setOrderedListIds(List.of(2L, 1L));
        when(listRepository.findByBoardIdAndIsArchivedFalseOrderByPosition(10L))
                .thenReturn(List.of(sampleList, second), List.of(second, sampleList));

        List<ListResponse> response = listService.reorderLists(request, 7L);

        assertThat(response).hasSize(2);
        assertThat(second.getPosition()).isEqualTo(0);
        assertThat(sampleList.getPosition()).isEqualTo(1);
        verify(listRepository, times(2)).save(any(TaskList.class));
    }

    @Test
    @DisplayName("reorderLists should reject foreign list ids")
    void reorderLists_invalidBoardMembership_throws() {
        ReorderListRequest request = new ReorderListRequest();
        request.setBoardId(10L);
        request.setOrderedListIds(List.of(999L));
        when(listRepository.findByBoardIdAndIsArchivedFalseOrderByPosition(10L))
                .thenReturn(List.of(sampleList));

        assertThatThrownBy(() -> listService.reorderLists(request, 7L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("unarchiveList should reject already active list")
    void unarchiveList_active_throws() {
        when(listRepository.findById(1L)).thenReturn(Optional.of(sampleList));

        assertThatThrownBy(() -> listService.unarchiveList(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("getArchivedLists should map archived entries")
    void getArchivedLists_success() {
        sampleList.setArchived(true);
        when(listRepository.findByBoardIdAndIsArchivedTrue(10L)).thenReturn(List.of(sampleList));

        assertThat(listService.getArchivedLists(10L)).hasSize(1);
    }

    @Test
    @DisplayName("moveList should insert at requested target position")
    void moveList_withExplicitPosition_success() {
        MoveListRequest request = new MoveListRequest();
        request.setTargetBoardId(20L);
        request.setTargetPosition(2);
        when(listRepository.findById(1L)).thenReturn(Optional.of(sampleList));
        when(listRepository.save(any())).thenReturn(sampleList);

        ListResponse response = listService.moveList(1L, request, 11L);

        assertThat(response.getBoardId()).isEqualTo(20L);
        assertThat(sampleList.getPosition()).isEqualTo(2);
        verify(listRepository).shiftPositionsLeft(10L, 0);
        verify(listRepository).shiftPositionsRight(20L, 2);
    }

    @Test
    @DisplayName("moveList should append to end when target position missing")
    void moveList_append_success() {
        MoveListRequest request = new MoveListRequest();
        request.setTargetBoardId(20L);
        when(listRepository.findById(1L)).thenReturn(Optional.of(sampleList));
        when(listRepository.findMaxPositionByBoardId(20L)).thenReturn(Optional.of(3));
        when(listRepository.save(any())).thenReturn(sampleList);

        ListResponse response = listService.moveList(1L, request, 11L);

        assertThat(response.getBoardId()).isEqualTo(20L);
        assertThat(sampleList.getPosition()).isEqualTo(4);
    }

    @Test
    @DisplayName("moveList should reject same board target")
    void moveList_sameBoard_throws() {
        MoveListRequest request = new MoveListRequest();
        request.setTargetBoardId(10L);
        when(listRepository.findById(1L)).thenReturn(Optional.of(sampleList));

        assertThatThrownBy(() -> listService.moveList(1L, request, 11L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("deleteList should not shift archived list positions")
    void deleteList_archived_skipsShift() {
        sampleList.setArchived(true);
        when(listRepository.findById(1L)).thenReturn(Optional.of(sampleList));

        listService.deleteList(1L, 1L);

        verify(listRepository, never()).shiftPositionsLeft(anyLong(), anyInt());
        verify(listRepository).delete(sampleList);
    }

    @Test
    @DisplayName("event publish failures should not break create flow")
    void createList_publishFailure_stillSucceeds() {
        CreateListRequest req = new CreateListRequest();
        req.setBoardId(10L);
        req.setName("Blocked");
        doThrow(new AmqpException("broker down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), ArgumentMatchers.<Object>any());
        when(listRepository.findMaxPositionByBoardId(anyLong())).thenReturn(Optional.of(0));
        when(listRepository.save(any())).thenReturn(sampleList);

        ListResponse response = listService.createList(req, 1L);

        assertThat(response.getName()).isEqualTo("Blocked");
    }
}
