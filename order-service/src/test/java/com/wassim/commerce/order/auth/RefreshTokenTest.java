package com.wassim.commerce.order.auth;

import com.wassim.commerce.order.auth.domain.RefreshToken;
import com.wassim.commerce.order.auth.dto.request.LoginRequestDto;
import com.wassim.commerce.order.auth.dto.request.LogoutRequestDto;
import com.wassim.commerce.order.auth.dto.request.RefreshTokenRequestDto;
import com.wassim.commerce.order.auth.enums.RefreshTokenStatus;
import com.wassim.commerce.order.auth.mapper.AuthServiceMapper;
import com.wassim.commerce.order.auth.presistance.RefreshTokenRepository;
import com.wassim.commerce.order.auth.service.AuthService;
import com.wassim.commerce.order.config.JwtProperties;
import com.wassim.commerce.order.config.JwtService;
import com.wassim.commerce.order.user.domain.User;
import com.wassim.commerce.order.user.presistance.RoleRepository;
import com.wassim.commerce.order.user.presistance.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefreshTokenTest {
    final JwtService jwt = new JwtService(new JwtProperties(
            Base64.getEncoder().encodeToString(new byte[32]), 900000, 120000));
    final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final AuthenticationManager authentication = mock(AuthenticationManager.class);
    final AuthService service = new AuthService(jwt, authentication, mock(AuthServiceMapper.class),
            users, mock(PasswordEncoder.class), mock(RoleRepository.class), tokens);

    User user() {
        User user = new User();
        user.setId(1L);
        user.setEmail("alice@example.com");
        return user;
    }

    RefreshToken stored(String raw, Instant expiry, RefreshTokenStatus status) throws Exception {
        RefreshToken token = new RefreshToken();
        token.setId(1L);
        token.setSub(user().getEmail());
        token.setExpiresAt(expiry);
        token.setStatus(status);
        when(tokens.findByJti(service.hashToken(raw))).thenReturn(Optional.of(token));
        when(tokens.findSubByJti(service.hashToken(raw))).thenReturn(Optional.of(token.getSub()));
        when(users.findByEmailForUpdate(token.getSub())).thenReturn(Optional.of(user()));
        return token;
    }

    @Test
    void everyIssuanceIsUnique() {
        assertNotEquals(jwt.generateRefreshToken(user()), jwt.generateRefreshToken(user()));
    }

    @Test
    void rejectsExpiredAndRevokedTokensBeforeIssuingCredentials() throws Exception {
        stored("expired", Instant.now().minusSeconds(1), RefreshTokenStatus.ACTIVE);
        stored("revoked", Instant.now().plusSeconds(60), RefreshTokenStatus.REVOKED);
        assertThrows(BadCredentialsException.class,
                () -> service.updateRefreshToken(new RefreshTokenRequestDto("expired"), null));
        assertThrows(BadCredentialsException.class,
                () -> service.updateRefreshToken(new RefreshTokenRequestDto("revoked"), null));
        verify(tokens, never()).save(any());
        verify(tokens, never()).deleteById(any());
    }

    @Test
    void rotatesActiveTokenAndStoresActualJwtExpiry() throws Exception {
        String raw = jwt.generateRefreshToken(user());
        stored(raw, Instant.now().plusSeconds(60), RefreshTokenStatus.ACTIVE);
        var result = service.updateRefreshToken(new RefreshTokenRequestDto(raw), null);
        assertNotEquals(raw, result.get("refreshToken"));
        verify(tokens).deleteById(1L);
        var saved = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).save(saved.capture());
        assertEquals(jwt.extractExpiration(result.get("refreshToken")).toInstant(), saved.getValue().getExpiresAt());
        assertEquals(service.hashToken(result.get("refreshToken")), saved.getValue().getJti());
    }

    @Test
    void successfulLoginRemovesPreviousTokensBeforeSavingNewOne() throws Exception {
        when(authentication.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(user(), null));
        when(users.findByEmailForUpdate(user().getEmail())).thenReturn(Optional.of(user()));
        service.authenticate(new LoginRequestDto(user().getEmail(), "password"));
        var order = inOrder(users, tokens);
        order.verify(users).findByEmailForUpdate(user().getEmail());
        order.verify(tokens).deleteAllBySub(user().getEmail());
        order.verify(tokens).save(any(RefreshToken.class));
    }

    @Test
    void rejectsTokenRemovedWhileWaitingForAccountLock() throws Exception {
        stored("old", Instant.now().plusSeconds(60), RefreshTokenStatus.ACTIVE);
        when(tokens.findByJti(service.hashToken("old"))).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class,
                () -> service.updateRefreshToken(new RefreshTokenRequestDto("old"), null));
        var order = inOrder(users, tokens);
        order.verify(tokens).findSubByJti(service.hashToken("old"));
        order.verify(users).findByEmailForUpdate(user().getEmail());
        order.verify(tokens).findByJti(service.hashToken("old"));
        verify(tokens, never()).save(any());
    }

    @Test
    void logoutRevokesTheStoredToken() throws Exception {
        RefreshToken token = stored("logout", Instant.now().plusSeconds(60), RefreshTokenStatus.ACTIVE);
        service.logout(new LogoutRequestDto("logout"));
        assertEquals(RefreshTokenStatus.REVOKED, token.getStatus());
        verify(tokens).save(token);
    }

    @Test
    void failedLoginDoesNotRevokeExistingSessions() {
        when(authentication.authenticate(any())).thenThrow(new BadCredentialsException("Invalid credentials"));
        assertThrows(BadCredentialsException.class,
                () -> service.authenticate(new LoginRequestDto(user().getEmail(), "wrong")));
        verifyNoInteractions(tokens);
    }
}
