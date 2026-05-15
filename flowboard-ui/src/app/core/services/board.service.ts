import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Board, CreateBoardRequest,
  UpdateBoardRequest, BoardAnalytics, PublicBoardView
} from '../models/board.model';

@Injectable({ providedIn: 'root' })
export class BoardService {

  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/boards`;

  getByWorkspace(workspaceId: number): Observable<Board[]> {
    return this.http.get<Board[]>(
      `${this.base}/workspace/${workspaceId}`);
  }

  getById(id: number): Observable<Board> {
    return this.http.get<Board>(`${this.base}/${id}`);
  }

  getPublicView(id: number): Observable<PublicBoardView> {
    return this.http.get<PublicBoardView>(`${this.base}/public/${id}`);
  }

  getByMember(userId: number): Observable<Board[]> {
    return this.http.get<Board[]>(`${this.base}/member/${userId}`);
  }

  create(data: CreateBoardRequest): Observable<Board> {
    return this.http.post<Board>(this.base, data);
  }

  update(id: number, data: UpdateBoardRequest): Observable<Board> {
    return this.http.put<Board>(`${this.base}/${id}`, data);
  }

  close(id: number): Observable<Board> {
    return this.http.put<Board>(`${this.base}/${id}/close`, {});
  }

  reopen(id: number): Observable<Board> {
    return this.http.put<Board>(`${this.base}/${id}/reopen`, {});
  }

  delete(id: number): Observable<string> {
    return this.http.delete(`${this.base}/${id}`,
      { responseType: 'text' });
  }

  addMember(boardId: number,
    userId: number, role: string): Observable<unknown> {
    return this.http.post(
      `${this.base}/${boardId}/members`, { userId, role }
    );
  }

  removeMember(boardId: number, userId: number): Observable<string> {
    return this.http.delete(
      `${this.base}/${boardId}/members/${userId}`,
      { responseType: 'text' }
    );
  }

  updateMemberRole(boardId: number,
    userId: number, role: string): Observable<string> {
    return this.http.put(
      `${this.base}/${boardId}/members/${userId}/role`,
      { role }, { responseType: 'text' }
    );
  }

  getAnalytics(id: number): Observable<BoardAnalytics> {
    return this.http.get<BoardAnalytics>(`${this.base}/${id}/analytics`);
  }

  search(keyword: string): Observable<Board[]> {
    return this.http.get<Board[]>(`${this.base}/search`, {
      params: new HttpParams().set('keyword', keyword)
    });
  }
}
