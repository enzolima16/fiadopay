package edu.ucsal.fiadopay.plugin.paymentmethod;

import edu.ucsal.fiadopay.annotation.PaymentMethod;
import edu.ucsal.fiadopay.domain.Payment;

import java.math.BigDecimal;

@PaymentMethod(type = "DEBIT", supportsInstallments = false)
public class DebitPaymentHandler implements PaymentHandler {
    @Override
    public boolean validate(Payment payment) {
        return payment.getInstallments() <= 1;
    }

    @Override
    public void process(Payment payment) {
        payment.setInstallments(1);
        payment.setMonthlyInterest(null);
        payment.setTotalWithInterest(payment.getAmount());
    }

    @Override
    public BigDecimal calculateTotal(BigDecimal amount, int installments) {
        return amount;
    }
}
