package com.flowboard.notification_service.service;

import com.flowboard.notification_service.dto.BoardChangeEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BoardRealtimeStreamServiceTest {

    private final BoardRealtimeStreamService service = new BoardRealtimeStreamService();

    @Test
    @DisplayName("subscribe registers emitter for board")
    @SuppressWarnings("unchecked")
    void subscribe_registersEmitter() {
        SseEmitter emitter = service.subscribe(11L, 2L);

        Map<Long, Map<String, SseEmitter>> emittersByBoard =
                (Map<Long, Map<String, SseEmitter>>) ReflectionTestUtils.getField(service, "emittersByBoard");

        assertThat(emitter).isNotNull();
        assertThat(emittersByBoard).containsKey(11L);
        assertThat(emittersByBoard.get(11L)).hasSize(1);
    }

    @Test
    @DisplayName("broadcast returns quietly when no subscribers exist")
    void broadcast_withoutSubscribers_returnsQuietly() {
        BoardChangeEvent event = BoardChangeEvent.builder()
                .boardId(99L)
                .entityType("CARD")
                .action("UPDATED")
                .occurredAt(LocalDateTime.now())
                .build();

        service.broadcast(event);
    }

    @Test
    @DisplayName("broadcast removes stale emitters when send fails")
    @SuppressWarnings("unchecked")
    void broadcast_removesStaleEmittersWhenSendFails() {
        Map<Long, Map<String, SseEmitter>> emittersByBoard =
                (Map<Long, Map<String, SseEmitter>>) ReflectionTestUtils.getField(service, "emittersByBoard");
        emittersByBoard.put(7L, new java.util.concurrent.ConcurrentHashMap<>(Map.of(
                "bad", new BrokenSseEmitter()
        )));

        BoardChangeEvent event = BoardChangeEvent.builder()
                .boardId(7L)
                .entityType("CARD")
                .action("UPDATED")
                .occurredAt(LocalDateTime.now())
                .build();

        service.broadcast(event);

        assertThat(emittersByBoard).doesNotContainKey(7L);
    }

    private static final class BrokenSseEmitter extends SseEmitter {
        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            throw new IOException("boom");
        }
    }
}
