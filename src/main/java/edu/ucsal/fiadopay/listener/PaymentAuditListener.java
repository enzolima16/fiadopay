package edu.ucsal.fiadopay.listener;

import edu.ucsal.fiadopay.annotation.WebhookSink;
import edu.ucsal.fiadopay.controller.WebhookEventData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component

public class PaymentAuditListener {

    @WebhookSink(
            events = {"PAYMENT_APPROVED", "PAYMENT_DECLINED", "PAYMENT_REFUNDED"},
            async = false,  // Bloqueia até completar (garantia de log)
            priority = 1    // Executa primeiro
    )
    public void auditPaymentStatusChange(WebhookEventData event) {
        log.info("📝 [AUDITORIA] Payment {} changed to {} for merchant {}",
                event.paymentId(), event.paymentStatus(), event.merchantId());

    }
}