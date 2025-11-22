package edu.ucsal.fiadopay.processor;

import edu.ucsal.fiadopay.annotation.WebhookSink;
import edu.ucsal.fiadopay.controller.WebhookEventData;
import edu.ucsal.fiadopay.domain.WebhookEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
public class WebhookSinkProcessor {

    private final ApplicationContext applicationContext;
    private final ExecutorService asyncExecutor;

    @Getter
    private final Map<WebhookEvent, List<SinkMethod>> eventSinks = new EnumMap<>(WebhookEvent.class);

    public WebhookSinkProcessor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.asyncExecutor = Executors.newFixedThreadPool(5, r -> {
            Thread t = new Thread(r);
            t.setName("webhook-sink-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void scanAndRegister() {
        log.info("📡 Scanning for @WebhookSink methods...");

        // Cria scanner para buscar classes com @Component
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(org.springframework.stereotype.Component.class));

        // Varre pacote de listeners
        Set<BeanDefinition> candidates = scanner.findCandidateComponents("edu.ucsal.fiadopay.listener");

        for (BeanDefinition bd : candidates) {
            try {
                // Carrega classe via reflexão
                Class<?> clazz = Class.forName(bd.getBeanClassName());

                // Obtém bean gerenciado pelo Spring (permite @Autowired)
                Object bean = applicationContext.getBean(clazz);

                // Itera sobre todos os métodos da classe
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(WebhookSink.class)) {
                        WebhookSink annotation = method.getAnnotation(WebhookSink.class);

                        // Valida assinatura do método
                        if (!isValidSinkMethod(method)) {
                            log.warn("⚠️  Invalid sink method signature: {}.{} (must accept WebhookEventData)",
                                    clazz.getSimpleName(), method.getName());
                            continue;
                        }

                        // Cria wrapper com bean + method + annotation
                        SinkMethod sinkMethod = new SinkMethod(bean, method, annotation);

                        // Registra para cada evento configurado
                        for (String eventName : annotation.events()) {
                            try {
                                WebhookEvent event = WebhookEvent.valueOf(eventName);
                                eventSinks.computeIfAbsent(event, k -> new ArrayList<>()).add(sinkMethod);

                                log.info("✅ Registered webhook sink: {}.{} for event {} [async={}, priority={}]",
                                        clazz.getSimpleName(),
                                        method.getName(),
                                        event,
                                        annotation.async(),
                                        annotation.priority());
                            } catch (IllegalArgumentException e) {
                                log.error("❌ Invalid event name: {} in {}.{}",
                                        eventName, clazz.getSimpleName(), method.getName());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("❌ Failed to register webhook sinks from {}", bd.getBeanClassName(), e);
            }
        }

        // Ordena sinks por prioridade (menor = executa primeiro)
        eventSinks.values().forEach(sinks ->
                sinks.sort(Comparator.comparingInt(s -> s.annotation.priority()))
        );

        int totalSinks = eventSinks.values().stream().mapToInt(List::size).sum();
        log.info("📡 Registered {} webhook sinks across {} event types", totalSinks, eventSinks.size());
    }

    /**
     * Dispara todos os sinks registrados para um evento
     */
    public void dispatch(WebhookEventData eventData) {
        List<SinkMethod> sinks = eventSinks.get(eventData.eventType());

        if (sinks == null || sinks.isEmpty()) {
            log.debug("No sinks registered for event {}", eventData.eventType());
            return;
        }

        log.info("📤 Dispatching {} to {} sinks", eventData.eventType(), sinks.size());

        for (SinkMethod sink : sinks) {
            if (sink.annotation.async()) {
                // Execução assíncrona em thread pool
                asyncExecutor.submit(() -> executeSink(sink, eventData));
            } else {
                // Execução síncrona (bloqueia até completar)
                executeSink(sink, eventData);
            }
        }
    }

    /**
     * Executa um sink individual com timeout safety
     */
    private void executeSink(SinkMethod sink, WebhookEventData eventData) {
        try {
            // Cria executor single-thread para aplicar timeout
            ExecutorService timeoutExecutor = Executors.newSingleThreadExecutor();

            Future<?> future = timeoutExecutor.submit(() -> {
                try {
                    // Invoca método via reflexão
                    sink.method.invoke(sink.bean, eventData);
                } catch (Exception e) {
                    log.error("❌ Sink execution failed: {}.{}",
                            sink.bean.getClass().getSimpleName(),
                            sink.method.getName(), e);
                }
            });

            // Aplica timeout configurado na annotation
            int timeout = sink.annotation.timeoutSeconds();
            future.get(timeout, TimeUnit.SECONDS);

            timeoutExecutor.shutdown();

        } catch (TimeoutException e) {
            log.error("⏱️  Sink timeout after {}s: {}.{}",
                    sink.annotation.timeoutSeconds(),
                    sink.bean.getClass().getSimpleName(),
                    sink.method.getName());
        } catch (Exception e) {
            log.error("❌ Sink dispatch failed: {}.{}",
                    sink.bean.getClass().getSimpleName(),
                    sink.method.getName(), e);
        }
    }

    /**
     * Valida se método tem assinatura correta: void method(WebhookEventData)
     */
    private boolean isValidSinkMethod(Method method) {
        Class<?>[] params = method.getParameterTypes();
        return params.length == 1 && params[0] == WebhookEventData.class;
    }

    /**
     * Holder interno para método + bean + metadados
     */
    private record SinkMethod(Object bean, Method method, WebhookSink annotation) {}
}