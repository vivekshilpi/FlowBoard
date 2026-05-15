import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { Board } from '../../../core/models/board.model';
import { Card } from '../../../core/models/card.model';

@Component({
  selector: 'app-board-analytics',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatDividerModule],
  templateUrl: './board-analytics.component.html',
  styleUrl: './board-analytics.component.scss'
})
export class BoardAnalyticsComponent implements OnInit {

  @Input() board!: Board;
  @Input() allCards: Card[] = [];

  stats = {
    total: 0,
    todo: 0,
    inProgress: 0,
    done: 0,
    overdue: 0,
    withDueDates: 0
  };

  ngOnInit(): void {
    this.calculateStats();
  }

  calculateStats(): void {
    this.stats.total = this.allCards.length;
    this.stats.todo = this.allCards.filter(c => c.status === 'TO_DO').length;
    this.stats.inProgress = this.allCards.filter(c => c.status === 'IN_PROGRESS').length;
    this.stats.done = this.allCards.filter(c => c.status === 'DONE').length;
    this.stats.overdue = this.allCards.filter(c => c.isOverdue).length;
    this.stats.withDueDates = this.allCards.filter(c => c.dueDate).length;
  }

  getPercentage(count: number): number {
    if (this.stats.total === 0) return 0;
    return Math.round((count / this.stats.total) * 100);
  }
}
