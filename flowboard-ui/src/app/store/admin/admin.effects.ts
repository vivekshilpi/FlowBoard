import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { map, catchError, switchMap, concatMap } from 'rxjs/operators';
import * as AdminActions from './admin.actions';
import { AdminService } from '../../core/services/admin.service';
import { UserProfile } from '../../core/models/user.model';
import { getApiErrorMessage } from '../../core/utils/http-error';

@Injectable()
export class AdminEffects {
  private actions$ = inject(Actions);
  private adminService = inject(AdminService);

  loadStats$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminActions.loadAdminStats),
      switchMap(() =>
        this.adminService.getStats().pipe(
          map(stats => AdminActions.loadAdminStatsSuccess({ stats })),
          catchError(error => of(AdminActions.loadAdminStatsFailure({
            error: getApiErrorMessage(error, 'Failed to load admin stats')
          })))
        )
      )
    )
  );

  loadAllUsers$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminActions.loadAllUsers),
      switchMap(() =>
        this.adminService.getUsers().pipe(
          map(users => AdminActions.loadAllUsersSuccess({ users })),
          catchError(error => of(AdminActions.loadAllUsersFailure({
            error: getApiErrorMessage(error, 'Failed to load users')
          })))
        )
      )
    )
  );

  loadAllWorkspaces$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminActions.loadAllWorkspaces),
      switchMap(() =>
        this.adminService.getWorkspaces().pipe(
          map(workspaces => AdminActions.loadAllWorkspacesSuccess({ workspaces })),
          catchError(error => of(AdminActions.loadAllWorkspacesFailure({
            error: getApiErrorMessage(error, 'Failed to load workspaces')
          })))
        )
      )
    )
  );

  loadAllBoards$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminActions.loadAllBoards),
      switchMap(() =>
        this.adminService.getBoards().pipe(
          map(boards => AdminActions.loadAllBoardsSuccess({ boards })),
          catchError(error => of(AdminActions.loadAllBoardsFailure({
            error: getApiErrorMessage(error, 'Failed to load boards')
          })))
        )
      )
    )
  );

  updateUserRole$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminActions.updateUserRole),
      concatMap(({ userId, role }) =>
        this.adminService.updateUserRole(userId, role).pipe(
          switchMap(message =>
            this.adminService.getUsers().pipe(
              map(users => {
                const user = this.requireUser(users, userId);
                return AdminActions.updateUserRoleSuccess({ user, message });
              })
            )
          ),
          catchError(error => of(AdminActions.updateUserRoleFailure({
            error: getApiErrorMessage(error, 'Failed to update user role')
          })))
        )
      )
    )
  );

  suspendUser$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminActions.suspendUser),
      concatMap(({ userId }) =>
        this.adminService.suspendUser(userId).pipe(
          switchMap(message =>
            this.adminService.getUsers().pipe(
              map(users => {
                const user = this.requireUser(users, userId);
                return AdminActions.suspendUserSuccess({ user, message });
              })
            )
          ),
          catchError(error => of(AdminActions.suspendUserFailure({
            error: getApiErrorMessage(error, 'Failed to suspend user')
          })))
        )
      )
    )
  );

  reactivateUser$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminActions.reactivateUser),
      concatMap(({ userId }) =>
        this.adminService.reactivateUser(userId).pipe(
          switchMap(message =>
            this.adminService.getUsers().pipe(
              map(users => {
                const user = this.requireUser(users, userId);
                return AdminActions.reactivateUserSuccess({ user, message });
              })
            )
          ),
          catchError(error => of(AdminActions.reactivateUserFailure({
            error: getApiErrorMessage(error, 'Failed to reactivate user')
          })))
        )
      )
    )
  );

  deleteUser$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminActions.deleteUser),
      concatMap(({ userId }) =>
        this.adminService.deleteUser(userId).pipe(
          map(message => AdminActions.deleteUserSuccess({ userId, message })),
          catchError(error => of(AdminActions.deleteUserFailure({
            error: getApiErrorMessage(error, 'Failed to delete user')
          })))
        )
      )
    )
  );

  deleteWorkspace$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminActions.deleteWorkspace),
      concatMap(({ workspaceId }) =>
        this.adminService.deleteWorkspace(workspaceId).pipe(
          map(message => AdminActions.deleteWorkspaceSuccess({ workspaceId, message })),
          catchError(error => of(AdminActions.deleteWorkspaceFailure({
            error: getApiErrorMessage(error, 'Failed to delete workspace')
          })))
        )
      )
    )
  );

  closeBoard$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminActions.closeBoard),
      concatMap(({ boardId }) =>
        this.adminService.closeBoard(boardId).pipe(
          map(board => AdminActions.closeBoardSuccess({ board, message: 'Board closed successfully' })),
          catchError(error => of(AdminActions.closeBoardFailure({
            error: getApiErrorMessage(error, 'Failed to close board')
          })))
        )
      )
    )
  );

  reopenBoard$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminActions.reopenBoard),
      concatMap(({ boardId }) =>
        this.adminService.reopenBoard(boardId).pipe(
          map(board => AdminActions.reopenBoardSuccess({ board, message: 'Board reopened successfully' })),
          catchError(error => of(AdminActions.reopenBoardFailure({
            error: getApiErrorMessage(error, 'Failed to reopen board')
          })))
        )
      )
    )
  );

  deleteBoard$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AdminActions.deleteBoard),
      concatMap(({ boardId }) =>
        this.adminService.deleteBoard(boardId).pipe(
          map(message => AdminActions.deleteBoardSuccess({ boardId, message })),
          catchError(error => of(AdminActions.deleteBoardFailure({
            error: getApiErrorMessage(error, 'Failed to delete board')
          })))
        )
      )
    )
  );

  refreshStatsAfterDelete$ = createEffect(() =>
    this.actions$.pipe(
      ofType(
        AdminActions.deleteUserSuccess,
        AdminActions.deleteWorkspaceSuccess,
        AdminActions.closeBoardSuccess,
        AdminActions.reopenBoardSuccess,
        AdminActions.deleteBoardSuccess
      ),
      map(() => AdminActions.loadAdminStats())
    )
  );

  private requireUser(users: UserProfile[], userId: number): UserProfile {
    const user = users.find(candidate => candidate.id === userId);
    if (!user) {
      throw new Error(`User ${userId} not found after admin action`);
    }
    return user;
  }
}
