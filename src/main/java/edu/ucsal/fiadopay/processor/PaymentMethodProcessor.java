package edu.ucsal.fiadopay.processor;

import edu.ucsal.fiadopay.annotation.PaymentMethod;
import edu.ucsal.fiadopay.plugin.paymentmethod.PaymentHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class PaymentMethodProcessor {
    private final Map<String, PaymentHandler> handlers = new HashMap<>();
    private final Map<String, PaymentMethod> metadata = new HashMap<>();

    @PostConstruct
    public void scanAndRegister() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(PaymentMethod.class));

        Set<BeanDefinition> candidates = scanner.findCandidateComponents("edu.ucsal.fiadopay.plugin.paymentmethod");

        for (BeanDefinition candidate : candidates) {
            try {
                Class<?> clazz = Class.forName(candidate.getBeanClassName());
                PaymentMethod annotation = clazz.getAnnotation(PaymentMethod.class);

                if (PaymentHandler.class.isAssignableFrom(clazz)) {
                    PaymentHandler handler = (PaymentHandler) clazz.getDeclaredConstructor().newInstance();
                    String type = annotation.type().toUpperCase();

                    handlers.put(type, handler);
                    metadata.put(type, annotation);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public PaymentHandler getHandler(String type) {
        return handlers.get(type.toUpperCase());
    }

    public PaymentMethod getMetadata(String type) {
        return metadata.get(type.toUpperCase());
    }

    public Collection<String> getRegisteredTypes() {
        return handlers.keySet();
    }
}
