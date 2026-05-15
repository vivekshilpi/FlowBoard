import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { Router } from '@angular/router';
import { throwError, of } from 'rxjs';

import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  const interceptor: HttpInterceptorFn = (req, next) =>
    TestBed.runInInjectionContext(() => authInterceptor(req, next));
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    router = jasmine.createSpyObj<Router>('Router', ['navigate'], { url: '/boards/1' });
    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: router }
      ]
    });
    localStorage.clear();
  });

  it('should be created', () => {
    expect(interceptor).toBeTruthy();
  });

  it('adds auth and user headers for non-form requests', (done) => {
    localStorage.setItem('token', 'token');
    localStorage.setItem('userId', '7');
    localStorage.setItem('userEmail', 'user@example.com');
    localStorage.setItem('userRole', 'MEMBER');

    const req = new HttpRequest('POST', '/api/test', { hello: 'world' });
    const next: HttpHandlerFn = request => {
      expect(request.headers.get('Authorization')).toBe('Bearer token');
      expect(request.headers.get('X-User-Id')).toBe('7');
      expect(request.headers.get('X-User-Email')).toBe('user@example.com');
      expect(request.headers.get('X-User-Role')).toBe('MEMBER');
      expect(request.headers.get('Content-Type')).toBe('application/json');
      return of({} as any);
    };

    interceptor(req, next).subscribe(() => {
      done();
    });
  });

  it('omits content-type for FormData bodies', (done) => {
    localStorage.setItem('token', 'token');
    const formData = new FormData();
    formData.append('file', new Blob(['a']), 'a.txt');
    const req = new HttpRequest('POST', '/upload', formData);
    const next: HttpHandlerFn = request => {
      expect(request.headers.has('Content-Type')).toBeFalse();
      return of({} as any);
    };

    interceptor(req, next).subscribe(() => done());
  });

  it('clears storage and redirects on unauthorized responses', (done) => {
    localStorage.setItem('token', 'token');
    localStorage.setItem('userId', '7');
    const req = new HttpRequest('GET', '/secure');
    const next: HttpHandlerFn = () => throwError(() => new HttpErrorResponse({ status: 401 }));

    interceptor(req, next).subscribe({
      error: () => {
        expect(localStorage.getItem('token')).toBeNull();
        expect(router.navigate).toHaveBeenCalledWith(['/login'], {
          queryParams: { redirectUrl: '/boards/1' }
        });
        done();
      }
    });
  });
});
