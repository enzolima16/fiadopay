package edu.ucsal.fiadopay.controller;

import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.domain.WebhookEvent;

import java.time.Instant;
import java.util.UUID;

public record WebhookEventData(
        String eventId,           // evt_abc123
        WebhookEvent eventType,   // PAYMENT_APPROVED
        String paymentId,         // pay_xyz789
        Payment.Status paymentStatus,
        Long merchantId,
        Instant occurredAt
) {
    public static WebhookEventData fromPayment(Payment payment, WebhookEvent event) {
        return new WebhookEventData(
                "evt_" + UUID.randomUUID().toString().substring(0, 8),
                event,
                payment.getId(),
                payment.getStatus(),
                payment.getMerchantId(),
                Instant.now()
        );
    }
}