package com.wassim.commerce.order.auth.service;

import com.wassim.commerce.order.auth.domain.RefreshToken;
import com.wassim.commerce.order.auth.dto.request.LoginRequestDto;
import com.wassim.commerce.order.auth.dto.request.LogoutRequestDto;
import com.wassim.commerce.order.auth.dto.request.RefreshTokenRequestDto;
import com.wassim.commerce.order.auth.dto.request.RegisterRequestDto;
import com.wassim.commerce.order.auth.dto.response.AuthResponseDto;
import com.wassim.commerce.order.auth.enums.RefreshTokenStatus;
import com.wassim.commerce.order.auth.mapper.AuthServiceMapper;
import com.wassim.commerce.order.auth.presistance.RefreshTokenRepository;
import com.wassim.commerce.order.config.JwtService;
import com.wassim.commerce.order.user.domain.Role;
import com.wassim.commerce.order.user.domain.User;
import com.wassim.commerce.order.user.presistance.RoleRepository;
import com.wassim.commerce.order.user.presistance.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@RequiredArgsConstructor
@Service
public class AuthService {
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;
        private final AuthServiceMapper authServiceMapper;
        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final RoleRepository roleRepository;
        private final RefreshTokenRepository refreshTokenRepository;

        @Transactional
        public AuthResponseDto authenticate(LoginRequestDto loginRequestDto) throws NoSuchAlgorithmException {
                Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(loginRequestDto.email(),
                                                loginRequestDto.password()));
                authentication.isAuthenticated();

                User principal = (User) authentication.getPrincipal();
                User uuser = userRepository.findByEmailForUpdate(principal.getUsername())
                                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

                String token = jwtService.generateAccessToken(uuser);
                String refreshToken = jwtService.generateRefreshToken(uuser);

                refreshTokenRepository.deleteAllBySub(uuser.getUsername());
                saveRefreshToken(uuser, refreshToken);

                return authServiceMapper.toAuthResponseDto(
                                uuser,
                                token,
                                refreshToken);
        }

        @Transactional
        public AuthResponseDto register(RegisterRequestDto registerRequestDto) throws NoSuchAlgorithmException {
                Role role = roleRepository.findByName("USER");
                User user = new User();

                authServiceMapper.createUserFromDto(
                                user,
                                registerRequestDto);
                user.setPassword(passwordEncoder.encode(registerRequestDto.password()));
                user.setRoles(List.of(role));

                userRepository.save(user);

                String token = jwtService.generateAccessToken(user);
                String refreshToken = jwtService.generateRefreshToken(user);
                saveRefreshToken(user, refreshToken);

                return authServiceMapper.toAuthResponseDto(user, token, refreshToken);
        }

        @Transactional
        public Map<String, String> updateRefreshToken(RefreshTokenRequestDto refreshTokenRequestDto,
                        Authentication authentication) throws NoSuchAlgorithmException {

                String hashedToken = hashToken(refreshTokenRequestDto.refreshToken());
                User user = lockRefreshTokenOwner(hashedToken);
                RefreshToken refreshTokenResults = refreshTokenRepository.findByJti(hashedToken).orElseThrow(
                                () -> new BadCredentialsException("Invalid refresh token"));
                if (refreshTokenResults.getStatus() != RefreshTokenStatus.ACTIVE
                                || !Instant.now().isBefore(refreshTokenResults.getExpiresAt())) {
                        throw new BadCredentialsException("Invalid refresh token");
                }

                refreshTokenRepository.deleteById(refreshTokenResults.getId());

                String newRefreshToken = jwtService.generateRefreshToken((user));
                String token = jwtService.generateAccessToken(user);

                saveRefreshToken(user, newRefreshToken);

                return Map.of(
                                "refreshToken", newRefreshToken,
                                "token", token

                );
        }

        @Transactional
        public void logout(LogoutRequestDto logoutRequestDto) throws NoSuchAlgorithmException {
                String hashedToken = hashToken(logoutRequestDto.refreshToken());

                lockRefreshTokenOwner(hashedToken);
                RefreshToken tokenEntity = refreshTokenRepository.findByJti(hashedToken)
                                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

                tokenEntity.setStatus(RefreshTokenStatus.REVOKED);
                refreshTokenRepository.save(tokenEntity);

        }

        private User lockRefreshTokenOwner(String hashedToken) {
                String email = refreshTokenRepository.findSubByJti(hashedToken)
                                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
                return userRepository.findByEmailForUpdate(email)
                                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        }

        private void saveRefreshToken(User user, String rawRefreshToken) throws NoSuchAlgorithmException {
                String hashedToken = hashToken(rawRefreshToken);

                RefreshToken tokenEntity = new RefreshToken();
                tokenEntity.setJti(hashedToken);
                tokenEntity.setSub(user.getUsername());
                tokenEntity.setStatus(RefreshTokenStatus.ACTIVE);

                tokenEntity.setExpiresAt(jwtService.extractExpiration(rawRefreshToken).toInstant());
                refreshTokenRepository.save(tokenEntity);
        }

        public String hashToken(String token) throws NoSuchAlgorithmException {
                byte[] hash = MessageDigest
                                .getInstance("SHA-256")
                                .digest(token.getBytes(StandardCharsets.UTF_8));

                return HexFormat.of().formatHex(hash);
        }

}
