import { EntityState, EntityAdapter, createEntityAdapter } from '@ngrx/entity';
import { Notification } from '../../core/models/notification.model';

export interface NotificationState extends EntityState<Notification> {
  unreadCount: number;
  loading: boolean;
  error: string | null;
}

export const notificationAdapter: EntityAdapter<Notification> = createEntityAdapter<Notification>();

export const initialNotificationState: NotificationState = notificationAdapter.getInitialState({
  unreadCount: 0,
  loading: false,
  error: null
});
