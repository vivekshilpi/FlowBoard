export interface WorkspaceMemberUser {
  id: number;
  fullName: string;
  username?: string;
  email?: string;
  avatarUrl: string | null;
}

export interface WorkspaceMember {
  userId: number;
  role: 'ADMIN' | 'MEMBER';
  joinedAt: string;
  user?: WorkspaceMemberUser;
}

export interface Workspace {
  id: number;
  name: string;
  description: string | null;
  ownerId: number;
  visibility: 'PUBLIC' | 'PRIVATE';
  logoUrl: string | null;
  createdAt: string;
  updatedAt: string | null;
  members: WorkspaceMember[];
}

export interface CreateWorkspaceRequest {
  name: string;
  description?: string;
  visibility: 'PUBLIC' | 'PRIVATE';
  logoUrl?: string;
}

export interface UpdateWorkspaceRequest {
  name: string;
  description?: string;
  visibility?: 'PUBLIC' | 'PRIVATE';
  logoUrl?: string;
}
