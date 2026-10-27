package com.wassim.commerce.order.order.service;


import com.wassim.commerce.order.TestcontainersConfiguration;
import com.wassim.commerce.order.exaptions.BadRequest;
import com.wassim.commerce.order.fixtures.GenerateProducts;
import com.wassim.commerce.order.fixtures.GenerateRoles;
import com.wassim.commerce.order.fixtures.GenerateUser;
import com.wassim.commerce.order.order.dto.request.CreateRequestDto;
import com.wassim.commerce.order.order.dto.response.CreateOrderResponse;
import com.wassim.commerce.order.order.presisstance.OrderRepository;
import com.wassim.commerce.order.order.records.OrderRequest;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import com.wassim.commerce.order.user.domain.Role;
import com.wassim.commerce.order.user.domain.User;
import com.wassim.commerce.order.user.presistance.RoleRepository;
import com.wassim.commerce.order.user.presistance.UserRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
public class OrderServiceTest {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    private OrderService orderService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    private TestcontainersConfiguration testcontainersConfiguration;



    @Test
    public void TestCreateOrderWithGoodInputs(){
        User generateUser = GenerateUser.generateUser();
        com.wassim.commerce.order.product.domain.Product product = GenerateProducts.generateGoodProduct();
        List<Role> roles = roleRepository.findAll();
        generateUser.setRoles(roles);
        roleRepository.saveAll(roles);

        userRepository.saveAndFlush(generateUser);
        Product newProduct  = productRepository.saveAndFlush(product);
        userRepository.findAll();

      int  quantity= 3;

        CreateRequestDto requestDto  =new  CreateRequestDto(
                List.of(
                        new OrderRequest(
                                newProduct.getId(),
                                quantity,
                                "Adress"
                        )
                )

        );

       CreateOrderResponse response  = orderService.createOrder(generateUser.getId(), requestDto);

        Assertions.assertNotNull(response);
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isNotNull();
        assertThat(response.totalAmount()).isEqualTo(newProduct.getProductPrice().multiply(BigDecimal.valueOf(quantity)));
        assertThat(response.totalQuantity()).isEqualTo(quantity);
        Product updatedProduct = productRepository
                .findById(product.getId())
                .orElseThrow();

        assertThat(updatedProduct.getProductQuantity())
                .isEqualTo(97);

        assertThat(orderRepository.existsById(response.orderId()))
                .isTrue();
    }



    @Test
    public void TestCreateOrderWithStatusNotActive(){
        User generateUser = GenerateUser.generateUser();
        Product product = GenerateProducts.generateProductStatusDisabled();
        Product newProduct =  productRepository.saveAndFlush(product);
        userRepository.saveAndFlush(generateUser);


        int  quantity= 3;
        CreateRequestDto requestDto  =new  CreateRequestDto(
                List.of(
                        new OrderRequest(
                                newProduct.getId(),
                                quantity,
                                "Adress"
                        )
                )

        );

        Runnable createOrder = ()->  orderService.createOrder(generateUser.getId(), requestDto);
        assertThatThrownBy(createOrder::run).isInstanceOf(BadRequest.class).hasMessage("Product  is not available"+ newProduct.getId() );
    }
}
