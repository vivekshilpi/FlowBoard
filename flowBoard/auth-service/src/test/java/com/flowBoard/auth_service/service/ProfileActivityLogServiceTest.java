package com.flowBoard.auth_service.service;

import com.flowBoard.auth_service.entity.ProfileActivityLog;
import com.flowBoard.auth_service.entity.User;
import com.flowBoard.auth_service.repository.ProfileActivityLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileActivityLogServiceTest {

    @Mock
    private ProfileActivityLogRepository repository;

    @Test
    void log_savesActivityEntry() {
        ProfileActivityLogService service = new ProfileActivityLogService(repository);
        User user = User.builder().id(1L).email("user@example.com").build();

        service.log(user, "PROFILE_UPDATE", "Updated profile");

        ArgumentCaptor<ProfileActivityLog> captor = ArgumentCaptor.forClass(ProfileActivityLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getAction()).isEqualTo("PROFILE_UPDATE");
    }

    @Test
    void getRecentActivity_returnsRepositoryData() {
        ProfileActivityLog log = ProfileActivityLog.builder().action("LOGIN").summary("Logged in").build();
        when(repository.findTop20ByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(log));
        ProfileActivityLogService service = new ProfileActivityLogService(repository);

        assertThat(service.getRecentActivity(1L)).containsExactly(log);
    }
}
