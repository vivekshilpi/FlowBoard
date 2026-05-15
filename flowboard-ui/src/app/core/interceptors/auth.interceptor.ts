import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const token  = localStorage.getItem('token');
  const userId = localStorage.getItem('userId');
  const userEmail = localStorage.getItem('userEmail');
  const userRole = localStorage.getItem('userRole');

  let authReq = req;

  if (token) {
    const isFormData = req.body instanceof FormData;

    const headers: Record<string, string> ={
      "Authorization": `Bearer ${token}`,
      "X-User-Id": userId ?? '',
      "X-User-Email": userEmail ?? '',
      "X-User-Role": userRole ?? '',
    };

    if(!isFormData){
      headers['Content-Type'] = 'application/json';
    }

    authReq = req.clone({ setHeaders: headers});
  }

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        localStorage.clear();
        const redirectUrl = router.url && router.url !== '/login' ? router.url : '/dashboard';
        router.navigate(['/login'], { queryParams: { redirectUrl } });
      }
      return throwError(() => err);
    })
  );
};
