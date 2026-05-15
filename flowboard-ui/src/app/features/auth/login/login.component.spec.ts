import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router, ActivatedRoute } from '@angular/router';

import { LoginComponent } from './login.component';
import { createComponentTestingProviders } from '../../../../test-helpers/test-providers';
import { AuthService } from '../../../core/services/auth.service';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;
  let route: ActivatedRoute;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [...createComponentTestingProviders({
        queryParams: { redirectUrl: '/workspace/7' }
      })]
    }).compileComponents();

    component = TestBed.createComponent(LoginComponent).componentInstance;
    auth = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    route = TestBed.inject(ActivatedRoute);

    spyOn(auth, 'login').and.returnValue(of({} as any));
    spyOn(auth, 'getProfile').and.returnValue(of({} as any));
    spyOn(auth as any, 'getErrorMessage').and.callFake((err: unknown, fallback: string) => (err as any)?.error?.message ?? fallback);
    spyOn(router, 'navigate');
    spyOn(router, 'navigateByUrl');
    localStorage.removeItem('pendingEmail');
  });

  it('logs in and respects redirect urls', () => {
    component.form.setValue({ email: 'a@example.com', password: 'secret1' });

    component.onSubmit();

    expect(auth.login).toHaveBeenCalledWith(jasmine.objectContaining({
      email: 'a@example.com',
      password: 'secret1'
    }));
    expect(auth.getProfile).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/workspace/7');
  });

  it('still completes login navigation when profile refresh fails', () => {
    auth.getProfile.and.returnValue(throwError(() => new Error('profile failed')));
    component.form.setValue({ email: 'a@example.com', password: 'secret1' });

    component.onSubmit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/workspace/7');
  });

  it('stores pending email and redirects to verification on 403 errors', fakeAsync(() => {
    auth.login.and.returnValue(throwError(() => ({
      status: 403,
      error: { message: 'verify first' }
    })));
    component.form.setValue({ email: 'verify@example.com', password: 'secret1' });

    component.onSubmit();
    tick(2000);

    expect(component.loading).toBeFalse();
    expect(component.errorMessage).toBe('verify first');
    expect(localStorage.getItem('pendingEmail')).toBe('verify@example.com');
    expect(router.navigate).toHaveBeenCalledWith(['/verify-email']);
  }));

  it('shows a generic login failure for non-403 errors', () => {
    auth.login.and.returnValue(throwError(() => ({
      status: 500,
      error: { message: 'bad login' }
    })));
    component.form.setValue({ email: 'a@example.com', password: 'secret1' });

    component.onSubmit();

    expect(component.loading).toBeFalse();
    expect(component.errorMessage).toBe('bad login');
  });

  it('navigates to forgot password and ignores invalid forms', () => {
    component.form.setValue({ email: '', password: '' });

    component.onSubmit();
    component.goToForgotPassword();

    expect(auth.login).not.toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/forgot-password']);
  });
});
