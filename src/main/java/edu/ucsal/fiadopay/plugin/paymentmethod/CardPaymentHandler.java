package edu.ucsal.fiadopay.plugin.paymentmethod;

import edu.ucsal.fiadopay.annotation.PaymentMethod;
import edu.ucsal.fiadopay.domain.Payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

@PaymentMethod(type = "CARD", supportsInstallments = true)
public class CardPaymentHandler implements PaymentHandler {

    @Override
    public boolean validate(Payment payment) {
        int installments = payment.getInstallments();

        if (installments < 1 || installments > 12) return false;
        return payment.getAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public void process(Payment payment) {
        int installments = payment.getInstallments();
        BigDecimal total;
        Double interestRate = null;

        if (installments > 1) {
            interestRate = 1.0; // 1% ao mês
            total = calculateTotal(payment.getAmount(), installments);
        } else {
            total = payment.getAmount();
        }

        payment.setMonthlyInterest(interestRate);
        payment.setTotalWithInterest(total);
    }

    @Override
    public BigDecimal calculateTotal(BigDecimal amount, int installments) {
        if (installments == 1) return amount;

        BigDecimal base = new BigDecimal("1.01");
        BigDecimal factor = base.pow(installments);
        return amount.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
