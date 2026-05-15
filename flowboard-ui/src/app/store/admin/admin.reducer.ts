import { createReducer, on } from '@ngrx/store';
import { initialAdminState } from './admin.state';
import * as AdminActions from './admin.actions';

const upsertUser = (users: typeof initialAdminState.users, user: (typeof initialAdminState.users)[number]) => {
  const index = users.findIndex(existing => existing.id === user.id);
  if (index === -1) {
    return [user, ...users];
  }

  return users.map(existing => existing.id === user.id ? user : existing);
};

const upsertWorkspace = (
  workspaces: typeof initialAdminState.workspaces,
  workspace: (typeof initialAdminState.workspaces)[number]
) => {
  const index = workspaces.findIndex(existing => existing.id === workspace.id);
  if (index === -1) {
    return [workspace, ...workspaces];
  }

  return workspaces.map(existing => existing.id === workspace.id ? workspace : existing);
};

const upsertBoard = (
  boards: typeof initialAdminState.boards,
  board: (typeof initialAdminState.boards)[number]
) => {
  const index = boards.findIndex(existing => existing.id === board.id);
  if (index === -1) {
    return [board, ...boards];
  }

  return boards.map(existing => existing.id === board.id ? board : existing);
};

export const adminReducer = createReducer(
  initialAdminState,
  on(AdminActions.loadAdminStats, state => ({
    ...state,
    loading: true,
    error: null,
    message: null
  })),
  on(AdminActions.loadAdminStatsSuccess, (state, { stats }) => ({
    ...state,
    stats,
    loading: false
  })),
  on(AdminActions.loadAdminStatsFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  })),
  on(AdminActions.loadAllUsers, state => ({
    ...state,
    loading: true,
    error: null,
    message: null
  })),
  on(AdminActions.loadAllUsersSuccess, (state, { users }) => ({
    ...state,
    users,
    loading: false
  })),
  on(AdminActions.loadAllUsersFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  })),
  on(AdminActions.loadAllWorkspaces, AdminActions.loadAllBoards, state => ({
    ...state,
    loading: true,
    error: null,
    message: null
  })),
  on(AdminActions.loadAllWorkspacesSuccess, (state, { workspaces }) => ({
    ...state,
    workspaces,
    loading: false
  })),
  on(AdminActions.loadAllBoardsSuccess, (state, { boards }) => ({
    ...state,
    boards,
    loading: false
  })),
  on(
    AdminActions.loadAllWorkspacesFailure,
    AdminActions.loadAllBoardsFailure,
    (state, { error }) => ({
      ...state,
      error,
      loading: false
    })
  ),
  on(
    AdminActions.updateUserRole,
    AdminActions.suspendUser,
    AdminActions.reactivateUser,
    AdminActions.deleteUser,
    AdminActions.deleteWorkspace,
    AdminActions.closeBoard,
    AdminActions.reopenBoard,
    AdminActions.deleteBoard,
    state => ({
      ...state,
      loading: true,
      error: null,
      message: null
    })
  ),
  on(
    AdminActions.updateUserRoleSuccess,
    AdminActions.suspendUserSuccess,
    AdminActions.reactivateUserSuccess,
    (state, { user, message }) => ({
      ...state,
      users: upsertUser(state.users, user),
      loading: false,
      message
    })
  ),
  on(
    AdminActions.closeBoardSuccess,
    AdminActions.reopenBoardSuccess,
    (state, { board, message }) => ({
      ...state,
      boards: upsertBoard(state.boards, board),
      loading: false,
      message
    })
  ),
  on(AdminActions.deleteUserSuccess, (state, { userId, message }) => ({
    ...state,
    users: state.users.filter(user => user.id !== userId),
    loading: false,
    message
  })),
  on(AdminActions.deleteWorkspaceSuccess, (state, { workspaceId, message }) => ({
    ...state,
    workspaces: state.workspaces.filter(workspace => workspace.id !== workspaceId),
    loading: false,
    message
  })),
  on(AdminActions.deleteBoardSuccess, (state, { boardId, message }) => ({
    ...state,
    boards: state.boards.filter(board => board.id !== boardId),
    loading: false,
    message
  })),
  on(
    AdminActions.updateUserRoleFailure,
    AdminActions.suspendUserFailure,
    AdminActions.reactivateUserFailure,
    AdminActions.deleteUserFailure,
    AdminActions.deleteWorkspaceFailure,
    AdminActions.closeBoardFailure,
    AdminActions.reopenBoardFailure,
    AdminActions.deleteBoardFailure,
    (state, { error }) => ({
      ...state,
      error,
      loading: false
    })
  ),
  on(AdminActions.clearAdminMessage, state => ({
    ...state,
    message: null
  }))
);
