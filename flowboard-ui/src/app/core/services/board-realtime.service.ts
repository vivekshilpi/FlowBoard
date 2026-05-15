import { Injectable, NgZone, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { BoardRealtimeEvent } from '../models/board-realtime.model';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class BoardRealtimeService {

  private auth = inject(AuthService);
  private zone = inject(NgZone);

  connect(
    boardId: number,
    handlers: {
      onEvent: (event: BoardRealtimeEvent) => void;
      onError?: () => void;
    }
  ): () => void {
    const token = this.auth.getToken();
    if (!token) {
      return () => {};
    }

    const streamUrl =
      `${environment.apiUrl}/notifications/boards/${boardId}/stream?access_token=${encodeURIComponent(token)}`;
    const source = new EventSource(streamUrl);

    source.addEventListener('board-change', (message: MessageEvent) => {
      this.zone.run(() => {
        handlers.onEvent(JSON.parse(message.data) as BoardRealtimeEvent);
      });
    });

    source.onerror = () => {
      this.zone.run(() => handlers.onError?.());
    };

    return () => source.close();
  }
}
