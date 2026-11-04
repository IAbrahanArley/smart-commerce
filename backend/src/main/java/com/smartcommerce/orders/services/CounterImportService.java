package com.smartcommerce.orders.services;

import com.smartcommerce.finance.services.CounterSaleMoneyService;
import com.smartcommerce.inventory.domain.ReservationLine;
import com.smartcommerce.inventory.services.InventoryReservationService;
import com.smartcommerce.orders.domain.CounterImportParser;
import com.smartcommerce.orders.domain.CounterSaleException;
import com.smartcommerce.orders.domain.CounterSaleRecords;
import com.smartcommerce.orders.repositories.CounterSaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counter sales imported from a spreadsheet (SC-054, ADR-0019 C14). Only the store's OWNER.
 *
 * <ul>
 *   <li>Preview: reads the file and says, sale by sale, what would be written, what was already
 *       imported (same fingerprint) and what is wrong (line by line). Nothing is written.
 *   <li>Confirm: reads the same file again under the store lock and writes every sale that is
 *       ready, in one transaction, the way a live counter sale is written (stock, ledger, money).
 *       If the stock changed since the check (an online sale took the last unit), nothing is
 *       written and the OWNER previews again.
 *   <li>The date of the file is the sale's competence; the stock leaves at the import. Prices come
 *       from the file (the sale already happened); costs from the catalog now.
 *   <li>Cash goes into the account the OWNER chooses at confirmation, outside any cash session; Pix
 *       into the store's Pix account; cards stay receivable from the sale date.
 * </ul>
 */
@Service
public class CounterImportService {
  private static final String REASON = "Venda na loja importada";
  private final CounterSaleRepository sales;
  private final InventoryReservationService inventory;
  private final CounterSaleMoneyService money;
  private final Clock clock;

  public CounterImportService(
      CounterSaleRepository sales,
      InventoryReservationService inventory,
      CounterSaleMoneyService money,
      Clock clock) {
    this.sales = sales;
    this.inventory = inventory;
    this.money = money;
    this.clock = clock;
  }

  public enum Status {
    READY,
    IMPORTED,
    ERROR
  }

  public record PreviewItem(
      int line, String sku, String name, int quantity, BigDecimal unitPrice, BigDecimal discount) {}

  public record PreviewSale(
      List<Integer> lines,
      LocalDate date,
      String code,
      String method,
      List<PreviewItem> items,
      BigDecimal total,
      Status status,
      List<String> messages) {}

  /** What the file holds: file-level problems (line 0) or problems of lines no sale kept. */
  public record Preview(
      int itemLines,
      List<PreviewSale> sales,
      List<CounterImportParser.Problem> problems,
      boolean fileRefused) {
    public long count(Status status) {
      return sales.stream().filter(sale -> sale.status() == status).count();
    }

    public boolean needsCashAccount() {
      return sales.stream()
          .anyMatch(sale -> sale.status() == Status.READY && "CASH".equals(sale.method()));
    }
  }

  public record Result(UUID importId, int imported, Preview preview) {}

  @Transactional
  public Preview preview(UUID actor, UUID tenant, byte[] file) {
    requireOwner(actor, tenant, false);
    return analyse(tenant, file).preview();
  }

  /** Writes the ready sales of the file; the same file again writes nothing new. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Result confirm(UUID actor, UUID tenant, byte[] file, UUID cashAccount) {
    requireOwner(actor, tenant, true);
    Analysis analysis = analyse(tenant, file);
    Preview preview = analysis.preview();
    if (preview.fileRefused()) throw new IllegalArgumentException("Invalid file");
    List<Ready> ready = analysis.ready();
    if (ready.isEmpty()) return new Result(null, 0, preview);
    boolean cash = ready.stream().anyMatch(sale -> "CASH".equals(sale.parsed().method()));
    if (cash && (cashAccount == null || money.cashOrBankAccountName(tenant, cashAccount).isEmpty()))
      throw new CounterSaleException(CounterSaleException.Reason.CASH_ACCOUNT_REQUIRED);
    var settings = sales.settings(tenant).orElse(CounterSaleRecords.Settings.NONE);
    Instant now = now();
    UUID importId = UUID.randomUUID();
    sales.insertImport(
        tenant,
        importId,
        digest(file),
        preview.itemLines(),
        ready.size(),
        cash ? cashAccount : null,
        actor,
        now);
    for (Ready sale : ready) write(actor, tenant, importId, sale, settings, cashAccount, now);
    return new Result(importId, ready.size(), preview);
  }

  private record Ready(
      CounterImportParser.Sale parsed, Map<String, CounterSaleRecords.Product> products) {}

  private record Analysis(Preview preview, List<Ready> ready) {}

  private Analysis analyse(UUID tenant, byte[] file) {
    Instant now = clock.instant();
    var parsed = CounterImportParser.parse(file, today());
    if (parsed.fileRefused())
      return new Analysis(new Preview(0, List.of(), parsed.problems(), true), List.of());
    Map<String, CounterSaleRecords.Product> bySku =
        sales
            .productsBySku(
                tenant,
                parsed.sales().stream()
                    .flatMap(sale -> sale.items().stream())
                    .map(CounterImportParser.Item::sku)
                    .collect(Collectors.toSet()),
                now)
            .stream()
            .collect(Collectors.toMap(CounterSaleRecords.Product::sku, Function.identity()));
    var imported =
        sales.importedKeys(
            tenant, parsed.sales().stream().map(CounterImportParser.Sale::importKey).toList());
    var settings = sales.settings(tenant).orElse(CounterSaleRecords.Settings.NONE);
    boolean pix =
        settings.pixAccountId() != null
            && money.bankAccountName(tenant, settings.pixAccountId()).isPresent();
    Map<String, Integer> taken = new HashMap<>();
    List<PreviewSale> shown = new ArrayList<>();
    List<Ready> ready = new ArrayList<>();
    for (var sale : parsed.sales()) {
      List<String> messages = new ArrayList<>();
      List<PreviewItem> items = new ArrayList<>();
      BigDecimal total = BigDecimal.ZERO.setScale(2);
      for (var item : sale.items()) {
        var product = bySku.get(item.sku());
        if (product == null)
          messages.add("Linha " + item.line() + ": SKU " + item.sku() + " não está no catálogo.");
        items.add(
            new PreviewItem(
                item.line(),
                item.sku(),
                product == null ? null : label(product),
                item.quantity(),
                item.unitPrice(),
                item.discount()));
        total =
            total
                .add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .subtract(item.discount());
      }
      Status status;
      if (imported.contains(sale.importKey())) status = Status.IMPORTED;
      else {
        if ("PIX".equals(sale.method()) && !pix)
          messages.add("O dono da loja ainda não escolheu a conta que recebe o Pix.");
        if (messages.isEmpty())
          for (var item : sale.items()) {
            var product = bySku.get(item.sku());
            int wanted = taken.getOrDefault(item.sku(), 0) + item.quantity();
            if (wanted > product.available())
              messages.add(
                  "Linha "
                      + item.line()
                      + ": estoque insuficiente de "
                      + item.sku()
                      + " (disponível: "
                      + Math.max(0, product.available() - taken.getOrDefault(item.sku(), 0))
                      + ").");
          }
        status = messages.isEmpty() ? Status.READY : Status.ERROR;
        if (status == Status.READY) {
          for (var item : sale.items()) taken.merge(item.sku(), item.quantity(), Integer::sum);
          ready.add(new Ready(sale, bySku));
        }
      }
      shown.add(
          new PreviewSale(
              sale.lines(),
              sale.date(),
              sale.code(),
              sale.method(),
              items,
              total,
              status,
              messages));
    }
    return new Analysis(new Preview(parsed.itemLines(), shown, parsed.problems(), false), ready);
  }

  private void write(
      UUID actor,
      UUID tenant,
      UUID importId,
      Ready ready,
      CounterSaleRecords.Settings settings,
      UUID cashAccount,
      Instant now) {
    var parsed = ready.parsed();
    String method = parsed.method();
    List<CounterSaleRecords.Item> items = new ArrayList<>();
    BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
    BigDecimal discount = BigDecimal.ZERO.setScale(2);
    BigDecimal cost = BigDecimal.ZERO.setScale(2);
    boolean costIncomplete = false;
    for (var item : parsed.items()) {
      var product = ready.products().get(item.sku());
      BigDecimal line = item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
      subtotal = subtotal.add(line);
      discount = discount.add(item.discount());
      if (product.cost() == null) costIncomplete = true;
      else cost = cost.add(product.cost().multiply(BigDecimal.valueOf(item.quantity())));
      items.add(
          new CounterSaleRecords.Item(
              product.variantId(),
              product.name(),
              product.options(),
              product.sku(),
              item.unitPrice(),
              product.cost(),
              item.quantity(),
              line));
    }
    BigDecimal limit = new BigDecimal("1000000000000000");
    if (subtotal.compareTo(limit) >= 0 || cost.compareTo(limit) >= 0)
      throw new IllegalArgumentException("Invalid amount");
    BigDecimal total = subtotal.subtract(discount);
    UUID account =
        "CASH".equals(method) ? cashAccount : "PIX".equals(method) ? settings.pixAccountId() : null;
    BigDecimal feePercent = settings.feePercent(method);
    BigDecimal fee =
        feePercent == null
            ? null
            : total.multiply(feePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN);
    Integer days = settings.releaseDays(method);
    LocalDate release = days == null ? null : parsed.date().plusDays(days);
    UUID id = UUID.randomUUID();
    var sale =
        new CounterSaleRecords.Sale(
            id,
            UUID.nameUUIDFromBytes(
                ("counter-import:" + parsed.importKey()).getBytes(StandardCharsets.UTF_8)),
            method,
            subtotal,
            discount,
            total,
            feePercent,
            fee,
            account,
            null,
            null,
            release,
            "ACTIVE",
            null,
            now,
            null,
            null,
            null,
            null,
            items,
            parsed.date(),
            null,
            null,
            null);
    if (!sales.insert(tenant, sale, actor, now, importId, parsed.importKey(), parsed.date()))
      throw new CounterSaleException(CounterSaleException.Reason.REQUEST_CONFLICT);
    if (!inventory.sellAtCounter(
        tenant,
        id,
        actor,
        items.stream().map(item -> new ReservationLine(item.variantId(), item.quantity())).toList(),
        REASON)) throw new CounterSaleException(CounterSaleException.Reason.STALE_IMPORT);
    boolean card = "DEBIT".equals(method) || "CREDIT".equals(method);
    money.recordSale(
        tenant,
        id,
        total,
        cost,
        costIncomplete,
        new CounterSaleMoneyService.Payment(account, null, fee, !card, REASON),
        actor,
        parsed.date().atStartOfDay(CounterSaleService.ZONE).toInstant(),
        parsed.date());
  }

  private static String label(CounterSaleRecords.Product product) {
    return product.options().isEmpty()
        ? product.name()
        : product.name() + " (" + product.options() + ")";
  }

  private void requireOwner(UUID actor, UUID tenant, boolean write) {
    if (!"OWNER".equals(sales.lockMember(actor, tenant, write).orElse(null)))
      throw new CounterSaleException(CounterSaleException.Reason.MISSING);
  }

  private static String digest(byte[] file) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(CounterSaleService.ZONE));
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
