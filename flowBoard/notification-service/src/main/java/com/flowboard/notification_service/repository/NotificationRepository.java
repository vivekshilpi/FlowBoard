package com.flowboard.notification_service.repository;

import com.flowboard.notification_service.entity.Notification;
import com.flowboard.notification_service.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);
    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    List<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(Long recipientId);

    List<Notification> findByRecipientIdAndIsReadTrueOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndIsReadFalse(Long recipientId);

    List<Notification> findByRecipientIdAndTypeOrderByCreatedAtDesc(
            Long recipientId, NotificationType type);

    Optional<Notification> findFirstByRecipientIdAndTypeAndRelatedIdAndRelatedTypeOrderByCreatedAtDesc(
            Long recipientId, NotificationType type, Long relatedId, String relatedType);

    List<Notification> findByRelatedIdAndRelatedType( Long relatedId, String relatedType);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.relatedId = :relatedId AND n.relatedType = :relatedType")
    void deleteByRelatedIdAndRelatedType(@Param("relatedId") Long relatedId,
                                         @Param("relatedType") String relatedType);

    @Modifying
    @Query("""
            DELETE FROM Notification n
            WHERE n.recipientId = :recipientId
              AND n.relatedId = :relatedId
              AND n.relatedType = :relatedType
              AND n.type = :type
            """)
    void deleteByRecipientAndRelatedAndType(@Param("recipientId") Long recipientId,
                                            @Param("relatedId") Long relatedId,
                                            @Param("relatedType") String relatedType,
                                            @Param("type") NotificationType type);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, "+
    "n.readAt = CURRENT_TIMESTAMP "+
    "WHERE n.recipientId = :recipientId AND n.isRead = false")
    void markAllAsRead(@Param("recipientId") Long recipientId);

    @Modifying
    @Query("DELETE FROM Notification n "+
    "WHERE n.recipientId = :recipientId AND n.isRead = true")
    void deleteReadByRecipientId(@Param("recipientId") Long recipientId);

    boolean existsByIdAndRecipientId(Long id, Long recipientId);
}
