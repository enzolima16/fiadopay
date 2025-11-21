package edu.ucsal.fiadopay.plugin.fraud;

import edu.ucsal.fiadopay.annotation.AntiFraud;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.repo.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@AntiFraud(name = "HighFrequency", severity = "CRITICAL", order = 2)
public class HighFrequencyFraudRule implements FraudRule {

    @Autowired
    private PaymentRepository paymentRepository;

    private String reason;

    @Override
    public double evaluate(Payment payment) {
        Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
        long count = paymentRepository.countByMerchantIdAndCreatedAtAfter(payment.getMerchantId(), fiveMinutesAgo);

        if (count > 10) {
            reason = "Alta frequência de transações: " + count + " em 5 min";
            return 0.9;
        }
        if (count > 5) {
            reason = "Frequência moderada: " + count + " em 5 min";
            return 0.6;
        }
        return 0.0;
    }

    @Override
    public String getReason() {
        return reason;
    }
}
