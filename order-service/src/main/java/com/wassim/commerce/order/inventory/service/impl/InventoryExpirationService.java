package com.wassim.commerce.order.inventory.service.impl;
import com.wassim.commerce.order.inventory.domain.InventoryReservation;
import com.wassim.commerce.order.inventory.enums.ReservationStatus;
import com.wassim.commerce.order.inventory.persistence.InventoryRepository;
import com.wassim.commerce.order.inventory.persistence.InventoryReservationRepository;
import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.order.enums.EOrderStatus;
import com.wassim.commerce.order.order.presisstance.OrderRepository;
import com.wassim.commerce.order.payment.enums.EPaymentStatus;
import com.wassim.commerce.order.payment.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryExpirationService {

    private final OrderRepository orderRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public int processBatch() {
        List<Long> orderIds =
                orderRepository.findExpiredOrderIdsForUpdate(20);

        int expiredCount = 0;

        for (Long orderId : orderIds) {
            if (expireOrder(orderId)) {
                expiredCount++;
            }
        }

        return expiredCount;
    }

    private boolean expireOrder(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Order not found: " + orderId
                        )
                );

        if (order.getOrderStatus() != EOrderStatus.PENDING_PAYMENT) {
            return false;
        }

        if (paymentRepository.existsByOrder_IdAndStatus(orderId, EPaymentStatus.SUCCEEDED)) {

            log.error(
                    "Order {} has a successful payment but is still pending. "
                            + "Skipping expiration.",
                    orderId
            );

            return false;
        }

        List<InventoryReservation> reservations =
                reservationRepository.findAllByOrderId(orderId);

        Instant now = Instant.now();

        boolean hasExpiredReservation = reservations.stream()
                .anyMatch(r ->
                        r.getStatus() == ReservationStatus.RESERVED
                                && !r.getExpiresAt().isAfter(now)
                );

        if (!hasExpiredReservation) {
            return false;
        }

        for (InventoryReservation reservation : reservations) {

            if (reservation.getStatus() != ReservationStatus.RESERVED) {
                continue;
            }

            Long productId = reservation.getProduct().getId();

            int updated = inventoryRepository.releaseStock(
                    productId,
                    reservation.getQuantity()
            );

            if (updated != 1) {
                throw new IllegalStateException(
                        "Failed to release reserved inventory for product "
                                + productId
                );
            }

            reservation.setStatus(ReservationStatus.EXPIRED);
        }

        order.setOrderStatus(EOrderStatus.EXPIRED);

        log.info("Expired order {} and released its reservations", orderId);

        return true;
    }
}

