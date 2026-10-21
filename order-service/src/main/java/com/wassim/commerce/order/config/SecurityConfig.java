package com.wassim.commerce.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.server.SecurityWebFilterChain;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

     @Bean
     public SecurityFilterChain SecurityFilterChain(HttpSecurity http) throws Exception {
         http.csrf(crsf-> crsf.disable()).authorizeHttpRequests(
                 request->
                          request.requestMatchers("/api/").permitAll()
                                  .requestMatchers("/auth/**").permitAll()
                         .requestMatchers("/api/admin/**").authenticated()
         );
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

}
