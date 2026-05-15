package com.flowBoard.auth_service.service;

import com.flowBoard.auth_service.entity.ProfileActivityLog;
import com.flowBoard.auth_service.entity.User;
import com.flowBoard.auth_service.repository.ProfileActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileActivityLogService {

    private final ProfileActivityLogRepository profileActivityLogRepository;

    public void log(User user, String action, String summary) {
        profileActivityLogRepository.save(ProfileActivityLog.builder()
                .user(user)
                .action(action)
                .summary(summary)
                .build());
    }

    public List<ProfileActivityLog> getRecentActivity(Long userId) {
        return profileActivityLogRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }
}
