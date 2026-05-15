import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';

import { DialogService } from './dialog.service';
import { DialogModalComponent } from './dialog-modal.component';

describe('DialogService', () => {
  let service: DialogService;
  let matDialog: jasmine.SpyObj<MatDialog>;

  function createDialogRef(canDismiss = true) {
    const afterClosed$ = new Subject<unknown>();
    const backdrop$ = new Subject<MouseEvent>();
    const keydown$ = new Subject<KeyboardEvent>();

    const dialogRef = {
      close: jasmine.createSpy('close'),
      afterClosed: () => afterClosed$.asObservable(),
      backdropClick: () => backdrop$.asObservable(),
      keydownEvents: () => keydown$.asObservable(),
      componentInstance: { canDismiss }
    } as unknown as MatDialogRef<DialogModalComponent, unknown>;

    return { dialogRef, afterClosed$, backdrop$, keydown$ };
  }

  beforeEach(() => {
    matDialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    TestBed.configureTestingModule({
      providers: [
        DialogService,
        { provide: MatDialog, useValue: matDialog }
      ]
    });

    service = TestBed.inject(DialogService);
  });

  it('opens alert dialogs and resolves after close', async () => {
    const { dialogRef, afterClosed$ } = createDialogRef();
    matDialog.open.and.returnValue(dialogRef);

    const resultPromise = service.success({ title: 'Saved', description: 'Done' });
    afterClosed$.next(true);
    afterClosed$.complete();

    await expectAsync(resultPromise).toBeResolvedTo(true);
    expect(matDialog.open).toHaveBeenCalled();
  });

  it('closes existing dialog before opening another when allowed', () => {
    const first = createDialogRef();
    const second = createDialogRef();
    matDialog.open.and.returnValues(first.dialogRef, second.dialogRef);

    service.info({ title: 'First', description: 'A' });
    service.warning({ title: 'Second', description: 'B' });

    expect(first.dialogRef.close).toHaveBeenCalledWith(false);
  });

  it('wires backdrop and escape dismiss handlers', async () => {
    const { dialogRef, afterClosed$, backdrop$, keydown$ } = createDialogRef();
    matDialog.open.and.returnValue(dialogRef);

    const promise = service.confirm({ title: 'Confirm', description: 'Proceed?' });

    backdrop$.next(new MouseEvent('click'));
    expect(dialogRef.close).toHaveBeenCalledWith(false);

    const escapeEvent = new KeyboardEvent('keydown', { key: 'Escape' });
    spyOn(escapeEvent, 'preventDefault');
    keydown$.next(escapeEvent);
    expect(escapeEvent.preventDefault).toHaveBeenCalled();

    afterClosed$.next(false);
    afterClosed$.complete();
    await expectAsync(promise).toBeResolvedTo(false);
  });

  it('does not dismiss when component cannot close', () => {
    const { dialogRef, backdrop$, keydown$ } = createDialogRef(false);
    matDialog.open.and.returnValue(dialogRef);

    service.confirm({ title: 'Busy', description: 'Wait' });
    backdrop$.next(new MouseEvent('click'));
    keydown$.next(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
