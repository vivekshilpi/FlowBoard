import { EntityState, EntityAdapter, createEntityAdapter } from '@ngrx/entity';
import { Workspace } from '../../core/models/workspace.model';

export interface WorkspaceState extends EntityState<Workspace> {
  selectedId: number | null;
  loading: boolean;
  error: string | null;
}

export const workspaceAdapter: EntityAdapter<Workspace> = createEntityAdapter<Workspace>();

export const initialWorkspaceState: WorkspaceState = workspaceAdapter.getInitialState({
  selectedId: null,
  loading: false,
  error: null
});
