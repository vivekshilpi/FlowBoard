import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  TaskList, CreateListRequest,
  UpdateListRequest, ReorderListRequest
} from '../models/list.model';

@Injectable({ providedIn: 'root' })
export class ListService {

  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/lists`;

  getByBoard(boardId: number): Observable<TaskList[]> {
    return this.http.get<TaskList[]>(`${this.base}/board/${boardId}`);
  }

  getArchived(boardId: number): Observable<TaskList[]> {
    return this.http.get<TaskList[]>(
      `${this.base}/board/${boardId}/archived`);
  }

  create(data: CreateListRequest): Observable<TaskList> {
    return this.http.post<TaskList>(this.base, data);
  }

  update(id: number, data: UpdateListRequest): Observable<TaskList> {
    return this.http.put<TaskList>(`${this.base}/${id}`, data);
  }

  reorder(data: ReorderListRequest): Observable<TaskList[]> {
    return this.http.put<TaskList[]>(`${this.base}/reorder`, data);
  }

  archive(id: number): Observable<TaskList> {
    return this.http.put<TaskList>(`${this.base}/${id}/archive`, {});
  }

  unarchive(id: number): Observable<TaskList> {
    return this.http.put<TaskList>(`${this.base}/${id}/unarchive`, {});
  }

  move(id: number, targetBoardId: number,
    targetPosition?: number): Observable<TaskList> {
    return this.http.put<TaskList>(`${this.base}/${id}/move`,
      { targetBoardId, targetPosition });
  }

  delete(id: number): Observable<string> {
    return this.http.delete(`${this.base}/${id}`,
      { responseType: 'text' });
  }
}