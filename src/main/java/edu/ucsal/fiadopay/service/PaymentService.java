package edu.ucsal.fiadopay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsal.fiadopay.controller.PaymentRequest;
import edu.ucsal.fiadopay.controller.PaymentResponse;
import edu.ucsal.fiadopay.controller.WebhookEventData;
import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.domain.WebhookDelivery;
import edu.ucsal.fiadopay.domain.WebhookEvent;
import edu.ucsal.fiadopay.plugin.paymentmethod.PaymentHandler;
import edu.ucsal.fiadopay.processor.PaymentMethodProcessor;
import edu.ucsal.fiadopay.processor.WebhookSinkProcessor;
import edu.ucsal.fiadopay.repo.MerchantRepository;
import edu.ucsal.fiadopay.repo.PaymentRepository;
import edu.ucsal.fiadopay.repo.WebhookDeliveryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.concurrent.Executor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


@Service
public class PaymentService {
  private final MerchantRepository merchants;
  private final PaymentRepository payments;
  private final WebhookDeliveryRepository deliveries;
  private final ObjectMapper objectMapper;

  @Autowired
  private PaymentMethodProcessor paymentMethodProcessor;

  @Autowired
  private WebhookSinkProcessor webhookSinkProcessor;

  @Autowired
  private FraudDetectionService fraudDetectionService;

  @Autowired
  @Qualifier("paymentExecutor")
  private Executor paymentExecutor;

  @Autowired
  @Qualifier("webhookExecutor")
  private Executor webhookExecutor;

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentService.class);

  @Value("${fiadopay.webhook-secret}")
  String secret;
  @Value("${fiadopay.processing-delay-ms}")
  long delay;
  @Value("${fiadopay.failure-rate}")
  double failRate;

  public PaymentService(MerchantRepository merchants, PaymentRepository payments, WebhookDeliveryRepository deliveries,
      ObjectMapper objectMapper) {
    this.merchants = merchants;
    this.payments = payments;
    this.deliveries = deliveries;
    this.objectMapper = objectMapper;
  }

  private Merchant merchantFromAuth(String auth) {
    if (auth == null || !auth.startsWith("Bearer FAKE-")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    var raw = auth.substring("Bearer FAKE-".length());
    long id;
    try {
      id = Long.parseLong(raw);
    } catch (NumberFormatException ex) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    var merchant = merchants.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (merchant.getStatus() != Merchant.Status.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return merchant;
  }

  @Transactional
  public PaymentResponse createPayment(String auth, String idemKey, PaymentRequest req) {
    var merchant = merchantFromAuth(auth);
    var mid = merchant.getId();

    if (idemKey != null) {
      var existing = payments.findByIdempotencyKeyAndMerchantId(idemKey, mid);
      if (existing.isPresent())
        return toResponse(existing.get());
    }

    String method = req.method().toUpperCase();
    PaymentHandler handler = paymentMethodProcessor.getHandler(method);

    if (handler == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Método de pagamento não suportado");
    }

    var payment = Payment.builder()
        .id("pay_" + UUID.randomUUID().toString().substring(0, 8))
        .merchantId(mid)
        .method(method)
        .amount(req.amount())
        .currency(req.currency())
        .installments(req.installments() == null ? 1 : req.installments())
        .status(Payment.Status.PENDING)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .idempotencyKey(idemKey)
        .metadataOrderId(req.metadataOrderId())
        .build();

    if (!handler.validate(payment)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parâmetros inválidos para o método de pagamento: " + method);
    }

    handler.process(payment);

    var fraudEval = fraudDetectionService.evaluate(payment);
    log.info("Fraud evaluation for {}: score={} ({})", payment.getId(), fraudEval.score(), fraudEval.getSummary()); 

    if (fraudEval.isHighRisk()) {
      log.warn("Payment {} auto-declined: {}", payment.getId(), fraudEval.getSummary());
      payment.setStatus(Payment.Status.DECLINED);
      payments.save(payment);
      return toResponse(payment);
    }

    handler.process(payment);
    payments.save(payment);

    // 📡 Dispara sinks internos
    webhookSinkProcessor.dispatch(
        WebhookEventData.fromPayment(payment, WebhookEvent.PAYMENT_CREATED));

    if (payment.getStatus() == Payment.Status.PENDING) {
      paymentExecutor.execute(() -> {
          try {
              log.debug("💳 Processing payment {} in thread: {}", 
                  payment.getId(), Thread.currentThread().getName());
              processAndWebhook(payment.getId());
          } catch (Exception e) {
              log.error("❌ Payment processing failed for {}", payment.getId(), e);
          }
      });
  } else {
      // Se já foi recusado por fraude, envia webhook imediatamente
      sendWebhook(payment);
  }

    return toResponse(payment);
  }

  public PaymentResponse getPayment(String id) {
    return toResponse(payments.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }

  public Map<String, Object> refund(String auth, String paymentId) {
    var merchant = merchantFromAuth(auth);
    var p = payments.findById(paymentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!merchant.getId().equals(p.getMerchantId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    p.setStatus(Payment.Status.REFUNDED);
    p.setUpdatedAt(Instant.now());
    payments.save(p);
    // 📡 Dispara sinks de estorno
    webhookSinkProcessor.dispatch(
        WebhookEventData.fromPayment(p, WebhookEvent.PAYMENT_REFUNDED));
    sendWebhook(p);
    return Map.of("id", "ref_" + UUID.randomUUID(), "status", "PENDING");
  }

  private void processAndWebhook(String paymentId) {
    try {
      Thread.sleep(delay);
    } catch (InterruptedException ignored) {
    }
    var p = payments.findById(paymentId).orElse(null);
    if (p == null)
      return;

    var approved = Math.random() > failRate;
    p.setStatus(approved ? Payment.Status.APPROVED : Payment.Status.DECLINED);
    p.setUpdatedAt(Instant.now());
    payments.save(p);

    // 📡 Dispara sinks de mudança de status
    WebhookEvent event = WebhookEvent.fromPaymentStatus(p.getStatus());
    webhookSinkProcessor.dispatch(WebhookEventData.fromPayment(p, event));

    sendWebhook(p);
  }

  private void sendWebhook(Payment p) {
    var merchant = merchants.findById(p.getMerchantId()).orElse(null);
    if (merchant == null || merchant.getWebhookUrl() == null || merchant.getWebhookUrl().isBlank())
      return;

    String payload;
    try {
      var data = Map.of(
          "paymentId", p.getId(),
          "status", p.getStatus().name(),
          "occurredAt", Instant.now().toString());
      var event = Map.of(
          "id", "evt_" + UUID.randomUUID().toString().substring(0, 8),
          "type", "payment.updated",
          "data", data);
      payload = objectMapper.writeValueAsString(event);
    } catch (Exception e) {
      // fallback mínimo: não envia webhook se falhar a serialização
      return;
    }

    var signature = hmac(payload, secret);

    var delivery = deliveries.save(WebhookDelivery.builder()
        .eventId("evt_" + UUID.randomUUID().toString().substring(0, 8))
        .eventType("payment.updated")
        .paymentId(p.getId())
        .targetUrl(merchant.getWebhookUrl())
        .signature(signature)
        .payload(payload)
        .attempts(0)
        .delivered(false)
        .lastAttemptAt(null)
        .build());

      webhookExecutor.execute(() -> {
        try {
            log.debug("📡 Delivering webhook {} in thread: {}", 
                delivery.getId(), Thread.currentThread().getName());
            tryDeliver(delivery.getId());
        } catch (Exception e) {
            log.error("Webhook delivery failed for {}", delivery.getId(), e);
        }
      });
  }

  private void tryDeliver(Long deliveryId) {
    var d = deliveries.findById(deliveryId).orElse(null);
    if (d == null)
      return;
    try {
      var client = HttpClient.newHttpClient();
      var req = HttpRequest.newBuilder(URI.create(d.getTargetUrl()))
          .header("Content-Type", "application/json")
          .header("X-Event-Type", d.getEventType())
          .header("X-Signature", d.getSignature())
          .POST(HttpRequest.BodyPublishers.ofString(d.getPayload()))
          .build();
      var res = client.send(req, HttpResponse.BodyHandlers.ofString());
      d.setAttempts(d.getAttempts() + 1);
      d.setLastAttemptAt(Instant.now());
      d.setDelivered(res.statusCode() >= 200 && res.statusCode() < 300);
      deliveries.save(d);
      if (!d.isDelivered() && d.getAttempts() < 5) {
        Thread.sleep(1000L * d.getAttempts());
        tryDeliver(deliveryId);
      }
    } catch (Exception e) {
      d.setAttempts(d.getAttempts() + 1);
      d.setLastAttemptAt(Instant.now());
      d.setDelivered(false);
      deliveries.save(d);
      if (d.getAttempts() < 5) {
        try {
          Thread.sleep(1000L * d.getAttempts());
        } catch (InterruptedException ignored) {
        }
        tryDeliver(deliveryId);
      }
    }
  }

  private static String hmac(String payload, String secret) {
    try {
      var mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
      return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes()));
    } catch (Exception e) {
      return "";
    }
  }

  private PaymentResponse toResponse(Payment p) {
    return new PaymentResponse(
        p.getId(), p.getStatus().name(), p.getMethod(),
        p.getAmount(), p.getInstallments(), p.getMonthlyInterest(),
        p.getTotalWithInterest());
  }
}
