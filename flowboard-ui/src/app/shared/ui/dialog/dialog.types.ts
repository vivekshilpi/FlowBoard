import { Observable } from 'rxjs';

export type DialogVariant = 'default' | 'info' | 'success' | 'warning' | 'danger' | 'error';
export type DialogMode = 'alert' | 'confirm' | 'prompt';
export type DialogButtonTone = 'primary' | 'secondary' | 'danger' | 'ghost';
export type DialogAsyncValue<T> = T | Promise<T> | Observable<T>;

export interface DialogActionOptions {
  text?: string;
  tone?: DialogButtonTone;
  icon?: string;
}

export interface DialogBaseOptions {
  title: string;
  description?: string;
  variant?: DialogVariant;
  icon?: string;
  width?: string;
  maxWidth?: string;
  backdropClose?: boolean;
  escClose?: boolean;
  closeExisting?: boolean;
  panelClass?: string | string[];
}

export interface DialogAlertOptions extends DialogBaseOptions {
  acknowledgeText?: string;
  action?: DialogActionOptions;
}

export interface DialogConfirmOptions<T = unknown> extends DialogBaseOptions {
  confirmText?: string;
  cancelText?: string;
  confirmAction?: DialogActionOptions;
  cancelAction?: DialogActionOptions;
  onConfirm?: () => DialogAsyncValue<T>;
  errorMessage?: string | ((error: unknown) => string);
}

export interface DialogPromptOptions<T = unknown> extends DialogBaseOptions {
  confirmText?: string;
  cancelText?: string;
  confirmAction?: DialogActionOptions;
  cancelAction?: DialogActionOptions;
  placeholder?: string;
  initialValue?: string;
  inputType?: 'text' | 'email' | 'password' | 'url' | 'search';
  multiline?: boolean;
  required?: boolean;
  readOnly?: boolean;
  selectTextOnOpen?: boolean;
  validate?: (value: string) => string | null;
  onConfirm?: (value: string) => DialogAsyncValue<T>;
  errorMessage?: string | ((error: unknown) => string);
}

export interface DialogComponentData {
  mode: DialogMode;
  title: string;
  description?: string;
  variant: DialogVariant;
  icon?: string;
  acknowledgeText?: string;
  confirmText: string;
  cancelText: string;
  confirmAction: Required<DialogActionOptions>;
  cancelAction: Required<DialogActionOptions>;
  backdropClose: boolean;
  escClose: boolean;
  placeholder?: string;
  initialValue?: string;
  inputType?: DialogPromptOptions['inputType'];
  multiline?: boolean;
  required?: boolean;
  readOnly?: boolean;
  selectTextOnOpen?: boolean;
  validate?: (value: string) => string | null;
  execute?: (value?: string) => DialogAsyncValue<unknown>;
  errorMessage?: string | ((error: unknown) => string);
}
