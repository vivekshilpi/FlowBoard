import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subject, takeUntil } from 'rxjs';
import { Store } from '@ngrx/store';
import { Notification } from '../../../core/models/notification.model';
import { NotificationService } from '../../../core/services/notification.service';
import * as NotificationActions from '../../../store/notification/notification.actions';

@Component({
  selector: 'app-notification-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  templateUrl: './notification-detail.component.html',
  styleUrl: './notification-detail.component.scss'
})
export class NotificationDetailComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private notificationService = inject(NotificationService);
  private snack = inject(MatSnackBar);
  private store = inject(Store);
  private destroy$ = new Subject<void>();

  notification: Notification | null = null;
  loading = true;

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      const id = Number(params.get('id'));
      if (!id) {
        this.loading = false;
        this.snack.open('Notification not found', 'Close', { duration: 3000 });
        this.router.navigate(['/notifications']);
        return;
      }

      this.loadNotification(id);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  goBack(): void {
    this.router.navigate(['/notifications']);
  }

  openRelated(): void {
    if (!this.notification?.deepLinkUrl) {
      return;
    }
    this.router.navigateByUrl(this.notification.deepLinkUrl);
  }

  getTypeIcon(type: string): string {
    switch (type) {
      case 'ASSIGNMENT': return 'person_add';
      case 'DUE_DATE': return 'event';
      case 'OVERDUE': return 'error';
      case 'COMMENT': return 'comment';
      case 'WORKSPACE_INVITE': return 'mail';
      case 'WORKSPACE_INVITE_ACCEPTED': return 'task_alt';
      case 'WORKSPACE_INVITE_REJECTED': return 'block';
      default: return 'notifications';
    }
  }

  getCategoryLabel(type: string): string {
    if (type.startsWith('WORKSPACE_INVITE')) {
      return 'Invite';
    }

    switch (type) {
      case 'ASSIGNMENT': return 'Assignment';
      case 'MENTION': return 'Mention';
      case 'DUE_DATE':
      case 'OVERDUE': return 'Deadline';
      case 'COMMENT': return 'Comment';
      case 'MOVE': return 'Activity';
      default: return 'System';
    }
  }

  getPrimaryActionLabel(notification: Notification): string {
    if (!notification.deepLinkUrl) {
      return '';
    }
    if (notification.deepLinkUrl.startsWith('/invitations/')) {
      return 'View invitation';
    }
    if (notification.deepLinkUrl.startsWith('/workspace/')) {
      return 'View workspace';
    }
    if (notification.deepLinkUrl.startsWith('/board/')) {
      return 'Open board';
    }
    if (notification.deepLinkUrl.startsWith('/cards/')) {
      return 'Open card';
    }
    return 'Open related item';
  }

  private loadNotification(id: number): void {
    this.loading = true;
    this.notificationService.getById(id).subscribe({
      next: notification => {
        this.notification = notification;
        this.loading = false;
        if (!notification.isRead) {
          this.store.dispatch(NotificationActions.markAsRead({ id: notification.id }));
          this.notification = {
            ...notification,
            isRead: true,
            readAt: notification.readAt ?? new Date().toISOString()
          };
        }
      },
      error: err => {
        this.loading = false;
        this.snack.open(err.error?.message ?? 'Failed to load notification', 'Close', {
          duration: 4000
        });
        this.router.navigate(['/notifications']);
      }
    });
  }
}
