import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Workspace, CreateWorkspaceRequest, UpdateWorkspaceRequest
} from '../models/workspace.model';
import {
  InvitationActionResponse,
  InvitationDetails,
  WorkspaceInvitation
} from '../models/invitation.model';

@Injectable({ providedIn: 'root' })
export class WorkspaceService {

  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/workspaces`;

  getByMember(userId: number): Observable<Workspace[]> {
    return this.http.get<Workspace[]>(`${this.base}/member/${userId}`);
  }

  getByOwner(userId: number): Observable<Workspace[]> {
    return this.http.get<Workspace[]>(`${this.base}/owner/${userId}`);
  }

  getPublic(): Observable<Workspace[]> {
    return this.http.get<Workspace[]>(`${this.base}/public`);
  }

  getById(id: number): Observable<Workspace> {
    return this.http.get<Workspace>(`${this.base}/${id}`);
  }

  create(data: CreateWorkspaceRequest): Observable<Workspace> {
    return this.http.post<Workspace>(this.base, data);
  }

  update(id: number, data: UpdateWorkspaceRequest): Observable<Workspace> {
    return this.http.put<Workspace>(`${this.base}/${id}`, data);
  }

  delete(id: number): Observable<string> {
    return this.http.delete(`${this.base}/${id}`,
      { responseType: 'text' });
  }

  getMembers(id: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/${id}/members`);
  }

  addMember(workspaceId: number,
    userId: number, role: string): Observable<any> {
    return this.http.post(
      `${this.base}/${workspaceId}/members`, { userId, role }
    );
  }

  removeMember(workspaceId: number, userId: number): Observable<string> {
    return this.http.delete(
      `${this.base}/${workspaceId}/members/${userId}`,
      { responseType: 'text' }
    );
  }

  updateMemberRole(workspaceId: number,
    userId: number, role: string): Observable<string> {
    return this.http.put(
      `${this.base}/${workspaceId}/members/${userId}/role`,
      { role }, { responseType: 'text' }
    );
  }

  inviteMember(workspaceId: number, email: string, role: string): Observable<string> {
    return this.http.post(
      `${this.base}/${workspaceId}/invite`,
      { email, role },
      { responseType: 'text' }
    );
  }

  acceptInvitation(token: string): Observable<string> {
    return this.http.get(
      `${this.base}/invite/accept`,
      {
        params: new HttpParams().set('token', token),
        responseType: 'text'
      }
    );
  }

  getInvitationDetails(id: number): Observable<InvitationDetails> {
    return this.http.get<InvitationDetails>(`${this.base}/invite/${id}`);
  }

  acceptInvitationById(id: number): Observable<InvitationActionResponse> {
    return this.http.post<InvitationActionResponse>(
      `${this.base}/invite/${id}/accept`, {}
    );
  }

  rejectInvitation(id: number): Observable<InvitationActionResponse> {
    return this.http.post<InvitationActionResponse>(
      `${this.base}/invite/${id}/reject`, {}
    );
  }

  revokeInvitation(workspaceId: number, invitationId: number): Observable<string> {
    return this.http.delete(
      `${this.base}/${workspaceId}/invitations/${invitationId}`,
      { responseType: 'text' }
    );
  }

  getPendingInvitations(workspaceId: number): Observable<WorkspaceInvitation[]> {
    return this.http.get<WorkspaceInvitation[]>(`${this.base}/${workspaceId}/invitations`);
  }

  search(keyword: string): Observable<Workspace[]> {
    return this.http.get<Workspace[]>(`${this.base}/search`, {
      params: new HttpParams().set('keyword', keyword)
    });
  }
}
