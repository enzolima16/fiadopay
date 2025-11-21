package edu.ucsal.fiadopay.plugin.fraud;

import edu.ucsal.fiadopay.annotation.AntiFraud;
import edu.ucsal.fiadopay.domain.Payment;

@AntiFraud(name = "HighAmount", severity = "HIGH", order = 1)
public class HighAmountFraudRule implements FraudRule {

    private String reason;

    @Override
    public double evaluate(Payment payment) {
        if (payment.getAmount().doubleValue() > 10000.0) {
            reason = "Valor muito alto: " + payment.getAmount();
            return 0.85;
        }
        if (payment.getAmount().doubleValue() > 5000.0) {
            reason = "Valor suspeito: " + payment.getAmount();
            return 0.55;
        }
        return 0.0;
    }

    @Override
    public String getReason() {
        return reason;
    }
}
