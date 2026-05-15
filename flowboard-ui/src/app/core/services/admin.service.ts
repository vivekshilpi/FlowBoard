import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AdminStats } from '../models/admin.model';
import { UserProfile } from '../models/user.model';
import { Workspace } from '../models/workspace.model';
import { Board } from '../models/board.model';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);
  private authBase = `${environment.apiUrl}/auth/admin`;
  private workspaceBase = `${environment.apiUrl}/workspaces`;
  private boardBase = `${environment.apiUrl}/boards`;

  getStats(): Observable<AdminStats> {
    return this.http.get<AdminStats>(`${this.authBase}/stats`);
  }

  getUsers(): Observable<UserProfile[]> {
    return this.http.get<UserProfile[]>(`${this.authBase}/users`);
  }

  getWorkspaces(): Observable<Workspace[]> {
    return this.http.get<Workspace[]>(`${this.workspaceBase}/admin`);
  }

  getBoards(): Observable<Board[]> {
    return this.http.get<Board[]>(`${this.boardBase}/admin`);
  }

  updateUserRole(userId: number, role: string): Observable<string> {
    return this.http.put(
      `${this.authBase}/users/${userId}/role`,
      { role },
      { responseType: 'text' }
    );
  }

  suspendUser(userId: number): Observable<string> {
    return this.http.put(
      `${this.authBase}/users/${userId}/suspend`,
      {},
      { responseType: 'text' }
    );
  }

  reactivateUser(userId: number): Observable<string> {
    return this.http.put(
      `${this.authBase}/users/${userId}/reactivate`,
      {},
      { responseType: 'text' }
    );
  }

  deleteUser(userId: number): Observable<string> {
    return this.http.delete(
      `${this.authBase}/users/${userId}`,
      { responseType: 'text' }
    );
  }

  deleteWorkspace(workspaceId: number): Observable<string> {
    return this.http.delete(
      `${this.workspaceBase}/${workspaceId}`,
      { responseType: 'text' }
    );
  }

  closeBoard(boardId: number): Observable<Board> {
    return this.http.put<Board>(`${this.boardBase}/${boardId}/close`, {});
  }

  reopenBoard(boardId: number): Observable<Board> {
    return this.http.put<Board>(`${this.boardBase}/${boardId}/reopen`, {});
  }

  deleteBoard(boardId: number): Observable<string> {
    return this.http.delete(
      `${this.boardBase}/${boardId}`,
      { responseType: 'text' }
    );
  }
}
