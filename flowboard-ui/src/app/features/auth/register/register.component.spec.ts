import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';

import { RegisterComponent } from './register.component';
import { createComponentTestingProviders } from '../../../../test-helpers/test-providers';
import { AuthService } from '../../../core/services/auth.service';

describe('RegisterComponent', () => {
  let component: RegisterComponent;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [...createComponentTestingProviders()]
    }).compileComponents();

    component = TestBed.createComponent(RegisterComponent).componentInstance;
    auth = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;

    spyOn(auth, 'register').and.returnValue(of({ message: 'Registered' } as any));
    spyOn(auth as any, 'getErrorMessage').and.callFake((err: unknown, fallback: string) => (err as any)?.error?.message ?? fallback);
    spyOn(router, 'navigate');
    localStorage.removeItem('pendingEmail');
  });

  it('registers users and redirects to verification', fakeAsync(() => {
    component.form.setValue({
      fullName: 'Flow User',
      email: 'flow@example.com',
      username: 'flowuser',
      password: 'secret1',
      confirmPassword: 'secret1'
    });

    component.onSubmit();
    tick(1500);

    expect(auth.register).toHaveBeenCalledWith(jasmine.objectContaining({
      fullName: 'Flow User',
      email: 'flow@example.com',
      username: 'flowuser',
      password: 'secret1'
    }));
    expect(component.successMessage).toBe('Registered');
    expect(localStorage.getItem('pendingEmail')).toBe('flow@example.com');
    expect(router.navigate).toHaveBeenCalledWith(['/verify-email']);
  }));

  it('surfaces registration failures', () => {
    auth.register.and.returnValue(throwError(() => ({
      error: { message: 'already exists' }
    })));
    component.form.setValue({
      fullName: 'Flow User',
      email: 'flow@example.com',
      username: 'flowuser',
      password: 'secret1',
      confirmPassword: 'secret1'
    });

    component.onSubmit();

    expect(component.loading).toBeFalse();
    expect(component.errorMessage).toBe('already exists');
  });

  it('validates matching passwords', () => {
    component.form.setValue({
      fullName: 'Flow User',
      email: 'flow@example.com',
      username: 'flowuser',
      password: 'secret1',
      confirmPassword: 'different'
    });

    expect(component.passwordMatch(component.form)).toEqual({ mismatch: true });

    component.onSubmit();
    expect(auth.register).not.toHaveBeenCalled();
  });
});
