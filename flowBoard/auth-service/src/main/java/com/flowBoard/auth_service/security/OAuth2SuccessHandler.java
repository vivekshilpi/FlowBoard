package com.flowBoard.auth_service.security;

import com.flowBoard.auth_service.entity.User;
import com.flowBoard.auth_service.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(
                        "OAuth user not found after login: " + email));

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generateToken(
                user.getEmail(), user.getId(), user.getRole().name());

        log.info("OAuth2 login success: email={} userId={}", email, user.getId());
        String frontendBaseUrl = resolveFrontendBaseUrl(request);
        String frontendUrl = frontendBaseUrl + "/oauth2/callback?token=" + token
                + "&userId=" + user.getId()
                + "&role=" + user.getRole().name();

        response.sendRedirect(frontendUrl);
    }

    private String resolveFrontendBaseUrl(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            try {
                URI uri = new URI(referer);
                String host = uri.getHost();
                int port = uri.getPort();
                if (("localhost".equals(host) || "127.0.0.1".equals(host)) && port == 4200) {
                    return uri.getScheme() + "://" + host + ":" + port;
                }
            } catch (URISyntaxException exception) {
                log.warn("Could not parse OAuth referer: {}", referer, exception);
            }
        }

        return "http://localhost:4200";
    }
}
