package com.flowboard.notification_service.listener;

import com.flowboard.notification_service.config.RabbitMQConfig;
import com.flowboard.notification_service.dto.BoardChangeEvent;
import com.flowboard.notification_service.service.BoardRealtimeStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BoardChangeListener {

    private final BoardRealtimeStreamService boardRealtimeStreamService;

    @RabbitListener(queues = RabbitMQConfig.BOARD_EVENTS_QUEUE)
    public void handleBoardChange(BoardChangeEvent event) {
        log.debug("Board realtime event received: boardId={} entityType={} action={}",
                event.getBoardId(), event.getEntityType(), event.getAction());
        boardRealtimeStreamService.broadcast(event);
    }
}
