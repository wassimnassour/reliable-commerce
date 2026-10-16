package com.wassim.commerce.order.product.service;


import com.wassim.commerce.order.exaptions.BadRequest;
import com.wassim.commerce.order.exaptions.NotFoundException;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.mapper.ProductMapper;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import jakarta.validation.constraints.AssertTrue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.math.BigDecimal;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
public class ProductServiceTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductMapper productMapper;

    @Test
    void getOneProductWithRightId(){
        productRepository.deleteAll();
        productRepository.findAll();
        Product product = new Product();
        product.setProductName("test");
        product.setProductDescription("test");
        product.setProductPrice(BigDecimal.valueOf(100));
        product.setProductQuantity(1);

        productRepository.saveAndFlush(product);
        productRepository.findAll();

        Assertions.assertNotNull(productService.getProductById(product.getId()));

        Assertions.assertEquals( productMapper.toOneProductResponse(product), productService.getProductById(product.getId()));

    }

    @Test
    void getOneProductWithWrongId(){
        productRepository.deleteAll();
        Long id = 9292L;
        Exception exception= Assertions.assertThrows(NotFoundException.class, ()-> productService.getProductById(id));
        Assertions.assertEquals( exception.getMessage(),"Can't find product with id "+id);


    }
}
