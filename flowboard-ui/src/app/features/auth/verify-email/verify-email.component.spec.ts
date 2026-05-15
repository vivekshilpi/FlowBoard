import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';

import { VerifyEmailComponent } from './verify-email.component';
import { createComponentTestingProviders } from '../../../../test-helpers/test-providers';
import { AuthService } from '../../../core/services/auth.service';

describe('VerifyEmailComponent', () => {
  let component: VerifyEmailComponent;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    localStorage.setItem('pendingEmail', 'verify@example.com');
    await TestBed.configureTestingModule({
      imports: [VerifyEmailComponent],
      providers: [...createComponentTestingProviders()]
    }).compileComponents();

    component = TestBed.createComponent(VerifyEmailComponent).componentInstance;
    auth = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;

    spyOn(auth, 'verifyEmail').and.returnValue(of('ok'));
    spyOn(auth, 'resendVerification').and.returnValue(of('ok'));
    spyOn(router, 'navigate');
  });

  afterEach(() => {
    localStorage.removeItem('pendingEmail');
  });

  it('verifies email and redirects to login', fakeAsync(() => {
    component.ngOnInit();
    component.form.setValue({ otp: '123456' });

    component.onSubmit();
    tick(1500);

    expect(component.email).toBe('verify@example.com');
    expect(auth.verifyEmail).toHaveBeenCalledWith('verify@example.com', '123456');
    expect(localStorage.getItem('pendingEmail')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  }));

  it('resends verification codes and handles failures', () => {
    component.email = 'verify@example.com';

    component.resend();
    expect(component.successMessage).toContain('verify@example.com');

    auth.resendVerification.and.returnValue(throwError(() => new Error('nope')));
    component.resend();
    expect(component.errorMessage).toBe('Failed to resend OTP.');
  });

  it('redirects when email is missing and handles invalid otp failures', () => {
    localStorage.removeItem('pendingEmail');
    component.ngOnInit();
    expect(router.navigate).toHaveBeenCalledWith(['/register']);

    auth.verifyEmail.and.returnValue(throwError(() => ({
      error: { message: 'Invalid OTP' }
    })));
    component.email = 'verify@example.com';
    component.form.setValue({ otp: '123456' });
    component.onSubmit();

    expect(component.errorMessage).toBe('Invalid OTP');
  });
});
