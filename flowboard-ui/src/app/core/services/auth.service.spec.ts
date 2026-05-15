import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HttpTestingController } from '@angular/common/http/testing';

import { AuthService } from './auth.service';
import { createHttpTestingProviders } from '../../../test-helpers/test-providers';
import { environment } from '../../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    TestBed.configureTestingModule({
      providers: [
        ...createHttpTestingProviders(),
        { provide: Router, useValue: router }
      ]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('registers, verifies, resends, forgets, resets and changes password', () => {
    service.register({} as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/register`).flush({ message: 'ok', token: null });

    service.verifyEmail('user@example.com', '123456').subscribe();
    const verifyReq = httpMock.expectOne(`${environment.apiUrl}/auth/verify-email`);
    expect(verifyReq.request.body).toEqual({ email: 'user@example.com', otp: '123456' });
    verifyReq.flush('verified');

    service.resendVerification('user@example.com').subscribe();
    const resendReq = httpMock.expectOne(`${environment.apiUrl}/auth/resend-verification?email=user@example.com`);
    expect(resendReq.request.method).toBe('POST');
    resendReq.flush('resent');

    service.forgotPassword('user@example.com').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/forgot-password`).flush('sent');

    service.resetPassword('user@example.com', '123456', 'Password@123').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/reset-password`).flush('reset');

    service.changePassword({} as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/password`).flush('changed');
  });

  it('persists login token and profile details', () => {
    service.login({} as any).subscribe();
    const payload = btoa(JSON.stringify({ userId: 5, role: 'PLATFORM_ADMIN', email: 'admin@example.com' }));
    httpMock.expectOne(`${environment.apiUrl}/auth/login`).flush({ message: 'ok', token: `x.${payload}.y` });

    expect(service.isLoggedIn()).toBeTrue();
    expect(service.getToken()).toContain(payload);
    expect(service.getUserId()).toBe(5);
    expect(service.getRole()).toBe('PLATFORM_ADMIN');
    expect(service.isAdmin()).toBeTrue();
  });

  it('loads and persists profile data from profile endpoints', () => {
    const profile = {
      id: 7,
      role: 'MEMBER',
      email: 'user@example.com',
      fullName: 'User Name',
      avatarUrl: '/uploads/avatar.png'
    };

    service.getProfile().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/profile`).flush(profile);
    expect(localStorage.getItem('userName')).toBe('User Name');

    service.getProfileActivity().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/profile/activity`).flush([]);

    service.updateProfile({} as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/profile`).flush(profile);

    service.updateThemePreference('dark' as any).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/profile/theme`).flush(profile);

    service.uploadAvatar(new File(['a'], 'avatar.png')).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/avatar`).flush(profile);
  });

  it('supports search, asset urls, error helper, and logout cleanup', () => {
    localStorage.setItem('token', 'token');
    localStorage.setItem('userRole', 'MEMBER');
    localStorage.setItem('userId', '9');
    service.searchUsers('abc').subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/search?key=abc`).flush([]);

    expect(service.getApiOrigin()).toBe(new URL(environment.apiUrl).origin);
    expect(service.resolveAssetUrl(null)).toBe('');
    expect(service.resolveAssetUrl('http://cdn/image.png')).toBe('http://cdn/image.png');
    expect(service.resolveAssetUrl('/uploads/a.png')).toContain('/uploads/a.png');
    expect(service.getErrorMessage({ error: { message: 'boom' } }, 'fallback')).toBe('boom');

    service.logout();
    const logoutReq = httpMock.expectOne(`${environment.apiUrl}/auth/logout`);
    logoutReq.flush('ok');
    expect(localStorage.getItem('token')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
