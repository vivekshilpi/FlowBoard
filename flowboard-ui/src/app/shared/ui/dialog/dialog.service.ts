import { Injectable, inject } from '@angular/core';
import { MatDialog, MatDialogConfig, MatDialogRef } from '@angular/material/dialog';

import {
  DialogActionOptions,
  DialogAlertOptions,
  DialogComponentData,
  DialogConfirmOptions,
  DialogPromptOptions,
  DialogVariant
} from './dialog.types';
import { DialogModalComponent } from './dialog-modal.component';

@Injectable({ providedIn: 'root' })
export class DialogService {
  private readonly dialog = inject(MatDialog);
  private activeRef: MatDialogRef<DialogModalComponent, unknown> | null = null;

  alert(options: DialogAlertOptions): Promise<boolean> {
    return this.open<boolean>({
      mode: 'alert',
      title: options.title,
      description: options.description,
      variant: options.variant ?? 'info',
      icon: options.icon ?? this.resolveIcon(options.variant ?? 'info', 'alert'),
      acknowledgeText: options.acknowledgeText ?? options.action?.text ?? 'OK',
      confirmText: options.action?.text ?? options.acknowledgeText ?? 'OK',
      cancelText: '',
      confirmAction: this.resolveAction(options.action, options.variant ?? 'info', false),
      cancelAction: this.resolveSecondaryAction(undefined),
      backdropClose: options.backdropClose ?? true,
      escClose: options.escClose ?? true
    }, options);
  }

  confirm<T = boolean>(options: DialogConfirmOptions<T>): Promise<T | boolean> {
    return this.open<T | boolean>({
      mode: 'confirm',
      title: options.title,
      description: options.description,
      variant: options.variant ?? 'default',
      icon: options.icon ?? this.resolveIcon(options.variant ?? 'default', 'confirm'),
      acknowledgeText: options.confirmText ?? options.confirmAction?.text ?? 'Continue',
      confirmText: options.confirmText ?? options.confirmAction?.text ?? 'Continue',
      cancelText: options.cancelText ?? options.cancelAction?.text ?? 'Cancel',
      confirmAction: this.resolveAction(options.confirmAction, options.variant ?? 'default', true),
      cancelAction: this.resolveSecondaryAction(options.cancelAction),
      backdropClose: options.backdropClose ?? true,
      escClose: options.escClose ?? true,
      execute: options.onConfirm,
      errorMessage: options.errorMessage
    }, options);
  }

  prompt<T = string>(options: DialogPromptOptions<T>): Promise<T | string | boolean> {
    return this.open<T | string | boolean>({
      mode: 'prompt',
      title: options.title,
      description: options.description,
      variant: options.variant ?? 'default',
      icon: options.icon ?? this.resolveIcon(options.variant ?? 'default', 'prompt'),
      acknowledgeText: options.confirmText ?? options.confirmAction?.text ?? 'Continue',
      confirmText: options.confirmText ?? options.confirmAction?.text ?? 'Continue',
      cancelText: options.cancelText ?? options.cancelAction?.text ?? 'Cancel',
      confirmAction: this.resolveAction(options.confirmAction, options.variant ?? 'default', true),
      cancelAction: this.resolveSecondaryAction(options.cancelAction),
      backdropClose: options.backdropClose ?? true,
      escClose: options.escClose ?? true,
      placeholder: options.placeholder,
      initialValue: options.initialValue,
      inputType: options.inputType ?? 'text',
      multiline: options.multiline ?? false,
      required: options.required ?? false,
      readOnly: options.readOnly ?? false,
      selectTextOnOpen: options.selectTextOnOpen ?? false,
      validate: options.validate,
      execute: options.onConfirm ? (value?: string) => options.onConfirm?.(value ?? '') : undefined,
      errorMessage: options.errorMessage
    }, options);
  }

  success(options: Omit<DialogAlertOptions, 'variant'>): Promise<boolean> {
    return this.alert({ ...options, variant: 'success' });
  }

  warning(options: Omit<DialogAlertOptions, 'variant'>): Promise<boolean> {
    return this.alert({ ...options, variant: 'warning' });
  }

  error(options: Omit<DialogAlertOptions, 'variant'>): Promise<boolean> {
    return this.alert({ ...options, variant: 'error' });
  }

  info(options: Omit<DialogAlertOptions, 'variant'>): Promise<boolean> {
    return this.alert({ ...options, variant: 'info' });
  }

  private open<TResult>(
    data: DialogComponentData,
    options: {
      width?: string;
      maxWidth?: string;
      closeExisting?: boolean;
      panelClass?: string | string[];
    }
  ): Promise<TResult> {
    if (options.closeExisting !== false && this.activeRef) {
      this.activeRef.close(false);
    }

    const config: MatDialogConfig<DialogComponentData> = {
      data,
      width: options.width ?? 'min(520px, calc(100vw - 32px))',
      maxWidth: options.maxWidth ?? 'calc(100vw - 32px)',
      panelClass: ['fb-dialog-panel', ...(this.toClassArray(options.panelClass))],
      backdropClass: 'fb-dialog-backdrop',
      autoFocus: false,
      restoreFocus: true,
      disableClose: true,
      closeOnNavigation: true
    };

    const dialogRef = this.dialog.open(DialogModalComponent, config);
    this.activeRef = dialogRef;
    this.bindDismissHandlers(dialogRef, data);

    return new Promise<TResult>(resolve => {
      dialogRef.afterClosed().subscribe(result => {
        if (this.activeRef === dialogRef) {
          this.activeRef = null;
        }

        resolve((result ?? false) as TResult);
      });
    });
  }

  private bindDismissHandlers(dialogRef: MatDialogRef<DialogModalComponent, unknown>, data: DialogComponentData): void {
    dialogRef.backdropClick().subscribe(() => {
      if (data.backdropClose && dialogRef.componentInstance?.canDismiss) {
        dialogRef.close(false);
      }
    });

    dialogRef.keydownEvents().subscribe(event => {
      if (event.key === 'Escape' && data.escClose && dialogRef.componentInstance?.canDismiss) {
        event.preventDefault();
        dialogRef.close(false);
      }
    });
  }

  private resolveAction(
    action: DialogActionOptions | undefined,
    variant: DialogVariant,
    isConfirm: boolean
  ): Required<DialogActionOptions> {
    const tone = action?.tone ?? (variant === 'danger' || variant === 'error' ? 'danger' : isConfirm ? 'primary' : 'secondary');
    return {
      text: action?.text ?? '',
      tone,
      icon: action?.icon ?? (variant === 'danger' || variant === 'error' ? 'delete_forever' : '')
    };
  }

  private resolveSecondaryAction(action: DialogActionOptions | undefined): Required<DialogActionOptions> {
    return {
      text: action?.text ?? 'Cancel',
      tone: action?.tone ?? 'secondary',
      icon: action?.icon ?? ''
    };
  }

  private resolveIcon(variant: DialogVariant, mode: 'alert' | 'confirm' | 'prompt'): string {
    if (mode === 'prompt') {
      return variant === 'danger' || variant === 'error' ? 'edit_note' : 'link';
    }

    switch (variant) {
      case 'success':
        return 'task_alt';
      case 'warning':
        return 'warning_amber';
      case 'danger':
      case 'error':
        return 'delete_forever';
      case 'info':
        return 'info';
      case 'default':
      default:
        return mode === 'alert' ? 'notifications' : 'help_outline';
    }
  }

  private toClassArray(panelClass?: string | string[]): string[] {
    if (!panelClass) {
      return [];
    }

    return Array.isArray(panelClass) ? panelClass : [panelClass];
  }
}
