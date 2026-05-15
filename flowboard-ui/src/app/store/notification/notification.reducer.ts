import { createReducer, on } from '@ngrx/store';
import { initialNotificationState, notificationAdapter } from './notification.state';
import * as NotificationActions from './notification.actions';
import { Notification } from '../../core/models/notification.model';

export const notificationReducer = createReducer(
  initialNotificationState,
  on(NotificationActions.loadNotifications, state => ({
    ...state,
    loading: true,
    error: null
  })),
  on(NotificationActions.loadNotificationsSuccess, (state, { notifications }) => {
    const unreadCount = notifications.filter(n => !n.isRead).length;
    return notificationAdapter.setAll(notifications, { ...state, unreadCount, loading: false });
  }),
  on(NotificationActions.loadNotificationsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error
  })),
  on(NotificationActions.addNotification, (state, { notification }) => {
    const unreadCount = state.unreadCount + (notification.isRead ? 0 : 1);
    return notificationAdapter.addOne(notification, { ...state, unreadCount });
  }),
  on(NotificationActions.markAsRead, (state, { id }) => {
    const notification = state.entities[id];
    const unreadCount = state.unreadCount - (notification && !notification.isRead ? 1 : 0);
    return notificationAdapter.updateOne({ id, changes: { isRead: true } }, { ...state, unreadCount });
  }),
  on(NotificationActions.markAsReadSuccess, (state, { notification }) => {
    const notifications: Notification[] = Object.values({
      ...state.entities,
      [notification.id]: notification
    }).filter((value): value is Notification => !!value);
    const unreadCount = notifications.filter(item => !item.isRead).length;
    return notificationAdapter.upsertOne(notification, { ...state, unreadCount });
  }),
  on(NotificationActions.markAsReadFailure, (state, { error }) => ({
    ...state,
    error
  })),
  on(NotificationActions.markAllAsRead, state => {
    const updated = Object.values(state.entities)
      .filter((value): value is NonNullable<typeof value> => !!value)
      .map(notification => ({
        ...notification,
        isRead: true,
        readAt: notification.readAt ?? new Date().toISOString()
      }));

    return notificationAdapter.setAll(updated, {
      ...state,
      unreadCount: 0,
      error: null
    });
  }),
  on(NotificationActions.markAllAsReadFailure, (state, { error }) => ({
    ...state,
    error
  })),
  on(NotificationActions.deleteNotification, (state, { id }) => {
    const notification = state.entities[id];
    const unreadCount = state.unreadCount - (notification && !notification.isRead ? 1 : 0);
    return notificationAdapter.removeOne(id, { ...state, unreadCount, error: null });
  }),
  on(NotificationActions.deleteNotificationFailure, (state, { error }) => ({
    ...state,
    error
  }))
);
