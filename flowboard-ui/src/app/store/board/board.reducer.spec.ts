import * as BoardActions from './board.actions';
import { boardReducer } from './board.reducer';
import { initialBoardState } from './board.state';
import { Card } from '../../core/models/card.model';

const createCard = (overrides: Partial<Card>): Card => ({
  id: 0,
  listId: 0,
  boardId: 100,
  title: 'Card',
  description: null,
  position: 0,
  priority: 'MEDIUM',
  status: 'TO_DO',
  dueDate: null,
  startDate: null,
  assigneeId: null,
  createdById: 1,
  isArchived: false,
  isOverdue: false,
  coverColor: null,
  createdAt: '',
  updatedAt: null,
  ...overrides
});

describe('boardReducer moveCard', () => {
  it('reorders cards within the same list using ordered ids', () => {
    const state = {
      ...initialBoardState,
      cards: [
        createCard({ id: 1, listId: 10, title: 'One', position: 0 }),
        createCard({ id: 2, listId: 10, title: 'Two', position: 1 }),
        createCard({ id: 3, listId: 10, title: 'Three', position: 2 })
      ]
    };

    const next = boardReducer(state, BoardActions.moveCard({
      cardId: 3,
      fromListId: 10,
      toListId: 10,
      prevIndex: 2,
      currentIndex: 0,
      orderedCardIds: [3, 1, 2]
    }));

    expect(next.cards.filter(card => card.listId === 10).map(card => [card.id, card.position])).toEqual([
      [1, 1],
      [2, 2],
      [3, 0]
    ]);
  });

  it('moves a card across lists and recomputes positions', () => {
    const state = {
      ...initialBoardState,
      cards: [
        createCard({ id: 1, listId: 10, title: 'One', position: 0 }),
        createCard({ id: 2, listId: 10, title: 'Two', position: 1 }),
        createCard({ id: 3, listId: 20, title: 'Three', position: 0 })
      ]
    };

    const next = boardReducer(state, BoardActions.moveCard({
      cardId: 2,
      fromListId: 10,
      toListId: 20,
      prevIndex: 1,
      currentIndex: 1
    }));

    const movedCard = next.cards.find(card => card.id === 2);
    const sourceCard = next.cards.find(card => card.id === 1);
    const targetCard = next.cards.find(card => card.id === 3);

    expect(movedCard).toEqual(jasmine.objectContaining({ listId: 20, position: 1 }));
    expect(sourceCard?.position).toBe(0);
    expect(targetCard?.position).toBe(0);
  });

  it('restores original order for same-list move failures', () => {
    const state = {
      ...initialBoardState,
      cards: [
        createCard({ id: 1, listId: 10, title: 'One', position: 1 }),
        createCard({ id: 2, listId: 10, title: 'Two', position: 2 }),
        createCard({ id: 3, listId: 10, title: 'Three', position: 0 })
      ]
    };

    const next = boardReducer(state, BoardActions.moveCardFailure({
      error: 'sync failed',
      cardId: 3,
      originalListId: 10,
      originalIndex: 2,
      originalOrderedCardIds: [1, 2, 3]
    }));

    expect(next.cards.filter(card => card.listId === 10).map(card => [card.id, card.position])).toEqual([
      [1, 0],
      [2, 1],
      [3, 2]
    ]);
  });

  it('handles list and card management reducers', () => {
    const state = {
      ...initialBoardState,
      loading: true,
      lists: [
        { id: 10, boardId: 100, name: 'Todo', isArchived: false },
        { id: 11, boardId: 100, name: 'Doing', isArchived: false }
      ],
      cards: [
        createCard({ id: 1, listId: 10 }),
        createCard({ id: 2, listId: 11 })
      ]
    } as any;

    const detailSuccess = boardReducer(state, BoardActions.loadBoardDetailsSuccess({
      lists: [{ id: 12, boardId: 100, name: 'Done', isArchived: false }] as any,
      cards: [createCard({ id: 3, listId: 12 })]
    }));
    expect(detailSuccess.loading).toBeFalse();
    expect(detailSuccess.lists.length).toBe(1);

    const moveList = boardReducer(state, BoardActions.moveList({
      boardId: 100,
      prevIndex: 0,
      currentIndex: 1,
      orderedListIds: [11, 10]
    }));
    expect(moveList.lists.map(list => list.id)).toEqual([11, 10]);

    const moveListFailure = boardReducer(moveList, BoardActions.moveListFailure({
      error: 'oops',
      originalIndex: 0,
      newIndex: 1
    }));
    expect(moveListFailure.lists.map(list => list.id)).toEqual([10, 11]);

    const addList = boardReducer(state, BoardActions.addListSuccess({
      list: { id: 13, boardId: 100, name: 'Review', isArchived: false } as any
    }));
    expect(addList.lists.some(list => list.id === 13)).toBeTrue();

    const updateList = boardReducer(state, BoardActions.updateList({
      list: { id: 10, boardId: 100, name: 'Renamed', isArchived: false } as any
    }));
    expect(updateList.lists.find(list => list.id === 10)?.name).toBe('Renamed');

    const archiveList = boardReducer(state, BoardActions.archiveList({ listId: 10 }));
    expect(archiveList.lists.find(list => list.id === 10)?.isArchived).toBeTrue();

    const archiveListFailure = boardReducer(archiveList, BoardActions.archiveListFailure({
      error: 'oops',
      listId: 10
    }));
    expect(archiveListFailure.lists.find(list => list.id === 10)?.isArchived).toBeFalse();

    const deleteList = boardReducer(state, BoardActions.deleteListSuccess({ listId: 10 }));
    expect(deleteList.lists.some(list => list.id === 10)).toBeFalse();
    expect(deleteList.cards.some(card => card.listId === 10)).toBeFalse();

    const addCard = boardReducer(state, BoardActions.addCard({ card: createCard({ id: 9, listId: 11 }) }));
    expect(addCard.cards.some(card => card.id === 9)).toBeTrue();

    const updateCard = boardReducer(state, BoardActions.updateCard({
      card: createCard({ id: 2, listId: 11, title: 'Updated' })
    }));
    expect(updateCard.cards.find(card => card.id === 2)?.title).toBe('Updated');

    const deleteCard = boardReducer(state, BoardActions.deleteCard({ cardId: 2, listId: 11 }));
    expect(deleteCard.cards.some(card => card.id === 2)).toBeFalse();
  });

  it('handles board selection and loading failures', () => {
    const selected = boardReducer(initialBoardState, BoardActions.selectBoard({ id: 55 }));
    expect(selected.selectedId).toBe(55);

    const loading = boardReducer(initialBoardState, BoardActions.loadBoardDetails({ boardId: 55 }));
    expect(loading.loading).toBeTrue();

    const failed = boardReducer(initialBoardState, BoardActions.loadBoardDetailsFailure({
      error: 'failed'
    }));
    expect(failed.loading).toBeFalse();
    expect(failed.error).toBe('failed');
  });
});
