export type NotificationType =
  | 'ASSIGNMENT' | 'MENTION' | 'DUE_DATE'
  | 'COMMENT' | 'MOVE' | 'BROADCAST' | 'OVERDUE'
  | 'WORKSPACE_INVITE_ACCEPTED' | 'WORKSPACE_INVITE_REJECTED'
  | 'WORKSPACE_INVITE';

export interface Notification {
  id: number;
  recipientId: number;
  actorId: number | null;
  type: NotificationType;
  title: string;
  message: string;
  relatedId: number | null;
  relatedType: string | null;
  deepLinkUrl: string | null;
  isRead: boolean;
  createdAt: string;
  readAt: string | null;
}
