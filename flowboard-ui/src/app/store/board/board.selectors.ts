import { createFeatureSelector, createSelector } from '@ngrx/store';
import { BoardState, boardAdapter } from './board.state';

export const selectBoardState = createFeatureSelector<BoardState>('board');

const {
  selectAll,
  selectEntities,
} = boardAdapter.getSelectors(selectBoardState);

export const selectAllBoards = selectAll;

export const selectSelectedBoardId = createSelector(
  selectBoardState,
  (state: BoardState) => state.selectedId
);

export const selectSelectedBoard = createSelector(
  selectEntities,
  selectSelectedBoardId,
  (entities, id) => id ? entities[id] : null
);

export const selectLists = createSelector(
  selectBoardState,
  (state: BoardState) => state.lists
);

export const selectCards = createSelector(
  selectBoardState,
  (state: BoardState) => state.cards
);

export const selectCardsForList = (listId: number) => createSelector(
  selectCards,
  (cards) => cards.filter(c => c.listId === listId && !c.isArchived)
);

export const selectBoardLoading = createSelector(
  selectBoardState,
  (state: BoardState) => state.loading
);

