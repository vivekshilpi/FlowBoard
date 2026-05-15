import { Component, OnDestroy, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable, Subject, interval, startWith, takeUntil } from 'rxjs';
import { Store } from '@ngrx/store';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatBadgeModule } from '@angular/material/badge';
import { MatDividerModule } from '@angular/material/divider';
import { Router, RouterModule } from '@angular/router';

import * as NotificationActions from '../../../store/notification/notification.actions';
import * as NotificationSelectors from '../../../store/notification/notification.selectors';
import { Notification } from '../../../core/models/notification.model';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';

@Component({
  selector: 'app-notification-center',
  standalone: true,
  imports: [
    CommonModule, 
    RouterModule,
    MatButtonModule, 
    MatIconModule, 
    MatMenuModule, 
    MatBadgeModule,
    MatDividerModule
  ],
  templateUrl: './notification-center.component.html',
  styleUrl: './notification-center.component.scss'
})
export class NotificationCenterComponent implements OnInit, OnDestroy {
  private store = inject(Store);
  private router = inject(Router);
  private destroy$ = new Subject<void>();
  private readonly pageChunkSize = 12;
  private suppressPollingUntil = 0;
  private dialog = inject(DialogService);

  notifications$: Observable<Notification[]> = this.store.select(NotificationSelectors.selectAllNotifications);
  unreadCount$:   Observable<number>        = this.store.select(NotificationSelectors.selectUnreadCount);
  loading$:       Observable<boolean>       = this.store.select(NotificationSelectors.selectNotificationLoading);
  visiblePageItems = this.pageChunkSize;

  ngOnInit(): void {
    interval(20000)
      .pipe(startWith(0), takeUntil(this.destroy$))
      .subscribe(() => {
        if (Date.now() < this.suppressPollingUntil) {
          return;
        }
        this.store.dispatch(NotificationActions.loadNotifications({}));
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  markAsRead(n: Notification): void {
    if (!n.isRead) {
      this.store.dispatch(NotificationActions.markAsRead({ id: n.id }));
    }
  }

  markAllAsRead(): void {
    this.suppressPollingUntil = Date.now() + 3000;
    this.store.dispatch(NotificationActions.markAllAsRead());
  }

  async deleteNotification(id: number, event: Event): Promise<void> {
    event.stopPropagation();
    const confirmed = await this.dialog.confirm({
      title: 'Delete notification?',
      description: 'This notification will be removed from your inbox.',
      confirmText: 'Delete notification',
      cancelText: 'Keep notification',
      variant: 'danger'
    });
    if (!confirmed) {
      return;
    }
    this.store.dispatch(NotificationActions.deleteNotification({ id }));
  }

  openNotification(n: Notification): void {
    this.markAsRead(n);
    this.router.navigate(['/notifications', n.id]);
  }

  getTypeIcon(type: string): string {
    switch(type) {
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

  formatRelativeTime(dateString: string): string {
    const date = new Date(dateString).getTime();
    const diffMs = Date.now() - date;
    const minutes = Math.max(1, Math.floor(diffMs / 60000));

    if (minutes < 60) return `${minutes}m ago`;

    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;

    const days = Math.floor(hours / 24);
    if (days < 7) return `${days}d ago`;

    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric'
    }).format(new Date(dateString));
  }

  formatAbsoluteTime(dateString: string): string {
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit'
    }).format(new Date(dateString));
  }

  canLoadMore(totalNotifications: number): boolean {
    return this.isNotificationsRoute() && totalNotifications > this.visiblePageItems;
  }

  loadMore(): void {
    this.visiblePageItems += this.pageChunkSize;
  }

  isNotificationsRoute(): boolean {
    return this.router.url.startsWith('/notifications');
  }
}
