package edu.ucsal.fiadopay.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WebhookSink {
    String[] events();              // Ex: {"PAYMENT_APPROVED", "PAYMENT_DECLINED"}
    boolean async() default true;   // Executar em thread separada
    int timeoutSeconds() default 30;
 int priority() default 100;
}