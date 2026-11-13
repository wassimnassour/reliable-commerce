package com.wassim.commerce.order.payment.service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TestClass {
    @Test
    void testLatchDown(){
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstLatch =  new CountDownLatch(1);
        CountDownLatch secondLatch = new CountDownLatch(1);

        executor.submit(() -> {
            try {
                firstLatch.await();
                method1();
            }catch (InterruptedException exception){
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        });

        executor.submit(() -> {
            method2();
        });



    }
}
