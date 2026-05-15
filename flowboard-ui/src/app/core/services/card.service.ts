import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Card, CardActivity, CreateCardRequest,
  UpdateCardRequest, MoveCardRequest, Priority, CardStatus, AddCardCommentRequest
} from '../models/card.model';

@Injectable({ providedIn: 'root' })
export class CardService {

  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/cards`;

  getByList(listId: number): Observable<Card[]> {
    return this.http.get<Card[]>(`${this.base}/list/${listId}`).pipe(
      map(cards => cards.map(card => this.normalizeCard(card)))
    );
  }

  getByBoard(boardId: number): Observable<Card[]> {
    return this.http.get<Card[]>(`${this.base}/board/${boardId}`).pipe(
      map(cards => cards.map(card => this.normalizeCard(card)))
    );
  }

  getById(id: number): Observable<Card> {
    return this.http.get<Card>(`${this.base}/${id}`).pipe(
      map(card => this.normalizeCard(card))
    );
  }

  getByAssignee(userId: number): Observable<Card[]> {
    return this.http.get<Card[]>(`${this.base}/assignee/${userId}`).pipe(
      map(cards => cards.map(card => this.normalizeCard(card)))
    );
  }

  create(data: CreateCardRequest): Observable<Card> {
    return this.http.post<Card>(this.base, data).pipe(
      map(card => this.normalizeCard(card))
    );
  }

  update(id: number, data: UpdateCardRequest): Observable<Card> {
    return this.http.put<Card>(`${this.base}/${id}`, data).pipe(
      map(card => this.normalizeCard(card))
    );
  }

  delete(id: number): Observable<string> {
    return this.http.delete(`${this.base}/${id}`, { responseType: 'text' });
  }

  move(id: number, data: MoveCardRequest): Observable<Card> {
    return this.http.put<Card>(`${this.base}/${id}/move`, data).pipe(
      map(card => this.normalizeCard(card))
    );
  }

  reorder(listId: number, orderedCardIds: number[]): Observable<Card[]> {
    return this.http.put<Card[]>(`${this.base}/reorder`, { listId, orderedCardIds });
  }

  archive(id: number): Observable<Card> {
    return this.http.put<Card>(`${this.base}/${id}/archive`, {}).pipe(
      map(card => this.normalizeCard(card))
    );
  }

  unarchive(id: number): Observable<Card> {
    return this.http.put<Card>(`${this.base}/${id}/unarchive`, {}).pipe(
      map(card => this.normalizeCard(card))
    );
  }

  setAssignee(id: number, assigneeId: number | null): Observable<Card> {
    return this.http.put<Card>(`${this.base}/${id}/assignee`, { assigneeId }).pipe(
      map(card => this.normalizeCard(card))
    );
  }

  setPriority(id: number, priority: Priority): Observable<Card> {
    return this.http.put<Card>(`${this.base}/${id}/priority`, { priority }).pipe(
      map(card => this.normalizeCard(card))
    );
  }

  setStatus(id: number, status: CardStatus): Observable<Card> {
    return this.http.put<Card>(`${this.base}/${id}/status`, { status }).pipe(
      map(card => this.normalizeCard(card))
    );
  }

  getByStatus(boardId: number, status: CardStatus): Observable<Card[]> {
    return this.http.get<Card[]>(`${this.base}/board/${boardId}/status/${status}`).pipe(
      map(cards => cards.map(card => this.normalizeCard(card)))
    );
  }

  getByPriority(boardId: number,
    priority: Priority): Observable<Card[]> {
    return this.http.get<Card[]>(`${this.base}/board/${boardId}/priority/${priority}`).pipe(
      map(cards => cards.map(card => this.normalizeCard(card)))
    );
  }

  getOverdue(boardId: number): Observable<Card[]> {
    return this.http.get<Card[]>(`${this.base}/board/${boardId}/overdue`).pipe(
      map(cards => cards.map(card => this.normalizeCard(card)))
    );
  }

  getAllOverdue(): Observable<Card[]> {
    return this.http.get<Card[]>(`${this.base}/overdue/all`).pipe(
      map(cards => cards.map(card => this.normalizeCard(card)))
    );
  }

  search(boardId: number, keyword: string): Observable<Card[]> {
    return this.http.get<Card[]>(`${this.base}/board/${boardId}/search`, {
      params: new HttpParams().set('keyword', keyword)
    }).pipe(
      map(cards => cards.map(card => this.normalizeCard(card)))
    );
  }

  getArchivedByBoard(boardId: number): Observable<Card[]> {
    return this.http.get<Card[]>(`${this.base}/board/${boardId}/archived`).pipe(
      map(cards => cards.map(card => this.normalizeCard(card)))
    );
  }

  getActivity(cardId: number): Observable<CardActivity[]> {
    return this.http.get<CardActivity[]>(`${this.base}/${cardId}/activity`);
  }

  addComment(cardId: number, data: AddCardCommentRequest): Observable<CardActivity> {
    return this.http.post<CardActivity>(`${this.base}/${cardId}/comments`, data);
  }

  updateComment(cardId: number, commentId: number, content: string): Observable<CardActivity> {
    return this.http.put<CardActivity>(`${this.base}/${cardId}/comments/${commentId}`, { content });
  }

  deleteComment(cardId: number, commentId: number): Observable<string> {
    return this.http.delete(`${this.base}/${cardId}/comments/${commentId}`, { responseType: 'text' });
  }

  uploadAttachment(cardId: number, file: File): Observable<Card> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Card>(`${this.base}/${cardId}/attachments`, formData).pipe(
      map(card => this.normalizeCard(card))
    );
  }

  deleteAttachment(cardId: number, attachmentId: string): Observable<Card> {
    return this.http.delete<Card>(`${this.base}/${cardId}/attachments/${attachmentId}`).pipe(
      map(card => this.normalizeCard(card))
    );
  }

  downloadAttachment(cardId: number, attachmentId: string): Observable<Blob> {
    return this.http.get(`${this.base}/${cardId}/attachments/${attachmentId}/download`, {
      responseType: 'blob'
    });
  }

  getBoardStats(boardId: number): Observable<any> {
    return this.http.get(`${this.base}/board/${boardId}/stats`);
  }

  copyCard(id: number, targetListId?: number): Observable<Card> {
    const params = targetListId
      ? new HttpParams().set('targetListId', targetListId)
      : undefined;
    return this.http.post<Card>(`${this.base}/${id}/copy`, {}, { params }).pipe(
      map(card => this.normalizeCard(card))
    );
  }

  getActivityPaged(
    cardId: number,
    page: number = 0,
    size: number = 20
  ): Observable<any> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size);
    return this.http.get(`${this.base}/${cardId}/activity/paged`, { params });
  }

  globalSearch(keyword?: string, assigneeId?: number): Observable<Card[]> {
    let params = new HttpParams();
    if (keyword) {
      params = params.set('keyword', keyword);
    }
    if (assigneeId !== undefined && assigneeId !== null) {
      params = params.set('assigneeId', assigneeId);
    }
    return this.http.get<Card[]>(`${this.base}/search`, { params }).pipe(
      map(cards => cards.map(card => this.normalizeCard(card)))
    );
  }

  private normalizeCard(card: Card & { archived?: boolean; overdue?: boolean }): Card {
    return {
      ...card,
      isArchived: card.isArchived ?? card.archived ?? false,
      isOverdue: card.isOverdue ?? card.overdue ?? false
    };
  }
}
