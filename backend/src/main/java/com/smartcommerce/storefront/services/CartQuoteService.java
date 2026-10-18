package com.smartcommerce.storefront.services;

import com.smartcommerce.catalog.domain.VariantValues;
import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.storefront.domain.CartLine;
import com.smartcommerce.storefront.domain.CartLineStatus;
import com.smartcommerce.storefront.domain.PublicLocator;
import com.smartcommerce.storefront.domain.QuotedVariant;
import com.smartcommerce.storefront.domain.StorefrontException;
import com.smartcommerce.storefront.dto.response.CartQuoteLineView;
import com.smartcommerce.storefront.dto.response.CartQuoteView;
import com.smartcommerce.storefront.repositories.CartQuoteRepository;
import com.smartcommerce.storefront.repositories.StorefrontRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Side-effect-free cart quote: the tenant comes from the published slug, every variant is
 * revalidated against it and prices always come from the catalog. Stock status uses the available
 * quantity net of active reservations. No reservation, no write (ADR-0012 D5).
 *
 * <p>Extension point: delivery options (SC-006) join {@link CartQuoteView} once the store
 * fulfillment configuration exists; that integration happens here, without changing line pricing.
 */
@Service
public class CartQuoteService {
  private static final String CURRENCY = "BRL";
  private final StorefrontRepository storefront;
  private final CartQuoteRepository quotes;
  private final Clock clock;

  public CartQuoteService(
      StorefrontRepository storefront, CartQuoteRepository quotes, Clock clock) {
    this.storefront = storefront;
    this.quotes = quotes;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CartQuoteView quote(String slug, List<CartLine> lines) {
    storefront.store(PublicLocator.slug(slug)).orElseThrow(StorefrontException::new);
    Map<UUID, QuotedVariant> found =
        quotes
            .variants(
                slug,
                lines.stream().map(CartLine::variantId).toList(),
                ReservationPolicy.now(clock))
            .stream()
            .collect(Collectors.toMap(QuotedVariant::id, Function.identity()));
    BigDecimal subtotal = BigDecimal.ZERO;
    boolean purchasable = true;
    List<CartQuoteLineView> views = new ArrayList<>(lines.size());
    for (CartLine line : lines) {
      QuotedVariant variant = found.get(line.variantId());
      CartLineStatus status =
          variant == null || !CURRENCY.equals(variant.currency())
              ? CartLineStatus.UNAVAILABLE
              : CartLineStatus.of(variant.sellable(), line.quantity());
      if (status != CartLineStatus.AVAILABLE) purchasable = false;
      if (status == CartLineStatus.UNAVAILABLE) {
        views.add(
            new CartQuoteLineView(
                line.variantId(), line.quantity(), status.name(), null, null, null, null, null));
        continue;
      }
      BigDecimal total = variant.price().multiply(BigDecimal.valueOf(line.quantity()));
      if (status == CartLineStatus.AVAILABLE) subtotal = subtotal.add(total);
      views.add(
          new CartQuoteLineView(
              line.variantId(),
              line.quantity(),
              status.name(),
              variant.productId(),
              variant.productName(),
              VariantValues.decode(variant.optionsJson()),
              money(variant.price()),
              money(total)));
    }
    return new CartQuoteView(CURRENCY, List.copyOf(views), money(subtotal), purchasable);
  }

  private static String money(BigDecimal value) {
    return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }
}
