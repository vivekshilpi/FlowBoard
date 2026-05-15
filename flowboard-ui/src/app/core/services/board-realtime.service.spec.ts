import { BoardRealtimeService } from './board-realtime.service';
import { AuthService } from './auth.service';
import { BoardRealtimeEvent } from '../models/board-realtime.model';

class FakeEventSource {
  public onerror: (() => void) | null = null;
  private listeners = new Map<string, (event: MessageEvent) => void>();

  constructor(public readonly url: string) {}

  addEventListener(type: string, listener: (event: MessageEvent) => void): void {
    this.listeners.set(type, listener);
  }

  emit(type: string, data: unknown): void {
    this.listeners.get(type)?.({ data: JSON.stringify(data) } as MessageEvent);
  }

  close(): void {}
}

describe('BoardRealtimeService', () => {
  let service: BoardRealtimeService;
  let authService: jasmine.SpyObj<AuthService>;
  let sourceInstance: FakeEventSource | null;
  let closeSpy: jasmine.Spy;
  const originalEventSource = (globalThis as any).EventSource;

  beforeEach(() => {
    sourceInstance = null;
    closeSpy = jasmine.createSpy('close');
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['getToken']);

    class EventSourceMock extends FakeEventSource {
      constructor(url: string) {
        super(url);
        sourceInstance = this;
        this.close = closeSpy;
      }
    }

    (globalThis as any).EventSource = EventSourceMock;

    service = Object.create(BoardRealtimeService.prototype) as BoardRealtimeService;
    (service as any).auth = authService;
    (service as any).zone = { run: (fn: () => void) => fn() };
  });

  afterEach(() => {
    (globalThis as any).EventSource = originalEventSource;
  });

  it('returns noop disconnect when no token exists', () => {
    authService.getToken.and.returnValue(null);

    const disconnect = service.connect(5, { onEvent: () => fail('should not emit') });

    expect(typeof disconnect).toBe('function');
    disconnect();
  });

  it('creates event source, forwards events, handles errors, and closes connection', () => {
    const received: BoardRealtimeEvent[] = [];
    let errored = false;
    authService.getToken.and.returnValue('token value');

    const disconnect = service.connect(7, {
      onEvent: event => received.push(event),
      onError: () => { errored = true; }
    });

    expect(sourceInstance?.url).toContain('/notifications/boards/7/stream');
    expect(sourceInstance?.url).toContain('access_token=token%20value');

    sourceInstance?.emit('board-change', { action: 'UPDATED' });
    expect(received).toEqual([{ action: 'UPDATED' } as BoardRealtimeEvent]);

    sourceInstance?.onerror?.();
    expect(errored).toBeTrue();

    disconnect();
    expect(closeSpy).toHaveBeenCalled();
  });
});
