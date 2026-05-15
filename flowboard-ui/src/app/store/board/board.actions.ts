import { createAction, props } from '@ngrx/store';
import { Board, CreateBoardRequest, UpdateBoardRequest } from '../../core/models/board.model';
import { TaskList } from '../../core/models/list.model';
import { Card } from '../../core/models/card.model';

export const loadBoards = createAction(
  '[Board] Load Boards',
  props<{ workspaceId: number }>()
);
export const loadBoardsSuccess = createAction(
  '[Board] Load Boards Success',
  props<{ boards: Board[] }>()
);
export const loadBoardsFailure = createAction(
  '[Board] Load Boards Failure',
  props<{ error: string }>()
);

export const selectBoard = createAction(
  '[Board] Select Board',
  props<{ id: number }>()
);

export const loadBoardDetails = createAction(
  '[Board] Load Board Details',
  props<{ boardId: number }>()
);
export const loadBoardDetailsSuccess = createAction(
  '[Board] Load Board Details Success',
  props<{ lists: TaskList[]; cards: Card[] }>()
);
export const loadBoardDetailsFailure = createAction(
  '[Board] Load Board Details Failure',
  props<{ error: string }>()
);

export const moveCard = createAction(
  '[Board] Move Card',
  props<{ 
    cardId: number; 
    fromListId: number; 
    toListId: number; 
    prevIndex: number; 
    currentIndex: number;
    orderedCardIds?: number[];
    originalOrderedCardIds?: number[];
  }>()
);

export const moveCardSuccess = createAction('[Board] Move Card Success');
export const moveCardFailure = createAction(
  '[Board] Move Card Failure',
  props<{
    error: string,
    cardId: number,
    originalListId: number,
    originalIndex: number,
    originalOrderedCardIds?: number[]
  }>()
);

export const moveList = createAction(
  '[Board] Move List',
  props<{
    boardId: number;
    prevIndex: number;
    currentIndex: number;
    orderedListIds: number[];  
  }>()
);
export const moveListSuccess = createAction('[Board] Move List Success');
export const moveListFailure = createAction(
  '[Board] Move List Failure',
  props<{ error: string, originalIndex: number, newIndex: number }>()
);

export const addList = createAction(
  '[Board] Add List',
  props<{ boardId: number, name: string }>()
);
export const addListSuccess = createAction(
  '[Board] Add List Success',
  props<{ list: TaskList }>()
);
export const addListFailure = createAction(
  '[Board] Add List Failure',
  props<{ error: string }>()
);

export const updateList = createAction(
  '[Board] Update List',
  props<{ list: TaskList }>()
);

export const archiveList = createAction(
  '[Board] Archive List',
  props<{ listId: number }>()
);
export const archiveListSuccess = createAction(
  '[Board] Archive List Success',
  props<{ listId: number }>()
);
export const archiveListFailure = createAction(
  '[Board] Archive List Failure',
  props<{ error: string, listId: number }>()
);

export const deleteList = createAction(
  '[Board] Delete List',
  props<{ listId: number }>()
);
export const deleteListSuccess = createAction(
  '[Board] Delete List Success',
  props<{ listId: number }>()
);
export const deleteListFailure = createAction(
  '[Board] Delete List Failure',
  props<{ error: string, listId: number }>()
);

export const addCard = createAction(
  '[Board] Add Card',
  props<{ card: Card }>() 
);

export const updateCard = createAction(
  '[Board] Update Card',
  props<{ card: Card }>()
);

export const deleteCard = createAction(
  '[Board] Delete Card',
  props<{ cardId: number, listId: number }>()
);
