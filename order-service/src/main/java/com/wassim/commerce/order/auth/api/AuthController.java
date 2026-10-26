package com.wassim.commerce.order.auth.api;

import com.wassim.commerce.order.auth.dto.request.LoginRequestDto;
import com.wassim.commerce.order.auth.dto.request.RegisterRequestDto;
import com.wassim.commerce.order.auth.dto.response.AuthResponseDto;
import com.wassim.commerce.order.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private  final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto loginRequestDto) {

      return ResponseEntity.ok(authService.authenticate(loginRequestDto));
    };

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> signup(@Valid @RequestBody RegisterRequestDto registerRequestDto) {
        return  ResponseEntity.ok( authService.register(registerRequestDto));
    }

    @PostMapping("/logout")
    public String logout() {
        return "logout";
    }
}
