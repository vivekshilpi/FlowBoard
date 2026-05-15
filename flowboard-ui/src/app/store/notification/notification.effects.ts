import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { map, catchError, switchMap } from 'rxjs/operators';
import { NotificationService } from '../../core/services/notification.service';
import * as NotificationActions from './notification.actions';
import { getApiErrorMessage } from '../../core/utils/http-error';

@Injectable()
export class NotificationEffects {
  private actions$ = inject(Actions);
  private notificationService = inject(NotificationService);

  loadNotifications$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.loadNotifications),
      switchMap(({ limit }) =>
        this.notificationService.getNotifications(limit).pipe(
          map(notifications => NotificationActions.loadNotificationsSuccess({ notifications })),
          catchError(error => of(NotificationActions.loadNotificationsFailure({
            error: getApiErrorMessage(error, 'Failed to load notifications')
          })))
        )
      )
    )
  );

  markAsRead$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.markAsRead),
      switchMap(({ id }) =>
        this.notificationService.markAsRead(id).pipe(
          map(notification => NotificationActions.markAsReadSuccess({ notification })),
          catchError(error => of(NotificationActions.markAsReadFailure({
            error: getApiErrorMessage(error, 'Failed to mark notification as read')
          })))
        )
      )
    )
  );

  markAsReadSuccessReload$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.markAsReadSuccess),
      map(() => NotificationActions.loadNotifications({}))
    )
  );

  markAsReadFailureReload$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.markAsReadFailure),
      map(() => NotificationActions.loadNotifications({}))
    )
  );

  markAllAsRead$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.markAllAsRead),
      switchMap(() =>
        this.notificationService.markAllAsRead().pipe(
          map(() => NotificationActions.markAllAsReadSuccess()),
          catchError(error => of(NotificationActions.markAllAsReadFailure({
            error: getApiErrorMessage(error, 'Failed to mark all notifications as read')
          })))
        )
      )
    )
  );

  markAllAsReadSuccessReload$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.markAllAsReadSuccess),
      map(() => NotificationActions.loadNotifications({}))
    )
  );

  markAllAsReadFailureReload$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.markAllAsReadFailure),
      map(() => NotificationActions.loadNotifications({}))
    )
  );

  deleteNotification$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.deleteNotification),
      switchMap(({ id }) =>
        this.notificationService.delete(id).pipe(
          map(() => NotificationActions.deleteNotificationSuccess({ id })),
          catchError(error => of(NotificationActions.deleteNotificationFailure({
            id,
            error: getApiErrorMessage(error, 'Failed to delete notification')
          })))
        )
      )
    )
  );

  deleteNotificationFailureReload$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.deleteNotificationFailure),
      map(() => NotificationActions.loadNotifications({}))
    )
  );
}
