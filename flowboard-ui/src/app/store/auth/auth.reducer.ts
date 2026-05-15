import { createReducer, on } from '@ngrx/store';
import { initialAuthState } from './auth.state';
import * as AuthActions from './auth.actions';
import { clearAuthStorage } from '../../core/utils/auth-storage';

export const authReducer = createReducer(
  initialAuthState,
  on(AuthActions.login, state => ({
    ...state,
    loading: true,
    error: null
  })),
  on(AuthActions.loginSuccess, (state, { response }) => ({
    ...state,
    token: response.token,
    isAuthenticated: true,
    loading: false,
    error: null
  })),
  on(AuthActions.loginFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error
  })),
  on(AuthActions.logout, state => {
    clearAuthStorage();
    return {
      ...initialAuthState,
      token: null,
      isAuthenticated: false
    };
  }),
  on(AuthActions.getProfileSuccess, (state, { user }) => ({
    ...state,
    user,
    loading: false
  })),
  on(AuthActions.getProfileFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  }))
);
