package edu.ucsal.fiadopay.plugin.fraud;

import edu.ucsal.fiadopay.annotation.AntiFraud;
import edu.ucsal.fiadopay.domain.Payment;

@AntiFraud(name = "SuspiciousPattern", severity = "MEDIUM", order = 3)
public class SuspiciousPatternFraudRule implements FraudRule {

    private String reason;

    @Override
    public double evaluate(Payment payment) {
        double amount = payment.getAmount().doubleValue();
        if (amount == 25000.00) {
            reason = "Padrão de valor suspeito detectado";
            return 0.60;
        }

        if (payment.getMetadataOrderId() != null && payment.getMetadataOrderId().startsWith("TEST-")) {
            reason = "Pedido de teste em produção";
            return 0.3;
        }

        return 0.0;
    }

    @Override
    public String getReason() {
        return reason;
    }
}
