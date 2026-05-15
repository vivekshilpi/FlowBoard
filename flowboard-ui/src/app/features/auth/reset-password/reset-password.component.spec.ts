import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';

import { ResetPasswordComponent } from './reset-password.component';
import { createComponentTestingProviders } from '../../../../test-helpers/test-providers';
import { AuthService } from '../../../core/services/auth.service';

describe('ResetPasswordComponent', () => {
  let component: ResetPasswordComponent;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    localStorage.setItem('resetEmail', 'reset@example.com');
    await TestBed.configureTestingModule({
      imports: [ResetPasswordComponent],
      providers: [...createComponentTestingProviders()]
    }).compileComponents();

    component = TestBed.createComponent(ResetPasswordComponent).componentInstance;
    auth = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;

    spyOn(auth, 'resetPassword').and.returnValue(of('ok'));
    spyOn(router, 'navigate');
  });

  afterEach(() => {
    localStorage.removeItem('resetEmail');
  });

  it('loads the reset email and redirects after success', fakeAsync(() => {
    component.ngOnInit();
    component.form.setValue({
      otp: '123456',
      newPassword: 'Password1!',
      confirmPassword: 'Password1!'
    });

    component.onSubmit();
    tick(1500);

    expect(component.email).toBe('reset@example.com');
    expect(auth.resetPassword).toHaveBeenCalledWith('reset@example.com', '123456', 'Password1!');
    expect(component.successMessage).toContain('Redirecting');
    expect(localStorage.getItem('resetEmail')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  }));

  it('handles missing reset email, validation, and reset failures', () => {
    localStorage.removeItem('resetEmail');
    component.ngOnInit();
    expect(router.navigate).toHaveBeenCalledWith(['/forgot-password']);

    component.form.setValue({
      otp: '123456',
      newPassword: 'Password1!',
      confirmPassword: 'mismatch'
    });
    expect(component.passwordMatch(component.form)).toEqual({ mismatch: true });

    auth.resetPassword.and.returnValue(throwError(() => ({
      error: { message: 'bad otp' }
    })));
    component.email = 'reset@example.com';
    component.form.setValue({
      otp: '123456',
      newPassword: 'Password1!',
      confirmPassword: 'Password1!'
    });
    component.onSubmit();

    expect(component.errorMessage).toBe('bad otp');
  });
});
