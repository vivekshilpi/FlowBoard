import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse, LoginRequest, RegisterRequest,
  UserProfile, UpdateProfileRequest, ChangePasswordRequest, ProfileActivityLog, UpdateThemePreferenceRequest
} from '../models/user.model';
import { clearAuthStorage } from '../utils/auth-storage';
import { getApiErrorMessage } from '../utils/http-error';
import type { ThemePreference } from './theme.service';


interface JwtPayload {
  userId?: string | number;
  role?: string;
  email?: string;
  exp?: number;
  iat?: number;
}

@Injectable({ providedIn: 'root' })
export class AuthService {

  private http   = inject(HttpClient);
  private router = inject(Router);
  private base   = `${environment.apiUrl}/auth`;

  register(data: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/register`, data);
  }

  verifyEmail(email: string, otp: string): Observable<string> {
    return this.http.post(
      `${this.base}/verify-email`, { email, otp },
      { responseType: 'text' }
    );
  }

  resendVerification(email: string): Observable<string> {
    return this.http.post(
      `${this.base}/resend-verification`,
      {},
      {
        params: new HttpParams().set('email', email),
        responseType: 'text'
      }
    );
  }

  login(data: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/login`, data).pipe(
      tap(res => {
        if (res.token) {
          localStorage.setItem('token', res.token);
          const payload = this.decodePayload(res.token);
          localStorage.setItem('userId', String(payload.userId ?? ''));
          localStorage.setItem('userRole', payload.role ?? 'MEMBER');
          localStorage.setItem('userEmail', payload.email ?? '');
        }
      })
    );
  }

  logout(): void {
    this.http.post(
      `${this.base}/logout`, {},
      { responseType: 'text' }
    ).subscribe({ error: () => {} });
    clearAuthStorage();
    this.router.navigate(['/login']);
  }

  forgotPassword(email: string): Observable<string> {
    return this.http.post(
      `${this.base}/forgot-password`, { email },
      { responseType: 'text' }
    );
  }

  resetPassword(
    email: string, otp: string, newPassword: string
  ): Observable<string> {
    return this.http.post(
      `${this.base}/reset-password`,
      { email, otp, newPassword },
      { responseType: 'text' }
    );
  }

  getProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.base}/profile`).pipe(
      tap(user => this.persistUserProfile(user))
    );
  }

  getProfileActivity(): Observable<ProfileActivityLog[]> {
    return this.http.get<ProfileActivityLog[]>(`${this.base}/profile/activity`);
  }

  updateProfile(data: UpdateProfileRequest): Observable<UserProfile> {
    return this.http.put<UserProfile>(`${this.base}/profile`, data).pipe(
      tap(user => this.persistUserProfile(user))
    );
  }

  updateThemePreference(themePreference: ThemePreference): Observable<UserProfile> {
    const request: UpdateThemePreferenceRequest = { themePreference };
    return this.http.put<UserProfile>(`${this.base}/profile/theme`, request).pipe(
      tap(user => this.persistUserProfile(user))
    );
  }

  uploadAvatar(file: File): Observable<UserProfile> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<UserProfile>(
      `${this.base}/avatar`, formData
    ).pipe(
      tap(user => this.persistUserProfile(user))
    );
  }

  changePassword(data: ChangePasswordRequest): Observable<string> {
    return this.http.put(
      `${this.base}/password`, data, { responseType: 'text' }
    );
  }

  searchUsers(key: string): Observable<UserProfile[]> {
    return this.http.get<UserProfile[]>(
      `${this.base}/search`,
      { params: new HttpParams().set('key', key) }
    );
  }

  getErrorMessage(error: unknown, fallback: string): string {
    return getApiErrorMessage(error, fallback);
  }

  isLoggedIn(): boolean  { return !!localStorage.getItem('token'); }
  getToken(): string | null { return localStorage.getItem('token'); }
  getUserId(): number    { return Number(localStorage.getItem('userId')); }
  getRole(): string      { return localStorage.getItem('userRole') ?? 'MEMBER'; }
  isAdmin(): boolean     { return this.getRole() === 'PLATFORM_ADMIN'; }
  getApiOrigin(): string { return new URL(environment.apiUrl).origin; }
  resolveAssetUrl(url: string | null | undefined): string {
    if (!url) {
      return '';
    }
    if (url.startsWith('http') || url.startsWith('data:')) {
      return url;
    }
    return `${this.getApiOrigin()}${url}`;
  }

  private decodePayload(token: string): JwtPayload {
    try {
      return JSON.parse(atob(token.split('.')[1]));
    } catch {
      return {};
    }
  }

  private persistUserProfile(user: UserProfile): void {
    localStorage.setItem('userId', String(user.id));
    localStorage.setItem('userRole', user.role);
    localStorage.setItem('userEmail', user.email);
    localStorage.setItem('userName', user.fullName);
    localStorage.setItem('userAvatarUrl', user.avatarUrl ?? '');
  }
}
