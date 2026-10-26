package com.wassim.commerce.order.auth.domain;

import com.wassim.commerce.order.auth.enums.RefreshTokenStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity(name = "refresh_token")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String  jti;

    private Instant expiresAt;

    private String sub;

    @Enumerated(EnumType.STRING)
    private RefreshTokenStatus status;
}
