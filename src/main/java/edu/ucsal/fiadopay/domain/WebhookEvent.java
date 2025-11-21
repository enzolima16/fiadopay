package edu.ucsal.fiadopay.domain;

public enum WebhookEvent {
    PAYMENT_CREATED,
    PAYMENT_APPROVED,
    PAYMENT_DECLINED,
    PAYMENT_REFUNDED,
    PAYMENT_EXPIRED;

    public static WebhookEvent fromPaymentStatus(Payment.Status status) {
        return switch (status) {
            case APPROVED -> PAYMENT_APPROVED;
            case DECLINED -> PAYMENT_DECLINED;
            case REFUNDED -> PAYMENT_REFUNDED;
            case EXPIRED -> PAYMENT_EXPIRED;
            case PENDING -> PAYMENT_CREATED;
        };
    }
}