package com.wassim.commerce.order.config;

import com.wassim.commerce.order.user.domain.User;
import com.wassim.commerce.order.user.domain.Role;
import com.wassim.commerce.order.user.presistance.UserRepository;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JwtRequestSecurityTest {
    @Test
    void signedTokensAuthenticateOnlyExistingUsersAndOnlyForAccess() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestConfig.class);
            context.refresh();
            var users = context.getBean(TestConfig.class).users;
            User user = new User();
            user.setId(42L);
            user.setRoles(List.of());
            users.set(Optional.of(user));
            var tokens = new JwtService(context.getBean(JwtProperties.class));
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();

            mvc.perform(get("/api/products").header("Authorization", "Bearer " + tokens.generateAccessToken(user)))
                    .andExpect(status().isOk()).andExpect(content().string("42"));

            Role ordinaryRole = new Role();
            ordinaryRole.setName("USER");
            ordinaryRole.setPermissions(List.of());
            user.setRoles(List.of(ordinaryRole));
            String accessToken = tokens.generateAccessToken(user);
            mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isForbidden());

            Role adminRole = new Role();
            adminRole.setName("ADMIN");
            adminRole.setPermissions(List.of());
            user.setRoles(List.of(adminRole));
            mvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk());

            user.setRoles(List.of(ordinaryRole));
            mvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isForbidden());

            mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/products").header("Authorization", "Bearer " + tokens.generateRefreshToken(user)))
                    .andExpect(status().isUnauthorized());

            users.set(Optional.empty());
            mvc.perform(get("/api/products").header("Authorization", "Bearer " + tokens.generateAccessToken(user)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, DatabaseJwtAuthenticationConverter.class, ProbeController.class})
    static class TestConfig {
        final AtomicReference<Optional<User>> users = new AtomicReference<>(Optional.empty());
        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(Base64.getEncoder().encodeToString(new byte[32]), 900000, 604800000);
        }

        @Bean
        org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
            return username -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException(username); };
        }

        @Bean
        UserRepository userRepository() {
            return (UserRepository) Proxy.newProxyInstance(UserRepository.class.getClassLoader(),
                    new Class<?>[]{UserRepository.class}, (proxy, method, args) -> {
                        if (method.getName().equals("findById")) {
                            return Long.valueOf(42L).equals(args[0]) ? users.get() : Optional.empty();
                        }
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("equals")) return proxy == args[0];
                        if (method.getName().equals("toString")) return "TestUserRepository";
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/api/products", "/api/admin/products"})
        String products(Authentication authentication) {
            return authentication.getName();
        }
    }
}
