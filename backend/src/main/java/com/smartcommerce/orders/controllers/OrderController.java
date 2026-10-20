package com.smartcommerce.orders.controllers;

import com.smartcommerce.orders.domain.OrderException;
import com.smartcommerce.orders.dto.request.CheckoutRequest;
import com.smartcommerce.orders.dto.response.CheckoutView;
import com.smartcommerce.orders.dto.response.OrderView;
import com.smartcommerce.orders.dto.response.PaymentStartView;
import com.smartcommerce.orders.security.ClientIpVerifier;
import com.smartcommerce.orders.services.OrderCheckoutService;
import com.smartcommerce.orders.services.OrderPaymentService;
import com.smartcommerce.orders.services.OrderQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public checkout, buyer status (SC-008) and payment start (SC-013). Stateless: no cookie or
 * session of the backend is used; the storefront-web server keeps the buyer token and sends it in
 * {@code X-Order-Token}.
 */
@RestController
@Tag(name = "Public storefront")
@RequestMapping("/api/public/stores/{slug}/orders")
public final class OrderController {
  private static final String UUID_PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
  private final OrderCheckoutService checkout;
  private final OrderQueryService queries;
  private final OrderPaymentService payments;
  private final ClientIpVerifier clientIp;

  public OrderController(
      OrderCheckoutService checkout,
      OrderQueryService queries,
      OrderPaymentService payments,
      ClientIpVerifier clientIp) {
    this.checkout = checkout;
    this.queries = queries;
    this.payments = payments;
    this.clientIp = clientIp;
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> create(
      @PathVariable String slug, InputStream body, HttpServletRequest request) throws IOException {
    if (!validSlug(slug)) throw new OrderException(OrderException.Reason.MISSING);
    // Bounded read: never buffer more than the limit, whatever Content-Length claims.
    byte[] content = body.readNBytes(CheckoutRequest.MAX_BYTES + 1);
    if (content.length > CheckoutRequest.MAX_BYTES)
      return ResponseEntity.status(413)
          .cacheControl(CacheControl.noStore())
          .body(Map.of("message", "Pedido grande demais."));
    String ip =
        clientIp.resolve(
            request.getRemoteAddr(),
            request.getHeader("X-SC-Client-IP"),
            request.getHeader("X-SC-Client-IP-Timestamp"),
            request.getHeader("X-SC-Client-IP-Signature"));
    CheckoutView result = checkout.checkout(slug, CheckoutRequest.parse(content), ip);
    return ResponseEntity.status(result.replayed() ? 200 : 201)
        .cacheControl(CacheControl.noStore())
        .body(result);
  }

  @GetMapping(path = "/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<OrderView> find(
      @PathVariable String slug,
      @PathVariable String orderId,
      @RequestHeader(name = "X-Order-Token", required = false) String token) {
    if (!validSlug(slug) || !orderId.matches(UUID_PATTERN))
      throw new OrderException(OrderException.Reason.MISSING);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(queries.find(slug, UUID.fromString(orderId), token));
  }

  /**
   * Opens (or reopens) the Checkout Pro payment of a pending order. Idempotent: every call for the
   * same order returns the same provider page. No body; the token proves the buyer.
   */
  @PostMapping(path = "/{orderId}/payment", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PaymentStartView> pay(
      @PathVariable String slug,
      @PathVariable String orderId,
      @RequestHeader(name = "X-Order-Token", required = false) String token) {
    if (!validSlug(slug) || !orderId.matches(UUID_PATTERN))
      throw new OrderException(OrderException.Reason.MISSING);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new PaymentStartView(payments.start(slug, UUID.fromString(orderId), token)));
  }

  private static boolean validSlug(String slug) {
    return slug != null
        && slug.length() >= 3
        && slug.length() <= 63
        && slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*");
  }
}
