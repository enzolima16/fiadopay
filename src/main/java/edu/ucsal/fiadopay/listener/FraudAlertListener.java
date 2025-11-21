package edu.ucsal.fiadopay.listener;


import edu.ucsal.fiadopay.annotation.WebhookSink;
import edu.ucsal.fiadopay.controller.WebhookEventData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeoutException;
@Slf4j
@Component
public class FraudAlertListener {
@WebhookSink(

        events = {"PAYMENT_DECLINED"},
        async = false,
        priority = 10,
        timeoutSeconds = 15
)
    public  void alertFraudTeam(WebhookEventData event){
    log.warn("🚨 [FRAUD ALERT] Payment {} declined", event.paymentId());

    simulateSendAlert(event);
}

private void simulateSendAlert(WebhookEventData event){
    try {
        Thread.sleep(500); // Simula latência de API externa
        log.info("✉️ Alert sent to fraud team");
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
}

