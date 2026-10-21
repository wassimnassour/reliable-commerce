package com.wassim.commerce.order.auth.service;

import com.wassim.commerce.order.auth.dto.request.LoginRequestDto;
import com.wassim.commerce.order.auth.dto.request.RegisterRequestDto;
import com.wassim.commerce.order.auth.dto.response.AuthResponseDto;
import com.wassim.commerce.order.auth.mapper.AuthServiceMapper;
import com.wassim.commerce.order.config.JwtProperties;
import com.wassim.commerce.order.config.JwtService;
import com.wassim.commerce.order.exaptions.BadRequest;
import com.wassim.commerce.order.user.domain.Role;
import com.wassim.commerce.order.user.domain.User;
import com.wassim.commerce.order.user.presistance.RoleRepository;
import com.wassim.commerce.order.user.presistance.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Service
public class AuthService {
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private  final AuthServiceMapper authServiceMapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;

    public AuthService(JwtService jwtService,RoleRepository roleRepository,  PasswordEncoder passwordEncoder
            , AuthenticationManager authenticationManager , AuthServiceMapper authServiceMapper, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.authServiceMapper = authServiceMapper;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
    }


     public AuthResponseDto authenticate(LoginRequestDto loginRequestDto) {
            Authentication authentication= authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequestDto.username() , loginRequestDto.password())
            );
            authentication.isAuthenticated();

            User uuser = (User) authentication.getPrincipal();
            String token= jwtService.generateAccessToken(uuser);
            String refreshToken= jwtService.generateRefreshToken(uuser);

            return authServiceMapper.toAuthResponseDto(
                    uuser,
                    token,
                    refreshToken
            );
     }

     @Transactional
     public AuthResponseDto register(RegisterRequestDto  registerRequestDto) {
         Role role = roleRepository.findByName("USER");
        User user = new User();

         authServiceMapper.createUserFromDto(
                 user ,
                 registerRequestDto
         );
        user.setPassword(passwordEncoder.encode(registerRequestDto.password()));
        user.setRoles(List.of(role));

         userRepository.save(user);

         String token= jwtService.generateAccessToken(user);
         String refreshToken =  jwtService.generateRefreshToken(user);

         return  authServiceMapper.toAuthResponseDto(user , token , refreshToken);
     }
}
