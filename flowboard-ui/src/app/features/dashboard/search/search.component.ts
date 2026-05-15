import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, switchMap, forkJoin, of } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { WorkspaceService } from '../../../core/services/workspace.service';
import { BoardService } from '../../../core/services/board.service';
import { Workspace } from '../../../core/models/workspace.model';
import { Board } from '../../../core/models/board.model';
import { Card } from '../../../core/models/card.model';
import { CardService } from '../../../core/services/card.service';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './search.component.html',
  styleUrl: './search.component.scss'
})
export class SearchComponent implements OnInit {

  private workspaceService = inject(WorkspaceService);
  private boardService     = inject(BoardService);
  private cardService      = inject(CardService);
  private router           = inject(Router);

  searchControl = new FormControl('');
  
  boards: Board[] = [];
  workspaces: Workspace[] = [];
  cards: Card[] = [];
  
  loading = false;
  showResults = false;

  ngOnInit(): void {
    this.searchControl.valueChanges.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(val => {
        if (!val || val.length < 2) {
          this.showResults = false;
          return forkJoin({ boards: of([]), workspaces: of([]), cards: of([]) });
        }
        this.loading = true;
        this.showResults = true;
        return forkJoin({
          boards: this.boardService.search(val).pipe(catchError(() => of([]))),
          workspaces: this.workspaceService.search(val).pipe(catchError(() => of([]))),
          cards: this.cardService.globalSearch(val).pipe(catchError(() => of([])))
        });
      })
    ).subscribe({
      next: res => {
        this.boards = res.boards;
        this.workspaces = res.workspaces;
        this.cards = res.cards;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.showResults = false;
      }
    });
  }

  navigateTo(type: 'workspace' | 'board' | 'card', id: number, boardId?: number): void {
    this.showResults = false;
    this.searchControl.setValue('');
    if (type === 'workspace') {
      this.router.navigate(['/workspace', id]);
    } else if (type === 'board') {
      this.router.navigate(['/board', id]);
    } else if (boardId) {
      this.router.navigate(['/board', boardId], { queryParams: { cardId: id } });
    }
  }

  closeResults(): void {
    setTimeout(() => this.showResults = false, 200);
  }
}
