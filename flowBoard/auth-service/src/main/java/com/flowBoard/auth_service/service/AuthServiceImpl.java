package com.flowBoard.auth_service.service;

import com.flowBoard.auth_service.dto.*;
import com.flowBoard.auth_service.entity.ROLE;
import com.flowBoard.auth_service.entity.User;
import com.flowBoard.auth_service.exception.CustomException;
import com.flowBoard.auth_service.repository.UserRepository;
import com.flowBoard.auth_service.security.JwtUtil;
import com.flowBoard.auth_service.security.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final EmailService emailService;
    private final TokenBlacklistService blacklistService;
    private final AvatarStorageService avatarStorageService;
    private final PasswordChangeRateLimiter passwordChangeRateLimiter;
    private final ProfileActivityLogService profileActivityLogService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.workspace-service.base-url:http://localhost:8084}")
    private String workspaceServiceBaseUrl;

    @Value("${services.board-service.base-url:http://localhost:8082}")
    private String boardServiceBaseUrl;

    @Override
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedUsername = normalizeUsername(request.getUsername());

        User existingByEmail = repository.findByEmail(normalizedEmail).orElse(null);
        if (existingByEmail != null) {
            if (!existingByEmail.isEmailVerified()) {
                otpService.sendVerificationOtp(normalizedEmail);
                return new AuthResponse(
                        "Account already exists but is not verified. A new OTP has been sent to your email.",
                        null
                );
            }
            throw new CustomException("Email already exists", HttpStatus.BAD_REQUEST);
        }
        if (repository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new CustomException("Username already taken", HttpStatus.BAD_REQUEST);
        }

        User user = User.builder()
                .fullName(sanitizePlainText(request.getFullName()))
                .email(normalizedEmail)
                .username(normalizedUsername)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(ROLE.MEMBER)
                .build();

        repository.save(user);
        profileActivityLogService.log(user, "REGISTERED", "Account created.");

        otpService.sendVerificationOtp(normalizedEmail);

//        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse("Registration successful. Please check your email for the verification OTP.",
        null  // no JWT yet — user must verify email first
        );
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = repository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new CustomException("Invalid email", HttpStatus.NOT_FOUND));

        // Check account is active before verifying password
        if (!user.isActive()) {
            if(!otpService.hasActiveOtp(user.getEmail())){
                otpService.sendReactivationOtp(user.getEmail(), user.getFullName());
            }
            throw new CustomException("Account is deactivated. A reactivation OTP has been sent to your email.", HttpStatus.FORBIDDEN);
        }


        if(!user.isEmailVerified()){
            if(!otpService.hasActiveOtp(user.getEmail())){
                otpService.sendVerificationOtp(user.getEmail());
            }
            throw new CustomException(
                    "Email not verified. A new OTP has been sent to your email.",
                    HttpStatus.FORBIDDEN
            );
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException("Invalid password", HttpStatus.UNAUTHORIZED);
        }

        user.setLastLoginAt(LocalDateTime.now());
        repository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getRole().name());
        return new AuthResponse("Login successful", token);
    }

    @Override
    public void sendVerificationOtp(String email){
        String normalizedEmail = normalizeEmail(email);
        User user = repository.findByEmail(normalizedEmail)
                .orElseThrow(()-> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if(user.isEmailVerified()){
            if (user.isActive()) {
                throw new CustomException("Email is already verified", HttpStatus.BAD_REQUEST);
            }
            // If verified but inactive, send reactivation OTP instead
            otpService.sendReactivationOtp(normalizedEmail, user.getFullName());
            return;
        }
        otpService.sendVerificationOtp(normalizedEmail);

    }

    @Override
    public void verifyEmail(String email, String otp){
        String normalizedEmail = normalizeEmail(email);
        User user = repository.findByEmail(normalizedEmail)
                .orElseThrow(()-> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if(user.isEmailVerified() && user.isActive()){
            throw new CustomException("Email is already verified", HttpStatus.BAD_REQUEST);
        }

        otpService.verifyOtp(normalizedEmail, otp);

        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            log.info("Email verified for userId={}", user.getId());
        }
        
        if (!user.isActive()) {
            user.setActive(true);
            log.info("Account reactivated via verification flow: userId={}", user.getId());
            emailService.sendAccountStatusEmail(user.getEmail(), user.getFullName(), true);
        }

        repository.save(user);
    }


    @Override
    public void sendForgotPasswordOtp(String email){
        String normalizedEmail = normalizeEmail(email);
        User user = repository.findByEmail(normalizedEmail)
                .orElseThrow(()-> new CustomException("No account is found for this email", HttpStatus.NOT_FOUND));

        if(!user.isActive()){
            log.warn("Forgot password requested for inactive account: {}", email);
            // We allow sending OTP even for inactive accounts to facilitate recovery
        }

        otpService.sendForgotPasswordOtp(normalizedEmail);
        log.info("Forgot password OTP sent to {}", normalizedEmail);
    }

    @Override
    public void sendReactivationOtp(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = repository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (user.isActive()) {
            throw new CustomException("Account is already active", HttpStatus.BAD_REQUEST);
        }

        otpService.sendReactivationOtp(normalizedEmail, user.getFullName());
        log.info("Reactivation OTP sent to {}", normalizedEmail);
    }

    @Override
    public void reactivateWithOtp(String email, String otp) {
        String normalizedEmail = normalizeEmail(email);
        User user = repository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (user.isActive()) {
            throw new CustomException("Account is already active", HttpStatus.BAD_REQUEST);
        }

        otpService.verifyOtp(normalizedEmail, otp);

        user.setActive(true);
        repository.save(user);
        log.info("Account reactivated via OTP: userId={}", user.getId());
        emailService.sendAccountStatusEmail(user.getEmail(), user.getFullName(), true);
    }


    @Override
    public void resetPassword(ResetPasswordRequest request){
        String normalizedEmail = normalizeEmail(request.getEmail());
        User user = repository.findByEmail(normalizedEmail)
                .orElseThrow(()-> new CustomException("User not found", HttpStatus.NOT_FOUND));

        otpService.verifyOtp(normalizedEmail, request.getOtp());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        repository.save(user);
        log.info("Password reset successfully for userId={}", user.getId());
    }

    @Override
    public String validateToken(String token) {
        try {
            String email = jwtUtil.extractEmail(token);
            return "Valid token for user: " + email;
        } catch (Exception e) {
            throw new CustomException("Invalid or expired token", HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    public String refreshToken(String token) {
        if (!jwtUtil.isTokenValid(token)) {
            throw new CustomException("Token is invalid or expired", HttpStatus.UNAUTHORIZED);
        }
        try {
            String email = jwtUtil.extractEmail(token);
            // Verify user still exists and is active before issuing new token
            User user = repository.findByEmail(email)
                    .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
            if (!user.isActive()) {
                throw new CustomException("Account is deactivated", HttpStatus.FORBIDDEN);
            }
            return jwtUtil.generateToken(email, user.getId(), user.getRole().name());
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException("Cannot refresh token", HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    public User getUserByEmail(String email) {
        return repository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
    }

    @Override
    public User getUserById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
    }

    @Override
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUserById(userId);
        String previousFullName = user.getFullName();
        String previousUsername = user.getUsername();
        String previousBio = user.getBio();
        String normalizedUsername = normalizeUsername(request.getUsername());

        repository.findByUsernameIgnoreCase(normalizedUsername)
                .filter(existing -> !Objects.equals(existing.getId(), userId))
                .ifPresent(existing -> {
                    throw new CustomException("Username already taken", HttpStatus.BAD_REQUEST);
                });

        user.setFullName(sanitizePlainText(request.getFullname()));
        user.setUsername(normalizedUsername);
        user.setBio(sanitizeNullableText(request.getBio()));

        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(sanitizeNullableUrl(request.getAvatarUrl()));
        }

        User savedUser = repository.save(user);
        profileActivityLogService.log(savedUser, "PROFILE_UPDATED", buildProfileUpdateSummary(savedUser, previousFullName, previousUsername, previousBio));
        log.info("Profile updated for userId={}", userId);
        return savedUser;
    }

    @Override
    public User updateThemePreference(Long userId, String themePreference) {
        User user = getUserById(userId);
        String normalizedThemePreference = themePreference == null ? null : themePreference.trim().toLowerCase(Locale.ROOT);

        if (!Objects.equals(normalizedThemePreference, "light") && !Objects.equals(normalizedThemePreference, "dark")) {
            throw new CustomException("Theme preference must be either light or dark", HttpStatus.BAD_REQUEST);
        }

        if (Objects.equals(user.getThemePreference(), normalizedThemePreference)) {
            return user;
        }

        user.setThemePreference(normalizedThemePreference);

        User savedUser = repository.save(user);
        profileActivityLogService.log(savedUser, "THEME_UPDATED", "Updated application theme preference to " + normalizedThemePreference + ".");
        log.info("Theme preference updated for userId={}", userId);
        return savedUser;
    }

    @Override
    public User updateAvatar(Long userId, MultipartFile file) {
        User user = getUserById(userId);
        String avatarUrl = avatarStorageService.storeAvatar(userId, file);
        user.setAvatarUrl(avatarUrl);
        User savedUser = repository.save(user);
        profileActivityLogService.log(savedUser, "AVATAR_UPDATED", "Updated profile avatar.");
        log.info("Avatar updated for userId={}", userId);
        return savedUser;
    }

    @Override
    public List<ProfileActivityLogDto> getProfileActivity(Long userId) {
        getUserById(userId);
        return profileActivityLogService.getRecentActivity(userId)
                .stream()
                .map(ProfileActivityLogDto::from)
                .toList();
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        passwordChangeRateLimiter.checkAllowed(userId);
        User user = getUserById(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new CustomException("Old password is incorrect", HttpStatus.BAD_REQUEST);
        }

        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new CustomException("New password must be different from the current password", HttpStatus.BAD_REQUEST);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        repository.save(user);
        profileActivityLogService.log(user, "PASSWORD_CHANGED", "Changed account password.");
        log.info("Password changed for userId={}", userId);
    }

    // logout: stateless JWT has no server-side session to clear.
    // Real blacklisting requires a Redis store — adding a simple log for now.
    // To fully implement: store token in a Redis blacklist with TTL = token expiry.
    @Override
    public void logout(String token) {

        try{
            Date expiry = jwtUtil.extractAllClaims(token).getExpiration();
            long ttlSeconds = (expiry.getTime() - System.currentTimeMillis())/1000;
            if(ttlSeconds>0) {
                blacklistService.blacklist(token, ttlSeconds);
            }
        }catch (Exception e){
            log.warn("Could not blacklist token during: {}", e.getMessage());
        }
        log.info("User logged out successfully");
    }

    @Override
    public void deactivateAccount(Long id) {
        User user = getUserById(id);
        user.setActive(false);
        repository.save(user);
        profileActivityLogService.log(user, "ACCOUNT_DEACTIVATED", "Deactivated account.");
        log.info("Account deactivated for userId: {}", id);
    }

    @Override
    public List<User> searchUsers(String key) {
        // Searches by full name — matches the repository method searchByFullName
        return repository.searchByNameOrUsername(key == null ? "" : key.trim());
    }

    @Override
    public List<User> getAllUsers(){
        return repository.findAll();
    }

    @Override
    public List<User> getUsersByRole(ROLE role){
        return repository.findAllByRole(role);
    }

    @Override
    public void suspendUser(Long id){
        User user = getUserById(id);
        if(!user.isActive()){
            throw new CustomException("User is already suspended", HttpStatus.BAD_REQUEST);
        }
        user.setActive(false);
        repository.save(user);
        profileActivityLogService.log(user, "ACCOUNT_SUSPENDED", "Account suspended by administrator.");
        emailService.sendAccountStatusEmail(user.getEmail(), user.getFullName(), false);
        log.info("User suspended by admin: userId={}", id);
    }

    private String normalizeEmail(String email) {
        return requireNonBlank(email, "Email is required").toLowerCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        return requireNonBlank(username, "Username is required");
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new CustomException(message, HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private String sanitizePlainText(String value) {
        String trimmed = requireNonBlank(value, "Profile field is required");
        return trimmed.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ");
    }

    private String sanitizeNullableText(String value) {
        if (value == null) {
            return null;
        }

        String sanitized = value.trim().replaceAll("<[^>]*>", "").replaceAll("\\s+", " ");
        return sanitized.isEmpty() ? null : sanitized;
    }

    private String sanitizeNullableUrl(String value) {
        if (value == null) {
            return null;
        }

        String sanitized = value.trim();
        return sanitized.isEmpty() ? null : sanitized;
    }

    private String buildProfileUpdateSummary(User user, String previousFullName, String previousUsername, String previousBio) {
        StringBuilder summary = new StringBuilder("Updated");
        boolean appended = false;

        if (!Objects.equals(previousFullName, user.getFullName())) {
            summary.append(" full name");
            appended = true;
        }

        if (!Objects.equals(previousUsername, user.getUsername())) {
            summary.append(appended ? ", username" : " username");
            appended = true;
        }

        if (!Objects.equals(previousBio, user.getBio())) {
            summary.append(appended ? ", bio" : " bio");
            appended = true;
        }

        if (!appended) {
            return "Saved profile details.";
        }

        summary.append(".");
        return summary.toString();
    }


    @Override
    public void reactivateUser(Long id) {
        User user = getUserById(id);
        if (user.isActive()) {
            throw new CustomException("User is already active", HttpStatus.BAD_REQUEST);
        }
        user.setActive(true);
        repository.save(user);
        emailService.sendAccountStatusEmail(user.getEmail(), user.getFullName(), true);
        profileActivityLogService.log(user, "ACCOUNT_REACTIVATED", "Account reactivated by administrator.");
        log.info("User reactivated by admin: userId={}", id);
    }


    @Override
    public void updateUserRole(Long id, ROLE role) {
        User user = getUserById(id);
        user.setRole(role);
        repository.save(user);
        profileActivityLogService.log(user, "ROLE_UPDATED", "Role changed to " + role + ".");
        log.info("User role updated by admin: userId={} newRole={}", id, role);
    }

    @Override
    public com.flowBoard.auth_service.dto.AdminStatsResponse getAdminStats() {
        long totalUsers = repository.count();
        long activeToday = repository.countByLastLoginAtAfter(LocalDateTime.now().minusDays(1));
        long activeUsers = repository.countByActiveTrue();
        long suspendedUsers = repository.countByActiveFalse();
        long platformAdmins = repository.countByRole(ROLE.PLATFORM_ADMIN);
        long members = repository.countByRole(ROLE.MEMBER);

        Object[] workspaces = fetchWorkspaces();
        Object[] boards = fetchBoards();
        long totalWorkspaces = workspaces.length;
        long totalBoards = boards.length;

        return com.flowBoard.auth_service.dto.AdminStatsResponse.builder()
                .totalUsers(totalUsers)
                .activeUsersToday(activeToday)
                .activeUsers(activeUsers)
                .suspendedUsers(suspendedUsers)
                .platformAdmins(platformAdmins)
                .members(members)
                .totalWorkspaces(totalWorkspaces)
                .totalBoards(totalBoards)
                .publicWorkspaces(countByValue(workspaces, "visibility", "PUBLIC"))
                .privateWorkspaces(countByValue(workspaces, "visibility", "PRIVATE"))
                .publicBoards(countByValue(boards, "visibility", "PUBLIC"))
                .privateBoards(countByValue(boards, "visibility", "PRIVATE"))
                .openBoards(countByBoolean(boards, "closed", false))
                .closedBoards(countByBoolean(boards, "closed", true))
                .build();
    }

    // Permanent hard delete — case study §2.4
    @Override
    public void deleteUser(Long id) {
        if (!repository.existsById(id)) {
            throw new CustomException("User not found", HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
        log.info("User permanently deleted by admin: userId={}", id);
    }

    private Object[] fetchWorkspaces() {
        try {
            ResponseEntity<Object[]> response = restTemplate.exchange(
                    workspaceServiceBaseUrl + "/api/v1/workspaces/admin",
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    Object[].class
            );
            return response.getBody() == null ? new Object[0] : response.getBody();
        } catch (RestClientException ex) {
            log.warn("Failed to fetch workspace count: {}", ex.getMessage());
            return new Object[0];
        }
    }

    private Object[] fetchBoards() {
        try {
            ResponseEntity<Object[]> response = restTemplate.exchange(
                    boardServiceBaseUrl + "/api/v1/boards/admin",
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    Object[].class
            );
            return response.getBody() == null ? new Object[0] : response.getBody();
        } catch (RestClientException ex) {
            log.warn("Failed to fetch board count: {}", ex.getMessage());
            return new Object[0];
        }
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Role", "PLATFORM_ADMIN");
        return headers;
    }

    private long countByValue(Object[] records, String key, String expected) {
        return java.util.Arrays.stream(records)
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(record -> expected.equals(record.get(key)))
                .count();
    }

    private long countByBoolean(Object[] records, String key, boolean expected) {
        return java.util.Arrays.stream(records)
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(record -> record.get(key))
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast)
                .filter(value -> value == expected)
                .count();
    }
}
