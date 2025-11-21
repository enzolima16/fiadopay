package edu.ucsal.fiadopay.plugin.fraud;

import edu.ucsal.fiadopay.domain.Payment;

public interface FraudRule {
    double evaluate(Payment payment);

    String getReason();
}
