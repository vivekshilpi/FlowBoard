export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type CardStatus = 'TO_DO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE';

export interface ChecklistItem {
  id: string;
  text: string;
  completed: boolean;
}

export interface CardAttachment {
  id: string;
  fileName: string;
  contentType: string;
  size: number;
  downloadUrl: string;
  uploadedAt: string;
}

export interface Card {
  id: number;
  listId: number;
  boardId: number;
  title: string;
  description: string | null;
  position: number;
  priority: Priority;
  status: CardStatus;
  dueDate: string | null;
  startDate: string | null;
  assigneeId: number | null;
  createdById: number;
  isArchived: boolean;
  isOverdue: boolean;
  coverColor: string | null;
  createdAt: string;
  updatedAt: string | null;
  labels?: string[];
  checklistItems?: ChecklistItem[];
  attachments?: CardAttachment[];
}

export interface CardActivity {
  id: number;
  cardId: number;
  actorId: number;
  actionType: string;
  parentActivityId?: number | null;
  description: string;
  oldValue: string | null;
  newValue: string | null;
  createdAt: string;
}

export interface AddCardCommentRequest {
  content: string;
  parentActivityId?: number | null;
  mentionedUserIds?: number[];
}

export interface CreateCardRequest {
  listId: number;
  boardId: number;
  title: string;
  description?: string;
  priority?: Priority;
  dueDate?: string;
  startDate?: string;
  assigneeId?: number;
  coverColor?: string;
  labels?: string[];
  checklistItems?: ChecklistItem[];
}

export interface UpdateCardRequest {
  title: string;
  description?: string;
  priority?: Priority;
  status?: CardStatus;
  dueDate?: string;
  startDate?: string;
  coverColor?: string;
  labels?: string[];
  checklistItems?: ChecklistItem[];
}

export interface MoveCardRequest {
  targetListId: number;
  targetBoardId: number;
  targetPosition?: number;
}
