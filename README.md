# FiadoPay - Gateway de Pagamentos

**Trabalho AVII - POOA 2025.2**  
**Tecnologia:** Java 21, Spring Boot 3.x, Maven, H2 Database

---

## Como Rodar

```bash
./mvnw spring-boot:run
```

**Acessos:**
- API: http://localhost:8080
- H2 Console: http://localhost:8080/h2 (user: `sa`, password: vazio)
- Swagger: http://localhost:8080/swagger-ui.html

---

## Implementações do Trabalho

### 1. Anotações Customizadas + Reflexão

**@PaymentMethod** - Descoberta de Handlers de Pagamento

Marca classes que processam métodos de pagamento (CARD, PIX, DEBIT, BOLETO).

```java
@PaymentMethod(type = "CARD", supportsInstallments = true, priority = 10)
public class CardPaymentHandler implements PaymentHandler {
    // Valida parcelas, calcula juros de 1% a.m.
}
```

Descoberta automática no startup:
```
Registered handler: CARD -> CardPaymentHandler
Registered handler: PIX -> PixPaymentHandler
Registered handler: DEBIT -> DebitPaymentHandler
Registered handler: BOLETO -> BoletoPaymentHandler
```

---

**@AntiFraud** - Descoberta de Regras de Fraude

Marca classes que avaliam risco de fraude em pagamentos.

```java
@AntiFraud(name = "HighAmount", threshold = 10000.0, severity = "HIGH")
public class HighAmountFraudRule implements FraudRule {
    // Retorna score de 0.0-1.0 baseado no valor
}
```

Descoberta automática no startup:
```
Registered fraud rule: HighAmount [severity=HIGH, threshold=10000.0]
Registered fraud rule: HighFrequency [severity=MEDIUM]
Registered fraud rule: SuspiciousPattern [severity=MEDIUM]
```

Bloqueio automático: Pagamentos com score >= 0.7 são automaticamente recusados.

---

**@WebhookSink** - Descoberta de Listeners Internos

Marca métodos que processam eventos de pagamento internamente (auditoria, métricas, alertas).

```java
@WebhookSink(events = {"PAYMENT_APPROVED", "PAYMENT_DECLINED"}, async = false, priority = 1)
public void auditPaymentStatusChange(WebhookEventData event) {
    log.info("Payment {} changed to {}", event.paymentId(), event.paymentStatus());
}
```

Descoberta automática no startup:
```
Registered webhook sink: PaymentAuditListener.auditPaymentStatusChange
Registered webhook sink: FraudAlertListener.alertFraudTeam
Registered webhook sink: MetricsCollectorListener.collectMetrics
```

---

### 2. Threads com ExecutorService

Pools dedicados para processamento assíncrono:

```java
@Bean(name = "paymentExecutor")
public Executor paymentExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(3);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("payment-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    return executor;
}
```

Logs de threading:
```
Processing payment pay_abc123 in thread: payment-1
Delivering webhook wh_xyz789 in thread: webhook-1
```

---

## Uso Básico

### 1. Criar Merchant

```bash
curl -X POST http://localhost:8080/fiadopay/admin/merchants \
  -H "Content-Type: application/json" \
  -d '{"name":"Loja Exemplo","webhookUrl":"http://httpbin.org/post"}'
```

### 2. Obter Token

```bash
curl -X POST http://localhost:8080/fiadopay/auth/token \
  -d "grant_type=client_credentials&client_id=CLIENT_ID&client_secret=SECRET"
```

### 3. Criar Pagamento

```bash
curl -X POST http://localhost:8080/fiadopay/gateway/payments \
  -H "Authorization: Bearer FAKE-1" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "CARD",
    "amount": 1000.00,
    "currency": "BRL",
    "installments": 3
  }'
```

**Resposta:**
```json
{
  "id": "pay_abc123",
  "amount": 1000.00,
  "totalWithInterest": 1030.30,
  "monthlyInterest": 1.0,
  "status": "PENDING"
}
```

Juros: R$ 1.000 x 1.01³ = R$ 1.030,30 (1% a.m. composto)

### 4. Consultar Pagamento

```bash
curl http://localhost:8080/fiadopay/gateway/payments/pay_abc123 \
  -H "Authorization: Bearer FAKE-1"
```

### 5. Solicitar Reembolso

```bash
curl -X POST http://localhost:8080/fiadopay/gateway/refunds \
  -H "Authorization: Bearer FAKE-1" \
  -d '{"paymentId":"pay_abc123"}'
```

---

## Evidências


---

## Padrões Aplicados

- **Strategy**: Cada handler/regra implementa estratégia específica
- **Observer**: WebhookSinks observam eventos via reflexão
- **Chain of Responsibility**: Regras de fraude executam em sequência
- **Factory**: Processadores retornam handlers dinamicamente

---

## Documentação Completa

Consulte `docs/` para guias técnicos detalhados:
- `PaymentMethod-Implementation-Guide.md`
- `AntiFraud-Implementation-Guide.md`
- `WebhookSink-Implementation-Guide.md`
- `Threading-Implementation-Guide.md`
