package com.flowBoard.list_service.controller;

import com.flowBoard.list_service.dto.*;
import com.flowBoard.list_service.service.ListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListControllerTest {

    @Mock
    private ListService listService;

    private ListController controller;
    private ListResponse response;

    @BeforeEach
    void setUp() {
        controller = new ListController(listService);
        response = ListResponse.builder()
                .id(1L)
                .boardId(10L)
                .name("Todo")
                .position(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void create_requiresUserIdHeader() {
        CreateListRequest request = new CreateListRequest();

        assertThatThrownBy(() -> controller.create(request, null))
                .hasMessageContaining("X-User-Id header is required");
    }

    @Test
    void create_returnsCreatedResponse() {
        CreateListRequest request = new CreateListRequest();
        when(listService.createList(request, 1L)).thenReturn(response);

        var entity = controller.create(request, 1L);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(entity.getBody()).isSameAs(response);
    }

    @Test
    void standardEndpoints_delegateToService() {
        UpdateListRequest updateRequest = new UpdateListRequest();
        ReorderListRequest reorderRequest = new ReorderListRequest();
        MoveListRequest moveRequest = new MoveListRequest();

        when(listService.getListById(1L)).thenReturn(response);
        when(listService.getListsByBoard(10L)).thenReturn(List.of(response));
        when(listService.updateList(1L, updateRequest, 2L)).thenReturn(response);
        when(listService.reorderLists(reorderRequest, 2L)).thenReturn(List.of(response));
        when(listService.archiveList(1L, 2L)).thenReturn(response);
        when(listService.unarchiveList(1L, 2L)).thenReturn(response);
        when(listService.getArchivedLists(10L)).thenReturn(List.of(response));
        when(listService.moveList(1L, moveRequest, 2L)).thenReturn(response);

        assertThat(controller.getById(1L).getBody()).isSameAs(response);
        assertThat(controller.getByBoard(10L).getBody()).containsExactly(response);
        assertThat(controller.update(1L, updateRequest, 2L).getBody()).isSameAs(response);
        assertThat(controller.delete(1L, 2L).getBody()).isEqualTo("List deleted successfully");
        verify(listService).deleteList(1L, 2L);
        assertThat(controller.reorder(reorderRequest, 2L).getBody()).containsExactly(response);
        assertThat(controller.archive(1L, 2L).getBody()).isSameAs(response);
        assertThat(controller.unarchive(1L, 2L).getBody()).isSameAs(response);
        assertThat(controller.getArchived(10L).getBody()).containsExactly(response);
        assertThat(controller.move(1L, moveRequest, 2L).getBody()).isSameAs(response);
    }
}
