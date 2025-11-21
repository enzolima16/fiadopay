package edu.ucsal.fiadopay.service;

import java.util.List;

public record FraudEvaluation(double score, List<String> reasons) {
    public boolean isHighRisk() {
        return score >= 0.7;
    }

    public String getSummary() {
        return String.join("; ", reasons);
    }
}
