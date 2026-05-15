import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { map, catchError, switchMap, tap } from 'rxjs/operators';
import { WorkspaceService } from '../../core/services/workspace.service';
import * as WorkspaceActions from './workspace.actions';
import { AuthService } from '../../core/services/auth.service';
import { getApiErrorMessage } from '../../core/utils/http-error';

@Injectable()
export class WorkspaceEffects {
  private actions$ = inject(Actions);
  private workspaceService = inject(WorkspaceService);
  private authService = inject(AuthService);

  loadWorkspaces$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WorkspaceActions.loadWorkspaces),
      switchMap(() => {
        const userId = this.authService.getUserId();
        return this.workspaceService.getByMember(userId).pipe(
          map(workspaces => WorkspaceActions.loadWorkspacesSuccess({ workspaces })),
          catchError(error => of(WorkspaceActions.loadWorkspacesFailure({
            error: getApiErrorMessage(error, 'Failed to load workspaces')
          })))
        );
      })
    )
  );

  createWorkspace$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WorkspaceActions.createWorkspace),
      switchMap(({ request }) =>
        this.workspaceService.create(request).pipe(
          map(workspace => WorkspaceActions.createWorkspaceSuccess({ workspace })),
          catchError(error => of(WorkspaceActions.createWorkspaceFailure({
            error: getApiErrorMessage(error, 'Failed to create workspace')
          })))
        )
      )
    )
  );

  deleteWorkspace$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WorkspaceActions.deleteWorkspace),
      switchMap(({ id }) =>
        this.workspaceService.delete(id).pipe(
          map(() => WorkspaceActions.deleteWorkspaceSuccess({ id })),
          catchError(error => of(WorkspaceActions.deleteWorkspaceFailure({
            error: getApiErrorMessage(error, 'Failed to delete workspace')
          })))
        )
      )
    )
  );
}
