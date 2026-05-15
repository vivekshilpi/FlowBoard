import { Component, EventEmitter, inject, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { NotificationService } from '../../../core/services/notification.service';
import { Notification } from '../../../core/models/notification.model';

@Component({
  selector: 'app-notification-dropdown',
  standalone: true,
  imports: [
    CommonModule, MatIconModule, MatButtonModule,
    MatDividerModule, MatProgressSpinnerModule
  ],
  templateUrl: './notification-dropdown.component.html',
  styleUrl: './notification-dropdown.component.scss'
})
export class NotificationDropdownComponent implements OnInit {

  private notifService = inject(NotificationService);

  @Output() closed = new EventEmitter<void>();

  notifications: Notification[] = [];
  loading = false;

  get unreadCount() {
    return this.notifications.filter(n => !n.isRead).length;
  }

  ngOnInit(): void {
    this.loadNotifications();
  }

  loadNotifications(): void {
    this.loading = true;
    this.notifService.getAll().subscribe({
      next: (n: Notification[]) => {
        this.notifications = n.slice(0, 10); // Show last 10
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  markAsRead(n: Notification): void {
    if (n.isRead) return;
    this.notifService.markAsRead(n.id).subscribe({
      next: updated => {
        const index = this.notifications.findIndex(x => x.id === n.id);
        if (index !== -1) this.notifications[index] = updated;
      }
    });
  }

  markAllAsRead(): void {
    this.notifService.markAllAsRead().subscribe({
      next: () => {
        this.notifications.forEach(n => n.isRead = true);
      }
    });
  }

  getIcon(type: string): string {
    switch (type) {
      case 'ASSIGNMENT': return 'person_add';
      case 'MENTION':    return 'alternate_email';
      case 'DUE_DATE':   return 'schedule';
      case 'OVERDUE':    return 'priority_high';
      case 'WORKSPACE_INVITE': return 'group_add';
      case 'WORKSPACE_INVITE_ACCEPTED': return 'task_alt';
      case 'WORKSPACE_INVITE_REJECTED': return 'block';
      default:           return 'notifications';
    }
  }

  close(): void {
    this.closed.emit();
  }
}
