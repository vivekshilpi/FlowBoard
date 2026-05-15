import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatSnackBar } from '@angular/material/snack-bar';

import { BoardViewComponent } from './board-view.component';
import { createComponentTestingProviders } from '../../../../test-helpers/test-providers';
import { BoardService } from '../../../core/services/board.service';
import { ListService } from '../../../core/services/list.service';
import { CardService } from '../../../core/services/card.service';
import { AuthService } from '../../../core/services/auth.service';
import { BoardRealtimeService } from '../../../core/services/board-realtime.service';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';

describe('BoardViewComponent', () => {
  let component: BoardViewComponent;
  let store: jasmine.SpyObj<Store>;
  let boardService: jasmine.SpyObj<BoardService>;
  let listService: jasmine.SpyObj<ListService>;
  let cardService: jasmine.SpyObj<CardService>;
  let auth: jasmine.SpyObj<AuthService>;
  let realtime: jasmine.SpyObj<BoardRealtimeService>;
  let dialog: jasmine.SpyObj<DialogService>;
  let router: jasmine.SpyObj<Router>;
  let snack: jasmine.SpyObj<MatSnackBar>;
  let realtimeCallback: ((event: any) => void) | null = null;

  const board = {
    id: 1,
    workspaceId: 9,
    name: 'Board',
    visibility: 'PUBLIC',
    background: null,
    isArchived: false,
    members: [
      { userId: 7, role: 'ADMIN' },
      { userId: 8, role: 'MEMBER' }
    ]
  } as any;

  const archivedCard = {
    id: 42,
    listId: 7,
    boardId: 1,
    title: 'Archived card',
    description: null,
    position: 2,
    priority: 'MEDIUM',
    status: 'TO_DO',
    dueDate: null,
    startDate: null,
    assigneeId: 8,
    createdById: 1,
    isArchived: true,
    isOverdue: false,
    coverColor: null,
    createdAt: '2026-05-14T10:00:00',
    updatedAt: '2026-05-14T10:05:00'
  } as any;

  const archivedList = {
    id: 11,
    boardId: 1,
    name: 'Archived list',
    color: '#333',
    position: 0,
    isArchived: true
  } as any;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BoardViewComponent],
      providers: [
        ...createComponentTestingProviders({
          routeParams: { id: '1' },
          includeStore: true
        })
      ]
    }).compileComponents();

    component = TestBed.createComponent(BoardViewComponent).componentInstance;
    store = TestBed.inject(Store) as jasmine.SpyObj<Store>;
    boardService = TestBed.inject(BoardService) as jasmine.SpyObj<BoardService>;
    listService = TestBed.inject(ListService) as jasmine.SpyObj<ListService>;
    cardService = TestBed.inject(CardService) as jasmine.SpyObj<CardService>;
    auth = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    realtime = TestBed.inject(BoardRealtimeService) as jasmine.SpyObj<BoardRealtimeService>;
    dialog = TestBed.inject(DialogService) as jasmine.SpyObj<DialogService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    snack = TestBed.inject(MatSnackBar) as jasmine.SpyObj<MatSnackBar>;

    spyOn(store, 'dispatch');
    spyOn(boardService, 'getById').and.returnValue(of(board));
    spyOn(boardService, 'getPublicView').and.returnValue(of({
      board,
      lists: [{ id: 5, boardId: 1, name: 'Todo', position: 0, isArchived: false }],
      cards: [{ id: 100, listId: 5, position: 0, isArchived: false }]
    } as any));
    spyOn(boardService, 'update').and.returnValue(of({ ...board, visibility: 'PRIVATE' } as any));
    spyOn(boardService, 'delete').and.returnValue(of({} as any));

    spyOn(listService, 'getArchived').and.returnValue(of([archivedList]));
    spyOn(listService, 'update').and.returnValue(of({ id: 5, boardId: 1, name: 'Todo', color: '#222' } as any));
    spyOn(listService, 'unarchive').and.returnValue(of({} as any));
    spyOn(listService, 'delete').and.returnValue(of('deleted'));

    spyOn(cardService, 'getArchivedByBoard').and.returnValue(of([archivedCard]));
    spyOn(cardService, 'unarchive').and.returnValue(of({ ...archivedCard, isArchived: false } as any));
    spyOn(cardService, 'delete').and.returnValue(of('deleted'));

    spyOn(auth, 'isAdmin').and.returnValue(false);
    spyOn(auth, 'getUserId').and.returnValue(7);
    spyOn(auth, 'isLoggedIn').and.returnValue(true);
    spyOn(auth as any, 'getErrorMessage').and.returnValue('This public board is unavailable');

    spyOn(realtime as any, 'connect').and.callFake((_boardId: number, handlers: any) => {
      realtimeCallback = handlers.onEvent;
      return () => undefined;
    });

    spyOn(dialog as any, 'confirm').and.callFake(async (options: any) => {
      if (options?.onConfirm) {
        await options.onConfirm();
      }
      return true;
    });
    spyOn(dialog as any, 'prompt').and.resolveTo(true);
    spyOn(router, 'navigate');
    spyOn(snack, 'open');

    component.board = { ...board };
    component.boardMembers = [
      { userId: 7, displayName: 'Flow Admin' },
      { userId: 8, displayName: 'Board User' },
      { userId: 9, displayName: 'Third User' },
      { userId: 10, displayName: 'Fourth User' },
      { userId: 11, displayName: 'Fifth User' }
    ];
  });

  it('loads private boards and responds to realtime refreshes', fakeAsync(() => {
    component.ngOnInit();

    expect(boardService.getById).toHaveBeenCalledWith(1);
    expect(cardService.getArchivedByBoard).toHaveBeenCalledWith(1);
    expect(listService.getArchived).toHaveBeenCalledWith(1);
    expect(realtime.connect).toHaveBeenCalled();

    realtimeCallback?.({ actorUserId: 99 });
    tick(200);
    expect(boardService.getById).toHaveBeenCalledTimes(2);

    realtimeCallback?.({ actorUserId: 7 });
    tick(200);
    expect(boardService.getById).toHaveBeenCalledTimes(2);
  }));

  it('loads public boards and handles public board errors', () => {
    component.publicView = true;
    component.loadPublicBoard(1);
    expect(boardService.getPublicView).toHaveBeenCalledWith(1);
    expect(component.loading).toBeFalse();

    boardService.getPublicView.and.returnValue(throwError(() => new Error('bad public board')));
    component.loadPublicBoard(1);
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('covers list and card update flows', async () => {
    component.board = { ...board };
    component.boardId = 1;
    component.lists = [{ id: 5, boardId: 1, name: 'Todo', isArchived: false } as any];
    component.archivedLists = [archivedList];
    component.archivedCards = [archivedCard];
    component.listForm.patchValue({ name: 'Doing' });

    component.addList();
    expect(store.dispatch).toHaveBeenCalledWith(jasmine.objectContaining({
      type: '[Board] Add List',
      boardId: 1,
      name: 'Doing'
    }));

    component.updateListColor(component.lists[0], '#222');
    expect(listService.update).toHaveBeenCalled();

    component.onCardCreated({ id: 101, listId: 5 } as any, 5);
    component.openCardDetail({ id: 101, listId: 5 } as any);
    expect(component.showCardDetail).toBeTrue();

    component.closeCardDetail();
    expect(component.showCardDetail).toBeFalse();

    component.onCardUpdated({ id: 42, listId: 5, isArchived: false } as any);
    expect(component.archivedCards).toEqual([]);

    component.onCardDeleted({ cardId: 42, listId: 5 });
    expect(store.dispatch).toHaveBeenCalledWith(jasmine.objectContaining({ type: '[Board] Delete Card', cardId: 42 }));

    component.archiveList(5);
    expect(component.archivedLists.some(list => list.id === 5)).toBeTrue();

    await component.deleteList(5);
    expect(dialog.confirm).toHaveBeenCalled();

    component.restoreCard(archivedCard);
    expect(cardService.unarchive).toHaveBeenCalledWith(42);

    component.restoreList(11);
    expect(listService.unarchive).toHaveBeenCalledWith(11);

    await component.deleteArchivedCard(archivedCard);
    expect(cardService.delete).toHaveBeenCalledWith(42);

    await component.deleteArchivedList(11);
    expect(listService.delete).toHaveBeenCalledWith(11);
  });

  it('covers sharing board deletion visibility and helper getters', async () => {
    component.board = { ...board };
    component.boardMembers = [
      { userId: 7, displayName: 'Flow Admin' },
      { userId: 8, displayName: 'Board User' },
      { userId: 9, displayName: 'Third User' },
      { userId: 10, displayName: 'Fourth User' },
      { userId: 11, displayName: 'Fifth User' }
    ];
    component.allCards = [{ id: 1 }, { id: 2 }] as any;
    component.archivedCards = [archivedCard];
    component.archivedLists = [archivedList];

    spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());
    component.shareBoard();
    expect(navigator.clipboard.writeText).toHaveBeenCalled();

    component.updateVisibility('PRIVATE');
    expect(boardService.update).toHaveBeenCalled();

    await component.deleteBoard();
    expect(boardService.delete).toHaveBeenCalledWith(1);
    expect(router.navigate).toHaveBeenCalledWith(['/workspace', 9]);

    expect(component.getAllCards().length).toBe(2);
    expect(component.getArchivedCards().length).toBe(1);
    expect(component.getArchivedLists().length).toBe(1);
    expect(component.getPriorityColor('HIGH')).toBe('#ef4444');
    expect(component.getPriorityColor('UNKNOWN')).toBe('#94a3b8');
    expect(component.getVisibleBoardMembers().length).toBe(4);
    expect(component.getRemainingBoardMembersCount()).toBe(1);
    expect(component.getMemberInitials('Flow Board')).toBe('FB');
    expect(component.getMemberInitials('')).toBe('U');
    expect(component.getAssigneeDisplayName(8)).toBe('Board User');
    expect(component.getAssigneeDisplayName(null)).toBe('Unassigned');
    expect(component.canManageBoard()).toBeTrue();
  });

  it('covers goBack drag state and fallback sharing prompt', async () => {
    component.board = { ...board, visibility: 'PRIVATE' };
    component.publicView = true;
    component.goBack();
    expect(router.navigate).toHaveBeenCalledWith(['/']);

    component.publicView = false;
    spyOn(window.history, 'back');
    component.goBack();
    expect(window.history.back).toHaveBeenCalled();

    component.onCardDragStarted();
    component.openCardDetail({ id: 101, listId: 5 } as any);
    expect(component.showCardDetail).toBeFalse();
    component.onCardDragEnded();

    auth.isAdmin.and.returnValue(false);
    component.board = { ...board, visibility: 'PUBLIC', members: [{ userId: 99, role: 'MEMBER', addedAt: '' }] } as any;
    component.shareBoard();
    expect(component.canManageBoard()).toBeFalse();

    component.board = { ...board, visibility: 'PRIVATE', members: [{ userId: 7, role: 'ADMIN', addedAt: '' }] } as any;
    component.shareBoard();
    expect(component.board?.visibility).toBe('PRIVATE');

    component.board = { ...board, visibility: 'PUBLIC' };
    spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.reject(new Error('blocked')));
    component.shareBoard();
    await Promise.resolve();
    expect(navigator.clipboard.writeText).toHaveBeenCalled();
  });
});
