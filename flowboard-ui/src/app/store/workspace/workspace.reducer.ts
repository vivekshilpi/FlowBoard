import { createReducer, on } from '@ngrx/store';
import { initialWorkspaceState, workspaceAdapter } from './workspace.state';
import * as WorkspaceActions from './workspace.actions';

export const workspaceReducer = createReducer(
  initialWorkspaceState,
  on(WorkspaceActions.loadWorkspaces, state => ({
    ...state,
    loading: true,
    error: null
  })),
  on(WorkspaceActions.loadWorkspacesSuccess, (state, { workspaces }) => 
    workspaceAdapter.setAll(workspaces, { ...state, loading: false })
  ),
  on(WorkspaceActions.loadWorkspacesFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error
  })),
  on(WorkspaceActions.createWorkspaceSuccess, (state, { workspace }) =>
    workspaceAdapter.addOne(workspace, state)
  ),
  on(WorkspaceActions.selectWorkspace, (state, { id }) => ({
    ...state,
    selectedId: id
  })),
  on(WorkspaceActions.deleteWorkspaceSuccess, (state, { id }) =>
    workspaceAdapter.removeOne(id, state)
  )
);
