package com.wassim.commerce.order.auth;

import com.wassim.commerce.order.auth.dto.request.LoginRequestDto;
import com.wassim.commerce.order.auth.dto.request.RefreshTokenRequestDto;
import com.wassim.commerce.order.auth.dto.request.RegisterRequestDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CredentialValidationTest {
    static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    static final Validator validator = factory.getValidator();

    @AfterAll
    static void close() { factory.close(); }

    @Test
    void rejectsMissingBlankAndMalformedRegistrationInputs() {
        assertFalse(validator.validate(new RegisterRequestDto(null, null)).isEmpty());
        assertFalse(validator.validate(new RegisterRequestDto(" ", " ")).isEmpty());
        assertFalse(validator.validate(new RegisterRequestDto("long-password", "not-an-email")).isEmpty());
        assertFalse(validator.validate(new RegisterRequestDto("short", "alice@example.com")).isEmpty());
    }

    @Test
    void rejectsMissingAndBlankRefreshTokens() {
        assertFalse(validator.validate(new RefreshTokenRequestDto(null)).isEmpty());
        assertFalse(validator.validate(new RefreshTokenRequestDto(" ")).isEmpty());
        assertTrue(validator.validate(new RefreshTokenRequestDto("token")).isEmpty());
    }

    @Test
    void enforcesBcryptByteBoundaryIncludingUnicode() {
        assertTrue(validator.validate(new RegisterRequestDto("a".repeat(72), "alice@example.com")).isEmpty());
        assertFalse(validator.validate(new RegisterRequestDto("a".repeat(73), "alice@example.com")).isEmpty());
        assertTrue(validator.validate(new RegisterRequestDto("é".repeat(36), "alice@example.com")).isEmpty());
        assertFalse(validator.validate(new RegisterRequestDto("é".repeat(37), "alice@example.com")).isEmpty());
        assertFalse(validator.validate(new LoginRequestDto("alice@example.com", "é".repeat(37))).isEmpty());
    }

    @Test
    void loginValidatesAtRuntimeWithoutRejectingLegacyShortPasswords() {
        assertFalse(validator.validate(new LoginRequestDto(null, null)).isEmpty());
        assertFalse(validator.validate(new LoginRequestDto(" ", " ")).isEmpty());
        assertFalse(validator.validate(new LoginRequestDto("not-an-email", "long-password")).isEmpty());
        assertFalse(validator.validate(new LoginRequestDto("alice@example.com", "a".repeat(73))).isEmpty());
        assertTrue(validator.validate(new LoginRequestDto("alice@example.com", "a".repeat(72))).isEmpty());
        assertTrue(validator.validate(new LoginRequestDto("alice@example.com", "short")).isEmpty());
        assertTrue(validator.validate(new RegisterRequestDto("long-password", "alice@example.com")).isEmpty());
    }
}
