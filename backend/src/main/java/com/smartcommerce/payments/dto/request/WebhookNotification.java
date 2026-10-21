package com.smartcommerce.payments.dto.request;

import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.domain.PaymentNotification;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses an already authenticated Mercado Pago notification (SC-014). Only routing fields are kept;
 * the body is never stored. The signed {@code data.id} of the query string is the order to look at,
 * and a body that names another one is rejected.
 */
public final class WebhookNotification {
  public static final int MAX_BYTES = 16_384;
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private WebhookNotification() {}

  /** Returns null for a well-formed notification of another topic, which is acknowledged only. */
  public static PaymentNotification parse(
      byte[] body, String queryType, String dataId, String requestId) {
    JsonNode json;
    try {
      json = body.length == 0 ? null : JSON.readTree(body);
    } catch (RuntimeException invalid) {
      throw new PaymentException(PaymentException.Reason.INVALID_NOTIFICATION);
    }
    if (json != null && !json.isObject())
      throw new PaymentException(PaymentException.Reason.INVALID_NOTIFICATION);
    String type = text(json, "type", 40);
    String topic = type != null ? type : queryType;
    if (topic == null || !topic.matches("[a-z_.]{1,40}"))
      throw new PaymentException(PaymentException.Reason.INVALID_NOTIFICATION);
    if (!"order".equals(topic)) return null;
    JsonNode data = json == null ? null : json.get("data");
    String bodyId = data == null || !data.isObject() ? null : text(data, "id", 64);
    if (bodyId != null && !bodyId.equalsIgnoreCase(dataId))
      throw new PaymentException(PaymentException.Reason.INVALID_NOTIFICATION);
    String action = text(json, "action", 60);
    if (action != null && !action.matches("[a-z_.]{1,60}")) action = null;
    String userId = scalar(json, "user_id");
    if (userId != null && !userId.matches("[0-9]{1,40}")) userId = null;
    // Only signed values make the key (review of SC-014): a captured request replayed with another
    // body id is the same event. A genuine redelivery with a new request id is a new event, which
    // is harmless because confirmation is idempotent.
    String key = "request:" + dataId.toLowerCase(java.util.Locale.ROOT) + ":" + requestId;
    String order = bodyId == null ? dataId : bodyId;
    if (!order.matches("[A-Za-z0-9_-]{1,64}"))
      throw new PaymentException(PaymentException.Reason.INVALID_NOTIFICATION);
    return new PaymentNotification(key, topic, action, order, userId);
  }

  private static String text(JsonNode json, String field, int max) {
    if (json == null) return null;
    JsonNode node = json.get(field);
    if (node == null || !node.isString()) return null;
    String value = node.asString();
    return value.isBlank() || value.length() > max ? null : value;
  }

  private static String scalar(JsonNode json, String field) {
    if (json == null) return null;
    JsonNode node = json.get(field);
    if (node == null || !(node.isString() || node.isIntegralNumber())) return null;
    String value = node.asString();
    return value.length() > 100 ? null : value;
  }
}
