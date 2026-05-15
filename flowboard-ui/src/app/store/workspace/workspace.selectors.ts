import { createFeatureSelector, createSelector } from '@ngrx/store';
import { WorkspaceState, workspaceAdapter } from './workspace.state';

export const selectWorkspaceState = createFeatureSelector<WorkspaceState>('workspace');

const {
  selectAll,
  selectEntities,
  selectTotal,
} = workspaceAdapter.getSelectors(selectWorkspaceState);

export const selectAllWorkspaces = selectAll;

export const selectSelectedWorkspaceId = createSelector(
  selectWorkspaceState,
  (state: WorkspaceState) => state.selectedId
);

export const selectSelectedWorkspace = createSelector(
  selectEntities,
  selectSelectedWorkspaceId,
  (entities, id) => id ? entities[id] : null
);

export const selectWorkspaceLoading = createSelector(
  selectWorkspaceState,
  (state: WorkspaceState) => state.loading
);

export const selectWorkspaceError = createSelector(
  selectWorkspaceState,
  (state: WorkspaceState) => state.error
);
