package com.flowboard.card_service.scheduler;

import com.flowboard.card_service.client.NotificationClient;
import com.flowboard.card_service.client.dto.NotifyDueDateRequest;
import com.flowboard.card_service.client.dto.NotifyOverdueRequest;
import com.flowboard.card_service.entity.Card;
import com.flowboard.card_service.enums.CardStatus;
import com.flowboard.card_service.enums.Priority;
import com.flowboard.card_service.repository.CardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DueDateSchedulerTest {

    @Mock CardRepository cardRepository;
    @Mock NotificationClient notificationClient;
    @InjectMocks DueDateScheduler scheduler;

    private Card card(Long assigneeId) {
        return Card.builder()
                .id(1L).boardId(100L).listId(10L).title("Card")
                .assigneeId(assigneeId).priority(Priority.MEDIUM).status(CardStatus.TO_DO)
                .position(0).createdById(1L).createdAt(LocalDateTime.now())
                .dueDate(LocalDate.now().plusDays(1))
                .build();
    }

    @Test
    void notifyDueTomorrowSendsForAssignedCards() {
        when(cardRepository.findByDueDateAndIsArchivedFalseAndStatusNot(any(), any()))
                .thenReturn(List.of(card(5L), card(null)));

        scheduler.notifyDueTomorrow();

        verify(notificationClient).notifyDueDate(any(NotifyDueDateRequest.class));
    }

    @Test
    void notifyDueTomorrowSwallowsNotificationFailures() {
        when(cardRepository.findByDueDateAndIsArchivedFalseAndStatusNot(any(), any()))
                .thenReturn(List.of(card(5L)));
        doThrow(new RuntimeException("boom")).when(notificationClient).notifyDueDate(any(NotifyDueDateRequest.class));

        scheduler.notifyDueTomorrow();

        verify(notificationClient).notifyDueDate(any(NotifyDueDateRequest.class));
    }

    @Test
    void notifyDueInTwoHoursReturnsEarlyOutsideTargetHour() {
        scheduler.notifyDueInTwoHours();
        verify(cardRepository, never()).findByDueDateAndIsArchivedFalseAndStatusNot(any(), any());
    }

    @Test
    void notifyOverdueCardsSendsForAssignedCards() {
        Card overdue = card(7L);
        overdue.setDueDate(LocalDate.now().minusDays(2));
        when(cardRepository.findAllOverdueBeforeDate(any())).thenReturn(List.of(overdue, card(null)));

        scheduler.notifyOverdueCards();

        verify(notificationClient).notifyOverdue(any(NotifyOverdueRequest.class));
    }
}
