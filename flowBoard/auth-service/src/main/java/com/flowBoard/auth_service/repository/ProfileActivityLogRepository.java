package com.flowBoard.auth_service.repository;

import com.flowBoard.auth_service.entity.ProfileActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileActivityLogRepository extends JpaRepository<ProfileActivityLog, Long> {
    List<ProfileActivityLog> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
