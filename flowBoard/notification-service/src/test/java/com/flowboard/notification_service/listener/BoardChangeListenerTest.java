package com.flowboard.notification_service.listener;

import com.flowboard.notification_service.dto.BoardChangeEvent;
import com.flowboard.notification_service.service.BoardRealtimeStreamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BoardChangeListenerTest {

    @Mock
    private BoardRealtimeStreamService boardRealtimeStreamService;

    @InjectMocks
    private BoardChangeListener listener;

    @Test
    @DisplayName("handleBoardChange broadcasts realtime event")
    void handleBoardChange_broadcastsEvent() {
        BoardChangeEvent event = BoardChangeEvent.builder()
                .boardId(10L)
                .workspaceId(5L)
                .entityType("CARD")
                .action("UPDATED")
                .entityId(9L)
                .actorUserId(1L)
                .occurredAt(LocalDateTime.now())
                .build();

        listener.handleBoardChange(event);

        verify(boardRealtimeStreamService).broadcast(event);
    }
}
