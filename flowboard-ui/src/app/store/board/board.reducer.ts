import { createReducer, on } from '@ngrx/store';
import { initialBoardState, boardAdapter } from './board.state';
import * as BoardActions from './board.actions';
import { moveItemInArray } from '@angular/cdk/drag-drop';

export const boardReducer = createReducer(
  initialBoardState,
  on(BoardActions.loadBoardsSuccess, (state, { boards }) => 
    boardAdapter.setAll(boards, { ...state, loading: false })
  ),
  on(BoardActions.selectBoard, (state, { id }) => ({
    ...state,
    selectedId: id
  })),
  on(BoardActions.loadBoardDetails, (state) => ({
    ...state,
    loading: true
  })),
  on(BoardActions.loadBoardDetailsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error
  })),
  on(BoardActions.loadBoardDetailsSuccess, (state, { lists, cards }) => ({
    ...state,
    lists,
    cards,
    loading: false
  })),
  on(BoardActions.moveCard, (state, { cardId, fromListId, toListId, prevIndex, currentIndex, orderedCardIds }) => {
    const newCards = state.cards.map(c => ({ ...c }));

    if (fromListId === toListId && orderedCardIds?.length) {
      const positionByCardId = new Map(orderedCardIds.map((id, index) => [id, index]));
      return {
        ...state,
        cards: newCards.map(card =>
          card.listId === fromListId && positionByCardId.has(card.id)
            ? { ...card, position: positionByCardId.get(card.id)! }
            : card
        )
      };
    }

    const sourceCards = newCards
      .filter(c => c.listId === fromListId && c.id !== cardId)
      .sort((a, b) => (a.position ?? 0) - (b.position ?? 0));
    const targetCards = newCards
      .filter(c => c.listId === toListId && c.id !== cardId)
      .sort((a, b) => (a.position ?? 0) - (b.position ?? 0));
    const movedCard = newCards.find(c => c.id === cardId);

    if (!movedCard) {
      return state;
    }

    targetCards.splice(currentIndex, 0, { ...movedCard, listId: toListId, position: currentIndex });

    sourceCards.forEach((card, index) => {
      const globalIndex = newCards.findIndex(existing => existing.id === card.id);
      if (globalIndex > -1) {
        newCards[globalIndex] = { ...newCards[globalIndex], position: index };
      }
    });

    targetCards.forEach((card, index) => {
      const globalIndex = newCards.findIndex(existing => existing.id === card.id);
      if (globalIndex > -1) {
        newCards[globalIndex] = {
          ...newCards[globalIndex],
          listId: toListId,
          position: index
        };
      }
    });

    return { ...state, cards: newCards };
  }),
  on(BoardActions.moveCardFailure, (state, { cardId, originalListId, originalOrderedCardIds }) => {
    const newCards = state.cards.map(c => ({ ...c }));
    const cardIdx = newCards.findIndex(c => c.id === cardId);
    if (cardIdx > -1) {
      newCards[cardIdx] = { ...newCards[cardIdx], listId: originalListId };
    }

    if (originalOrderedCardIds?.length) {
      const originalPositionById = new Map(originalOrderedCardIds.map((id, index) => [id, index]));
      return {
        ...state,
        cards: newCards.map(card =>
          card.listId === originalListId && originalPositionById.has(card.id)
            ? { ...card, position: originalPositionById.get(card.id)! }
            : card
        )
      };
    }

    return { ...state, cards: newCards };
  }),
  on(BoardActions.moveList, (state, { prevIndex, currentIndex }) => {
    const newLists = [...state.lists];
    moveItemInArray(newLists, prevIndex, currentIndex);
    return { ...state, lists: newLists };
  }),
  on(BoardActions.moveListFailure, (state, { originalIndex, newIndex }) => {
    const newLists = [...state.lists];
    moveItemInArray(newLists, newIndex, originalIndex); // revert
    return { ...state, lists: newLists };
  }),
  on(BoardActions.addListSuccess, (state, { list }) => ({
    ...state,
    lists: [...state.lists, list]
  })),
  on(BoardActions.updateList, (state, { list }) => {
    const newLists = [...state.lists];
    const idx = newLists.findIndex(l => l.id === list.id);
    if (idx > -1) {
      newLists[idx] = list;
    }
    return { ...state, lists: newLists };
  }),
  on(BoardActions.archiveList, (state, { listId }) => {
    return {
      ...state,
      lists: state.lists.map(l => l.id === listId ? { ...l, isArchived: true } : l)
    };
  }),
  on(BoardActions.archiveListFailure, (state, { listId }) => {
    return {
      ...state,
      lists: state.lists.map(l => l.id === listId ? { ...l, isArchived: false } : l)
    };
  }),
  on(BoardActions.deleteListSuccess, (state, { listId }) => ({
    ...state,
    lists: state.lists.filter(l => l.id !== listId),
    cards: state.cards.filter(c => c.listId !== listId)
  })),
  on(BoardActions.addCard, (state, { card }) => ({
    ...state,
    cards: [...state.cards, card]
  })),
  on(BoardActions.updateCard, (state, { card }) => {
    const newCards = [...state.cards];
    const idx = newCards.findIndex(c => c.id === card.id);
    if (idx > -1) {
      newCards[idx] = card;
    }
    return { ...state, cards: newCards };
  }),
  on(BoardActions.deleteCard, (state, { cardId }) => ({
    ...state,
    cards: state.cards.filter(c => c.id !== cardId)
  }))
);
