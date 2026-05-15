export interface WorkspaceInvitation {
  id: number;
  workspaceId: number;
  inviteeEmail: string;
  role: 'ADMIN' | 'MEMBER';
  token: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'REVOKED' | 'EXPIRED';
  invitedBy: number;
  createdAt: string;
  expiresAt: string;
  acceptedAt?: string | null;
}

export interface InvitationDetails {
  id: number;
  workspaceId: number;
  workspaceName: string;
  inviterId: number;
  inviterName: string;
  inviterEmail: string;
  inviteeEmail: string;
  role: 'ADMIN' | 'MEMBER';
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'REVOKED' | 'EXPIRED';
  createdAt: string;
  expiresAt: string;
  acceptedAt?: string | null;
}

export interface InvitationActionResponse {
  invitationId: number;
  workspaceId: number;
  workspaceName: string;
  status: 'ACCEPTED' | 'REJECTED';
  message: string;
}

export interface InviteMemberRequest {
  email: string;
  role: 'ADMIN' | 'MEMBER';
}
