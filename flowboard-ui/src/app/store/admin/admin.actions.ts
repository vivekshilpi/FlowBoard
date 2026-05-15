import { createAction, props } from '@ngrx/store';
import { UserProfile } from '../../core/models/user.model';
import { AdminStats } from '../../core/models/admin.model';
import { Workspace } from '../../core/models/workspace.model';
import { Board } from '../../core/models/board.model';

export const loadAdminStats = createAction('[Admin] Load Stats');
export const loadAdminStatsSuccess = createAction(
  '[Admin] Load Stats Success',
  props<{ stats: AdminStats }>()
);
export const loadAdminStatsFailure = createAction(
  '[Admin] Load Stats Failure',
  props<{ error: string }>()
);

export const loadAllUsers = createAction('[Admin] Load All Users');
export const loadAllUsersSuccess = createAction(
  '[Admin] Load All Users Success',
  props<{ users: UserProfile[] }>()
);
export const loadAllUsersFailure = createAction(
  '[Admin] Load All Users Failure',
  props<{ error: string }>()
);

export const loadAllWorkspaces = createAction('[Admin] Load All Workspaces');
export const loadAllWorkspacesSuccess = createAction(
  '[Admin] Load All Workspaces Success',
  props<{ workspaces: Workspace[] }>()
);
export const loadAllWorkspacesFailure = createAction(
  '[Admin] Load All Workspaces Failure',
  props<{ error: string }>()
);

export const loadAllBoards = createAction('[Admin] Load All Boards');
export const loadAllBoardsSuccess = createAction(
  '[Admin] Load All Boards Success',
  props<{ boards: Board[] }>()
);
export const loadAllBoardsFailure = createAction(
  '[Admin] Load All Boards Failure',
  props<{ error: string }>()
);

export const updateUserRole = createAction(
  '[Admin] Update User Role',
  props<{ userId: number; role: string }>()
);

export const updateUserRoleSuccess = createAction(
  '[Admin] Update User Role Success',
  props<{ user: UserProfile; message: string }>()
);

export const updateUserRoleFailure = createAction(
  '[Admin] Update User Role Failure',
  props<{ error: string }>()
);

export const suspendUser = createAction(
  '[Admin] Suspend User',
  props<{ userId: number }>()
);

export const suspendUserSuccess = createAction(
  '[Admin] Suspend User Success',
  props<{ user: UserProfile; message: string }>()
);

export const suspendUserFailure = createAction(
  '[Admin] Suspend User Failure',
  props<{ error: string }>()
);

export const reactivateUser = createAction(
  '[Admin] Reactivate User',
  props<{ userId: number }>()
);

export const reactivateUserSuccess = createAction(
  '[Admin] Reactivate User Success',
  props<{ user: UserProfile; message: string }>()
);

export const reactivateUserFailure = createAction(
  '[Admin] Reactivate User Failure',
  props<{ error: string }>()
);

export const deleteUser = createAction(
  '[Admin] Delete User',
  props<{ userId: number }>()
);

export const deleteUserSuccess = createAction(
  '[Admin] Delete User Success',
  props<{ userId: number; message: string }>()
);

export const deleteUserFailure = createAction(
  '[Admin] Delete User Failure',
  props<{ error: string }>()
);

export const deleteWorkspace = createAction(
  '[Admin] Delete Workspace',
  props<{ workspaceId: number }>()
);

export const deleteWorkspaceSuccess = createAction(
  '[Admin] Delete Workspace Success',
  props<{ workspaceId: number; message: string }>()
);

export const deleteWorkspaceFailure = createAction(
  '[Admin] Delete Workspace Failure',
  props<{ error: string }>()
);

export const closeBoard = createAction(
  '[Admin] Close Board',
  props<{ boardId: number }>()
);

export const closeBoardSuccess = createAction(
  '[Admin] Close Board Success',
  props<{ board: Board; message: string }>()
);

export const closeBoardFailure = createAction(
  '[Admin] Close Board Failure',
  props<{ error: string }>()
);

export const reopenBoard = createAction(
  '[Admin] Reopen Board',
  props<{ boardId: number }>()
);

export const reopenBoardSuccess = createAction(
  '[Admin] Reopen Board Success',
  props<{ board: Board; message: string }>()
);

export const reopenBoardFailure = createAction(
  '[Admin] Reopen Board Failure',
  props<{ error: string }>()
);

export const deleteBoard = createAction(
  '[Admin] Delete Board',
  props<{ boardId: number }>()
);

export const deleteBoardSuccess = createAction(
  '[Admin] Delete Board Success',
  props<{ boardId: number; message: string }>()
);

export const deleteBoardFailure = createAction(
  '[Admin] Delete Board Failure',
  props<{ error: string }>()
);

export const clearAdminMessage = createAction('[Admin] Clear Message');
