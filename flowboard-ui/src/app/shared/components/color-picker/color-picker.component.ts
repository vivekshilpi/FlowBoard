import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'app-color-picker',
  standalone: true,
  imports: [CommonModule, MatMenuModule, MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <button mat-icon-button [matMenuTriggerFor]="colorMenu" [matTooltip]="tooltip" (click)="$event.stopPropagation()">
      <mat-icon [style.color]="selectedColor || '#94a3b8'">palette</mat-icon>
    </button>
    <mat-menu #colorMenu="matMenu" class="color-picker-menu">
      <div class="color-grid" (click)="$event.stopPropagation()">
        <div *ngFor="let color of colors" 
             class="color-swatch" 
             [style.background]="color"
             [class.selected]="color === selectedColor"
             (click)="selectColor(color)">
             <mat-icon *ngIf="color === selectedColor">check</mat-icon>
        </div>
      </div>
      <div class="color-actions" (click)="$event.stopPropagation()">
        <button mat-button color="warn" (click)="clearColor()">Clear</button>
      </div>
    </mat-menu>
  `,
  styleUrls: ['./color-picker.component.scss']
})
export class ColorPickerComponent {
  @Input() selectedColor: string | null = null;
  @Input() tooltip: string = 'Change Color';
  @Output() colorChange = new EventEmitter<string | null>();

  // Tailwind-inspired beautiful colors
  colors: string[] = [
    '#ef4444', '#f97316', '#f59e0b', '#eab308', '#84cc16', '#22c55e', '#10b981',
    '#14b8a6', '#06b6d4', '#0ea5e9', '#3b82f6', '#6366f1', '#8b5cf6', '#a855f7',
    '#d946ef', '#ec4899', '#f43f5e', '#64748b', '#78716c', '#0f172a'
  ];

  selectColor(color: string) {
    this.selectedColor = color;
    this.colorChange.emit(color);
  }

  clearColor() {
    this.selectedColor = null;
    this.colorChange.emit(null);
  }
}
