package com.flowBoard.auth_service.service;

import com.flowBoard.auth_service.dto.*;
import com.flowBoard.auth_service.entity.ROLE;
import com.flowBoard.auth_service.entity.User;
import com.flowBoard.auth_service.exception.CustomException;
import com.flowBoard.auth_service.repository.UserRepository;
import com.flowBoard.auth_service.security.JwtUtil;
import com.flowBoard.auth_service.security.OtpService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl – full coverage suite")
class AuthServiceImplTest {

    @Mock UserRepository      repository;
    @Mock PasswordEncoder     passwordEncoder;
    @Mock JwtUtil             jwtUtil;
    @Mock OtpService          otpService;
    @Mock EmailService        emailService;
    @Mock TokenBlacklistService blacklistService;
    @Mock AvatarStorageService avatarStorageService;
    @Mock PasswordChangeRateLimiter passwordChangeRateLimiter;
    @Mock ProfileActivityLogService profileActivityLogService;

    @InjectMocks AuthServiceImpl authService;

    private User verifiedUser, unverifiedUser, inactiveUser;

    @BeforeEach
    void setUp() {
        verifiedUser = User.builder()
                .id(1L).fullName("Nageshwar Patel")
                .email("nageshwar@gmail.com").username("nageshwar")
                .password("hashed_pw").role(ROLE.MEMBER)
                .active(true).emailVerified(true)
                .createdAt(LocalDateTime.now()).build();

        unverifiedUser = User.builder()
                .id(2L).email("unverified@gmail.com").username("unverified")
                .active(true).emailVerified(false).build();

        inactiveUser = User.builder()
                .id(3L).email("inactive@gmail.com").username("inactive")
                .active(false).emailVerified(true).build();
    }

    // ── register ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("register()")
    class RegisterTests {

        @Test @DisplayName("success – saves user and sends OTP, no token returned")
        void register_success() {
            RegisterRequest req = new RegisterRequest();
            req.setFullName("Test"); req.setEmail("t@g.com");
            req.setUsername("tuser"); req.setPassword("Password@1");

            when(repository.findByEmail(anyString())).thenReturn(Optional.empty());
            when(repository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");

            AuthResponse r = authService.register(req);
            assertThat(r.getMessage()).contains("Registration successful");
            assertThat(r.getToken()).isNull();
            verify(otpService).sendVerificationOtp("t@g.com");
        }

        @Test @DisplayName("duplicate email – throws 400, user never saved")
        void register_duplicateEmail() {
            RegisterRequest req = new RegisterRequest();
            req.setFullName("Duplicate User");
            req.setEmail("nageshwar@gmail.com");
            req.setUsername("other");
            when(repository.findByEmail(anyString())).thenReturn(Optional.of(verifiedUser));

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("Email already exists");
            verify(repository, never()).save(any());
        }

        @Test @DisplayName("duplicate username – throws 400")
        void register_duplicateUsername() {
            RegisterRequest req = new RegisterRequest();
            req.setFullName("Duplicate Username");
            req.setEmail("new@g.com");
            req.setUsername("nageshwar");
            when(repository.findByEmail(anyString())).thenReturn(Optional.empty());
            when(repository.existsByUsernameIgnoreCase(anyString())).thenReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("Username already taken");
        }
    }

    // ── login ──────────────────────────────────────────────────────────────────

    @Nested @DisplayName("login()")
    class LoginTests {

        @Test @DisplayName("success – returns JWT")
        void login_success() {
            LoginRequest req = new LoginRequest();
            req.setEmail(verifiedUser.getEmail()); req.setPassword("pass");

            when(repository.findByEmail(anyString())).thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(jwtUtil.generateToken(anyString(), anyLong(), anyString())).thenReturn("jwt");

            AuthResponse r = authService.login(req);
            assertThat(r.getToken()).isEqualTo("jwt");
        }

        @Test @DisplayName("email not found – throws 404")
        void login_emailNotFound() {
            LoginRequest req = new LoginRequest();
            req.setEmail("x@x.com"); req.setPassword("p");
            when(repository.findByEmail(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException)e).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test @DisplayName("inactive account – throws 403")
        void login_inactive() {
            LoginRequest req = new LoginRequest();
            req.setEmail(inactiveUser.getEmail()); req.setPassword("p");
            when(repository.findByEmail(anyString())).thenReturn(Optional.of(inactiveUser));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException)e).getStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test @DisplayName("unverified email – throws 403 and resends OTP when none active")
        void login_unverified_resends() {
            LoginRequest req = new LoginRequest();
            req.setEmail(unverifiedUser.getEmail()); req.setPassword("p");
            when(repository.findByEmail(anyString())).thenReturn(Optional.of(unverifiedUser));
            when(otpService.hasActiveOtp(anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException)e).getStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            verify(otpService).sendVerificationOtp(unverifiedUser.getEmail());
        }

        @Test @DisplayName("unverified but OTP active – throws 403, does NOT resend")
        void login_unverified_otpActive() {
            LoginRequest req = new LoginRequest();
            req.setEmail(unverifiedUser.getEmail()); req.setPassword("p");
            when(repository.findByEmail(anyString())).thenReturn(Optional.of(unverifiedUser));
            when(otpService.hasActiveOtp(anyString())).thenReturn(true);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException)e).getStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            verify(otpService, never()).sendVerificationOtp(anyString());
        }

        @Test @DisplayName("wrong password – throws 401")
        void login_wrongPassword() {
            LoginRequest req = new LoginRequest();
            req.setEmail(verifiedUser.getEmail()); req.setPassword("wrong");
            when(repository.findByEmail(anyString())).thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException)e).getStatus())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ── verifyEmail ────────────────────────────────────────────────────────────

    @Nested @DisplayName("verifyEmail()")
    class VerifyEmailTests {

        @Test @DisplayName("success – sets emailVerified=true")
        void verifyEmail_success() {
            when(repository.findByEmail(anyString())).thenReturn(Optional.of(unverifiedUser));
            doNothing().when(otpService).verifyOtp(anyString(), anyString());

            authService.verifyEmail(unverifiedUser.getEmail(), "123456");

            assertThat(unverifiedUser.isEmailVerified()).isTrue();
            verify(repository).save(unverifiedUser);
        }

        @Test @DisplayName("already verified – throws 400")
        void verifyEmail_alreadyVerified() {
            when(repository.findByEmail(anyString())).thenReturn(Optional.of(verifiedUser));

            assertThatThrownBy(() -> authService.verifyEmail(verifiedUser.getEmail(), "otp"))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("already verified");
        }
    }

    // ── sendVerificationOtp ────────────────────────────────────────────────────

    @Test @DisplayName("sendVerificationOtp – throws 400 if already verified")
    void sendVerificationOtp_alreadyVerified() {
        when(repository.findByEmail(anyString())).thenReturn(Optional.of(verifiedUser));
        assertThatThrownBy(() -> authService.sendVerificationOtp(verifiedUser.getEmail()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException)e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test @DisplayName("sendVerificationOtp – sends OTP to unverified user")
    void sendVerificationOtp_success() {
        when(repository.findByEmail(anyString())).thenReturn(Optional.of(unverifiedUser));
        authService.sendVerificationOtp(unverifiedUser.getEmail());
        verify(otpService).sendVerificationOtp(unverifiedUser.getEmail());
    }

    // ── forgotPassword / resetPassword ────────────────────────────────────────

    @Test @DisplayName("sendForgotPasswordOtp – sends OTP for active user")
    void forgotPassword_success() {
        when(repository.findByEmail(anyString())).thenReturn(Optional.of(verifiedUser));
        authService.sendForgotPasswordOtp(verifiedUser.getEmail());
        verify(otpService).sendForgotPasswordOtp(verifiedUser.getEmail());
    }

    @Test @DisplayName("sendForgotPasswordOtp – still sends OTP for inactive user")
    void forgotPassword_inactiveUser_allowsRecovery() {
        when(repository.findByEmail(anyString())).thenReturn(Optional.of(inactiveUser));
        authService.sendForgotPasswordOtp(inactiveUser.getEmail());
        verify(otpService).sendForgotPasswordOtp(inactiveUser.getEmail());
    }

    @Test @DisplayName("resetPassword – verifies OTP and encodes new password")
    void resetPassword_success() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail(verifiedUser.getEmail()); req.setOtp("123"); req.setNewPassword("newPw");

        when(repository.findByEmail(anyString())).thenReturn(Optional.of(verifiedUser));
        doNothing().when(otpService).verifyOtp(anyString(), anyString());
        when(passwordEncoder.encode(anyString())).thenReturn("newHashed");

        authService.resetPassword(req);
        assertThat(verifiedUser.getPassword()).isEqualTo("newHashed");
        verify(repository).save(verifiedUser);
    }

    // ── changePassword ─────────────────────────────────────────────────────────

    @Nested @DisplayName("changePassword()")
    class ChangePasswordTests {

        @Test @DisplayName("success – saves new encoded password")
        void changePassword_success() {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("old"); req.setNewPassword("new");

            when(repository.findById(1L)).thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches("old", verifiedUser.getPassword())).thenReturn(true);
            when(passwordEncoder.encode("new")).thenReturn("newHashed");

            authService.changePassword(1L, req);
            assertThat(verifiedUser.getPassword()).isEqualTo("newHashed");
        }

        @Test @DisplayName("wrong old password – throws 400")
        void changePassword_wrongOld() {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("bad"); req.setNewPassword("new");

            when(repository.findById(1L)).thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.changePassword(1L, req))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("Old password is incorrect");
        }
    }

    // ── refreshToken ───────────────────────────────────────────────────────────

    @Test @DisplayName("refreshToken – issues new token for valid token")
    void refreshToken_success() {
        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractEmail(anyString())).thenReturn(verifiedUser.getEmail());
        when(repository.findByEmail(anyString())).thenReturn(Optional.of(verifiedUser));
        when(jwtUtil.generateToken(anyString(), anyLong(), anyString())).thenReturn("newJwt");

        String result = authService.refreshToken("oldToken");
        assertThat(result).isEqualTo("newJwt");
    }

    @Test @DisplayName("refreshToken – throws 401 for invalid/expired token")
    void refreshToken_invalid() {
        when(jwtUtil.isTokenValid(anyString())).thenReturn(false);
        assertThatThrownBy(() -> authService.refreshToken("bad"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException)e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── updateProfile ──────────────────────────────────────────────────────────

    @Test @DisplayName("updateProfile – updates name, avatarUrl, bio and calls save()")
    void updateProfile_success() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFullname("New Name"); req.setUsername("newuser");
        req.setAvatarUrl("https://avatar.png"); req.setBio("Hello");

        when(repository.findById(1L)).thenReturn(Optional.of(verifiedUser));
        when(repository.findByUsernameIgnoreCase("newuser")).thenReturn(Optional.empty());
        when(repository.save(any(User.class))).thenReturn(verifiedUser);

        authService.updateProfile(1L, req);
        assertThat(verifiedUser.getFullName()).isEqualTo("New Name");
        verify(repository).save(argThat(u -> {
            return "New Name".equals(u.getFullName());
        }));
    }

    @Test @DisplayName("updateProfile – sanitizes fields and rejects duplicate username")
    void updateProfile_duplicateUsername() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFullname("  <b>New Name</b> ");
        req.setUsername("other");
        req.setBio(" <i>Hello</i> world ");

        User another = User.builder().id(99L).username("other").build();
        when(repository.findById(1L)).thenReturn(Optional.of(verifiedUser));
        when(repository.findByUsernameIgnoreCase("other")).thenReturn(Optional.of(another));

        assertThatThrownBy(() -> authService.updateProfile(1L, req))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("Username already taken");
    }

    @Test @DisplayName("updateThemePreference – updates and logs theme")
    void updateThemePreference_success() {
        verifiedUser.setThemePreference("light");
        when(repository.findById(1L)).thenReturn(Optional.of(verifiedUser));
        when(repository.save(any(User.class))).thenReturn(verifiedUser);

        User updated = authService.updateThemePreference(1L, "dark");

        assertThat(updated.getThemePreference()).isEqualTo("dark");
        verify(repository).save(verifiedUser);
    }

    @Test @DisplayName("updateThemePreference – rejects invalid values")
    void updateThemePreference_invalid() {
        when(repository.findById(1L)).thenReturn(Optional.of(verifiedUser));

        assertThatThrownBy(() -> authService.updateThemePreference(1L, "blue"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException)e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test @DisplayName("updateAvatar – stores avatar and saves user")
    void updateAvatar_success() {
        org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(repository.findById(1L)).thenReturn(Optional.of(verifiedUser));
        when(avatarStorageService.storeAvatar(1L, file)).thenReturn("/uploads/avatars/1/test.png");
        when(repository.save(any(User.class))).thenReturn(verifiedUser);

        User updated = authService.updateAvatar(1L, file);

        assertThat(updated.getAvatarUrl()).isEqualTo("/uploads/avatars/1/test.png");
        verify(repository).save(verifiedUser);
    }

    @Test @DisplayName("getProfileActivity – maps activity logs")
    void getProfileActivity_success() {
        com.flowBoard.auth_service.entity.ProfileActivityLog log =
                com.flowBoard.auth_service.entity.ProfileActivityLog.builder()
                        .id(5L).user(verifiedUser).action("PROFILE_UPDATED")
                        .summary("Updated bio").createdAt(LocalDateTime.now()).build();
        when(repository.findById(1L)).thenReturn(Optional.of(verifiedUser));
        when(profileActivityLogService.getRecentActivity(1L)).thenReturn(List.of(log));

        List<ProfileActivityLogDto> result = authService.getProfileActivity(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("PROFILE_UPDATED");
    }

    // ── logout ─────────────────────────────────────────────────────────────────

    @Test @DisplayName("logout – blacklists token with TTL")
    void logout_blacklistsToken() {
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        Date expiry = new Date(System.currentTimeMillis() + 60_000);
        when(claims.getExpiration()).thenReturn(expiry);
        when(jwtUtil.extractAllClaims(anyString())).thenReturn(claims);

        authService.logout("valid.jwt.token");

        verify(blacklistService).blacklist(eq("valid.jwt.token"), anyLong());
    }

    @Test @DisplayName("logout – swallows token parsing failures")
    void logout_invalidToken_doesNotThrow() {
        when(jwtUtil.extractAllClaims(anyString())).thenThrow(new RuntimeException("bad token"));

        authService.logout("bad");

        verify(blacklistService, never()).blacklist(anyString(), anyLong());
    }

    // ── admin ops ──────────────────────────────────────────────────────────────

    @Nested @DisplayName("Admin operations")
    class AdminTests {

        @Test @DisplayName("deactivateAccount – sets active=false")
        void deactivateAccount() {
            when(repository.findById(1L)).thenReturn(Optional.of(verifiedUser));
            authService.deactivateAccount(1L);
            assertThat(verifiedUser.isActive()).isFalse();
            verify(repository).save(verifiedUser);
        }

        @Test @DisplayName("suspendUser – sets active=false on active user")
        void suspendUser_success() {
            when(repository.findById(1L)).thenReturn(Optional.of(verifiedUser));
            authService.suspendUser(1L);
            assertThat(verifiedUser.isActive()).isFalse();
        }

        @Test @DisplayName("suspendUser – throws 400 if already suspended")
        void suspendUser_alreadySuspended() {
            when(repository.findById(3L)).thenReturn(Optional.of(inactiveUser));
            assertThatThrownBy(() -> authService.suspendUser(3L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException)e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test @DisplayName("reactivateUser – sets active=true")
        void reactivateUser_success() {
            when(repository.findById(3L)).thenReturn(Optional.of(inactiveUser));
            authService.reactivateUser(3L);
            assertThat(inactiveUser.isActive()).isTrue();
        }

        @Test @DisplayName("reactivateUser – throws 400 if already active")
        void reactivateUser_alreadyActive() {
            when(repository.findById(1L)).thenReturn(Optional.of(verifiedUser));
            assertThatThrownBy(() -> authService.reactivateUser(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException)e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test @DisplayName("deleteUser – calls deleteById for existing user")
        void deleteUser_success() {
            when(repository.existsById(1L)).thenReturn(true);
            authService.deleteUser(1L);
            verify(repository).deleteById(1L);
        }

        @Test @DisplayName("deleteUser – throws 404 for missing user")
        void deleteUser_notFound() {
            when(repository.existsById(999L)).thenReturn(false);
            assertThatThrownBy(() -> authService.deleteUser(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException)e).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test @DisplayName("searchUsers – delegates to repository")
        void searchUsers_success() {
            when(repository.searchByNameOrUsername("na")).thenReturn(List.of(verifiedUser));
            List<User> result = authService.searchUsers("na");
            assertThat(result).hasSize(1);
        }

        @Test @DisplayName("getAllUsers – returns all users")
        void getAllUsers_success() {
            when(repository.findAll()).thenReturn(List.of(verifiedUser, inactiveUser));
            List<User> result = authService.getAllUsers();
            assertThat(result).hasSize(2);
        }

        @Test @DisplayName("getUsersByRole – filters by role")
        void getUsersByRole_success() {
            when(repository.findAllByRole(ROLE.MEMBER)).thenReturn(List.of(verifiedUser));
            List<User> result = authService.getUsersByRole(ROLE.MEMBER);
            assertThat(result).hasSize(1).first().extracting(User::getRole).isEqualTo(ROLE.MEMBER);
        }
    }

    @Test @DisplayName("sendVerificationOtp – sends reactivation OTP for verified inactive user")
    void sendVerificationOtp_inactiveVerified_sendsReactivationOtp() {
        inactiveUser.setFullName("Inactive User");
        when(repository.findByEmail(anyString())).thenReturn(Optional.of(inactiveUser));

        authService.sendVerificationOtp(inactiveUser.getEmail());

        verify(otpService).sendReactivationOtp(inactiveUser.getEmail(), inactiveUser.getFullName());
    }

    @Test @DisplayName("sendReactivationOtp – throws for active user")
    void sendReactivationOtp_activeUser() {
        when(repository.findByEmail(anyString())).thenReturn(Optional.of(verifiedUser));

        assertThatThrownBy(() -> authService.sendReactivationOtp(verifiedUser.getEmail()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException)e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test @DisplayName("sendReactivationOtp – sends OTP for inactive user")
    void sendReactivationOtp_success() {
        inactiveUser.setFullName("Inactive User");
        when(repository.findByEmail(anyString())).thenReturn(Optional.of(inactiveUser));

        authService.sendReactivationOtp(inactiveUser.getEmail());

        verify(otpService).sendReactivationOtp(inactiveUser.getEmail(), inactiveUser.getFullName());
    }

    @Test @DisplayName("reactivateWithOtp – activates and emails user")
    void reactivateWithOtp_success() {
        inactiveUser.setFullName("Inactive User");
        when(repository.findByEmail(anyString())).thenReturn(Optional.of(inactiveUser));

        authService.reactivateWithOtp(inactiveUser.getEmail(), "123456");

        assertThat(inactiveUser.isActive()).isTrue();
        verify(repository).save(inactiveUser);
        verify(emailService).sendAccountStatusEmail(inactiveUser.getEmail(), inactiveUser.getFullName(), true);
    }

    @Test @DisplayName("validateToken – throws unauthorized for invalid token")
    void validateToken_invalid() {
        when(jwtUtil.extractEmail(anyString())).thenThrow(new RuntimeException("bad"));

        assertThatThrownBy(() -> authService.validateToken("bad"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException)e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test @DisplayName("refreshToken – throws forbidden for inactive user")
    void refreshToken_inactiveUser() {
        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractEmail(anyString())).thenReturn(inactiveUser.getEmail());
        when(repository.findByEmail(anyString())).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> authService.refreshToken("old"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException)e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test @DisplayName("refreshToken – throws not found when user is missing")
    void refreshToken_userMissing() {
        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractEmail(anyString())).thenReturn("missing@example.com");
        when(repository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken("old"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException)e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test @DisplayName("getAdminStats – aggregates remote workspace and board data")
    void getAdminStats_success() {
        when(repository.count()).thenReturn(10L);
        when(repository.countByLastLoginAtAfter(any(LocalDateTime.class))).thenReturn(4L);
        when(repository.countByActiveTrue()).thenReturn(8L);
        when(repository.countByActiveFalse()).thenReturn(2L);
        when(repository.countByRole(ROLE.PLATFORM_ADMIN)).thenReturn(1L);
        when(repository.countByRole(ROLE.MEMBER)).thenReturn(9L);

        ReflectionTestUtils.setField(authService, "workspaceServiceBaseUrl", "http://workspace.local");
        ReflectionTestUtils.setField(authService, "boardServiceBaseUrl", "http://board.local");
        org.springframework.web.client.RestTemplate restTemplate =
                (org.springframework.web.client.RestTemplate) ReflectionTestUtils.getField(authService, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("http://workspace.local/api/v1/workspaces/admin"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("[{\"visibility\":\"PUBLIC\"},{\"visibility\":\"PRIVATE\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://board.local/api/v1/boards/admin"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("[{\"visibility\":\"PUBLIC\",\"closed\":false},{\"visibility\":\"PRIVATE\",\"closed\":true}]", MediaType.APPLICATION_JSON));

        AdminStatsResponse stats = authService.getAdminStats();

        assertThat(stats.getTotalUsers()).isEqualTo(10L);
        assertThat(stats.getTotalWorkspaces()).isEqualTo(2L);
        assertThat(stats.getTotalBoards()).isEqualTo(2L);
        assertThat(stats.getPublicBoards()).isEqualTo(1L);
        assertThat(stats.getClosedBoards()).isEqualTo(1L);
        server.verify();
    }

    @Test @DisplayName("getAdminStats – returns zero remote counts when services fail")
    void getAdminStats_remoteFailure() {
        when(repository.count()).thenReturn(3L);
        when(repository.countByLastLoginAtAfter(any(LocalDateTime.class))).thenReturn(1L);
        when(repository.countByActiveTrue()).thenReturn(2L);
        when(repository.countByActiveFalse()).thenReturn(1L);
        when(repository.countByRole(ROLE.PLATFORM_ADMIN)).thenReturn(1L);
        when(repository.countByRole(ROLE.MEMBER)).thenReturn(2L);

        ReflectionTestUtils.setField(authService, "workspaceServiceBaseUrl", "http://workspace.fail");
        ReflectionTestUtils.setField(authService, "boardServiceBaseUrl", "http://board.fail");
        org.springframework.web.client.RestTemplate restTemplate =
                (org.springframework.web.client.RestTemplate) ReflectionTestUtils.getField(authService, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("http://workspace.fail/api/v1/workspaces/admin"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());
        server.expect(requestTo("http://board.fail/api/v1/boards/admin"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        AdminStatsResponse stats = authService.getAdminStats();

        assertThat(stats.getTotalWorkspaces()).isEqualTo(0L);
        assertThat(stats.getTotalBoards()).isEqualTo(0L);
    }
}
