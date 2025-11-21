package edu.ucsal.fiadopay.service;

import edu.ucsal.fiadopay.annotation.AntiFraud;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.plugin.fraud.FraudRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    @Autowired
    private ApplicationContext applicationContext;

    private List<FraudRule> rules = new ArrayList<>();

    @PostConstruct
    public void init() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(AntiFraud.class));

        String basePackage = "edu.ucsal.fiadopay.plugin.fraud";
        for (BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
            try {
                Class<?> clazz = Class.forName(bd.getBeanClassName());
                FraudRule rule;

                try {
                    rule = (FraudRule) applicationContext.getBean(clazz);
                } catch (Exception e) {
                    rule = (FraudRule) clazz.getDeclaredConstructor().newInstance();
                    applicationContext.getAutowireCapableBeanFactory().autowireBean(rule);
                }

                rules.add(rule);
                log.info("Registered fraud rule: {}", clazz.getSimpleName());
            } catch (Exception e) {
                log.error("Failed to register fraud rule: {}", bd.getBeanClassName(), e);
            }
        }

        rules.sort(Comparator.comparingInt(r -> {
            AntiFraud ann = r.getClass().getAnnotation(AntiFraud.class);
            return ann != null ? ann.order() : Integer.MAX_VALUE;
        }));
    }

    public FraudEvaluation evaluate(Payment payment) {
        double maxScore = 0.0;
        List<String> reasons = new ArrayList<>();

        for (FraudRule rule : rules) {
            AntiFraud ann = rule.getClass().getAnnotation(AntiFraud.class);
            if (ann != null && !ann.enabled()) {
                continue;
            }

            try {
                double score = rule.evaluate(payment);
                if (score > 0) {
                    maxScore = Math.max(maxScore, score);
                    if (rule.getReason() != null) {
                        reasons.add(rule.getReason());
                    }
                    log.info("Rule {} triggered: score={}, severity={}",
                            ann != null ? ann.name() : rule.getClass().getSimpleName(),
                            score,
                            ann != null ? ann.severity() : "UNKNOWN");
                }
            } catch (Exception e) {
                log.error("Rule {} failed", rule.getClass().getSimpleName(), e);
            }
        }

        return new FraudEvaluation(maxScore, reasons);
    }
}
