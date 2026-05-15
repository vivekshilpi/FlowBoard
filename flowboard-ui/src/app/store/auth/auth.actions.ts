import { createAction, props } from '@ngrx/store';
import { LoginRequest, RegisterRequest, AuthResponse, UserProfile } from '../../core/models/user.model';

export const login = createAction(
  '[Auth] Login',
  props<{ request: LoginRequest }>()
);

export const loginSuccess = createAction(
  '[Auth] Login Success',
  props<{ response: AuthResponse }>()
);

export const loginFailure = createAction(
  '[Auth] Login Failure',
  props<{ error: string }>()
);

export const register = createAction(
  '[Auth] Register',
  props<{ request: RegisterRequest }>()
);

export const registerSuccess = createAction(
  '[Auth] Register Success'
);

export const registerFailure = createAction(
  '[Auth] Register Failure',
  props<{ error: string }>()
);

export const logout = createAction('[Auth] Logout');

export const getProfile = createAction('[Auth] Get Profile');

export const getProfileSuccess = createAction(
  '[Auth] Get Profile Success',
  props<{ user: UserProfile }>()
);

export const getProfileFailure = createAction(
  '[Auth] Get Profile Failure',
  props<{ error: string }>()
);
