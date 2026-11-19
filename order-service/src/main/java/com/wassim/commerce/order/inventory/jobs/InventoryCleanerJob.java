package com.wassim.commerce.order.inventory.jobs;
import com.wassim.commerce.order.inventory.service.impl.InventoryExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;




@Slf4j
@RequiredArgsConstructor
@Component
public class InventoryCleanerJob {

    private final InventoryExpirationService expirationService;

    @Scheduled(fixedDelayString = "${inventory.expiration.poll-delay-ms:60000}")
    public void run() {

        int expiredOrders = expirationService.processBatch();

        if (expiredOrders > 0) {
            log.info("Inventory expiration job completed: {} orders expired",
                    expiredOrders);
        }
    }
}
