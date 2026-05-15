import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { DialogModalComponent } from './dialog-modal.component';

describe('DialogModalComponent', () => {
  let component: DialogModalComponent;
  let fixture: ComponentFixture<DialogModalComponent>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<DialogModalComponent, unknown>>;

  function configure(dataOverrides: Partial<any> = {}) {
    dialogRef = jasmine.createSpyObj<MatDialogRef<DialogModalComponent, unknown>>('MatDialogRef', ['close']);

    TestBed.configureTestingModule({
      imports: [DialogModalComponent],
      providers: [
        { provide: MatDialogRef, useValue: dialogRef },
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            mode: 'confirm',
            title: 'Confirm',
            description: 'Proceed',
            variant: 'default',
            confirmAction: { text: 'OK', tone: 'primary', icon: '' },
            cancelAction: { text: 'Cancel', tone: 'secondary', icon: '' },
            backdropClose: true,
            escClose: true,
            ...dataOverrides
          }
        }
      ]
    });

    fixture = TestBed.createComponent(DialogModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('exposes styling helpers and simple close actions', () => {
    configure();

    expect(component.variantClass).toBe('fb-dialog--default');
    expect(component.confirmButtonClass).toContain('btn-primary');
    expect(component.cancelButtonClass).toContain('btn-secondary');

    component.close();
    component.acknowledge();

    expect(dialogRef.close).toHaveBeenCalledWith(false);
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('closes prompt dialogs with trimmed value when no execute handler exists', async () => {
    configure({
      mode: 'prompt',
      initialValue: '  hello  ',
      required: true
    });

    await component.confirm();

    expect(dialogRef.close).toHaveBeenCalledWith('hello');
  });

  it('validates prompt input and surfaces validation errors', async () => {
    configure({
      mode: 'prompt',
      required: true,
      validate: () => 'Invalid value'
    });
    component.promptControl.setValue('value');

    await component.confirm();

    expect(component.inlineError()).toBe('Invalid value');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('resolves observable execute handlers and closes with returned value', async () => {
    configure({
      mode: 'confirm',
      execute: () => of('done')
    });

    await component.confirm();

    expect(dialogRef.close).toHaveBeenCalledWith('done');
  });

  it('surfaces execute errors and keeps dialog open', async () => {
    configure({
      mode: 'confirm',
      execute: () => throwError(() => new Error('Failed action'))
    });

    await component.confirm();

    expect(component.inlineError()).toBe('Failed action');
    expect(component.processing()).toBeFalse();
  });

  it('formats fallback and custom error messages', async () => {
    configure({
      mode: 'confirm',
      execute: () => Promise.reject('nope'),
      errorMessage: () => 'Custom message'
    });

    await component.confirm();
    expect(component.inlineError()).toBe('Custom message');
  });
});
