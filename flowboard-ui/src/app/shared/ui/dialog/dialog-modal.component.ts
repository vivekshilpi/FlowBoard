import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewChild,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { A11yModule } from '@angular/cdk/a11y';
import { firstValueFrom, isObservable } from 'rxjs';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { DialogButtonTone, DialogComponentData, DialogVariant } from './dialog.types';

@Component({
  selector: 'app-dialog-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, A11yModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './dialog-modal.component.html',
  styleUrl: './dialog-modal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DialogModalComponent implements AfterViewInit {
  private readonly dialogRef = inject(MatDialogRef<DialogModalComponent, unknown>);
  readonly data = inject<DialogComponentData>(MAT_DIALOG_DATA);

  @ViewChild('promptInput') private promptInput?: ElementRef<HTMLInputElement | HTMLTextAreaElement>;

  readonly processing = signal(false);
  readonly inlineError = signal<string | null>(null);
  readonly promptControl = new FormControl(this.data.initialValue ?? '', {
    nonNullable: true,
    validators: this.data.required ? [Validators.required] : []
  });

  ngAfterViewInit(): void {
    if (this.data.mode === 'prompt' && this.data.selectTextOnOpen) {
      queueMicrotask(() => this.promptInput?.nativeElement.select());
    }
  }

  get variantClass(): string {
    return `fb-dialog--${this.data.variant}`;
  }

  get confirmButtonClass(): string {
    return this.toButtonClass(this.data.confirmAction.tone);
  }

  get cancelButtonClass(): string {
    return this.toButtonClass(this.data.cancelAction.tone);
  }

  get canDismiss(): boolean {
    return !this.processing();
  }

  close(): void {
    if (!this.canDismiss) {
      return;
    }

    this.dialogRef.close(false);
  }

  acknowledge(): void {
    if (!this.canDismiss) {
      return;
    }

    this.dialogRef.close(true);
  }

  async confirm(): Promise<void> {
    if (this.processing()) {
      return;
    }

    const promptValue = this.promptControl.value.trim();
    if (this.data.mode === 'prompt') {
      this.promptControl.markAsTouched();

      if (this.promptControl.invalid) {
        return;
      }

      const validationMessage = this.data.validate?.(promptValue) ?? null;
      if (validationMessage) {
        this.inlineError.set(validationMessage);
        return;
      }
    }

    this.inlineError.set(null);

    if (!this.data.execute) {
      this.dialogRef.close(this.data.mode === 'prompt' ? promptValue : true);
      return;
    }

    this.processing.set(true);

    try {
      const result = await this.resolveAsyncValue(this.data.execute(this.data.mode === 'prompt' ? promptValue : undefined));
      const resolvedResult = result === undefined
        ? (this.data.mode === 'prompt' ? promptValue : true)
        : result;
      this.dialogRef.close(resolvedResult);
    } catch (error) {
      this.inlineError.set(this.formatError(error));
      this.processing.set(false);
    }
  }

  private async resolveAsyncValue<T>(value: T): Promise<unknown> {
    if (isObservable(value)) {
      return firstValueFrom(value);
    }

    return await Promise.resolve(value);
  }

  private formatError(error: unknown): string {
    const configuredError = this.data.errorMessage;
    if (typeof configuredError === 'function') {
      return configuredError(error);
    }

    if (configuredError) {
      return configuredError;
    }

    if (error instanceof Error && error.message) {
      return error.message;
    }

    return 'Something went wrong. Please try again.';
  }

  private toButtonClass(tone: DialogButtonTone): string {
    switch (tone) {
      case 'danger':
        return 'btn btn-danger btn-lg';
      case 'ghost':
        return 'btn btn-ghost btn-lg';
      case 'secondary':
        return 'btn btn-secondary btn-lg';
      case 'primary':
      default:
        return 'btn btn-primary btn-lg';
    }
  }
}
