import { TestBed } from '@angular/core/testing';
import { CanActivateFn, Router, UrlTree } from '@angular/router';

import { adminGuard } from './admin.guard';
import { AuthService } from '../services/auth.service';

describe('adminGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => adminGuard(...guardParameters));

  const authServiceMock = jasmine.createSpyObj<AuthService>('AuthService', ['isLoggedIn', 'isAdmin']);
  const urlTree = {} as UrlTree;
  const routerMock = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);

  beforeEach(() => {
    authServiceMock.isLoggedIn.calls.reset();
    authServiceMock.isAdmin.calls.reset();
    routerMock.createUrlTree.calls.reset();
    routerMock.createUrlTree.and.returnValue(urlTree);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerMock }
      ]
    });
  });

  it('redirects guests to login', () => {
    authServiceMock.isLoggedIn.and.returnValue(false);

    const result = executeGuard({} as never, { url: '/admin' } as never);
    expect(result).toBe(urlTree);
    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/login'], {
      queryParams: { redirectUrl: '/admin' }
    });
  });

  it('allows platform admins through', () => {
    authServiceMock.isLoggedIn.and.returnValue(true);
    authServiceMock.isAdmin.and.returnValue(true);

    expect(executeGuard({} as never, { url: '/admin' } as never)).toBeTrue();
    expect(routerMock.createUrlTree).not.toHaveBeenCalled();
  });

  it('redirects signed-in non-admins to dashboard', () => {
    authServiceMock.isLoggedIn.and.returnValue(true);
    authServiceMock.isAdmin.and.returnValue(false);

    const result = executeGuard({} as never, { url: '/admin' } as never);
    expect(result).toBe(urlTree);
    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/dashboard']);
  });
});
