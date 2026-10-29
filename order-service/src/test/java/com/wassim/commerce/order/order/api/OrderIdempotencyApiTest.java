package com.wassim.commerce.order.order.api;

import com.wassim.commerce.order.exaptions.GlobalExceptionHandler;
import com.wassim.commerce.order.exaptions.IdempotencyConflictException;
import com.wassim.commerce.order.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderIdempotencyApiTest {
    final OrderServiceImpl service = mock(OrderServiceImpl.class);
    final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    final String body = """
            {"products":[{"productId":1,"quantity":1,"adress":"Test address"}]}
            """;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(service))
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == Jwt.class;
                    }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory factory) {
                        return Jwt.withTokenValue("test").header("alg", "HS256").subject("1").build();
                    }
                }).build();
    }

    @AfterEach
    void close() { validator.close(); }

    @Test
    void missingBlankAndOversizedKeysReturn400BeforeCallingService() throws Exception {
        mvc.perform(post("/orders").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        for (String key : new String[]{" ", "a".repeat(256)}) {
            mvc.perform(post("/orders").header("Idempotency-Key", key)
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void changedPayloadReturns409() throws Exception {
        when(service.createOrder(eq("used-key"), eq(1L), any()))
                .thenThrow(new IdempotencyConflictException());
        mvc.perform(post("/orders").header("Idempotency-Key", "used-key")
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
