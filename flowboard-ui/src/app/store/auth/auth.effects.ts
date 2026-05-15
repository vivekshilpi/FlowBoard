import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { map, catchError, switchMap, tap } from 'rxjs/operators';
import { AuthService } from '../../core/services/auth.service';
import * as AuthActions from './auth.actions';
import { Router } from '@angular/router';
import { clearAuthStorage } from '../../core/utils/auth-storage';
import { getApiErrorMessage } from '../../core/utils/http-error';

@Injectable()
export class AuthEffects {
  private actions$ = inject(Actions);
  private authService = inject(AuthService);
  private router = inject(Router);

  login$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.login),
      switchMap(({ request }) =>
        this.authService.login(request).pipe(
          map(response => AuthActions.loginSuccess({ response })),
          catchError(error => of(AuthActions.loginFailure({
            error: getApiErrorMessage(error, 'Login failed')
          })))
        )
      )
    )
  );

  loginSuccess$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.loginSuccess),
      tap(({ response }) => {
        if (response.token) {
          localStorage.setItem('token', response.token);

          const payload = this.decodePayload(response.token);
          if(payload?.userId){
            localStorage.setItem('userId', String(payload.userId));
          }
          if(payload?.role){
            localStorage.setItem('userRole', payload.role);
          }
        }
        this.router.navigate(['/dashboard']); 
      }),
      map(() => AuthActions.getProfile())
    )
  );

  private decodePayload(token: string): any{
    try{
      return JSON.parse(atob(token.split('.')[1]))
    }
    catch{
      return null;
    }
  }

  getProfile$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.getProfile),
      switchMap(() =>
        this.authService.getProfile().pipe(
          map(user => AuthActions.getProfileSuccess({ user })),
          catchError(error => of(AuthActions.getProfileFailure({
            error: getApiErrorMessage(error, 'Failed to load profile')
          })))
        )
      )
    )
  );

  logout$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.logout),
      tap(() => {
        clearAuthStorage();
        this.router.navigate(['/login']);
      })
    ),
    { dispatch: false }
  );
}
