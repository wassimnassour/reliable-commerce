package com.wassim.commerce.order.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.SecretKey;
import java.util.Base64;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

     @Bean
     public SecurityFilterChain SecurityFilterChain(HttpSecurity http,
             DatabaseJwtAuthenticationConverter authenticationConverter) throws Exception {
         http.csrf(crsf-> crsf.disable()).authorizeHttpRequests(
                 request->
                          request.requestMatchers("/auth/**").permitAll()
                         .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                  .anyRequest().authenticated()
         )   .sessionManagement(session -> session
                         .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                 )
                 .oauth2ResourceServer(resourceServer -> resourceServer
                         .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
                 );
         ;
         return http.build();
     }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManagerBean(AuthenticationConfiguration authenticationConfiguration ) throws Exception {
         return authenticationConfiguration.getAuthenticationManager();

    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        byte[] keyBytes = Base64.getDecoder()
                .decode(properties.secret());

        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtClaimValidator<String> accessOnly =
                new JwtClaimValidator<>("token_use", "access"::equals);

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefault(),
                        accessOnly
                )
        );

        return decoder;
    }
}
