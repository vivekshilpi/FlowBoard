package com.flowboard.board_service.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicBoardViewResponse {

    private BoardResponse board;
    private List<PublicListResponse> lists;
    private List<PublicCardResponse> cards;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicListResponse {
        private Long id;
        private Long boardId;
        private String name;
        private Integer position;
        private String color;
        private boolean isArchived;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private int cardCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicCardResponse {
        private Long id;
        private Long listId;
        private Long boardId;
        private String title;
        private String description;
        private Integer position;
        private String priority;
        private String status;
        private LocalDate dueDate;
        private LocalDate startDate;
        private Long assigneeId;
        private Long createdById;
        private boolean isArchived;
        private boolean isOverdue;
        private String coverColor;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
