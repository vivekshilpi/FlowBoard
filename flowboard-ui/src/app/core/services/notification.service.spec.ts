import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { NotificationService } from './notification.service';
import { createHttpTestingProviders } from '../../../test-helpers/test-providers';
import { environment } from '../../../environments/environment';

describe('NotificationService', () => {
  let service: NotificationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...createHttpTestingProviders()]
    });
    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('normalizes notifications from list and detail endpoints', () => {
    let notifications: any[] = [];
    let notification: any;

    service.getNotifications(5).subscribe(value => notifications = value);
    httpMock.expectOne(`${environment.apiUrl}/notifications?limit=5`).flush([{ id: 1, read: true }]);

    service.getById(1).subscribe(value => notification = value);
    httpMock.expectOne(`${environment.apiUrl}/notifications/1`).flush({ id: 1, read: false });

    service.getAll().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/notifications`).flush([]);

    service.getUnread().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/notifications/unread`).flush([{ id: 2, isRead: false }]);

    expect(notifications[0].isRead).toBeTrue();
    expect(notification.isRead).toBeFalse();
  });

  it('refreshes unread count and covers mutation endpoints', () => {
    service.refreshUnreadCount();
    httpMock.expectOne(`${environment.apiUrl}/notifications/unread/count`).flush(7);
    service.unreadCount$.subscribe(value => expect(value).toBe(7));

    service.markAsRead(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/notifications/1/read`).flush({ id: 1, read: true });

    service.markAllAsRead().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/notifications/read/all`).flush('ok');

    service.delete(1).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/notifications/1`).flush('ok');

    service.deleteRead().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/notifications/read/all`).flush('ok');
  });
});
