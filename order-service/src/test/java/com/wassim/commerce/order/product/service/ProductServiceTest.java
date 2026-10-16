package com.wassim.commerce.order.product.service;

import com.wassim.commerce.order.TestcontainersConfiguration;
import com.wassim.commerce.order.enums.Currency;
import com.wassim.commerce.order.exaptions.NotFoundException;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ProductServiceTest {
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Test
    void getOneProductWithRightId() {
        Product product = new Product();
        product.setProductName("Coffee");
        product.setProductDescription("Beans");
        product.setProductPrice(new BigDecimal("100.00"));
        product.setProductQuantity(1);
        Product saved = productRepository.saveAndFlush(product);

        var response = productService.getProductById(saved.getId());
        assertEquals(saved.getId(), response.id());
        assertEquals("Coffee", response.name());
        assertEquals("Beans", response.description());
        assertEquals(new BigDecimal("100.00"), response.price());
        assertEquals(1, response.stockQuantity());
        assertEquals(Currency.MAD, response.currency());
    }

    @Test
    void getOneProductWithWrongId() {
        long id = Long.MAX_VALUE;
        var exception = assertThrows(NotFoundException.class, () -> productService.getProductById(id));
        assertEquals("Can't find product with id " + id, exception.getMessage());
    }
}
