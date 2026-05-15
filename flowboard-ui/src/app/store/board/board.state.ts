import { EntityState, EntityAdapter, createEntityAdapter } from '@ngrx/entity';
import { Board } from '../../core/models/board.model';
import { TaskList } from '../../core/models/list.model';
import { Card } from '../../core/models/card.model';

export interface BoardState extends EntityState<Board> {
  selectedId: number | null;
  lists: TaskList[];
  cards: Card[];
  loading: boolean;
  error: string | null;
}

export const boardAdapter: EntityAdapter<Board> = createEntityAdapter<Board>();

export const initialBoardState: BoardState = boardAdapter.getInitialState({
  selectedId: null,
  lists: [],
  cards: [],
  loading: false,
  error: null
});
