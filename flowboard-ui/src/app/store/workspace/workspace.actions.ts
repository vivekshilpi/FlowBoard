import { createAction, props } from '@ngrx/store';
import { Workspace, CreateWorkspaceRequest, UpdateWorkspaceRequest } from '../../core/models/workspace.model';

export const loadWorkspaces = createAction('[Workspace] Load Workspaces');
export const loadWorkspacesSuccess = createAction(
  '[Workspace] Load Workspaces Success',
  props<{ workspaces: Workspace[] }>()
);
export const loadWorkspacesFailure = createAction(
  '[Workspace] Load Workspaces Failure',
  props<{ error: string }>()
);

export const createWorkspace = createAction(
  '[Workspace] Create Workspace',
  props<{ request: CreateWorkspaceRequest }>()
);
export const createWorkspaceSuccess = createAction(
  '[Workspace] Create Workspace Success',
  props<{ workspace: Workspace }>()
);
export const createWorkspaceFailure = createAction(
  '[Workspace] Create Workspace Failure',
  props<{ error: string }>()
);

export const selectWorkspace = createAction(
  '[Workspace] Select Workspace',
  props<{ id: number }>()
);

export const deleteWorkspace = createAction(
  '[Workspace] Delete Workspace',
  props<{ id: number }>()
);
export const deleteWorkspaceSuccess = createAction(
  '[Workspace] Delete Workspace Success',
  props<{ id: number }>()
);
export const deleteWorkspaceFailure = createAction(
  '[Workspace] Delete Workspace Failure',
  props<{ error: string }>()
);
