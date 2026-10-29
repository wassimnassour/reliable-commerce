package com.wassim.commerce.order.order.api;

import com.wassim.commerce.order.order.dto.request.CreateRequestDto;
import com.wassim.commerce.order.order.dto.response.CreateOrderResponse;
import com.wassim.commerce.order.order.service.impl.OrderServiceImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderServiceImpl orderServiceImp;

    @GetMapping
    public String  getAllOrders(){
        return "Hello This is From Get All ORders";
    }


    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@AuthenticationPrincipal Jwt jwt,
                                                          @NotBlank @Size(max = 255) @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                           @Valid @RequestBody CreateRequestDto createRequestDto){
        Long userId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.ok(  orderServiceImp.createOrder(idempotencyKey,userId , createRequestDto));
    };

}
