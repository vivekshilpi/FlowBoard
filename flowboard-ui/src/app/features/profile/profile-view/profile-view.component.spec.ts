import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { Store } from '@ngrx/store';
import { MatSnackBar } from '@angular/material/snack-bar';

import { ProfileViewComponent } from './profile-view.component';
import { createComponentTestingProviders } from '../../../../test-helpers/test-providers';
import { AuthService } from '../../../core/services/auth.service';
import { PaymentService } from '../../../core/services/payment.service';

describe('ProfileViewComponent', () => {
  let component: ProfileViewComponent;
  let auth: jasmine.SpyObj<AuthService>;
  let paymentService: jasmine.SpyObj<PaymentService>;
  let snack: jasmine.SpyObj<MatSnackBar>;
  let store: jasmine.SpyObj<Store>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfileViewComponent],
      providers: [...createComponentTestingProviders({ includeStore: true })]
    }).compileComponents();

    component = TestBed.createComponent(ProfileViewComponent).componentInstance;
    auth = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    paymentService = TestBed.inject(PaymentService) as jasmine.SpyObj<PaymentService>;
    snack = TestBed.inject(MatSnackBar) as jasmine.SpyObj<MatSnackBar>;
    store = TestBed.inject(Store) as jasmine.SpyObj<Store>;

    spyOn(auth, 'getProfile').and.returnValue(of({
      id: 9,
      fullName: 'Flow Board',
      username: 'flowboard',
      bio: 'Hello',
      avatarUrl: '/avatar.png'
    } as any));
    spyOn(auth, 'getProfileActivity').and.returnValue(of([
      { id: 1, action: 'login' },
      { id: 2, action: 'update' }
    ] as any));
    spyOn(auth, 'updateProfile').and.returnValue(of({
      id: 9,
      fullName: 'Flow Board',
      username: 'flowboard',
      bio: 'Updated',
      avatarUrl: '/avatar.png'
    } as any));
    spyOn(auth, 'uploadAvatar').and.returnValue(of({} as any));
    spyOn(auth, 'changePassword').and.returnValue(of('ok'));
    spyOn(auth as any, 'resolveAssetUrl').and.callFake((url: string | null | undefined) => url ? `resolved:${url}` : '');
    spyOn(auth as any, 'getErrorMessage').and.callFake((err: unknown, fallback: string) => (err as any)?.error?.message ?? fallback);
    spyOn(paymentService, 'getSubscription').and.returnValue(of({ planDisplayName: 'Pro' } as any));
    spyOn(snack, 'open');
    spyOn(store, 'dispatch');
  });

  it('loads profile subscription and activity on init', () => {
    component.ngOnInit();

    expect(auth.getProfile).toHaveBeenCalled();
    expect(auth.getProfileActivity).toHaveBeenCalled();
    expect(paymentService.getSubscription).toHaveBeenCalled();
    expect(component.user?.username).toBe('flowboard');
    expect(component.activityLog.length).toBe(2);
    expect(component.subscription?.planDisplayName).toBe('Pro');
  });

  it('saves profile changes and password changes', () => {
    component.ngOnInit();
    component.profileForm.patchValue({ bio: 'Updated' });
    component.saveProfile();

    expect(auth.updateProfile).toHaveBeenCalled();
    expect(component.user?.bio).toBe('Updated');

    component.passwordForm.setValue({
      oldPassword: 'OldPass1!',
      newPassword: 'NewPass1!',
      confirmPassword: 'NewPass1!'
    });
    component.changePassword();

    expect(auth.changePassword).toHaveBeenCalledWith({
      oldPassword: 'OldPass1!',
      newPassword: 'NewPass1!'
    });
    expect(component.passwordForm.get('oldPassword')?.value).toBeNull();
  });

  it('handles avatar helpers and paging helpers', () => {
    component.ngOnInit();
    component.avatarPreviewUrl = 'data:image/png;base64,abc';
    expect(component.getAvatarUrl()).toContain('data:image/png');

    component.avatarPreviewUrl = null;
    expect(component.getAvatarUrl()).toBe('resolved:/avatar.png');
    expect(component.getHeaderAvatarUrl()).toBe('resolved:/avatar.png');
    expect(component.getInitials()).toBe('FB');

    component.onAvatarError(new Event('error'));
    expect(component.avatarLoadFailed).toBeTrue();
    expect(component.getAvatarUrl()).toBe('');

    component.onAvatarLoad();
    expect(component.avatarLoadFailed).toBeFalse();

    component.currentPage = 1;
    component.nextPage();
    expect(component.currentPage).toBe(2);
    component.previousPage();
    expect(component.currentPage).toBe(1);
    component.setPage(10);
    expect(component.currentPage).toBe(1);
  });

  it('handles profile and subscription failures', () => {
    auth.getProfile.and.returnValue(throwError(() => ({
      error: { message: 'profile failed' }
    })));
    auth.getProfileActivity.and.returnValue(throwError(() => new Error('activity failed')));
    paymentService.getSubscription.and.returnValue(throwError(() => new Error('subscription failed')));

    component.ngOnInit();

    expect(component.error).toBe('profile failed');
    expect(component.activityLog).toEqual([]);
    expect(component.subscription).toBeNull();
  });

  it('validates password rules and detects unchanged profiles', () => {
    component.ngOnInit();
    expect(component.hasProfileChanges()).toBeFalse();
    component.profileForm.patchValue({ bio: 'Changed' });
    expect(component.hasProfileChanges()).toBeTrue();

    const validator = (component as any).passwordsMatchValidator();
    const group = component.passwordForm;
    group.setValue({
      oldPassword: 'SamePass1!',
      newPassword: 'SamePass1!',
      confirmPassword: 'SamePass1!'
    });
    expect(validator(group)).toEqual({ sameAsCurrent: true });

    group.setValue({
      oldPassword: 'OldPass1!',
      newPassword: 'NewPass1!',
      confirmPassword: 'Mismatch1!'
    });
    expect(validator(group)).toEqual({ passwordMismatch: true });
  });
});
