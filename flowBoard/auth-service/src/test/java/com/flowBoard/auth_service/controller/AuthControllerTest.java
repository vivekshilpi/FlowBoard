package com.flowBoard.auth_service.controller;

import com.flowBoard.auth_service.dto.*;
import com.flowBoard.auth_service.entity.ROLE;
import com.flowBoard.auth_service.entity.User;
import com.flowBoard.auth_service.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController controller;
    private User user;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService);
        user = User.builder()
                .id(1L)
                .fullName("Test User")
                .username("testuser")
                .email("test@example.com")
                .avatarUrl("/avatar.png")
                .bio("bio")
                .themePreference("dark")
                .role(ROLE.MEMBER)
                .active(true)
                .emailVerified(true)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void register_returnsServiceResponse() {
        RegisterRequest request = new RegisterRequest();
        AuthResponse response = new AuthResponse("registered", null);
        when(authService.register(request)).thenReturn(response);

        assertThat(controller.register(request).getBody()).isSameAs(response);
        verify(authService).register(request);
    }

    @Test
    void login_returnsServiceResponse() {
        LoginRequest request = new LoginRequest();
        AuthResponse response = new AuthResponse("ok", "jwt");
        when(authService.login(request)).thenReturn(response);

        assertThat(controller.login(request).getBody()).isSameAs(response);
        verify(authService).login(request);
    }

    @Test
    void logout_stripsBearerPrefix() {
        assertThat(controller.logout("Bearer token-value").getBody()).isEqualTo("Logged out successfully");
        verify(authService).logout("token-value");
    }

    @Test
    void validateToken_returnsValidationMessage() {
        when(authService.validateToken("token")).thenReturn("valid");

        assertThat(controller.validateToken("token").getBody()).isEqualTo("valid");
    }

    @Test
    void refreshToken_delegatesToService() {
        TokenRefreshRequest request = new TokenRefreshRequest();
        request.setRefreshToken("refresh");
        when(authService.refreshToken("refresh")).thenReturn("new-token");

        assertThat(controller.refreshToken(request).getBody()).isEqualTo("new-token");
    }

    @Test
    void getProfile_mapsUserToDto() {
        when(authService.getUserById(1L)).thenReturn(user);

        UserProfileDto body = controller.getProfile(user).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getEmail()).isEqualTo("test@example.com");
        assertThat(body.getThemePreference()).isEqualTo("dark");
    }

    @Test
    void getProfileActivity_returnsLogs() {
        List<ProfileActivityLogDto> logs = List.of(new ProfileActivityLogDto(1L, "LOGIN", "Logged in", LocalDateTime.now()));
        when(authService.getProfileActivity(1L)).thenReturn(logs);

        assertThat(controller.getProfileActivity(user).getBody()).isEqualTo(logs);
    }

    @Test
    void updateProfile_returnsMappedUser() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        when(authService.updateProfile(1L, request)).thenReturn(user);

        assertThat(controller.updateProfile(user, request).getBody().getUsername()).isEqualTo("testuser");
    }

    @Test
    void updateThemePreference_returnsMappedUser() {
        UpdateThemePreferenceRequest request = new UpdateThemePreferenceRequest();
        request.setThemePreference("light");
        User updated = User.builder()
                .id(1L)
                .fullName("Test User")
                .username("testuser")
                .email("test@example.com")
                .themePreference("light")
                .role(ROLE.MEMBER)
                .active(true)
                .emailVerified(true)
                .build();
        when(authService.updateThemePreference(1L, "light")).thenReturn(updated);

        assertThat(controller.updateThemePreference(user, request).getBody().getThemePreference()).isEqualTo("light");
    }

    @Test
    void uploadAvatar_returnsMappedUser() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1, 2});
        when(authService.updateAvatar(1L, file)).thenReturn(user);

        assertThat(controller.uploadAvatar(user, file).getBody().getAvatarUrl()).isEqualTo("/avatar.png");
    }

    @Test
    void changePassword_returnsSuccessMessage() {
        ChangePasswordRequest request = new ChangePasswordRequest();

        assertThat(controller.changePassword(user, request).getBody()).isEqualTo("Password changed successfully");
        verify(authService).changePassword(1L, request);
    }

    @Test
    void searchUsers_returnsMatches() {
        when(authService.searchUsers("test")).thenReturn(List.of(user));

        assertThat(controller.searchUsers("test").getBody()).containsExactly(user);
    }

    @Test
    void getUserByEmail_returnsLookupResponse() {
        when(authService.getUserByEmail("test@example.com")).thenReturn(user);

        UserLookupResponse body = controller.getUserByEmail("test@example.com").getBody();

        assertThat(body).isNotNull();
        assertThat(body.getId()).isEqualTo(1L);
        assertThat(body.getAvatarUrl()).isEqualTo("/avatar.png");
    }

    @Test
    void getUserById_returnsLookupResponse() {
        when(authService.getUserById(1L)).thenReturn(user);

        UserLookupResponse body = controller.getUserById(1L).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void deactivate_returnsSuccessMessage() {
        assertThat(controller.deactivate(user).getBody()).isEqualTo("Account deactivated");
        verify(authService).deactivateAccount(1L);
    }

    @Test
    void adminEndpoints_delegateToService() {
        AdminStatsResponse stats = AdminStatsResponse.builder()
                .totalUsers(10L)
                .totalWorkspaces(8L)
                .totalBoards(7L)
                .activeUsersToday(5L)
                .activeUsers(4L)
                .suspendedUsers(2L)
                .build();
        when(authService.getAllUsers()).thenReturn(List.of(user));
        when(authService.getAdminStats()).thenReturn(stats);
        when(authService.getUsersByRole(ROLE.MEMBER)).thenReturn(List.of(user));

        assertThat(controller.getAllUsers().getBody()).containsExactly(user);
        assertThat(controller.getAdminStats().getBody()).isSameAs(stats);
        assertThat(controller.getUsersByRole(ROLE.MEMBER).getBody()).containsExactly(user);

        assertThat(controller.updateUserRole(2L, Map.of("role", "PLATFORM_ADMIN")).getBody())
                .isEqualTo("User role updated to PLATFORM_ADMIN");
        verify(authService).updateUserRole(2L, ROLE.PLATFORM_ADMIN);

        assertThat(controller.suspendUser(3L).getBody()).isEqualTo("User suspended");
        verify(authService).suspendUser(3L);

        assertThat(controller.reactivateUser(4L).getBody()).isEqualTo("User reactivated");
        verify(authService).reactivateUser(4L);

        assertThat(controller.deleteUser(5L).getBody()).isEqualTo("User permanently deleted");
        verify(authService).deleteUser(5L);
    }

    @Test
    void otpEndpoints_delegateToService() {
        VerifyOtpRequest verifyOtpRequest = new VerifyOtpRequest();
        verifyOtpRequest.setEmail("test@example.com");
        verifyOtpRequest.setOtp("123456");

        ForgotPasswordRequest forgotPasswordRequest = new ForgotPasswordRequest();
        forgotPasswordRequest.setEmail("test@example.com");

        ResetPasswordRequest resetPasswordRequest = new ResetPasswordRequest();
        resetPasswordRequest.setEmail("test@example.com");
        resetPasswordRequest.setOtp("123456");
        resetPasswordRequest.setNewPassword("Password@123");

        assertThat(controller.resendVerification("test@example.com").getBody())
                .isEqualTo("Verification OTP sent to test@example.com");
        verify(authService).sendVerificationOtp("test@example.com");

        assertThat(controller.verifyEmail(verifyOtpRequest).getBody()).isEqualTo("Email verified successfully");
        verify(authService).verifyEmail("test@example.com", "123456");

        assertThat(controller.forgotPassword(forgotPasswordRequest).getBody()).isEqualTo("OTP sent");
        verify(authService).sendForgotPasswordOtp("test@example.com");

        assertThat(controller.resetPassword(resetPasswordRequest).getBody()).isEqualTo("Password reset successfully");
        verify(authService).resetPassword(resetPasswordRequest);

        assertThat(controller.sendReactivationOtp("test@example.com").getBody())
                .isEqualTo("Reactivation OTP sent to test@example.com");
        verify(authService).sendReactivationOtp("test@example.com");

        assertThat(controller.reactivateAccount("test@example.com", "999999").getBody())
                .isEqualTo("Account successfully reactivated. You can now log in.");
        verify(authService).reactivateWithOtp("test@example.com", "999999");
    }
}
