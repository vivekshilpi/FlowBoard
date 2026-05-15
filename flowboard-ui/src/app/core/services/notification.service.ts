import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { Notification } from '../models/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationService {

  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/notifications`;

  private _unreadCount = new BehaviorSubject<number>(0);
  unreadCount$ = this._unreadCount.asObservable();

  getNotifications(limit?: number): Observable<Notification[]> {
    const suffix = limit ? `?limit=${limit}` : '';
    return this.http.get<unknown[]>(`${this.base}${suffix}`).pipe(
      map(notifications => notifications.map(notification => this.normalizeNotification(notification)))
    );
  }

  getById(id: number): Observable<Notification> {
    return this.http.get<unknown>(`${this.base}/${id}`).pipe(
      map(notification => this.normalizeNotification(notification))
    );
  }

  // Legacy alias for getNotifications
  getAll(limit?: number): Observable<Notification[]> {
    return this.getNotifications(limit);
  }

  getUnread(): Observable<Notification[]> {
    return this.http.get<unknown[]>(`${this.base}/unread`).pipe(
      map(notifications => notifications.map(notification => this.normalizeNotification(notification)))
    );
  }

  refreshUnreadCount(): void {
    this.http.get<number>(`${this.base}/unread/count`).subscribe({
      next: c => this._unreadCount.next(c),
      error: () => {}
    });
  }

  markAsRead(id: number): Observable<Notification> {
    return this.http.put<unknown>(
      `${this.base}/${id}/read`, {}
    ).pipe(map(notification => this.normalizeNotification(notification)));
  }

  markAllAsRead(): Observable<string> {
    return this.http.put(
      `${this.base}/read/all`, {},
      { responseType: 'text' }
    );
  }

  delete(id: number): Observable<string> {
    return this.http.delete(`${this.base}/${id}`,
      { responseType: 'text' }
    );
  }

  deleteRead(): Observable<string> {
    return this.http.delete(`${this.base}/read/all`,
      { responseType: 'text' });
  }

  private normalizeNotification(notification: any): Notification {
    return {
      ...notification,
      isRead: notification?.isRead ?? notification?.read ?? false,
      readAt: notification?.readAt ?? null
    } as Notification;
  }
}
