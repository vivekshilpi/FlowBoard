import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, Renderer2, RendererFactory2, computed, inject, signal } from '@angular/core';
import { EMPTY } from 'rxjs';
import { catchError, take } from 'rxjs/operators';
import { UserProfile } from '../models/user.model';
import { AuthService } from './auth.service';

export type ThemePreference = 'light' | 'dark';

const THEME_STORAGE_KEY = 'flowboard.theme';
const DARK_MEDIA_QUERY = '(prefers-color-scheme: dark)';

function isThemePreference(value: unknown): value is ThemePreference {
  return value === 'light' || value === 'dark';
}

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly authService = inject(AuthService);
  private readonly renderer: Renderer2 = inject(RendererFactory2).createRenderer(null, null);

  private readonly currentThemeSignal = signal<ThemePreference>('light');
  private initialized = false;
  private mediaQueryList: MediaQueryList | null = null;
  private mediaQueryListener: ((event: MediaQueryListEvent) => void) | null = null;
  private lastSyncedUserId: number | null = null;
  private lastSyncedTheme: ThemePreference | null = null;

  readonly theme = this.currentThemeSignal.asReadonly();
  readonly isDark = computed(() => this.currentThemeSignal() === 'dark');

  initialize(): void {
    if (this.initialized || !isPlatformBrowser(this.platformId)) {
      return;
    }

    this.initialized = true;

    const storedTheme = this.readStoredTheme();
    const initialTheme = storedTheme ?? this.getSystemTheme();

    this.applyTheme(initialTheme, false);
    this.watchSystemPreferenceChanges();
  }

  toggleTheme(): void {
    this.setTheme(this.isDark() ? 'light' : 'dark');
  }

  setTheme(theme: ThemePreference, syncRemote = true): void {
    this.applyTheme(theme, true);

    if (syncRemote) {
      this.persistThemePreference(theme);
    }
  }

  syncWithProfile(user: UserProfile | null): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    if (!user) {
      this.lastSyncedUserId = null;
      this.lastSyncedTheme = null;
      return;
    }

    const profileTheme = isThemePreference(user.themePreference) ? user.themePreference : null;

    if (profileTheme) {
      this.lastSyncedUserId = user.id;
      this.lastSyncedTheme = profileTheme;

      if (this.currentThemeSignal() !== profileTheme || this.readStoredTheme() !== profileTheme) {
        this.applyTheme(profileTheme, true);
      }
      return;
    }

    if (this.lastSyncedUserId !== user.id || this.lastSyncedTheme === null) {
      this.lastSyncedUserId = user.id;
      this.persistThemePreference(this.currentThemeSignal());
    }
  }

  private applyTheme(theme: ThemePreference, persist: boolean): void {
    this.currentThemeSignal.set(theme);
    this.renderer.setAttribute(this.document.documentElement, 'data-theme', theme);

    if (persist) {
      localStorage.setItem(THEME_STORAGE_KEY, theme);
    }
  }

  private persistThemePreference(theme: ThemePreference): void {
    if (!this.authService.isLoggedIn()) {
      return;
    }

    if (this.lastSyncedUserId !== null && this.lastSyncedTheme === theme) {
      return;
    }

    this.authService.updateThemePreference(theme).pipe(
      take(1),
      catchError(() => EMPTY)
    ).subscribe((user) => {
      const resolvedTheme = isThemePreference(user.themePreference) ? user.themePreference : theme;
      this.lastSyncedUserId = user.id;
      this.lastSyncedTheme = resolvedTheme;
      this.applyTheme(resolvedTheme, true);
    });
  }

  private readStoredTheme(): ThemePreference | null {
    const storedTheme = localStorage.getItem(THEME_STORAGE_KEY);
    return isThemePreference(storedTheme) ? storedTheme : null;
  }

  private getSystemTheme(): ThemePreference {
    return window.matchMedia(DARK_MEDIA_QUERY).matches ? 'dark' : 'light';
  }

  private watchSystemPreferenceChanges(): void {
    this.mediaQueryList = window.matchMedia(DARK_MEDIA_QUERY);
    this.mediaQueryListener = (event: MediaQueryListEvent) => {
      if (this.readStoredTheme()) {
        return;
      }

      this.applyTheme(event.matches ? 'dark' : 'light', false);
    };

    if (typeof this.mediaQueryList.addEventListener === 'function') {
      this.mediaQueryList.addEventListener('change', this.mediaQueryListener);
      return;
    }

    this.mediaQueryList.addListener(this.mediaQueryListener);
  }
}
