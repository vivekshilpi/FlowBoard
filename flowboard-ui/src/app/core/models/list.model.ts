export interface TaskList {
  id: number;
  boardId: number;
  name: string;
  position: number;
  color: string | null;
  isArchived: boolean;
  createdAt: string;
  updatedAt: string | null;
  cardCount: number;
}

export interface CreateListRequest {
  boardId: number;
  name: string;
  position?: number;
  color?: string;
}

export interface UpdateListRequest {
  name: string;
  color?: string;
}

export interface ReorderListRequest {
  boardId: number;
  orderedListIds: number[];
}