package com.wassim.commerce.order.auth.api;

import com.wassim.commerce.order.auth.dto.request.LoginRequestDto;
import com.wassim.commerce.order.auth.dto.request.LogoutRequestDto;
import com.wassim.commerce.order.auth.dto.request.RefreshTokenRequestDto;
import com.wassim.commerce.order.auth.dto.request.RegisterRequestDto;
import com.wassim.commerce.order.auth.dto.response.AuthResponseDto;
import com.wassim.commerce.order.auth.service.AuthService;
import com.wassim.commerce.order.user.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.NoSuchAlgorithmException;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private  final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto loginRequestDto) throws NoSuchAlgorithmException {

      return ResponseEntity.ok(authService.authenticate(loginRequestDto));
    };

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> signup(@Valid @RequestBody RegisterRequestDto registerRequestDto) throws NoSuchAlgorithmException {
        return  ResponseEntity.ok( authService.register(registerRequestDto));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshToken(
            @Valid @RequestBody RefreshTokenRequestDto refreshTokenRequestDto,
            Authentication authentication
    ) throws NoSuchAlgorithmException {

        return   ResponseEntity.ok(  authService.updateRefreshToken(refreshTokenRequestDto , authentication));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody LogoutRequestDto logoutRequestDto) throws NoSuchAlgorithmException {

        authService.logout(logoutRequestDto);
        return ResponseEntity.ok().build();
    }
}
