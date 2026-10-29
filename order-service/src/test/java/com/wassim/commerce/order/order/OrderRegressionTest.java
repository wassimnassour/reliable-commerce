package com.wassim.commerce.order.order;

import com.wassim.commerce.order.exaptions.BadRequest;
import com.wassim.commerce.order.exaptions.GlobalExceptionHandler;
import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.order.dto.request.CreateRequestDto;
import com.wassim.commerce.order.order.mapper.OrderMapper;
import com.wassim.commerce.order.order.presisstance.OrderItemsRepository;
import com.wassim.commerce.order.order.presisstance.OrderRepository;
import com.wassim.commerce.order.order.records.OrderRequest;
import com.wassim.commerce.order.order.service.impl.OrderServiceImpl;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import com.wassim.commerce.order.user.domain.User;
import com.wassim.commerce.order.user.presistance.UserRepository;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderRegressionTest {
    @Test
    void responseMapperPreservesFractionalAmounts() {
        Order order = new Order();
        order.setId(1L);
        order.setTotalQuantity(2);
        order.setTotalPrice(new BigDecimal("199.99"));
        var response = Mappers.getMapper(OrderMapper.class).toCreateOrderResponse(order);
        assertEquals(new BigDecimal("199.99"), response.totalAmount());
        assertEquals(1L, response.orderId());
        assertEquals(2, response.totalQuantity());
    }

    @Test
    void databaseErrorsReturnGenericConflictWithoutInternalDetails() {
        String detail = "INSERT INTO users violates constraint uk_users_email_lower";
        var response = new GlobalExceptionHandler().handleDataIntegrityViolation(
                new DataIntegrityViolationException(detail, new RuntimeException("private database details")));
        assertEquals(409, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("The request conflicts with existing data.", response.getBody().get("message"));
        assertFalse(response.getBody().toString().contains(detail));
        assertFalse(response.getBody().toString().contains("private database details"));
    }

    @Test
    void nullAndFalseProductStatusRejectOrdersWithoutChangingStock() {
        for (Boolean status : new Boolean[]{null, false}) {
            var products = mock(ProductRepository.class);
            var users = mock(UserRepository.class);
            var orders = mock(OrderRepository.class);
            var items = mock(OrderItemsRepository.class);
            var service = new OrderServiceImpl(products, items, users, orders,
                    Mappers.getMapper(OrderMapper.class),
                    mock(com.wassim.commerce.order.order.presisstance.OrderIdempotencyRepository.class),
                    new tools.jackson.databind.ObjectMapper());
            Product product = new Product();
            product.setProductPrice(new BigDecimal("199.99"));
            product.setProductQuantity(10);
            product.setStatus(status);
            when(products.findById(2L)).thenReturn(Optional.of(product));
            when(users.findById(1L)).thenReturn(Optional.of(new User()));
            var request = new CreateRequestDto(List.of(new OrderRequest(2L, 1, "Test address")));
            assertThrows(BadRequest.class, () -> service.createOrder(java.util.UUID.randomUUID().toString(), 1L, request));
            assertEquals(10, product.getProductQuantity());
            verifyNoInteractions(orders, items);
        }
    }
}
