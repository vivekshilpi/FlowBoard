package com.flowboard.board_service.service;

import com.flowboard.board_service.dto.*;
import com.flowboard.board_service.entity.BoardMember;

import java.util.List;

public interface BoardService {

    BoardResponse createBoard(CreateBoardRequest request, Long createdById);
    BoardResponse getBoardById(Long workspace, Long requesterId, String userRole);
    List<BoardResponse> getBoardsByWorkspace(Long workspaceId, Long requesterId, String userRole);
    List<BoardResponse> getBoardsByMember(Long userId);
    List<BoardResponse> getBoardsByCreator(Long createdById);
    List<BoardResponse> getPublicBoards();
    List<BoardResponse> searchBoards(String keyword, Long requesterId, String userRole);
    List<BoardResponse> getAllBoards();
    PublicBoardViewResponse getPublicBoardView(Long boardId);
    List<BoardResponse> getClosedBoards(Long workspaceId, Long requesterId, String userRole);
    BoardResponse updateBoard(Long boardId, UpdateBoardRequest request, Long requesterId, String userRole);
    BoardResponse closeBoard(Long boardId, Long requesterId, String userRole);
    BoardResponse reopenBoard(Long boardId, Long requesterId, String userRole);
    void deleteBoard(Long boardId, Long requesterId, String userRole);

    //Member management
    BoardMember addMember(Long boardId, AddBoardMemberRequest request, Long requesterId, String userRole);
    void removeMember(Long boardId, Long userId, Long requesterId, String userRole);
    void updateMemberRole(Long boardId, Long userId, UpdateBoardMemberRoleRequest request, Long requesterId, String userRole);
    List<BoardMember> getMembers(Long boardId, Long requesterId, String userRole);

    BoardResponse.BoardAnalytics getBoardAnalytics(Long boardId, Long requesterId, String userRole);
    long countAllBoards();
}
