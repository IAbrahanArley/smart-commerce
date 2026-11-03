package com.smartcommerce.orders.services;

import com.smartcommerce.finance.services.CounterSaleMoneyService;
import com.smartcommerce.inventory.domain.ReservationLine;
import com.smartcommerce.inventory.services.InventoryReservationService;
import com.smartcommerce.orders.domain.CounterSaleException;
import com.smartcommerce.orders.domain.CounterSaleInput;
import com.smartcommerce.orders.domain.CounterSaleRecords;
import com.smartcommerce.orders.repositories.CounterSaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sales registered at the physical store, without a POS (SC-053, ADR-0019 C6–C8).
 *
 * <ul>
 *   <li>The OWNER and the store's employees register; only the OWNER cancels and changes the
 *       settings. Anyone else gets one 404. Writes lock the store.
 *   <li>Items come from the catalog with price and cost snapshots, and leave the same stock the
 *       storefront sells, all or nothing, respecting its reservations in force.
 *   <li>A discount is a value off the sale; an employee's is capped by the OWNER's limit (decided
 *       on 2026-11-03).
 *   <li>Cash goes into the drawer of an open cash session, Pix into the OWNER's Pix account; cards
 *       stay receivable until released, with the fee and release days the OWNER informed (decided
 *       on 2026-11-03). The ledger records sale, COGS and, when known, the fee.
 *   <li>The request id makes a retry the same sale. Nothing is deleted: the OWNER cancels with a
 *       reason, choosing whether the goods come back to stock.
 * </ul>
 */
@Service
public class CounterSaleService {
  static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
  static final int SEARCH_LIMIT = 20;
  static final int RECENT = 50;
  private static final String REASON = "Venda na loja";

  private final CounterSaleRepository sales;
  private final InventoryReservationService inventory;
  private final CounterSaleMoneyService money;
  private final Clock clock;

  public CounterSaleService(
      CounterSaleRepository sales,
      InventoryReservationService inventory,
      CounterSaleMoneyService money,
      Clock clock) {
    this.sales = sales;
    this.inventory = inventory;
    this.money = money;
    this.clock = clock;
  }

  public record Overview(
      String role,
      CounterSaleRecords.Settings settings,
      String pixAccountName,
      List<CounterSaleMoneyService.OpenSession> sessions,
      List<CounterSaleRecords.Sale> sales) {}

  /** The counter screen: the viewer's role, what can be sold with and the latest sales. */
  @Transactional
  public Overview overview(UUID actor, UUID tenant) {
    String role = member(actor, tenant, false);
    var settings = settings(tenant);
    return new Overview(
        role,
        settings,
        settings.pixAccountId() == null
            ? null
            : money.bankAccountName(tenant, settings.pixAccountId()).orElse(null),
        money.openSessions(tenant),
        sales.recent(tenant, RECENT));
  }

  /** The stock search of "Adicionar produto do estoque": name or SKU, at least 2 characters. */
  @Transactional
  public List<CounterSaleRecords.Product> search(UUID actor, UUID tenant, String text) {
    member(actor, tenant, false);
    String clean = text == null ? "" : text.strip();
    if (clean.codePointCount(0, clean.length()) < 2 || clean.length() > 120)
      throw new IllegalArgumentException("Invalid search");
    return sales.search(tenant, clean, clock.instant(), SEARCH_LIMIT);
  }

  /** A registered sale and whether its viewer is the OWNER (employees never see fees). */
  public record Registered(CounterSaleRecords.Sale sale, boolean owner) {}

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Registered register(UUID actor, UUID tenant, CounterSaleInput.Sale input) {
    boolean owner = "OWNER".equals(member(actor, tenant, true));
    var previous = sales.byRequest(tenant, input.requestId());
    if (previous.isPresent()) {
      if (!same(previous.get(), input))
        throw new CounterSaleException(CounterSaleException.Reason.REQUEST_CONFLICT);
      return new Registered(previous.get(), owner);
    }
    Instant now = now();
    Map<UUID, CounterSaleRecords.Product> found =
        sales
            .products(
                tenant, input.lines().stream().map(CounterSaleInput.Line::variantId).toList(), now)
            .stream()
            .collect(Collectors.toMap(CounterSaleRecords.Product::variantId, Function.identity()));
    if (found.size() != input.lines().size()) throw missing();
    List<CounterSaleRecords.Item> items = new ArrayList<>();
    BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
    BigDecimal cost = BigDecimal.ZERO.setScale(2);
    boolean costIncomplete = false;
    for (var line : input.lines()) {
      var product = found.get(line.variantId());
      BigDecimal total = product.price().multiply(BigDecimal.valueOf(line.quantity()));
      subtotal = subtotal.add(total);
      if (product.cost() == null) costIncomplete = true;
      else cost = cost.add(product.cost().multiply(BigDecimal.valueOf(line.quantity())));
      items.add(
          new CounterSaleRecords.Item(
              product.variantId(),
              product.name(),
              product.options(),
              product.sku(),
              product.price(),
              product.cost(),
              line.quantity(),
              total));
    }
    BigDecimal limit = new BigDecimal("1000000000000000");
    if (subtotal.compareTo(limit) >= 0 || cost.compareTo(limit) >= 0)
      throw new IllegalArgumentException("Invalid amount");
    if (input.discount().compareTo(subtotal) >= 0)
      throw new IllegalArgumentException("Invalid discount");
    var settings = settings(tenant);
    if (!owner
        && input
                .discount()
                .multiply(BigDecimal.valueOf(100))
                .compareTo(subtotal.multiply(settings.staffDiscountLimit()))
            > 0) throw new CounterSaleException(CounterSaleException.Reason.DISCOUNT_LIMIT);
    BigDecimal total = subtotal.subtract(input.discount());

    UUID account = null;
    UUID session = null;
    BigDecimal feePercent = null;
    BigDecimal fee = null;
    LocalDate release = null;
    switch (input.method()) {
      case "CASH" -> {
        session = input.cashSessionId();
        account =
            money
                .lockOpenDrawer(tenant, session)
                .orElseThrow(
                    () -> new CounterSaleException(CounterSaleException.Reason.SESSION_CLOSED));
      }
      case "PIX" -> {
        account = settings.pixAccountId();
        if (account == null || money.bankAccountName(tenant, account).isEmpty())
          throw new CounterSaleException(CounterSaleException.Reason.PIX_NOT_CONFIGURED);
      }
      default -> {
        feePercent = settings.feePercent(input.method());
        if (feePercent != null)
          fee =
              total.multiply(feePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN);
        Integer days = settings.releaseDays(input.method());
        if (days != null) release = today().plusDays(days);
      }
    }
    UUID id = UUID.randomUUID();
    var sale =
        new CounterSaleRecords.Sale(
            id,
            input.requestId(),
            input.method(),
            subtotal,
            input.discount(),
            total,
            feePercent,
            fee,
            account,
            null,
            session,
            release,
            "ACTIVE",
            null,
            now,
            null,
            null,
            null,
            null,
            items);
    if (!sales.insert(tenant, sale, actor, now))
      throw new CounterSaleException(CounterSaleException.Reason.REQUEST_CONFLICT);
    if (!inventory.sellAtCounter(
        tenant,
        id,
        actor,
        input.lines().stream()
            .map(line -> new ReservationLine(line.variantId(), line.quantity()))
            .toList(),
        REASON)) throw new CounterSaleException(CounterSaleException.Reason.INSUFFICIENT_STOCK);
    money.recordSale(
        tenant,
        id,
        total,
        cost,
        costIncomplete,
        new CounterSaleMoneyService.Payment(
            account,
            session,
            fee,
            !"DEBIT".equals(input.method()) && !"CREDIT".equals(input.method()),
            REASON),
        actor);
    return new Registered(sales.byId(tenant, id).orElseThrow(), owner);
  }

  /** The OWNER cancels once with a reason; a cancelled sale is returned unchanged. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CounterSaleRecords.Sale cancel(
      UUID actor, UUID tenant, UUID id, String reason, boolean restock) {
    requireOwner(member(actor, tenant, true));
    var sale = sales.lock(tenant, id).orElseThrow(CounterSaleService::missing);
    if (!sale.active()) return sale;
    sales.cancel(tenant, id, actor, reason, restock, now());
    if (restock) inventory.reverseCounterSale(tenant, id, actor, "Cancelamento de venda na loja");
    money.recordRefund(
        tenant,
        id,
        sale.total(),
        restock,
        sale.accountId(),
        !sale.card(),
        "Cancelamento de venda na loja",
        actor);
    return sales.byId(tenant, id).orElseThrow();
  }

  @Transactional
  public CounterSaleRecords.Settings settings(UUID actor, UUID tenant) {
    requireOwner(member(actor, tenant, false));
    return settings(tenant);
  }

  /** The OWNER's settings; the Pix account must be one of the store's bank accounts. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CounterSaleRecords.Settings saveSettings(
      UUID actor, UUID tenant, CounterSaleInput.Settings input) {
    requireOwner(member(actor, tenant, true));
    if (input.pixAccountId() != null
        && money.bankAccountName(tenant, input.pixAccountId()).isEmpty())
      throw new CounterSaleException(CounterSaleException.Reason.PROVIDER_READ_ONLY);
    sales.saveSettings(tenant, input, actor, now());
    return settings(tenant);
  }

  /** A retry carries the same items, discount, method and session. */
  private static boolean same(CounterSaleRecords.Sale stored, CounterSaleInput.Sale input) {
    if (!stored.paymentMethod().equals(input.method())
        || stored.discount().compareTo(input.discount()) != 0
        || !Objects.equals(stored.cashSessionId(), input.cashSessionId())
        || stored.items().size() != input.lines().size()) return false;
    for (int index = 0; index < input.lines().size(); index++) {
      var item = stored.items().get(index);
      var line = input.lines().get(index);
      if (!item.variantId().equals(line.variantId()) || item.quantity() != line.quantity())
        return false;
    }
    return true;
  }

  private CounterSaleRecords.Settings settings(UUID tenant) {
    return sales.settings(tenant).orElse(CounterSaleRecords.Settings.NONE);
  }

  private String member(UUID actor, UUID tenant, boolean write) {
    return sales.lockMember(actor, tenant, write).orElseThrow(CounterSaleService::missing);
  }

  private static void requireOwner(String role) {
    if (!"OWNER".equals(role)) throw missing();
  }

  private static CounterSaleException missing() {
    return new CounterSaleException(CounterSaleException.Reason.MISSING);
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ZONE));
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
