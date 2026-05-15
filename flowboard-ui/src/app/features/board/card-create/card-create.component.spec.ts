import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';

import { CardCreateComponent } from './card-create.component';
import { createComponentTestingProviders } from '../../../../test-helpers/test-providers';
import { CardService } from '../../../core/services/card.service';
import { AuthService } from '../../../core/services/auth.service';

describe('CardCreateComponent', () => {
  let component: CardCreateComponent;
  let cardService: jasmine.SpyObj<CardService>;
  let auth: jasmine.SpyObj<AuthService>;
  let snack: jasmine.SpyObj<MatSnackBar>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CardCreateComponent],
      providers: [...createComponentTestingProviders()]
    }).compileComponents();

    component = TestBed.createComponent(CardCreateComponent).componentInstance;
    cardService = TestBed.inject(CardService) as jasmine.SpyObj<CardService>;
    auth = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    snack = TestBed.inject(MatSnackBar) as jasmine.SpyObj<MatSnackBar>;

    component.listId = 10;
    component.boardId = 20;
    spyOn(cardService, 'create').and.returnValue(of({ id: 1, title: 'New Card' } as any));
    spyOn(auth as any, 'getErrorMessage').and.returnValue('Failed to create card');
    spyOn(component.cardCreated, 'emit');
    spyOn(component.cancelled, 'emit');
    spyOn(snack, 'open');
  });

  it('creates cards and resets the form', () => {
    component.form.patchValue({
      title: 'New Card',
      description: 'Desc',
      priority: 'HIGH',
      assigneeId: '5',
      labels: ['tomato']
    });

    component.onSubmit();

    expect(cardService.create).toHaveBeenCalledWith(jasmine.objectContaining({
      listId: 10,
      boardId: 20,
      title: 'New Card',
      assigneeId: 5,
      labels: ['tomato']
    }));
    expect(component.cardCreated.emit).toHaveBeenCalled();
    expect(component.form.value.priority).toBe('MEDIUM');
    expect(component.form.value.title).toBe('');
  });

  it('handles label toggling submission failures and cancel events', () => {
    component.toggleLabel('tomato');
    expect(component.hasLabel('tomato')).toBeTrue();
    component.toggleLabel('tomato');
    expect(component.hasLabel('tomato')).toBeFalse();

    cardService.create.and.returnValue(throwError(() => new Error('nope')));
    component.form.patchValue({ title: 'New Card' });
    component.onSubmit();
    expect(component.loading).toBeFalse();
    expect((auth as any).getErrorMessage).toHaveBeenCalled();

    component.form.patchValue({ title: '' });
    component.onSubmit();
    expect(cardService.create).toHaveBeenCalledTimes(1);

    component.cancel();
    expect(component.cancelled.emit).toHaveBeenCalled();
  });
});
