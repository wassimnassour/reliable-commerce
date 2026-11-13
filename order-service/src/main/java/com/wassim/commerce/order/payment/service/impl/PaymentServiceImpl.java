package com.wassim.commerce.order.payment.service.impl;

import com.wassim.commerce.order.exaptions.BadRequest;
import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.order.enums.EOrderStatus;
import com.wassim.commerce.order.order.presisstance.OrderRepository;
import com.wassim.commerce.order.payment.domain.Payment;
import com.wassim.commerce.order.payment.dto.response.PaymentResponse;
import com.wassim.commerce.order.payment.enums.EPaymentStatus;
import com.wassim.commerce.order.payment.mapper.PaymentMapper;
import com.wassim.commerce.order.payment.persistence.PaymentRepository;
import com.wassim.commerce.order.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    @Override
    @Transactional
    public PaymentResponse initiatePayment(Long orderId, Long userId) {
        Order order = orderRepository.findToProcess(orderId, userId)
                .orElseThrow(() -> new BadRequest("Order not found"));

        if (order.getOrderStatus() != EOrderStatus.PENDING_PAYMENT) {
            throw new BadRequest("Order is not pending payment");
        }

        var pendingPayment = paymentRepository.findByOrderIdAndStatus(orderId, EPaymentStatus.PENDING);
        if (pendingPayment.isPresent()) {
            return paymentMapper.toResponse(pendingPayment.get());
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalPrice());
        payment.setCurrency(order.getCurrency());
        payment.setStatus(EPaymentStatus.PENDING);

        return paymentMapper.toResponse(paymentRepository.save(payment));
    }
    @Transactional
    @Override
    public void completePayment(Long paymentId, EPaymentStatus result) {

        if (result != EPaymentStatus.SUCCEEDED &&
                result != EPaymentStatus.FAILED) {
            throw new BadRequest("Invalid payment result");
        }

        Order order = orderRepository.findAndLockByPaymentId(paymentId)
                .orElseThrow(() -> new BadRequest("Order not found"));

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BadRequest("Payment not found"));

        if (payment.getStatus() == result) {
            return;
        }

        if (payment.getStatus() != EPaymentStatus.PENDING) {
            throw new BadRequest("Payment already completed");
        }

        if (order.getOrderStatus() != EOrderStatus.PENDING_PAYMENT) {
            throw new BadRequest("Order is not pending payment");
        }

        payment.setStatus(result);

        if (result == EPaymentStatus.SUCCEEDED) {
            order.setOrderStatus(EOrderStatus.PAID);
        }
    }
}
