package com.flowboard.board_service.service;

import com.flowboard.board_service.dto.*;
import com.flowboard.board_service.entity.Board;
import com.flowboard.board_service.entity.BoardMember;
import com.flowboard.board_service.enums.BoardMemberRole;
import com.flowboard.board_service.enums.Visibility;
import com.flowboard.board_service.exception.CustomException;
import com.flowboard.board_service.repository.BoardMemberRepository;
import com.flowboard.board_service.repository.BoardRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BoardServiceImpl Unit Tests")
class BoardServiceImplTest {
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String MEMBER_ROLE = "MEMBER";

    @Mock BoardRepository boardRepository;
    @Mock BoardMemberRepository memberRepository;
    @Mock RabbitTemplate rabbitTemplate;
    @Mock RestTemplate restTemplate;
    @InjectMocks BoardServiceImpl boardService;

    private Board openBoard;
    private Board closedBoard;
    private BoardMember adminMember;
    private BoardMember regularMember;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(boardService, "listServiceBaseUrl", "http://lists");
        ReflectionTestUtils.setField(boardService, "cardServiceBaseUrl", "http://cards");

        openBoard = Board.builder()
                .id(1L).workspaceId(10L).name("Sprint Board")
                .visibility(Visibility.PRIVATE)
                .createdById(1L).isClosed(false)
                .createdAt(LocalDateTime.now()).build();

        closedBoard = Board.builder()
                .id(2L).workspaceId(10L).name("Old Board")
                .visibility(Visibility.PRIVATE)
                .createdById(1L).isClosed(true)
                .createdAt(LocalDateTime.now()).build();

        adminMember = BoardMember.builder()
                .id(1L).board(openBoard).userId(1L)
                .role(BoardMemberRole.ADMIN)
                .addedAt(LocalDateTime.now()).build();

        regularMember = BoardMember.builder()
                .id(2L).board(openBoard).userId(2L)
                .role(BoardMemberRole.MEMBER)
                .addedAt(LocalDateTime.now()).build();
    }

    // ── createBoard ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createBoard should save board and auto-add creator as ADMIN")
    void createBoard_success() {
        CreateBoardRequest req = new CreateBoardRequest();
        req.setWorkspaceId(10L);
        req.setName("Sprint Board");
        req.setVisibility(Visibility.PRIVATE);

        when(boardRepository.save(any())).thenReturn(openBoard);
        when(memberRepository.save(any())).thenReturn(adminMember);
        when(memberRepository.findByBoardId(any())).thenReturn(List.of(adminMember));

        BoardResponse response = boardService.createBoard(req, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Sprint Board");
        verify(memberRepository).save(argThat(m ->
                m.getRole() == BoardMemberRole.ADMIN &&
                        m.getUserId().equals(1L)));
    }

    // ── closeBoard ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("closeBoard should set isClosed=true for admin")
    void closeBoard_success() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));
        when(memberRepository.findByBoardIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(adminMember));
        when(boardRepository.save(any())).thenReturn(openBoard);
        when(memberRepository.findByBoardId(1L))
                .thenReturn(List.of(adminMember));

        BoardResponse response = boardService.closeBoard(1L, 1L, ADMIN_ROLE);

        assertThat(openBoard.isClosed()).isTrue();
        verify(boardRepository).save(openBoard);
    }

    @Test
    @DisplayName("closeBoard should throw 400 when board already closed")
    void closeBoard_alreadyClosed_throws() {
        when(boardRepository.findById(2L)).thenReturn(Optional.of(closedBoard));
        when(memberRepository.findByBoardIdAndUserId(2L, 1L))
                .thenReturn(Optional.of(adminMember));

        assertThatThrownBy(() -> boardService.closeBoard(2L, 1L, ADMIN_ROLE))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── deleteBoard ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteBoard should succeed for board creator")
    void deleteBoard_creatorCanDelete() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));
        when(memberRepository.findByBoardIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(adminMember));

        boardService.deleteBoard(1L, 1L, ADMIN_ROLE);

        verify(boardRepository).delete(openBoard);
    }

    @Test
    @DisplayName("deleteBoard should throw 403 for non-creator")
    void deleteBoard_nonCreator_throws() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));
        when(memberRepository.findByBoardIdAndUserId(1L, 999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> boardService.deleteBoard(1L, 999L, MEMBER_ROLE))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(boardRepository, never()).delete(any());
    }

    // ── addMember ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addMember should add user with default MEMBER role")
    void addMember_success() {
        AddBoardMemberRequest req = new AddBoardMemberRequest();
        req.setUserId(3L);
        req.setRole(null); // should default to MEMBER

        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));
        when(memberRepository.findByBoardIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(adminMember));
        when(memberRepository.existsByBoardIdAndUserId(1L, 3L))
                .thenReturn(false);
        when(memberRepository.save(any())).thenReturn(regularMember);

        BoardMember result = boardService.addMember(1L, req, 1L, ADMIN_ROLE);

        verify(memberRepository).save(argThat(m ->
                m.getRole() == BoardMemberRole.MEMBER));
    }

    @Test
    @DisplayName("addMember to closed board should throw 400")
    void addMember_closedBoard_throws() {
        AddBoardMemberRequest req = new AddBoardMemberRequest();
        req.setUserId(3L);

        when(boardRepository.findById(2L)).thenReturn(Optional.of(closedBoard));
        when(memberRepository.findByBoardIdAndUserId(2L, 1L))
                .thenReturn(Optional.of(adminMember));

        assertThatThrownBy(() -> boardService.addMember(2L, req, 1L, ADMIN_ROLE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("closed");
    }

    // ── getBoardById ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getBoardById should throw 404 when board not found")
    void getBoardById_notFound() {
        when(boardRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> boardService.getBoardById(999L, 1L, MEMBER_ROLE))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getBoardById private board should throw 403 for non-member")
    void getBoardById_private_nonMember_throws() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));
        when(memberRepository.existsByBoardIdAndUserId(1L, 5L))
                .thenReturn(false);

        assertThatThrownBy(() -> boardService.getBoardById(1L, 5L, MEMBER_ROLE))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── getBoardAnalytics ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getBoardAnalytics should return correct member counts")
    void getBoardAnalytics_success() {
        BoardMember observer = BoardMember.builder()
                .userId(3L).role(BoardMemberRole.OBSERVER)
                .board(openBoard).addedAt(LocalDateTime.now()).build();

        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));
        when(memberRepository.existsByBoardIdAndUserId(1L, 1L)).thenReturn(true);
        when(memberRepository.findByBoardId(1L))
                .thenReturn(List.of(adminMember, regularMember, observer));

        BoardResponse.BoardAnalytics analytics =
                boardService.getBoardAnalytics(1L, 1L, MEMBER_ROLE);

        assertThat(analytics.getTotalMembers()).isEqualTo(3);
        assertThat(analytics.getAdminCount()).isEqualTo(1);
        assertThat(analytics.getMemberCount()).isEqualTo(1);
        assertThat(analytics.getObserverCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getBoardsByWorkspace should include public boards and member private boards")
    void getBoardsByWorkspace_filtersForMember() {
        Board publicBoard = Board.builder()
                .id(3L).workspaceId(10L).name("Public")
                .visibility(Visibility.PUBLIC).createdById(2L).build();

        when(boardRepository.findByWorkspaceId(10L)).thenReturn(List.of(openBoard, publicBoard));
        when(memberRepository.existsByBoardIdAndUserId(1L, 2L)).thenReturn(true);

        List<BoardResponse> responses = boardService.getBoardsByWorkspace(10L, 2L, MEMBER_ROLE);

        assertThat(responses).hasSize(2);
    }

    @Test
    @DisplayName("searchBoards should return all matches for platform admin")
    void searchBoards_platformAdminGetsAll() {
        Board publicBoard = Board.builder()
                .id(3L).workspaceId(10L).name("Public")
                .visibility(Visibility.PUBLIC).createdById(2L).build();

        when(boardRepository.search("Sprint")).thenReturn(List.of(openBoard, publicBoard));
        when(memberRepository.findByBoardId(anyLong())).thenReturn(List.of(adminMember));

        List<BoardResponse> responses = boardService.searchBoards("Sprint", 99L, "PLATFORM_ADMIN");

        assertThat(responses).hasSize(2);
    }

    @Test
    @DisplayName("getPublicBoardView should include lists and cards")
    void getPublicBoardView_success() {
        Board publicBoard = Board.builder()
                .id(3L).workspaceId(10L).name("Public")
                .visibility(Visibility.PUBLIC).createdById(2L).isClosed(false).build();
        PublicBoardViewResponse.PublicListResponse[] lists = {
                PublicBoardViewResponse.PublicListResponse.builder().id(11L).boardId(3L).name("Todo").build()
        };
        PublicBoardViewResponse.PublicCardResponse[] cards = {
                PublicBoardViewResponse.PublicCardResponse.builder().id(21L).boardId(3L).title("Card").build()
        };

        when(boardRepository.findById(3L)).thenReturn(Optional.of(publicBoard));
        when(memberRepository.findByBoardId(3L)).thenReturn(List.of(adminMember));
        when(restTemplate.getForObject("http://lists/api/v1/lists/board/{boardId}",
                PublicBoardViewResponse.PublicListResponse[].class, 3L)).thenReturn(lists);
        when(restTemplate.getForObject("http://cards/api/v1/cards/board/{boardId}",
                PublicBoardViewResponse.PublicCardResponse[].class, 3L)).thenReturn(cards);

        PublicBoardViewResponse response = boardService.getPublicBoardView(3L);

        assertThat(response.getLists()).hasSize(1);
        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getBoard().getMembers()).isEmpty();
    }

    @Test
    @DisplayName("getPublicBoardView should reject private board")
    void getPublicBoardView_privateBoardThrows() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));

        assertThatThrownBy(() -> boardService.getPublicBoardView(1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getPublicBoardView should swallow downstream fetch failures")
    void getPublicBoardView_fetchFailureReturnsEmptyCollections() {
        Board publicBoard = Board.builder()
                .id(3L).workspaceId(10L).name("Public")
                .visibility(Visibility.PUBLIC).createdById(2L).isClosed(false).build();

        when(boardRepository.findById(3L)).thenReturn(Optional.of(publicBoard));
        when(memberRepository.findByBoardId(3L)).thenReturn(List.of(adminMember));
        when(restTemplate.getForObject(eq("http://lists/api/v1/lists/board/{boardId}"),
                eq(PublicBoardViewResponse.PublicListResponse[].class), eq(3L)))
                .thenThrow(new RestClientException("list service down"));
        when(restTemplate.getForObject(eq("http://cards/api/v1/cards/board/{boardId}"),
                eq(PublicBoardViewResponse.PublicCardResponse[].class), eq(3L)))
                .thenReturn(null);

        PublicBoardViewResponse response = boardService.getPublicBoardView(3L);

        assertThat(response.getLists()).isEmpty();
        assertThat(response.getCards()).isEmpty();
    }

    @Test
    @DisplayName("getClosedBoards should filter out boards for non-members")
    void getClosedBoards_filtersByMembership() {
        Board otherClosed = Board.builder()
                .id(5L).workspaceId(10L).name("Other Closed")
                .visibility(Visibility.PRIVATE).createdById(3L).isClosed(true).build();

        when(boardRepository.findByWorkspaceIdAndIsClosed(10L, true)).thenReturn(List.of(closedBoard, otherClosed));
        when(memberRepository.existsByBoardIdAndUserId(2L, 2L)).thenReturn(true);
        when(memberRepository.existsByBoardIdAndUserId(5L, 2L)).thenReturn(false);
        when(memberRepository.findByBoardId(2L)).thenReturn(List.of(adminMember));

        List<BoardResponse> responses = boardService.getClosedBoards(10L, 2L, MEMBER_ROLE);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("updateBoard should update mutable fields")
    void updateBoard_success() {
        UpdateBoardRequest request = new UpdateBoardRequest();
        request.setName("Renamed");
        request.setDescription("Updated");
        request.setBackground("bg");
        request.setVisibility(Visibility.PUBLIC);

        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));
        when(memberRepository.findByBoardIdAndUserId(1L, 1L)).thenReturn(Optional.of(adminMember));
        when(boardRepository.save(any())).thenReturn(openBoard);
        when(memberRepository.findByBoardId(1L)).thenReturn(List.of(adminMember));

        BoardResponse response = boardService.updateBoard(1L, request, 1L, ADMIN_ROLE);

        assertThat(response.getName()).isEqualTo("Renamed");
        assertThat(openBoard.getVisibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    @DisplayName("updateBoard should reject closed boards")
    void updateBoard_closedBoardThrows() {
        UpdateBoardRequest request = new UpdateBoardRequest();
        request.setName("Renamed");

        when(boardRepository.findById(2L)).thenReturn(Optional.of(closedBoard));
        when(memberRepository.findByBoardIdAndUserId(2L, 1L)).thenReturn(Optional.of(adminMember));

        assertThatThrownBy(() -> boardService.updateBoard(2L, request, 1L, ADMIN_ROLE))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("reopenBoard should reopen a closed board")
    void reopenBoard_success() {
        when(boardRepository.findById(2L)).thenReturn(Optional.of(closedBoard));
        when(memberRepository.findByBoardIdAndUserId(2L, 1L)).thenReturn(Optional.of(adminMember));
        when(boardRepository.save(any())).thenReturn(closedBoard);
        when(memberRepository.findByBoardId(2L)).thenReturn(List.of(adminMember));

        BoardResponse response = boardService.reopenBoard(2L, 1L, ADMIN_ROLE);

        assertThat(response.isClosed()).isFalse();
        assertThat(closedBoard.isClosed()).isFalse();
    }

    @Test
    @DisplayName("reopenBoard should reject already open board")
    void reopenBoard_alreadyOpenThrows() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));
        when(memberRepository.findByBoardIdAndUserId(1L, 1L)).thenReturn(Optional.of(adminMember));

        assertThatThrownBy(() -> boardService.reopenBoard(1L, 1L, ADMIN_ROLE))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("removeMember should delete non-creator member")
    void removeMember_success() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));
        when(memberRepository.findByBoardIdAndUserId(1L, 1L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.existsByBoardIdAndUserId(1L, 2L)).thenReturn(true);

        boardService.removeMember(1L, 2L, 1L, ADMIN_ROLE);

        verify(memberRepository).deleteByBoardIdAndUserId(1L, 2L);
    }

    @Test
    @DisplayName("removeMember should reject removing creator")
    void removeMember_creatorThrows() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));
        when(memberRepository.findByBoardIdAndUserId(1L, 1L)).thenReturn(Optional.of(adminMember));

        assertThatThrownBy(() -> boardService.removeMember(1L, 1L, 1L, ADMIN_ROLE))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("removeMember should reject unknown member")
    void removeMember_missingMemberThrows() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));
        when(memberRepository.findByBoardIdAndUserId(1L, 1L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.existsByBoardIdAndUserId(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> boardService.removeMember(1L, 2L, 1L, ADMIN_ROLE))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("updateMemberRole should persist the new role")
    void updateMemberRole_success() {
        UpdateBoardMemberRoleRequest request = new UpdateBoardMemberRoleRequest();
        request.setRole(BoardMemberRole.OBSERVER);
        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));
        when(memberRepository.findByBoardIdAndUserId(1L, 1L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.findByBoardIdAndUserId(1L, 2L)).thenReturn(Optional.of(regularMember));

        boardService.updateMemberRole(1L, 2L, request, 1L, ADMIN_ROLE);

        assertThat(regularMember.getRole()).isEqualTo(BoardMemberRole.OBSERVER);
        verify(memberRepository).save(regularMember);
    }

    @Test
    @DisplayName("updateMemberRole should reject unknown member")
    void updateMemberRole_missingMemberThrows() {
        UpdateBoardMemberRoleRequest request = new UpdateBoardMemberRoleRequest();
        request.setRole(BoardMemberRole.OBSERVER);
        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));
        when(memberRepository.findByBoardIdAndUserId(1L, 1L)).thenReturn(Optional.of(adminMember));
        when(memberRepository.findByBoardIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> boardService.updateMemberRole(1L, 2L, request, 1L, ADMIN_ROLE))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getMembers should return board members for authorized requester")
    void getMembers_success() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(openBoard));
        when(memberRepository.existsByBoardIdAndUserId(1L, 2L)).thenReturn(true);
        when(memberRepository.findByBoardId(1L)).thenReturn(List.of(adminMember, regularMember));

        assertThat(boardService.getMembers(1L, 2L, MEMBER_ROLE)).hasSize(2);
    }

    @Test
    @DisplayName("countAllBoards should delegate to repository")
    void countAllBoards_success() {
        when(boardRepository.count()).thenReturn(12L);

        assertThat(boardService.countAllBoards()).isEqualTo(12L);
    }

    @Test
    @DisplayName("publishing board events should not break create flow when RabbitMQ fails")
    void createBoard_rabbitFailureDoesNotBreakFlow() {
        CreateBoardRequest req = new CreateBoardRequest();
        req.setWorkspaceId(10L);
        req.setName("Sprint Board");
        doThrow(new AmqpException("broker down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), ArgumentMatchers.<Object>any());
        when(boardRepository.save(any())).thenReturn(openBoard);
        when(memberRepository.save(any())).thenReturn(adminMember);
        when(memberRepository.findByBoardId(any())).thenReturn(List.of(adminMember));

        BoardResponse response = boardService.createBoard(req, 1L);

        assertThat(response.getName()).isEqualTo("Sprint Board");
    }
}
