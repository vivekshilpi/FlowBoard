export interface BoardRealtimeEvent {
  boardId: number;
  workspaceId?: number | null;
  entityType: string;
  action: string;
  entityId?: number | null;
  actorUserId?: number | null;
  occurredAt: string;
}
