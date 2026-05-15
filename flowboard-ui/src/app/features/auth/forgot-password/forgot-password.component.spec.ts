import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';

import { ForgotPasswordComponent } from './forgot-password.component';
import { createComponentTestingProviders } from '../../../../test-helpers/test-providers';
import { AuthService } from '../../../core/services/auth.service';

describe('ForgotPasswordComponent', () => {
  let component: ForgotPasswordComponent;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ForgotPasswordComponent],
      providers: [...createComponentTestingProviders()]
    }).compileComponents();

    component = TestBed.createComponent(ForgotPasswordComponent).componentInstance;
    auth = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;

    spyOn(auth, 'forgotPassword').and.returnValue(of('ok'));
    spyOn(router, 'navigate');
    localStorage.removeItem('resetEmail');
  });

  it('requests a reset otp and redirects', fakeAsync(() => {
    component.form.setValue({ email: 'reset@example.com' });

    component.onSubmit();
    tick(1500);

    expect(auth.forgotPassword).toHaveBeenCalledWith('reset@example.com');
    expect(component.successMessage).toContain('reset@example.com');
    expect(localStorage.getItem('resetEmail')).toBe('reset@example.com');
    expect(router.navigate).toHaveBeenCalledWith(['/reset-password']);
  }));

  it('shows backend errors and skips invalid submissions', () => {
    auth.forgotPassword.and.returnValue(throwError(() => ({
      error: { message: 'user not found' }
    })));
    component.form.setValue({ email: 'bad@example.com' });
    component.onSubmit();

    expect(component.errorMessage).toBe('user not found');

    component.form.setValue({ email: '' });
    component.onSubmit();
    expect(auth.forgotPassword).toHaveBeenCalledTimes(1);
  });
});
