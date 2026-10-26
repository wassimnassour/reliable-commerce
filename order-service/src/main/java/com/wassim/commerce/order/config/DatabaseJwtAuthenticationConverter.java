package com.wassim.commerce.order.config;

import com.wassim.commerce.order.user.domain.User;
import com.wassim.commerce.order.user.presistance.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;


@Component
@RequiredArgsConstructor
public class DatabaseJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId;

        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new InvalidBearerTokenException(
                    "Invalid user ID in token"
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new InvalidBearerTokenException(
                                "User no longer exists"
                        )
                );

        return new JwtAuthenticationToken(
                jwt,
                user.getAuthorities(),
                user.getId().toString()
        );
    }
}