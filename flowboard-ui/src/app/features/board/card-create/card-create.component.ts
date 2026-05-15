import { Component, EventEmitter, inject, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CardService } from '../../../core/services/card.service';
import { Card, ChecklistItem, Priority } from '../../../core/models/card.model';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-card-create',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatFormFieldModule, MatInputModule, MatIconModule,
    MatButtonModule, MatProgressSpinnerModule, MatSnackBarModule
  ],
  templateUrl: './card-create.component.html',
  styleUrl: './card-create.component.scss'
})
export class CardCreateComponent {

  @Input() listId!:  number;
  @Input() boardId!: number;
  @Input() boardMembers: Array<{ userId: number; displayName?: string }> = [];
  @Output() cardCreated = new EventEmitter<Card>();
  @Output() cancelled   = new EventEmitter<void>();

  private fb          = inject(FormBuilder);
  private cardService = inject(CardService);
  private authService = inject(AuthService);
  private snack = inject(MatSnackBar);

  loading = false;
  readonly priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  readonly labelOptions = [
    'tomato', 'orange', 'gold', 'limegreen',
    'deepskyblue', 'mediumpurple', 'hotpink', 'slategray'
  ];

  form = this.fb.group({
    title: ['', [Validators.required, Validators.minLength(1)]],
    description: [''],
    priority: ['MEDIUM' as Priority, Validators.required],
    dueDate: [''],
    startDate: [''],
    assigneeId: [''],
    labels: [[] as string[]]
  });

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;

    this.cardService.create({
      listId:  this.listId,
      boardId: this.boardId,
      title:   this.form.value.title!,
      description: this.form.value.description || undefined,
      priority: this.form.value.priority || 'MEDIUM',
      dueDate: this.form.value.dueDate || undefined,
      startDate: this.form.value.startDate || undefined,
      assigneeId: this.form.value.assigneeId ? Number(this.form.value.assigneeId) : undefined,
      labels: this.form.value.labels || [],
      checklistItems: [] as ChecklistItem[]
    }).subscribe({
      next: card => {
        this.loading = false;
        this.snack.open('Card created', 'Close', { duration: 2000 });
        this.cardCreated.emit(card);
        this.form.reset();
        this.form.patchValue({
          priority: 'MEDIUM',
          labels: [],
          assigneeId: '',
          description: '',
          dueDate: '',
          startDate: '',
          title: ''
        });
      },
      error: (error) => {
        this.loading = false;
        this.snack.open(
          this.authService.getErrorMessage(error, 'Failed to create card'),
          'Close',
          { duration: 4000 }
        );
      }
    });
  }

  toggleLabel(label: string): void {
    const current = this.form.value.labels || [];
    this.form.patchValue({
      labels: current.includes(label)
        ? current.filter(existing => existing !== label)
        : [...current, label]
    });
  }

  hasLabel(label: string): boolean {
    return (this.form.value.labels || []).includes(label);
  }

  cancel(): void { this.cancelled.emit(); }
}
