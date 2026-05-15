import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { of, forkJoin, Observable } from 'rxjs';
import { map, catchError, switchMap, withLatestFrom } from 'rxjs/operators';
import { BoardService } from '../../core/services/board.service';
import { CardService } from '../../core/services/card.service';
import { ListService } from '../../core/services/list.service';
import * as BoardActions from './board.actions';
import { selectSelectedBoardId } from './board.selectors';
import { getApiErrorMessage } from '../../core/utils/http-error';

@Injectable()
export class BoardEffects {
  private actions$     = inject(Actions);
  private store        = inject(Store);
  private boardService = inject(BoardService);
  private cardService  = inject(CardService);
  private listService  = inject(ListService);

  loadBoards$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BoardActions.loadBoards),
      switchMap(({ workspaceId }) =>
        this.boardService.getByWorkspace(workspaceId).pipe(
          map(boards => BoardActions.loadBoardsSuccess({ boards })),
          catchError(error => of(BoardActions.loadBoardsFailure({
            error: getApiErrorMessage(error, 'Failed to load boards') })))
        )
      )
    )
  );

  selectBoard$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BoardActions.selectBoard),
      map(({ id }) => BoardActions.loadBoardDetails({ boardId: id }))
    )
  );

  loadBoardDetails$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BoardActions.loadBoardDetails),
      switchMap(({ boardId }) =>
        forkJoin({
          lists: this.listService.getByBoard(boardId).pipe(catchError(() => of([]))),
          cards: this.cardService.getByBoard(boardId).pipe(catchError(() => of([])))
        }).pipe(
          map(({ lists, cards }) => BoardActions.loadBoardDetailsSuccess({ lists, cards })),
          catchError(error => of(BoardActions.loadBoardDetailsFailure({
            error: getApiErrorMessage(error, 'Failed to load board details') })))
        )
      )
    )
  );

  moveCard$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BoardActions.moveCard),
      withLatestFrom(this.store.select(selectSelectedBoardId)),
      switchMap(([{ cardId, fromListId, toListId, prevIndex, currentIndex, orderedCardIds, originalOrderedCardIds }, boardId]) => {
        const request$: Observable<unknown> = fromListId === toListId && orderedCardIds?.length
          ? this.cardService.reorder(fromListId, orderedCardIds)
          : this.cardService.move(cardId, {
            targetListId: toListId,
            targetBoardId: boardId ?? 0,
            targetPosition: currentIndex
          });

        return request$.pipe(
          map(() => BoardActions.moveCardSuccess()),
          catchError(error => of(BoardActions.moveCardFailure({
            error: getApiErrorMessage(error, 'Failed to sync card movement'),
            cardId,
            originalListId: fromListId,
            originalIndex: prevIndex,
            originalOrderedCardIds
          })))
        );
      })
    )
  );
  
  moveList$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BoardActions.moveList),
      switchMap(({ boardId, orderedListIds }) =>
        this.listService.reorder({ boardId, orderedListIds }).pipe(
          map(() => BoardActions.moveListSuccess()),
          catchError(error => of(BoardActions.moveListFailure({
            error: getApiErrorMessage(error, 'Failed to sync list order'),
            originalIndex: 0, newIndex: 0
          })))
        )
      )
    )
  );

  addList$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BoardActions.addList),
      switchMap(({ boardId, name }) =>
        this.listService.create({ boardId, name }).pipe(
          map(list => BoardActions.addListSuccess({ list })),
          catchError(error => of(BoardActions.addListFailure({
            error: getApiErrorMessage(error, 'Failed to create list') })))
        )
      )
    )
  );

  archiveList$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BoardActions.archiveList),
      switchMap(({ listId }) =>
        this.listService.archive(listId).pipe(
          map(() => BoardActions.archiveListSuccess({ listId })),
          catchError(error => of(BoardActions.archiveListFailure({
            error: getApiErrorMessage(error, 'Failed to archive list'), listId })))
        )
      )
    )
  );

  deleteList$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BoardActions.deleteList),
      switchMap(({ listId }) =>
        this.listService.delete(listId).pipe(
          map(() => BoardActions.deleteListSuccess({ listId })),
          catchError(error => of(BoardActions.deleteListFailure({
            error: getApiErrorMessage(error, 'Failed to delete list'), listId })))
        )
      )
    )
  );

}
