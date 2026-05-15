import { createFeatureSelector, createSelector } from '@ngrx/store';
import { NotificationState, notificationAdapter } from './notification.state';

export const selectNotificationState = createFeatureSelector<NotificationState>('notification');

const {
  selectAll,
} = notificationAdapter.getSelectors(selectNotificationState);

export const selectAllNotifications = selectAll;

export const selectUnreadCount = createSelector(
  selectNotificationState,
  (state: NotificationState) => state.unreadCount
);

export const selectNotificationLoading = createSelector(
  selectNotificationState,
  (state: NotificationState) => state.loading
);
