package com.flowBoard.auth_service.security;

import com.flowBoard.auth_service.entity.ROLE;
import com.flowBoard.auth_service.entity.User;
import com.flowBoard.auth_service.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User oauth2User;

    @Test
    void redirectsToFrontendUsingLocalReferer() throws IOException {
        User user = User.builder()
                .id(3L)
                .email("oauth@example.com")
                .role(ROLE.MEMBER)
                .active(true)
                .build();
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(jwtUtil, userRepository);
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(oauth2User.getAttribute("email")).thenReturn("oauth@example.com");
        when(userRepository.findByEmail("oauth@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("oauth@example.com", 3L, "MEMBER")).thenReturn("jwt");
        when(request.getHeader("Referer")).thenReturn("http://localhost:4200/login");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(userRepository).save(user);
        verify(response).sendRedirect("http://localhost:4200/oauth2/callback?token=jwt&userId=3&role=MEMBER");
    }

    @Test
    void malformedRefererFallsBackToDefaultFrontend() throws IOException {
        User user = User.builder()
                .id(4L)
                .email("oauth@example.com")
                .role(ROLE.PLATFORM_ADMIN)
                .active(true)
                .build();
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(jwtUtil, userRepository);
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(oauth2User.getAttribute("email")).thenReturn("oauth@example.com");
        when(userRepository.findByEmail("oauth@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("oauth@example.com", 4L, "PLATFORM_ADMIN")).thenReturn("jwt2");
        when(request.getHeader("Referer")).thenReturn("%%%not-a-uri");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect("http://localhost:4200/oauth2/callback?token=jwt2&userId=4&role=PLATFORM_ADMIN");
    }

    @Test
    void missingPersistedUserThrows() {
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(jwtUtil, userRepository);
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(oauth2User.getAttribute("email")).thenReturn("missing@example.com");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.onAuthenticationSuccess(request, response, authentication))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OAuth user not found");
    }
}
