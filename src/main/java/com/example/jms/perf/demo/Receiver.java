package com.example.jms.perf.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("receiver")
public class Receiver {
    private final Logger log = LoggerFactory.getLogger(Receiver.class);

    private static final AtomicInteger counter = new AtomicInteger();
    private static long startTime = 0;

    @JmsListener(destination = "DEV.QUEUE.3", containerFactory = "myFactory")
    public void receiveMessage(String message) {
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }
        long currentTime = System.currentTimeMillis();
        double timeDiff = (currentTime - startTime) / 1000.0;
        int count = counter.incrementAndGet();
        log.info(count + ", received msg: " + message.hashCode() + ", performance: " + count / timeDiff);
    }
}
