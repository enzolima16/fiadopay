package edu.ucsal.fiadopay.listener;


import edu.ucsal.fiadopay.annotation.WebhookSink;
import edu.ucsal.fiadopay.controller.WebhookEventData;
import edu.ucsal.fiadopay.domain.WebhookEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component

public class MetricsCollectorListener {

    private final ConcurrentHashMap<String, AtomicLong> eventsCounts  = new ConcurrentHashMap<>();

    @WebhookSink(

            events = {"PAYMENT_APPROVED", "PAYMENT_DECLINED"},
            async = false,
            priority = 50,
            timeoutSeconds = 5
    )
    public void collectMetrics(WebhookEventData event) {

        eventsCounts.computeIfAbsent(event.eventType().name(), k -> new AtomicLong(0))
                .incrementAndGet();





    }



}

