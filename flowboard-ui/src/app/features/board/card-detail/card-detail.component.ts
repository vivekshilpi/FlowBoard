import {
  Component, inject, OnInit, OnDestroy, Input, Output, EventEmitter
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subject, firstValueFrom, takeUntil } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import {
  Card, CardActivity, CardAttachment, CardStatus, ChecklistItem, Priority
} from '../../../core/models/card.model';
import { CardService } from '../../../core/services/card.service';
import { AuthService } from '../../../core/services/auth.service';
import { ColorPickerComponent } from '../../../shared/components/color-picker/color-picker.component';
import { Store } from '@ngrx/store';
import * as BoardActions from '../../../store/board/board.actions';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';

const CARD_ALREADY_REMOVED = '__CARD_ALREADY_REMOVED__';

@Component({
  selector: 'app-card-detail',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, FormsModule, DatePipe,
    MatIconModule, MatButtonModule, MatDividerModule,
    MatProgressSpinnerModule, MatMenuModule, MatProgressBarModule,
    MatSnackBarModule, ColorPickerComponent
  ],
  templateUrl: './card-detail.component.html',
  styleUrl: './card-detail.component.scss'
})
export class CardDetailComponent implements OnInit, OnDestroy {
  private store = inject(Store);
  private cardService = inject(CardService);
  private authService = inject(AuthService);
  private snack = inject(MatSnackBar);
  private destroy$ = new Subject<void>();
  private dialog = inject(DialogService);

  @Input() card!: Card;
  @Input() boardMembers: Array<{ userId: number; displayName?: string; avatarUrl?: string }> = [];
  @Output() cardUpdated = new EventEmitter<Card>();
  @Output() cardDeleted = new EventEmitter<{ cardId: number; listId: number }>();
  @Output() closed = new EventEmitter<void>();

  editMode = false;
  saving = false;
  editTitle = false;
  activity: CardActivity[] = [];
  loadingActivity = false;

  editedTitle = '';
  editedDescription = '';
  newDueDate = '';
  newStartDate = '';
  commentText = '';
  selectedMentionIds: number[] = [];
  replyToActivityId: number | null = null;
  editingCommentId: number | null = null;
  editingCommentText = '';
  selectedLabels: string[] = [];
  newChecklistText = '';
  checklistItems: ChecklistItem[] = [];
  readonly labelOptions = [
    'tomato', 'orange', 'gold', 'limegreen',
    'deepskyblue', 'mediumpurple', 'hotpink', 'slategray'
  ];

  statuses: CardStatus[] = ['TO_DO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE'];
  priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

  statusLabels: Record<CardStatus, string> = {
    TO_DO: 'To Do', IN_PROGRESS: 'In Progress',
    IN_REVIEW: 'In Review', DONE: 'Done'
  };
  statusIcons: Record<CardStatus, string> = {
    TO_DO: 'radio_button_unchecked', IN_PROGRESS: 'sync',
    IN_REVIEW: 'rate_review', DONE: 'check_circle'
  };
  priorityColors: Record<Priority, string> = {
    LOW: '#22c55e', MEDIUM: '#f59e0b',
    HIGH: '#ef4444', CRITICAL: '#7c3aed'
  };

  ngOnInit(): void {
    this.syncLocalStateFromCard(this.card);
    this.loadActivity();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadActivity(): void {
    this.loadingActivity = true;
    this.cardService.getActivity(this.card.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: logs => {
          this.activity = logs;
          this.loadingActivity = false;
        },
        error: () => this.loadingActivity = false
      });
  }

  submitComment(): void {
    const content = this.commentText.trim();
    if (!content) return;

    this.saving = true;
    this.cardService.addComment(this.card.id, {
      content,
      parentActivityId: this.replyToActivityId,
      mentionedUserIds: this.selectedMentionIds
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: comment => {
        this.saving = false;
        this.commentText = '';
        this.selectedMentionIds = [];
        this.replyToActivityId = null;
        this.activity = [comment, ...this.activity];
        this.snack.open('Comment added', 'Close', { duration: 2000 });
      },
      error: () => {
        this.saving = false;
        this.snack.open('Failed to add comment', 'Close', { duration: 3000 });
      }
    });
  }

  startReply(activityId: number): void {
    this.replyToActivityId = activityId;
  }

  cancelReply(): void {
    this.replyToActivityId = null;
  }

  startEditComment(comment: CardActivity): void {
    this.editingCommentId = comment.id;
    this.editingCommentText = comment.description;
  }

  cancelEditComment(): void {
    this.editingCommentId = null;
    this.editingCommentText = '';
  }

  saveEditedComment(comment: CardActivity): void {
    const content = this.editingCommentText.trim();
    if (!content) return;

    this.cardService.updateComment(this.card.id, comment.id, content)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: updated => {
          this.activity = this.activity.map(existing => existing.id === updated.id ? updated : existing);
          this.cancelEditComment();
          this.snack.open('Comment updated', 'Close', { duration: 2000 });
        },
        error: () => this.snack.open('Failed to update comment', 'Close', { duration: 3000 })
      });
  }

  async deleteComment(comment: CardActivity): Promise<void> {
    const confirmed = await this.dialog.confirm({
      title: 'Delete comment?',
      description: 'The comment will be removed from the activity feed.',
      confirmText: 'Delete comment',
      cancelText: 'Keep comment',
      variant: 'danger',
      onConfirm: () => firstValueFrom(this.cardService.deleteComment(this.card.id, comment.id)),
      errorMessage: 'Failed to delete comment'
    });
    if (!confirmed) return;
    this.activity = this.activity.map(existing => existing.id === comment.id
      ? { ...existing, actionType: 'COMMENT_DELETED', description: 'Comment deleted' }
      : existing);
    this.snack.open('Comment deleted', 'Close', { duration: 2000 });
  }

  saveTitle(): void {
    if (!this.editedTitle.trim() || this.editedTitle === this.card.title) {
      this.editTitle = false;
      return;
    }
    this.persistCardUpdate({ title: this.editedTitle }, 'Title updated', updated => {
      this.editTitle = false;
      this.syncLocalStateFromCard(updated);
    });
  }

  saveDescription(): void {
    this.persistCardUpdate({ description: this.editedDescription }, 'Description saved', updated => {
      this.editMode = false;
      this.syncLocalStateFromCard(updated);
    });
  }

  setStatus(status: CardStatus): void {
    this.saving = true;
    this.cardService.setStatus(this.card.id, status)
      .pipe(takeUntil(this.destroy$)).subscribe({
        next: updated => {
          this.saving = false;
          this.handleCardRefresh(updated);
          this.loadActivity();
        },
        error: () => {
          this.saving = false;
          this.snack.open('Failed to update status', 'Close', { duration: 3000 });
        }
      });
  }

  setPriority(priority: Priority): void {
    this.saving = true;
    this.cardService.setPriority(this.card.id, priority)
      .pipe(takeUntil(this.destroy$)).subscribe({
        next: updated => {
          this.saving = false;
          this.handleCardRefresh(updated);
          this.loadActivity();
        },
        error: () => {
          this.saving = false;
          this.snack.open('Failed to update priority', 'Close', { duration: 3000 });
        }
      });
  }

  saveDueDate(): void {
    this.persistCardUpdate({ dueDate: this.newDueDate || undefined }, 'Due date updated');
  }

  clearDueDate(): void {
    this.newDueDate = '';
    this.saveDueDate();
  }

  saveStartDate(): void {
    this.persistCardUpdate({ startDate: this.newStartDate || undefined }, 'Start date updated');
  }

  clearStartDate(): void {
    this.newStartDate = '';
    this.saveStartDate();
  }

  assignTo(userId: number | null): void {
    this.saving = true;
    this.cardService.setAssignee(this.card.id, userId)
      .pipe(takeUntil(this.destroy$)).subscribe({
        next: updated => {
          this.saving = false;
          this.handleCardRefresh(updated);
          this.snack.open(userId ? 'Card assigned' : 'Assignee removed', 'Close', { duration: 2000 });
          this.loadActivity();
        },
        error: () => {
          this.saving = false;
          this.snack.open('Failed to assign card', 'Close', { duration: 3000 });
        }
      });
  }

  toggleLabel(label: string): void {
    this.selectedLabels = this.selectedLabels.includes(label)
      ? this.selectedLabels.filter(existing => existing !== label)
      : [...this.selectedLabels, label];
  }

  saveLabels(): void {
    this.persistCardUpdate({ labels: this.selectedLabels }, 'Labels updated');
  }

  addChecklistItem(): void {
    const text = this.newChecklistText.trim();
    if (!text) return;
    this.checklistItems = [
      ...this.checklistItems,
      { id: crypto.randomUUID(), text, completed: false }
    ];
    this.newChecklistText = '';
    this.persistChecklist('Checklist updated');
  }

  toggleChecklistItem(itemId: string): void {
    this.checklistItems = this.checklistItems.map(item =>
      item.id === itemId ? { ...item, completed: !item.completed } : item
    );
    this.persistChecklist('Checklist updated');
  }

  removeChecklistItem(itemId: string): void {
    this.checklistItems = this.checklistItems.filter(item => item.id !== itemId);
    this.persistChecklist('Checklist updated');
  }

  onAttachmentSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.cardService.uploadAttachment(this.card.id, file)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: updated => {
          this.handleCardRefresh(updated);
          input.value = '';
          this.snack.open('Attachment uploaded', 'Close', { duration: 2000 });
        },
        error: () => this.snack.open('Failed to upload attachment', 'Close', { duration: 3000 })
      });
  }

  async deleteAttachment(attachment: CardAttachment): Promise<void> {
    const result = await this.dialog.confirm<Card>({
      title: `Delete "${attachment.fileName}"?`,
      description: 'This attachment will be removed from the card.',
      confirmText: 'Delete attachment',
      cancelText: 'Keep attachment',
      variant: 'danger',
      onConfirm: () => firstValueFrom(this.cardService.deleteAttachment(this.card.id, attachment.id)),
      errorMessage: 'Failed to delete attachment'
    });
    if (!result) return;
    const updated = result as Card;
    this.handleCardRefresh(updated);
    this.snack.open('Attachment deleted', 'Close', { duration: 2000 });
  }

  downloadAttachment(attachment: CardAttachment): void {
    this.cardService.downloadAttachment(this.card.id, attachment.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: blob => {
          const url = window.URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = attachment.fileName;
          link.click();
          window.URL.revokeObjectURL(url);
        },
        error: () => this.snack.open('Failed to download attachment', 'Close', { duration: 3000 })
      });
  }

  updateCoverColor(color: string | null): void {
    this.persistCardUpdate({ coverColor: color || undefined }, 'Cover updated');
  }

  archiveCard(): void {
    this.saving = true;
    this.cardService.archive(this.card.id)
      .pipe(takeUntil(this.destroy$)).subscribe({
        next: updated => {
          this.saving = false;
          this.handleCardRefresh(updated);
          this.snack.open('Card archived', 'Close', { duration: 2000 });
          this.close();
        },
        error: (error: HttpErrorResponse) => {
          this.saving = false;
          if (error.status === 404) {
            const archivedCard = { ...this.card, isArchived: true };
            this.handleCardRefresh(archivedCard);
            this.snack.open('Card was already archived. Board refreshed.', 'Close', { duration: 3000 });
            this.close();
            return;
          }

          this.snack.open(
            this.authService.getErrorMessage(error, 'Failed to archive card'),
            'Close',
            { duration: 3000 }
          );
        }
      });
  }

  async deleteCard(): Promise<void> {
    const result = await this.dialog.confirm<string>({
      title: 'Delete card permanently?',
      description: 'This card will be removed forever and cannot be recovered.',
      confirmText: 'Delete card',
      cancelText: 'Keep card',
      variant: 'danger',
      onConfirm: async () => {
        try {
          await firstValueFrom(this.cardService.delete(this.card.id));
          return 'deleted';
        } catch (error) {
          if (error instanceof HttpErrorResponse && error.status === 404) {
            return CARD_ALREADY_REMOVED;
          }

          throw error;
        }
      },
      errorMessage: (error) => this.authService.getErrorMessage(error, 'Failed to delete card')
    });
    if (!result) return;
    if (result === CARD_ALREADY_REMOVED) {
      this.cardDeleted.emit({ cardId: this.card.id, listId: this.card.listId });
      this.snack.open('Card was already removed. Board refreshed.', 'Close', { duration: 3000 });
      this.close();
      return;
    }
    this.cardDeleted.emit({ cardId: this.card.id, listId: this.card.listId });
    this.snack.open('Card deleted', 'Close', { duration: 2000 });
    this.close();
  }

  copyCard(): void {
    this.cardService.copyCard(this.card.id)
      .pipe(takeUntil(this.destroy$)).subscribe({
        next: copy => {
          this.store.dispatch(BoardActions.addCard({ card: copy }));
          this.snack.open('Card copied!', 'Close', { duration: 2000 });
        },
        error: () => this.snack.open('Failed to copy card', 'Close', { duration: 3000 })
      });
  }

  toggleMention(userId: number): void {
    this.selectedMentionIds = this.selectedMentionIds.includes(userId)
      ? this.selectedMentionIds.filter(id => id !== userId)
      : [...this.selectedMentionIds, userId];
  }

  isMentionSelected(userId: number): boolean {
    return this.selectedMentionIds.includes(userId);
  }

  isComment(log: CardActivity): boolean {
    return log.actionType === 'COMMENT' || log.actionType === 'COMMENT_DELETED';
  }

  isOwnComment(log: CardActivity): boolean {
    return this.isComment(log) && log.actorId === this.authService.getUserId();
  }

  isReply(log: CardActivity): boolean {
    return !!log.parentActivityId;
  }

  getParentPreview(parentActivityId: number | null | undefined): string {
    if (!parentActivityId) return '';
    return this.activity.find(log => log.id === parentActivityId)?.description || 'Parent comment';
  }

  getMemberAvatar(member: { avatarUrl?: string }): string {
    return this.authService.resolveAssetUrl(member.avatarUrl);
  }

  getAssigneeName(userId: number | null): string {
    if (!userId) return 'Unassigned';
    const member = this.boardMembers.find(existing => existing.userId === userId);
    return member?.displayName || `User #${userId}`;
  }

  getCommentAuthor(log: CardActivity): string {
    return this.getAssigneeName(log.actorId ?? null);
  }

  formatFileSize(size: number): string {
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`;
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }

  checklistProgress(): number {
    if (this.checklistItems.length === 0) return 0;
    const completed = this.checklistItems.filter(item => item.completed).length;
    return Math.round((completed / this.checklistItems.length) * 100);
  }

  isOverdue(): boolean {
    if (!this.card.dueDate || this.card.status === 'DONE') return false;
    return new Date(this.card.dueDate) < new Date();
  }

  close(): void {
    this.closed.emit();
  }

  private persistChecklist(message: string): void {
    this.persistCardUpdate({ checklistItems: this.checklistItems }, message);
  }

  private persistCardUpdate(overrides: Partial<{
    title: string;
    description?: string;
    priority?: Priority;
    status?: CardStatus;
    dueDate?: string;
    startDate?: string;
    coverColor?: string;
    labels?: string[];
    checklistItems?: ChecklistItem[];
  }>, successMessage: string, afterUpdate?: (updated: Card) => void): void {
    this.saving = true;
    this.cardService.update(this.card.id, {
      title: overrides.title ?? this.card.title,
      description: overrides.description ?? (this.editedDescription || this.card.description || undefined),
      priority: overrides.priority ?? this.card.priority,
      status: overrides.status ?? this.card.status,
      dueDate: Object.prototype.hasOwnProperty.call(overrides, 'dueDate')
        ? overrides.dueDate
        : (this.card.dueDate || undefined),
      startDate: Object.prototype.hasOwnProperty.call(overrides, 'startDate')
        ? overrides.startDate
        : (this.card.startDate || undefined),
      coverColor: Object.prototype.hasOwnProperty.call(overrides, 'coverColor')
        ? overrides.coverColor
        : (this.card.coverColor || undefined),
      labels: overrides.labels ?? this.selectedLabels,
      checklistItems: overrides.checklistItems ?? this.checklistItems
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: updated => {
        this.saving = false;
        this.handleCardRefresh(updated);
        if (successMessage) {
          this.snack.open(successMessage, 'Close', { duration: 2000 });
        }
        afterUpdate?.(updated);
      },
      error: () => {
        this.saving = false;
        this.snack.open('Failed to update card', 'Close', { duration: 3000 });
      }
    });
  }

  private handleCardRefresh(updated: Card): void {
    this.syncLocalStateFromCard(updated);
    this.cardUpdated.emit(updated);
    this.store.dispatch(BoardActions.updateCard({ card: updated }));
  }

  private syncLocalStateFromCard(card: Card): void {
    this.card = card;
    this.editedTitle = card.title;
    this.editedDescription = card.description || '';
    this.newDueDate = card.dueDate || '';
    this.newStartDate = card.startDate || '';
    this.selectedLabels = [...(card.labels || [])];
    this.checklistItems = [...(card.checklistItems || [])];
  }
}
