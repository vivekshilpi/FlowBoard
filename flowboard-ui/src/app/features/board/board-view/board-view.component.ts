import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import {
  CdkDragDrop, DragDropModule,
  moveItemInArray
} from '@angular/cdk/drag-drop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { BoardService } from '../../../core/services/board.service';
import { ListService } from '../../../core/services/list.service';
import { CardService } from '../../../core/services/card.service';
import { Board, PublicBoardView } from '../../../core/models/board.model';
import { TaskList } from '../../../core/models/list.model';
import { Card } from '../../../core/models/card.model';
import { CardCreateComponent } from '../card-create/card-create.component';
import { CardDetailComponent } from '../card-detail/card-detail.component';
import { BoardAnalyticsComponent } from '../board-analytics/board-analytics.component';
import { ArchiveManagerComponent } from '../archive-manager/archive-manager.component';
import { ColorPickerComponent } from '../../../shared/components/color-picker/color-picker.component';
import { AuthService } from '../../../core/services/auth.service';
import { BoardRealtimeService } from '../../../core/services/board-realtime.service';
import { Store } from '@ngrx/store';
import * as BoardActions from '../../../store/board/board.actions';
import { selectLists, selectCards, selectBoardLoading } from '../../../store/board/board.selectors';
import { Subject, debounceTime, firstValueFrom, takeUntil } from 'rxjs';
import { BoardRealtimeEvent } from '../../../core/models/board-realtime.model';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';

@Component({
  selector: 'app-board-view',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    DragDropModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatMenuModule,
    MatProgressSpinnerModule, MatSnackBarModule, MatTooltipModule,
    MatDividerModule,
    CardCreateComponent, CardDetailComponent,
    BoardAnalyticsComponent, ArchiveManagerComponent, ColorPickerComponent
  ],
  templateUrl: './board-view.component.html',
  styleUrl: './board-view.component.scss'
})
export class BoardViewComponent implements OnInit {
  private route        = inject(ActivatedRoute);
  private router       = inject(Router);
  private fb           = inject(FormBuilder);
  private boardService = inject(BoardService);
  private listService  = inject(ListService);
  private cardService  = inject(CardService);
  private auth         = inject(AuthService);
  private realtime     = inject(BoardRealtimeService);
  private snack        = inject(MatSnackBar);
  private dialog       = inject(DialogService);

  private store        = inject(Store);
  private destroy$     = new Subject<void>();
  private boardRefresh$ = new Subject<void>();
  private disconnectRealtime: (() => void) | null = null;
  private pendingCardId: number | null = null;
  private draggingCard = false;

  board: Board | null = null;
  boardId = 0;
  publicView = false;
  lists: TaskList[]   = [];
  archivedCards: Card[] = [];
  archivedLists: TaskList[] = [];
  allCards: Card[]    = []; // flat list of cards from store
  cardsByList: Record<number, Card[]> = {};
  loading      = true;
  addingList   = false;
  showAddList  = false;
  selectedCard: Card | null = null;
  showCardDetail = false;
  activeAddCardListId: number | null = null;
  
  showAnalytics = false;
  showArchive   = false;

  boardMembers: Array<{ userId: number; displayName?: string; avatarUrl?: string }> = [];

  listForm = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(1)]]
  });

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.boardId = id;
    this.publicView = this.route.snapshot.data['publicView'] === true;
    const cardIdParam = this.route.snapshot.queryParamMap.get('cardId');
    this.pendingCardId = cardIdParam ? Number(cardIdParam) : null;

    if (this.publicView) {
      this.loadPublicBoard(id);
      return;
    }

    this.store.dispatch(BoardActions.selectBoard({ id }));
    
    // Subscribe to loading state
    this.store.select(selectBoardLoading)
      .pipe(takeUntil(this.destroy$))
      .subscribe(loading => this.loading = loading);

    // Subscribe to lists
    this.store.select(selectLists)
      .pipe(takeUntil(this.destroy$))
      .subscribe(lists => this.lists = lists);

    // Subscribe to cards
    this.store.select(selectCards)
      .pipe(takeUntil(this.destroy$))
      .subscribe(cards => {
        this.allCards = cards;
        this.rebuildCardBuckets(cards);
        if (this.pendingCardId) {
          const targetCard = cards.find(card => card.id === this.pendingCardId);
          if (targetCard) {
            this.openCardDetail(targetCard);
            this.pendingCardId = null;
          }
        }
      });

    this.boardRefresh$
      .pipe(debounceTime(150), takeUntil(this.destroy$))
      .subscribe(() => {
        this.loadBoard(this.boardId);
        this.loadArchivedCards(this.boardId);
      });

    this.loadBoard(id);
    this.loadArchivedCards(id);
    this.loadArchivedLists(id);
    this.connectRealtime();
  }

  ngOnDestroy(): void {
    this.disconnectRealtime?.();
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadBoard(id: number): void {
    this.loading = true;
    this.boardService.getById(id).subscribe({
      next: b => {
        this.board = b;
        this.boardMembers = (b.members || []).map((m: any) => ({
          userId: m.userId,
          displayName: m.user?.fullName,
          avatarUrl: m.user?.avatarUrl
        }));
        this.store.dispatch(BoardActions.loadBoardDetails({ boardId: id }));
      },
      error: () => {
        this.loading = false;
        this.snack.open('Board not found', 'Close', { duration: 3000 });
      }
    });
  }

  loadPublicBoard(id: number): void {
    this.loading = true;
    this.boardService.getPublicView(id).subscribe({
      next: (response: PublicBoardView) => {
        this.board = response.board;
        this.lists = (response.lists || []).filter(list => !list.isArchived);
        this.allCards = (response.cards || []).filter(card => !card.isArchived);
        this.rebuildCardBuckets(this.allCards);
        this.archivedCards = [];
        this.archivedLists = [];
        this.boardMembers = [];
        this.loading = false;
      },
      error: (error) => {
        this.loading = false;
        const message = this.auth.getErrorMessage(error, 'This public board is unavailable');
        this.snack.open(message, 'Close', { duration: 4000 });
        this.router.navigate([this.auth.isLoggedIn() ? '/dashboard' : '/']);
      }
    });
  }

  loadArchivedLists(boardId: number): void {
    this.listService.getArchived(boardId).subscribe({
      next: lists => {
        this.archivedLists = lists;
      },
      error: () => {
        this.archivedLists = [];
      }
    });
  }

  loadArchivedCards(boardId: number): void {
    this.cardService.getArchivedByBoard(boardId).subscribe({
      next: cards => {
        this.archivedCards = cards;
      },
      error: () => {
        this.archivedCards = [];
      }
    });
  }

  private connectRealtime(): void {
    this.disconnectRealtime = this.realtime.connect(this.boardId, {
      onEvent: (event) => this.handleRealtimeEvent(event)
    });
  }

  private handleRealtimeEvent(event: BoardRealtimeEvent): void {
    if (event.actorUserId === this.auth.getUserId()) {
      return;
    }
    this.boardRefresh$.next();
  }

  getCards(listId: number): Card[] {
    return this.cardsByList[listId] ?? [];
  }

  getConnectedLists(): string[] {
    return this.lists.map(l => 'list-' + l.id);
  }

  onListDrop(event: CdkDragDrop<TaskList[]>): void {
    if (event.previousIndex === event.currentIndex) return;
    this.store.dispatch(BoardActions.moveList({
    boardId: this.board!.id,
    prevIndex: event.previousIndex,
    currentIndex: event.currentIndex,
    orderedListIds: this.lists.map(l => l.id)  // after moveItemInArray
  }));
  }

  onCardDrop(event: CdkDragDrop<Card[]>,
    targetListId: number): void {
    const card = event.previousContainer.data[event.previousIndex];
    const originalOrderedCardIds = event.previousContainer.data.map(listCard => listCard.id);

    if (event.previousContainer === event.container) {
      if (event.previousIndex === event.currentIndex) return;

      const reorderedCards = [...event.container.data];
      moveItemInArray(reorderedCards, event.previousIndex, event.currentIndex);

      this.store.dispatch(BoardActions.moveCard({
        cardId: card.id,
        fromListId: card.listId,
        toListId: targetListId,
        prevIndex: event.previousIndex,
        currentIndex: event.currentIndex,
        orderedCardIds: reorderedCards.map(listCard => listCard.id),
        originalOrderedCardIds
      }));
      return;
    }
    
    this.store.dispatch(BoardActions.moveCard({
      cardId: card.id,
      fromListId: card.listId,
      toListId: targetListId,
      prevIndex: event.previousIndex,
      currentIndex: event.currentIndex,
      originalOrderedCardIds
    }));
  }

  addList(): void {
    if (this.listForm.invalid) return;
    this.addingList = true;
    this.store.dispatch(BoardActions.addList({
      boardId: this.board!.id,
      name: this.listForm.value.name!
    }));
    this.listForm.reset();
    this.showAddList = false;
    this.addingList  = false;
  }

  updateListColor(list: TaskList, color: string | null): void {
    this.listService.update(list.id, {
      name: list.name,
      color: color || undefined
    }).subscribe({
      next: updatedList => {
        this.store.dispatch(BoardActions.updateList({ list: updatedList }));
        this.snack.open('List color updated!', 'Close', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to update list color', 'Close', { duration: 3000 })
    });
  }

  onCardCreated(card: Card, listId: number): void {
    this.store.dispatch(BoardActions.addCard({ card }));
    this.activeAddCardListId = null;
    this.store.dispatch(BoardActions.loadBoardDetails({ boardId: this.boardId }));
  }

  openCardDetail(card: Card): void {
    if (this.publicView || this.draggingCard) {
      return;
    }
    this.selectedCard  = card;
    this.showCardDetail = true;
  }

  onCardDragStarted(): void {
    this.draggingCard = true;
  }

  onCardDragEnded(): void {
    setTimeout(() => {
      this.draggingCard = false;
    });
  }

  closeCardDetail(): void {
    this.showCardDetail = false;
    this.selectedCard   = null;
  }

  onCardUpdated(updated: Card): void {
    this.store.dispatch(BoardActions.updateCard({ card: updated }));
    this.selectedCard = updated;
    if (updated.isArchived) {
      this.loadArchivedCards(this.boardId);
    } else {
      this.archivedCards = this.archivedCards.filter(card => card.id !== updated.id);
    }
  }

  onCardDeleted(data: { cardId: number; listId: number }): void {
    this.store.dispatch(BoardActions.deleteCard({ cardId: data.cardId, listId: data.listId }));
    this.store.dispatch(BoardActions.loadBoardDetails({ boardId: this.boardId }));
    this.closeCardDetail();
  }

  archiveList(listId: number): void {
    const archivedList = this.lists.find(list => list.id === listId);
    this.store.dispatch(BoardActions.archiveList({ listId }));
    if (archivedList && !this.archivedLists.some(list => list.id === listId)) {
      this.archivedLists = [{ ...archivedList, isArchived: true }, ...this.archivedLists];
    }
    this.snack.open('List archived!', 'Close', { duration: 3000 });
  }

  async deleteList(listId: number): Promise<void> {
    const confirmed = await this.dialog.confirm({
      title: 'Delete list?',
      description: 'This list will be removed from the board.',
      confirmText: 'Delete list',
      cancelText: 'Keep list',
      variant: 'danger'
    });
    if (!confirmed) return;
    this.store.dispatch(BoardActions.deleteList({ listId }));
  }

  getPriorityColor(p: string): string {
    const map: Record<string, string> = {
      LOW: '#22c55e', MEDIUM: '#f59e0b',
      HIGH: '#ef4444', CRITICAL: '#7c3aed'
    };
    return map[p] ?? '#94a3b8';
  }

  goBack(): void {
    if (this.publicView) {
      this.router.navigate(['/']);
      return;
    }
    window.history.back();
  }

  shareBoard(): void {
    if (!this.canManageBoard()) {
      this.snack.open('Board admin access required', 'Close', { duration: 3000 });
      return;
    }

    if (!this.board) return;

    if (this.board.visibility !== 'PUBLIC') {
      this.snack.open('Make this board public before sharing it', 'Close', { duration: 3000 });
      return;
    }

    const shareUrl = `${window.location.origin}/public/board/${this.board.id}`;
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(shareUrl).then(() => {
        this.snack.open('Public board link copied', 'Close', { duration: 3000 });
      }).catch(() => {
        void this.dialog.prompt({
          title: 'Copy public board link',
          description: 'Clipboard access was blocked, so you can copy the link manually here.',
          confirmText: 'Done',
          cancelText: 'Close',
          variant: 'info',
          initialValue: shareUrl,
          readOnly: true,
          selectTextOnOpen: true,
          backdropClose: true,
          escClose: true
        });
      });
      return;
    }

    void this.dialog.prompt({
      title: 'Copy public board link',
      description: 'Copy this share link to invite people to the public board.',
      confirmText: 'Done',
      cancelText: 'Close',
      variant: 'info',
      initialValue: shareUrl,
      readOnly: true,
      selectTextOnOpen: true,
      backdropClose: true,
      escClose: true
    });
  }

  // --- Advanced Analytics & Archive ---

  getAllCards(): Card[] {
    return this.allCards;
  }

  getArchivedCards(): Card[] {
    return this.archivedCards;
  }

  getArchivedLists(): TaskList[] {
    return this.archivedLists;
  }

  restoreCard(card: Card): void {
    this.cardService.unarchive(card.id).subscribe({
      next: () => {
        this.archivedCards = this.archivedCards.filter(existing => existing.id !== card.id);
        this.store.dispatch(BoardActions.loadBoardDetails({ boardId: this.board!.id }));
        this.loadArchivedCards(this.board!.id);
        this.snack.open('Card restored!', 'Close', { duration: 3000 });
      }
    });
  }

  restoreList(listId: number): void {
    this.listService.unarchive(listId).subscribe({
      next: () => {
        this.store.dispatch(BoardActions.loadBoardDetails({ boardId: this.board!.id }));
        this.archivedLists = this.archivedLists.filter(list => list.id !== listId);
        this.loadArchivedLists(this.board!.id);
        this.snack.open('List restored!', 'Close', { duration: 3000 });
      }
    });
  }

  async deleteArchivedCard(card: Card): Promise<void> {
    const confirmed = await this.dialog.confirm({
      title: `Delete "${card.title}" permanently?`,
      description: 'This archived card will be removed forever.',
      confirmText: 'Delete card',
      cancelText: 'Keep card',
      variant: 'danger',
      onConfirm: () => firstValueFrom(this.cardService.delete(card.id)),
      errorMessage: 'Failed to delete archived card'
    });
    if (!confirmed) return;
    this.archivedCards = this.archivedCards.filter(existing => existing.id !== card.id);
    this.snack.open('Archived card deleted', 'Close', { duration: 3000 });
  }

  async deleteArchivedList(listId: number): Promise<void> {
    const list = this.archivedLists.find(existing => existing.id === listId);
    if (!list) return;
    const confirmed = await this.dialog.confirm({
      title: `Delete "${list.name}" permanently?`,
      description: 'This archived list will be removed forever.',
      confirmText: 'Delete list',
      cancelText: 'Keep list',
      variant: 'danger',
      onConfirm: () => firstValueFrom(this.listService.delete(listId)),
      errorMessage: 'Failed to delete archived list'
    });
    if (!confirmed) return;
    this.archivedLists = this.archivedLists.filter(existing => existing.id !== listId);
    this.snack.open('Archived list deleted', 'Close', { duration: 3000 });
  }

  updateVisibility(visibility: 'PUBLIC' | 'PRIVATE'): void {
    if (!this.board || !this.canManageBoard()) return;
    this.boardService.update(this.board.id, {
      name: this.board.name,
      visibility
    }).subscribe({
      next: updated => {
        this.board = updated;
        this.snack.open(`Board is now ${visibility}`, 'Close', { duration: 3000 });
      },
      error: () => this.snack.open('Failed to update visibility', 'Close', { duration: 3000 })
    });
  }

  async deleteBoard(): Promise<void> {
    if (!this.board || !this.canManageBoard()) return;
    const confirmed = await this.dialog.confirm({
      title: 'Delete board permanently?',
      description: 'This board and its contents will be removed forever. This action cannot be undone.',
      confirmText: 'Delete board',
      cancelText: 'Keep board',
      variant: 'danger',
      onConfirm: () => firstValueFrom(this.boardService.delete(this.board!.id)),
      errorMessage: 'Failed to delete board'
    });
    if (!confirmed) return;

    const workspaceId = this.board.workspaceId;
    this.snack.open('Board deleted', 'Close', { duration: 3000 });
    this.router.navigate(['/workspace', workspaceId]);
  }

  canManageBoard(): boolean {
    if (this.publicView || !this.board) {
      return false;
    }

    if (this.auth.isAdmin()) {
      return true;
    }

    return this.board.members.some(member =>
      member.userId === this.auth.getUserId() && member.role === 'ADMIN'
    );
  }

  trackByListId(_: number, list: TaskList): number {
    return list.id;
  }

  trackByCardId(_: number, card: Card): number {
    return card.id;
  }

  trackByUserId(_: number, member: { userId: number }): number {
    return member.userId;
  }

  getVisibleBoardMembers(): Array<{ userId: number; displayName?: string; avatarUrl?: string }> {
    return this.boardMembers.slice(0, 4);
  }

  getRemainingBoardMembersCount(): number {
    return Math.max(this.boardMembers.length - 4, 0);
  }

  getMemberInitials(name?: string | null): string {
    if (!name?.trim()) {
      return 'U';
    }

    const parts = name.trim().split(/\s+/).slice(0, 2);
    return parts.map(part => part.charAt(0).toUpperCase()).join('');
  }

  getAssigneeDisplayName(assigneeId: number | null): string {
    if (!assigneeId) {
      return 'Unassigned';
    }

    return this.boardMembers.find(member => member.userId === assigneeId)?.displayName || `User ${assigneeId}`;
  }

  private rebuildCardBuckets(cards: Card[]): void {
    const buckets: Record<number, Card[]> = {};

    for (const card of cards) {
      if (card.isArchived) {
        continue;
      }

      if (!buckets[card.listId]) {
        buckets[card.listId] = [];
      }

      buckets[card.listId].push(card);
    }

    for (const listId of Object.keys(buckets)) {
      const numericListId = Number(listId);
      buckets[numericListId] = buckets[numericListId]
        .slice()
        .sort((a, b) => (a.position ?? 0) - (b.position ?? 0));
    }

    this.cardsByList = buckets;
  }
}
