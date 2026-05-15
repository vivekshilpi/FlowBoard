import { Component, DestroyRef, HostListener, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterModule, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { PaymentService } from '../../core/services/payment.service';
import { NotificationCenterComponent } from '../../features/notification/notification-center/notification-center.component';
import * as AuthSelectors from '../../store/auth/auth.selectors';
import * as AuthActions from '../../store/auth/auth.actions';
import { UserProfile } from '../../core/models/user.model';
import { ThemeService } from '../../core/services/theme.service';
import { SearchComponent } from '../../features/dashboard/search/search.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [
    CommonModule, RouterModule, RouterOutlet,
    MatButtonModule, MatIconModule, MatMenuModule,
    MatDividerModule, MatTooltipModule, NotificationCenterComponent, SearchComponent
  ],
  templateUrl: './main-layout.component.html',
  styleUrl: './main-layout.component.scss'
})
export class MainLayoutComponent implements OnInit {
  readonly authService    = inject(AuthService);
  private store           = inject(Store);
  private destroyRef      = inject(DestroyRef);
  private paymentService  = inject(PaymentService);
  readonly themeService   = inject(ThemeService);
  readonly sidebarCollapsed = signal(false);
  readonly mobileSidebarOpen = signal(false);
  readonly isDesktop = signal(true);

  user$: Observable<UserProfile | null> = this.store.select(AuthSelectors.selectUser);

  planName = computed(() => {
    const p = this.paymentService.currentPlan();
    return p?.planDisplayName ?? 'Free';
  });

  isFreeUser = computed(() => {
    const p = this.paymentService.currentPlan();
    return !p || p.planName === 'FREE';
  });

  ngOnInit() {
    this.syncViewport();
    this.store.dispatch(AuthActions.getProfile());
    this.paymentService.getSubscription().subscribe();
    this.user$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((user) => this.themeService.syncWithProfile(user));
  }

  @HostListener('window:resize')
  onWindowResize() {
    this.syncViewport();
  }

  toggleTheme() {
    this.themeService.toggleTheme();
  }

  toggleSidebar() {
    if (this.isDesktop()) {
      this.sidebarCollapsed.update((value) => !value);
      return;
    }

    this.mobileSidebarOpen.update((value) => !value);
  }

  closeMobileSidebar() {
    if (!this.isDesktop()) {
      this.mobileSidebarOpen.set(false);
    }
  }

  isSidebarVisible() {
    return this.isDesktop() || this.mobileSidebarOpen();
  }

  logout() {
    this.closeMobileSidebar();
    this.store.dispatch(AuthActions.logout());
  }

  getAvatarUrl(avatarUrl: string | null | undefined): string {
    return this.authService.resolveAssetUrl(avatarUrl);
  }

  getInitials(fullName: string | null | undefined): string {
    return (fullName || 'User')
      .split(' ')
      .map(part => part[0] ?? '')
      .join('')
      .toUpperCase()
      .slice(0, 2);
  }

  private syncViewport() {
    const isDesktop = window.innerWidth >= 1024;
    this.isDesktop.set(isDesktop);

    if (isDesktop) {
      this.mobileSidebarOpen.set(false);
    }
  }
}
