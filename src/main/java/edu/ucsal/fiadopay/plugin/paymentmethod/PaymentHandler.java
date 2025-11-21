package edu.ucsal.fiadopay.plugin.paymentmethod;

import edu.ucsal.fiadopay.domain.Payment;

import java.math.BigDecimal;

public interface PaymentHandler {
    boolean validate(Payment payment);

    void process(Payment payment);

    BigDecimal calculateTotal(BigDecimal amount, int installments);
}
