import { UserProfile } from '../../core/models/user.model';

const initialToken = typeof localStorage !== 'undefined' ? localStorage.getItem('token') : null;

export interface AuthState {
  user: UserProfile | null;
  token: string | null;
  loading: boolean;
  error: string | null;
  isAuthenticated: boolean;
}

export const initialAuthState: AuthState = {
  user: null,
  token: initialToken,
  loading: false,
  error: null,
  isAuthenticated: !!initialToken
};
