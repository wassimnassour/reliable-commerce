package com.wassim.commerce.order.order.service.impl;

import com.wassim.commerce.order.exaptions.BadRequest;
import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.order.domain.OrderItems;
import com.wassim.commerce.order.order.dto.request.CreateRequestDto;
import com.wassim.commerce.order.order.dto.response.CreateOrderResponse;
import com.wassim.commerce.order.order.mapper.OrderMapper;
import com.wassim.commerce.order.order.presisstance.OrderItemsRepository;
import com.wassim.commerce.order.order.presisstance.OrderRepository;
import com.wassim.commerce.order.order.records.OrderRequest;
import com.wassim.commerce.order.order.service.OrderService;
import com.wassim.commerce.order.product.api.dto.request.CreateProductDto;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import com.wassim.commerce.order.user.domain.User;
import com.wassim.commerce.order.user.presistance.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class OrderServiceImpl implements OrderService {
    private  final ProductRepository productRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private  final OrderMapper orderMapper;

    @Transactional
    public CreateOrderResponse createOrder(Long userId, CreateRequestDto createRequestDto){

        boolean hasDuplicates =  createRequestDto.products().stream().collect(
                Collectors.groupingBy(OrderRequest::productId, Collectors.counting())
        ).values().stream().anyMatch(count -> count > 1);

        if(hasDuplicates){
            throw new BadRequest("Duplicate product ids found");
        }


        User user = userRepository.findById(userId).orElseThrow(
                () -> new BadRequest("User not found")
        );

        List<OrderItems> orderItemsList = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        Integer totalQuantity = 0;

        Order order = new Order();
        order.setUser(user);

        for(OrderRequest pr : createRequestDto.products() ){
            Product product= productRepository.findById(pr.productId()).orElseThrow(() ->
                    new BadRequest("Product with id " + pr.productId() + " is not found"));


            BigDecimal totalAmountOfOneProduct =  product.getProductPrice().multiply(BigDecimal.valueOf(pr.quantity()));
            if(!product.getStatus()){
                throw new BadRequest("Product  is not available"+ pr.productId());
            }


            if (pr.quantity() > product.getProductQuantity()) {
                throw new BadRequest("Insufficient stock for this product" + pr.productId());
            }
            product.setProductQuantity(
                    product.getProductQuantity() - pr.quantity()
            );
            OrderItems orderItems = new OrderItems();
            orderItems.setProduct(product);
            orderItems.setQuantity(pr.quantity());
            orderItems.setTotalPrice(totalAmountOfOneProduct);
            orderItems.setOrder(order);
            orderItemsList.add(orderItems);
            totalAmount = totalAmount.add(totalAmountOfOneProduct);
            totalQuantity = totalQuantity+ pr.quantity();
        }


         order.setOrderDate(Instant.now());
         order.setTotalPrice(totalAmount);
         order.setTotalQuantity(totalQuantity);
         order.setOrderItems(orderItemsList);

         orderRepository.save(order);

        orderItemsRepository.saveAll(orderItemsList);


        return  orderMapper.toCreateOrderResponse(
                order
        );
    }
}
