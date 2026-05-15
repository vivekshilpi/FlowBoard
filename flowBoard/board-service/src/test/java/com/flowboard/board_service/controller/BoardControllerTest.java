package com.flowboard.board_service.controller;

import com.flowboard.board_service.dto.*;
import com.flowboard.board_service.entity.BoardMember;
import com.flowboard.board_service.enums.BoardMemberRole;
import com.flowboard.board_service.enums.Visibility;
import com.flowboard.board_service.service.BoardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardControllerTest {

    @Mock
    private BoardService boardService;

    private BoardController controller;
    private BoardResponse boardResponse;

    @BeforeEach
    void setUp() {
        controller = new BoardController(boardService);
        boardResponse = BoardResponse.builder()
                .id(1L)
                .workspaceId(10L)
                .name("Board")
                .visibility(Visibility.PRIVATE)
                .createdById(1L)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void create_delegatesAndReturnsCreated() {
        CreateBoardRequest request = new CreateBoardRequest();
        when(boardService.createBoard(request, 1L)).thenReturn(boardResponse);

        var response = controller.create(request, 1L, "user@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(boardResponse);
    }

    @Test
    void resolveUserIdRequiresHeader() {
        CreateBoardRequest request = new CreateBoardRequest();

        assertThatThrownBy(() -> controller.create(request, null, "user@example.com"))
                .hasMessageContaining("X-User-Id header is required");
    }

    @Test
    void standardEndpoints_delegateToService() {
        UpdateBoardRequest updateRequest = new UpdateBoardRequest();
        AddBoardMemberRequest addMemberRequest = new AddBoardMemberRequest();
        UpdateBoardMemberRoleRequest roleRequest = new UpdateBoardMemberRoleRequest();
        roleRequest.setRole(BoardMemberRole.ADMIN);
        BoardMember member = BoardMember.builder().userId(2L).role(BoardMemberRole.MEMBER).build();
        PublicBoardViewResponse publicView = PublicBoardViewResponse.builder().board(boardResponse).build();
        BoardResponse.BoardAnalytics analytics = BoardResponse.BoardAnalytics.builder().totalMembers(2).build();

        when(boardService.getBoardById(1L, 1L, "MEMBER")).thenReturn(boardResponse);
        when(boardService.getBoardsByWorkspace(10L, 1L, "MEMBER")).thenReturn(List.of(boardResponse));
        when(boardService.getBoardsByMember(2L)).thenReturn(List.of(boardResponse));
        when(boardService.getBoardsByCreator(1L)).thenReturn(List.of(boardResponse));
        when(boardService.getPublicBoards()).thenReturn(List.of(boardResponse));
        when(boardService.searchBoards("board", 1L, "MEMBER")).thenReturn(List.of(boardResponse));
        when(boardService.getPublicBoardView(1L)).thenReturn(publicView);
        when(boardService.getClosedBoards(10L, 1L, "MEMBER")).thenReturn(List.of(boardResponse));
        when(boardService.updateBoard(1L, updateRequest, 1L, "MEMBER")).thenReturn(boardResponse);
        when(boardService.closeBoard(1L, 1L, "MEMBER")).thenReturn(boardResponse);
        when(boardService.reopenBoard(1L, 1L, "MEMBER")).thenReturn(boardResponse);
        when(boardService.addMember(1L, addMemberRequest, 1L, "ADMIN")).thenReturn(member);
        when(boardService.getMembers(1L, 1L, "MEMBER")).thenReturn(List.of(member));
        when(boardService.getBoardAnalytics(1L, 1L, "MEMBER")).thenReturn(analytics);

        assertThat(controller.getById(1L, 1L, "x", "MEMBER").getBody()).isSameAs(boardResponse);
        assertThat(controller.getByWorkspace(10L, 1L, "x", "MEMBER").getBody()).containsExactly(boardResponse);
        assertThat(controller.getByMember(2L).getBody()).containsExactly(boardResponse);
        assertThat(controller.getByCreator(1L).getBody()).containsExactly(boardResponse);
        assertThat(controller.getPublic().getBody()).containsExactly(boardResponse);
        assertThat(controller.search("board", 1L, "x", "MEMBER").getBody()).containsExactly(boardResponse);
        assertThat(controller.getPublicBoardView(1L).getBody()).isSameAs(publicView);
        assertThat(controller.getClosedBoards(10L, 1L, "x", "MEMBER").getBody()).containsExactly(boardResponse);
        assertThat(controller.update(1L, updateRequest, 1L, "x", "MEMBER").getBody()).isSameAs(boardResponse);
        assertThat(controller.close(1L, 1L, "x", "MEMBER").getBody()).isSameAs(boardResponse);
        assertThat(controller.reopen(1L, 1L, "x", "MEMBER").getBody()).isSameAs(boardResponse);
        assertThat(controller.delete(1L, 1L, "x", "MEMBER").getBody()).isEqualTo("Board deleted successfully");
        verify(boardService).deleteBoard(1L, 1L, "MEMBER");
        assertThat(controller.addMember(1L, addMemberRequest, 1L, "x", "ADMIN").getBody()).isSameAs(member);
        assertThat(controller.deleteMember(1L, 2L, 1L, "x", "ADMIN").getBody()).isEqualTo("Member removed successfully");
        verify(boardService).removeMember(1L, 2L, 1L, "ADMIN");
        assertThat(controller.updateMemberRole(1L, 2L, roleRequest, 1L, "x", "ADMIN").getBody())
                .isEqualTo("Member role updated successfully");
        verify(boardService).updateMemberRole(1L, 2L, roleRequest, 1L, "ADMIN");
        assertThat(controller.getMembers(1L, 1L, "x", "MEMBER").getBody()).containsExactly(member);
        assertThat(controller.getAnalytics(1L, 1L, "x", "MEMBER").getBody()).isSameAs(analytics);
    }

    @Test
    void adminEndpoints_requirePlatformAdmin() {
        when(boardService.countAllBoards()).thenReturn(12L);
        when(boardService.getAllBoards()).thenReturn(List.of(boardResponse));

        assertThat(controller.getAdminBoardCount("PLATFORM_ADMIN").getBody()).isEqualTo(12L);
        assertThat(controller.getAllBoardsForAdmin("PLATFORM_ADMIN").getBody()).containsExactly(boardResponse);

        assertThatThrownBy(() -> controller.getAdminBoardCount("MEMBER"))
                .hasMessageContaining("Admin access required");
        assertThatThrownBy(() -> controller.getAllBoardsForAdmin(null))
                .hasMessageContaining("Admin access required");
    }
}
