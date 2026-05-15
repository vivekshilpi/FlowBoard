import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { Card } from '../../../core/models/card.model';
import { TaskList } from '../../../core/models/list.model';

@Component({
  selector: 'app-archive-manager',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule, MatDividerModule],
  templateUrl: './archive-manager.component.html',
  styleUrl: './archive-manager.component.scss'
})
export class ArchiveManagerComponent {

  @Input() archivedCards: Card[] = [];
  @Input() archivedLists: TaskList[] = [];

  @Output() restoreCard = new EventEmitter<Card>();
  @Output() restoreList = new EventEmitter<number>();
  @Output() deleteCard = new EventEmitter<Card>();
  @Output() deleteList = new EventEmitter<number>();
  @Output() closed = new EventEmitter<void>();

  activeTab: 'cards' | 'lists' = 'cards';

  onRestoreCard(card: Card): void {
    this.restoreCard.emit(card);
  }

  onRestoreList(listId: number): void {
    this.restoreList.emit(listId);
  }

  onDeleteCard(card: Card): void {
    this.deleteCard.emit(card);
  }

  onDeleteList(listId: number): void {
    this.deleteList.emit(listId);
  }

  close(): void {
    this.closed.emit();
  }
}
