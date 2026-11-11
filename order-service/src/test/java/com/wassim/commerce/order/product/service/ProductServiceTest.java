package com.wassim.commerce.order.product.service;

import com.wassim.commerce.order.TestcontainersConfiguration;
import com.wassim.commerce.order.enums.Currency;
import com.wassim.commerce.order.exaptions.NotFoundException;
import com.wassim.commerce.order.fixtures.CommerceFixtures;
import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.order.enums.EOrderStatus;
import com.wassim.commerce.order.order.presisstance.OrderRepository;
import com.wassim.commerce.order.payment.domain.Payment;
import com.wassim.commerce.order.payment.dto.response.PaymentResponse;
import com.wassim.commerce.order.payment.enums.EPaymentStatus;
import com.wassim.commerce.order.payment.persistence.PaymentRepository;
import com.wassim.commerce.order.payment.service.PaymentService;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import com.wassim.commerce.order.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Import({TestcontainersConfiguration.class , CommerceFixtures.class})
@SpringBootTest
class ProductServiceTest {
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    CommerceFixtures  commerceFixtures;

    @Autowired
    private ProductService productService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private  PaymentRepository paymentRepository;
    @Autowired
    private OrderRepository orderRepository;

    @Test
    void getOneProductWithRightId() {
        Product product = new Product();
        product.setProductName("Coffee");
        product.setProductDescription("Beans");
        product.setProductPrice(new BigDecimal("100.00"));
        product.setProductQuantity(1);
        product.setStatus(true);
        Product saved = productRepository.saveAndFlush(product);

        var response = productService.getProductById(saved.getId());
        assertEquals(saved.getId(), response.id());
        assertEquals("Coffee", response.name());
        assertEquals("Beans", response.description());
        assertEquals(new BigDecimal("100.00"), response.price());
        assertEquals(1, response.stockQuantity());
        assertEquals(Currency.MAD, response.currency());
        assertEquals(true, response.status());
    }

    @Test
    void getOneProductWithWrongId() {
        long id = Long.MAX_VALUE;
        var exception = assertThrows(NotFoundException.class, () -> productService.getProductById(id));
        assertEquals("Can't find product with id " + id, exception.getMessage());
    }

    @Test
    public void test_Payment_initiate(){

        User user = commerceFixtures.createUser();
        Product product = commerceFixtures.createProduct();
        Order order = commerceFixtures.createOrder(user , product , EOrderStatus.PENDING_PAYMENT);


        Optional<Payment> payment = paymentRepository.findByOrderIdAndStatus(order.getId(), EPaymentStatus.PENDING);
        assertFalse(payment.isPresent());

        paymentService.initiatePayment(order.getId() , user.getId());
        Optional<Payment> paymentAfter = paymentRepository.findByOrderIdAndStatus(order.getId(), EPaymentStatus.PENDING);

        assertTrue(paymentAfter.isPresent());
    }

    @Test
    public void test_Payment_concurrent(){



        User user = commerceFixtures.createUser();
        Product product = commerceFixtures.createProduct();
        Order order = commerceFixtures.createOrder(user , product , EOrderStatus.PENDING_PAYMENT);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);


        Callable<PaymentResponse> task = ()->{
            barrier.await(5 , TimeUnit.SECONDS);
            return paymentService.initiatePayment(order.getId() , user.getId());
        };

        try {
            Future<PaymentResponse> future1 =  executorService.submit(task);
            Future<PaymentResponse> future2= executorService.submit(task);
            assertEquals(future1.get(   ).paymentId() ,future2.get().paymentId() );
            PaymentResponse response1 =
                    future1.get(10, TimeUnit.SECONDS);

            PaymentResponse response2 =
                    future2.get(10, TimeUnit.SECONDS);

            // Both requests return the same payment
            assertEquals(
                    response1.paymentId(),
                    response2.paymentId()
            );

            // Database contains only one pending payment
            assertEquals(
                    1L,
                    paymentRepository.countByOrderIdAndStatus(
                            order.getId(),
                            EPaymentStatus.PENDING
                    )
            );


        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }

    }
}
