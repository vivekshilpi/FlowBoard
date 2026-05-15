package com.flowboard.notification_service.service;

import com.flowboard.notification_service.dto.BoardChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class BoardRealtimeStreamService {

    private final Map<Long, Map<String, SseEmitter>> emittersByBoard = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long boardId, Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        String emitterKey = userId + "-" + System.nanoTime();

        emittersByBoard
                .computeIfAbsent(boardId, ignored -> new ConcurrentHashMap<>())
                .put(emitterKey, emitter);

        emitter.onCompletion(() -> removeEmitter(boardId, emitterKey));
        emitter.onTimeout(() -> removeEmitter(boardId, emitterKey));
        emitter.onError(error -> removeEmitter(boardId, emitterKey));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("boardId", boardId, "userId", userId)));
        } catch (IOException e) {
            removeEmitter(boardId, emitterKey);
        }

        return emitter;
    }

    public void broadcast(BoardChangeEvent event) {
        Map<String, SseEmitter> boardEmitters = emittersByBoard.get(event.getBoardId());
        if (boardEmitters == null || boardEmitters.isEmpty()) {
            return;
        }

        boardEmitters.forEach((key, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("board-change")
                        .data(event));
            } catch (IOException e) {
                log.debug("Removing stale board emitter {} for board {}", key, event.getBoardId());
                removeEmitter(event.getBoardId(), key);
            }
        });
    }

    private void removeEmitter(Long boardId, String emitterKey) {
        Map<String, SseEmitter> boardEmitters = emittersByBoard.get(boardId);
        if (boardEmitters == null) {
            return;
        }

        boardEmitters.remove(emitterKey);
        if (boardEmitters.isEmpty()) {
            emittersByBoard.remove(boardId);
        }
    }
}
