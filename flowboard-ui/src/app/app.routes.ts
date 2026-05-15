import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';
import { publicOnlyGuard } from './core/guards/public-only.guard';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/landing/home.component').then(m => m.HomeComponent) },
  {
    path: 'public/board/:id',
    loadComponent: () => import('./features/board/board-view/board-view.component').then(m => m.BoardViewComponent),
    data: { publicView: true }
  },
  { path: 'login', canActivate: [publicOnlyGuard], loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent) },
  { path: 'register', canActivate: [publicOnlyGuard], loadComponent: () => import('./features/auth/register/register.component').then(m => m.RegisterComponent) },
  { path: 'verify-email', canActivate: [publicOnlyGuard], loadComponent: () => import('./features/auth/verify-email/verify-email.component').then(m => m.VerifyEmailComponent) },
  { path: 'forgot-password', canActivate: [publicOnlyGuard], loadComponent: () => import('./features/auth/forgot-password/forgot-password.component').then(m => m.ForgotPasswordComponent) },
  { path: 'reset-password', canActivate: [publicOnlyGuard], loadComponent: () => import('./features/auth/reset-password/reset-password.component').then(m => m.ResetPasswordComponent) },
  { path: 'oauth2/callback', loadComponent: () => import('./features/auth/oauth2-callback/oauth2-callback.component').then(m => m.Oauth2CallbackComponent) },

  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layouts/main-layout/main-layout.component').then(m => m.MainLayoutComponent),
    children: [
      { path: 'dashboard', loadComponent: () => import('./features/dashboard/dashboard/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'workspace/:id', loadComponent: () => import('./features/workspace/workspace-detail/workspace-detail.component').then(m => m.WorkspaceDetailComponent) },
      { path: 'invitations/:id', loadComponent: () => import('./features/invitations/invitation-detail/invitation-detail.component').then(m => m.InvitationDetailComponent) },
      { path: 'board/:id', loadComponent: () => import('./features/board/board-view/board-view.component').then(m => m.BoardViewComponent) },
      { path: 'notifications', loadComponent: () => import('./features/notification/notification-center/notification-center.component').then(m => m.NotificationCenterComponent) },
      { path: 'notifications/:id', loadComponent: () => import('./features/notification/notification-detail/notification-detail.component').then(m => m.NotificationDetailComponent) },
      { path: 'profile', loadComponent: () => import('./features/profile/profile-view/profile-view.component').then(m => m.ProfileViewComponent) },
      {
        path: 'admin',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/admin/admin-panel/admin-panel.component').then(m => m.AdminPanelComponent)
      },
      { path: 'pricing', loadComponent: () => import('./features/payment/pricing/pricing.component').then(m => m.PricingComponent) },
      { path: 'payment/success', loadComponent: () => import('./features/payment/payment-success/payment-success.component').then(m => m.PaymentSuccessComponent) },
      { path: 'payment/cancel', loadComponent: () => import('./features/payment/payment-cancel/payment-cancel.component').then(m => m.PaymentCancelComponent) },
    ]
  },
  { path: '**', loadComponent: () => import('./core/routing/route-fallback.component').then(m => m.RouteFallbackComponent) }
];
