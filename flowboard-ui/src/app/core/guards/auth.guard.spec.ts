import { TestBed } from '@angular/core/testing';
import { CanActivateFn, Router, UrlTree } from '@angular/router';

import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) => 
      TestBed.runInInjectionContext(() => authGuard(...guardParameters));
  const authServiceMock = jasmine.createSpyObj<AuthService>('AuthService', ['isLoggedIn']);
  const urlTree = {} as UrlTree;
  const routerMock = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);

  beforeEach(() => {
    authServiceMock.isLoggedIn.calls.reset();
    routerMock.createUrlTree.calls.reset();
    routerMock.createUrlTree.and.returnValue(urlTree);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerMock }
      ]
    });
  });

  it('allows logged-in users through', () => {
    authServiceMock.isLoggedIn.and.returnValue(true);

    expect(executeGuard({} as never, { url: '/dashboard' } as never)).toBeTrue();
    expect(routerMock.createUrlTree).not.toHaveBeenCalled();
  });

  it('redirects guests to login with the intended url', () => {
    authServiceMock.isLoggedIn.and.returnValue(false);

    const result = executeGuard({} as never, { url: '/workspace/7' } as never);
    expect(result).toBe(urlTree);
    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/login'], {
      queryParams: { redirectUrl: '/workspace/7' }
    });
  });
});
