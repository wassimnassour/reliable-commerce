package com.wassim.commerce.order.messaging;

import com.wassim.commerce.order.TestcontainersConfiguration;
import com.wassim.commerce.order.messaging.Consumers.OrdersConsumer;
import com.wassim.commerce.order.messaging.handlers.OrderEventHandler.OrderCancelledHandler;
import com.wassim.commerce.order.messaging.inbox.persistence.InboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
public class InboxRepositoryConcurrencyTest {

    @Autowired
    InboxRepository inboxRepository;

    @Autowired
    OrderCancelledHandler orderCancelledHandler;

    @Autowired
    OrdersConsumer ordersConsumer;

    @Autowired
    PlatformTransactionManager transactionManager;


    @Test
    public void twoConsumersClaimSameMessage_onlyOneSucceeds(){
        String messageId = UUID.randomUUID().toString();
        String consumerName = "orders-consumer";
        String payload = "{\"orderId\":\"123\"}";


        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<Integer>  claimFn = ()->{
            barrier.await(5 , TimeUnit.SECONDS);
            return inboxRepository.tryClaim(
                    messageId ,
                    consumerName,
                    payload
            );
        };

        try{
            Future<Integer> consumerA =
                    executorService.submit(claimFn);

            Future<Integer> consumerB =
                    executorService.submit(claimFn);

            int resultA = consumerA.get(10, TimeUnit.SECONDS);
            int resultB = consumerB.get(10, TimeUnit.SECONDS);

            assertThat(List.of(resultA ,resultB)).containsExactlyInAnyOrder(0,1);

           int countByMessageIdAndName =inboxRepository.countByMessageIdAndConsumerName(messageId,consumerName);

           assertThat(countByMessageIdAndName).isEqualTo(1);
        }catch (Exception e){


        }finally {
            executorService.shutdown();
        }
    }
    @Test
    public void failedProcessing_rollsBackClaim_andAllowsRetry(){
        String messageId = UUID.randomUUID().toString();
        String consumerName = "orders-consumer";
        String payload = "{\"orderId\":\"123\"}";
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(()->{
            tx.execute((status)->{
                int  result = inboxRepository.tryClaim(messageId ,consumerName,payload);

                assertThat(result).isEqualTo(1);

                throw new IllegalStateException("Order Processing Failed");
            });
        }).isInstanceOf(IllegalStateException.class);

        assertThat(inboxRepository.countByMessageIdAndConsumerName(messageId,consumerName)).isEqualTo(0);

        int retryResult = tx.execute(status ->
                inboxRepository.tryClaim(
                        messageId,
                        consumerName,
                        payload
                )
        );

        assertThat(retryResult).isEqualTo(1);

        assertThat(inboxRepository
                .countByMessageIdAndConsumerName(
                        messageId,
                        consumerName
                )).isEqualTo(1);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void duplicateDelivery_processesBusinessOperationOnlyOnce(CapturedOutput output) {
        String messageId = UUID.randomUUID().toString();
        String routingKey = orderCancelledHandler.routingKey();
        String payload = "{\"orderId\":33,\"userId\":333}";

        ordersConsumer.receiveMessage(payload, routingKey, messageId);
        ordersConsumer.receiveMessage(payload, routingKey, messageId);

        assertThat(inboxRepository.countByMessageIdAndConsumerName(messageId, "fulfillment-orders"))
                .isEqualTo(1);

        assertThat(output.getOut().lines()
                .filter(line -> line.endsWith("Processing order cancellation for order ID: 33"))
                .count()).isEqualTo(1);
    }



}
