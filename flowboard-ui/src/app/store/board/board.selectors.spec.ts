import {
  selectAllBoards,
  selectBoardLoading,
  selectCards,
  selectCardsForList,
  selectLists,
  selectSelectedBoard,
  selectSelectedBoardId
} from './board.selectors';
import { initialBoardState } from './board.state';

describe('board selectors', () => {
  const state = {
    board: {
      ...initialBoardState,
      ids: [1, 2],
      entities: {
        1: { id: 1, name: 'One' },
        2: { id: 2, name: 'Two' }
      },
      selectedId: 2,
      lists: [{ id: 10, boardId: 2, name: 'Todo' }],
      cards: [
        { id: 100, listId: 10, isArchived: false },
        { id: 101, listId: 10, isArchived: true },
        { id: 102, listId: 20, isArchived: false }
      ],
      loading: true
    }
  } as any;

  it('selects board entities and the selected board', () => {
    expect(selectAllBoards(state).map((board: any) => board.id)).toEqual([1, 2]);
    expect(selectSelectedBoardId(state)).toBe(2);
    expect(selectSelectedBoard(state)).toEqual(jasmine.objectContaining({ id: 2, name: 'Two' }));
  });

  it('selects lists cards and loading state', () => {
    expect(selectLists(state)).toEqual(state.board.lists);
    expect(selectCards(state)).toEqual(state.board.cards);
    expect(selectBoardLoading(state)).toBeTrue();
  });

  it('filters visible cards for a list', () => {
    const selector = selectCardsForList(10);
    expect(selector(state).map((card: any) => card.id)).toEqual([100]);
  });
});
