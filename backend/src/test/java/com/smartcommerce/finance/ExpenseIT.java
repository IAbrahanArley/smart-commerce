package com.smartcommerce.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SC-020 over HTTP: the OWNER records and corrects expenses with session and CSRF; each one enters
 * the ledger once, a cancellation adds its reversal and keeps the trail, a retry reuses the same
 * expense, and another store's OWNER sees and changes nothing.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"orders.maintenance.enabled=false"})
class ExpenseIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic expenses integration secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  UUID ownerA;
  UUID tenantA;
  UUID tenantB;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    ownerA = activeIdentity("despesas-a@example.test");
    UUID ownerB = activeIdentity("despesas-b@example.test");
    tenantA = store(ownerA, "despesas-a");
    tenantB = store(ownerB, "despesas-b");
  }

  @Test
  void ownerRecordsCancelsAndCorrectsExpensesWithAnAuditTrailAndLedgerEntries() throws Exception {
    Browser a = login("despesas-a@example.test");
    String request = UUID.randomUUID().toString();
    var created =
        a.post(path(tenantA), expense(request, "RENT", "FIXED", "1500.00", "2026-10-01", null));
    assertThat(created.statusCode()).isEqualTo(200);
    assertThat(created.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    JsonNode rent = JSON.readTree(created.body());
    assertThat(rent.get("amount").asString()).isEqualTo("1500.00");
    assertThat(rent.get("status").asString()).isEqualTo("ACTIVE");
    UUID rentId = UUID.fromString(rent.get("id").asString());

    // A retry of the same submission is the same expense; another body under its key conflicts.
    assertThat(
            JSON.readTree(
                    a.post(
                            path(tenantA),
                            expense(request, "RENT", "FIXED", "1500.00", "2026-10-01", null))
                        .body())
                .get("id")
                .asString())
        .isEqualTo(rentId.toString());
    var conflict =
        a.post(path(tenantA), expense(request, "RENT", "FIXED", "1600.00", "2026-10-01", null));
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(conflict.body()).get("code").asString()).isEqualTo("REQUEST_CONFLICT");

    String bags = UUID.randomUUID().toString();
    a.post(path(tenantA), expense(bags, "PACKAGING", "VARIABLE", "80.40", "2026-10-10", null));
    a.post(
        path(tenantA),
        expense(
            UUID.randomUUID().toString(), "MARKETING", "VARIABLE", "200.00", "2026-09-30", null));

    JsonNode october = JSON.readTree(a.get(path(tenantA) + "?month=2026-10").body());
    // Unpaid bills due in October (due date = competence by default) are still to pay.
    assertThat(october.get("toPay").asString()).isEqualTo("1580.40");
    assertThat(october.get("paid").asString()).isEqualTo("0.00");
    assertThat(october.get("expenses")).hasSize(2);

    // Correction: cancel with a reason (kept, with who and why) and record the right value.
    var cancelled =
        a.post(
            path(tenantA) + "/" + rentId + "/cancel",
            JSON.writeValueAsString(Map.of("reason", "Valor digitado errado")));
    assertThat(cancelled.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(cancelled.body()).get("status").asString()).isEqualTo("CANCELLED");
    assertThat(
            jdbc.queryForObject("SELECT cancelled_by FROM expense WHERE id=?", UUID.class, rentId))
        .isEqualTo(ownerA);
    // Cancelling again changes nothing.
    a.post(
        path(tenantA) + "/" + rentId + "/cancel",
        JSON.writeValueAsString(Map.of("reason", "De novo")));
    a.post(
        path(tenantA),
        expense(UUID.randomUUID().toString(), "RENT", "FIXED", "1450.00", "2026-10-01", null));

    october = JSON.readTree(a.get(path(tenantA) + "?month=2026-10").body());
    assertThat(october.get("toPay").asString()).isEqualTo("1530.40");
    assertThat(october.get("expenses")).hasSize(3); // the cancelled one stays visible

    // Ledger: one EXPENSE per expense and one EXPENSE_REVERSAL for the cancelled one, same dates.
    List<Map<String, Object>> rentEntries =
        jdbc.queryForList(
            "SELECT type,amount,competence_at,settled_at FROM finance_entry WHERE expense_id=? ORDER BY type",
            rentId);
    assertThat(rentEntries).hasSize(2);
    assertThat(rentEntries.get(0).get("type")).isEqualTo("EXPENSE");
    assertThat(rentEntries.get(0).get("amount").toString()).isEqualTo("-1500.00");
    assertThat(rentEntries.get(1).get("type")).isEqualTo("EXPENSE_REVERSAL");
    assertThat(rentEntries.get(1).get("amount").toString()).isEqualTo("1500.00");
    assertThat(rentEntries.get(1).get("competence_at"))
        .isEqualTo(rentEntries.get(0).get("competence_at"));
    assertThat(rentEntries.get(1).get("settled_at"))
        .isEqualTo(rentEntries.get(0).get("settled_at"));
    // Competence starts at midnight in São Paulo (03:00 UTC); unpaid has no settlement.
    assertThat(
            jdbc.queryForObject(
                "SELECT to_char(competence_at AT TIME ZONE 'UTC','YYYY-MM-DD HH24:MI') FROM finance_entry"
                    + " WHERE expense_id=(SELECT id FROM expense WHERE request_id=?::uuid)",
                String.class,
                bags))
        .isEqualTo("2026-10-10 03:00");
    assertThat(
            jdbc.queryForObject(
                    "SELECT sum(amount) FROM finance_entry WHERE tenant_id=? AND type IN ('EXPENSE','EXPENSE_REVERSAL')"
                        + " AND competence_at >= '2026-10-01 03:00+00' AND competence_at < '2026-11-01 03:00+00'",
                    java.math.BigDecimal.class,
                    tenantA)
                .toPlainString())
        .isEqualTo("-1530.40");
  }

  @Test
  void invalidDataCsrfSessionAndOtherStoresAreRefused() throws Exception {
    Browser a = login("despesas-a@example.test");
    Browser b = login("despesas-b@example.test");
    for (String body :
        List.of(
            expense(UUID.randomUUID().toString(), "TAXES", "FIXED", "10.00", "2026-10-01", null),
            expense(UUID.randomUUID().toString(), "RENT", "FIXED", "0", "2026-10-01", null),
            expense(UUID.randomUUID().toString(), "RENT", "FIXED", "10.001", "2026-10-01", null),
            expense(UUID.randomUUID().toString(), "RENT", "FIXED", "10.00", "2026-13-01", null),
            "{}",
            "not json")) assertThat(a.post(path(tenantA), body).statusCode()).isEqualTo(400);
    assertThat(a.get(path(tenantA) + "?month=2026-1").statusCode()).isEqualTo(400);

    String valid =
        expense(UUID.randomUUID().toString(), "RENT", "FIXED", "10.00", "2026-10-01", null);
    assertThat(a.send("POST", path(tenantA), valid, null).statusCode()).isEqualTo(403); // no CSRF
    assertThat(new Browser().get(path(tenantA)).statusCode()).isEqualTo(401);

    var mine = JSON.readTree(a.post(path(tenantA), valid).body());
    String id = mine.get("id").asString();
    // B cannot list, create in, or cancel A's expenses: one 404, nothing changes.
    assertThat(b.get(path(tenantA)).statusCode()).isEqualTo(404);
    assertThat(
            b.post(
                    path(tenantA),
                    expense(
                        UUID.randomUUID().toString(), "RENT", "FIXED", "1.00", "2026-10-01", null))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            b.post(
                    path(tenantA) + "/" + id + "/cancel",
                    JSON.writeValueAsString(Map.of("reason", "Tentativa")))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            b.post(
                    path(tenantB) + "/" + id + "/cancel",
                    JSON.writeValueAsString(Map.of("reason", "Tentativa")))
                .statusCode())
        .isEqualTo(404);
    assertThat(jdbc.queryForObject("SELECT status FROM expense WHERE id=?::uuid", String.class, id))
        .isEqualTo("ACTIVE");
    assertThat(JSON.readTree(b.get(path(tenantB) + "?month=2026-10").body()).get("expenses"))
        .isEmpty();
  }

  @Test
  void concurrentSubmissionsOfOneRequestCreateOneExpenseAndOneEntry() throws Exception {
    Browser a = login("despesas-a@example.test");
    String body =
        expense(UUID.randomUUID().toString(), "OTHER", "VARIABLE", "9.90", "2026-10-02", null);
    CyclicBarrier barrier = new CyclicBarrier(4);
    List<Callable<Integer>> work = new java.util.ArrayList<>();
    for (int i = 0; i < 4; i++)
      work.add(
          () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return a.post(path(tenantA), body).statusCode();
          });
    try (var executor = Executors.newFixedThreadPool(4)) {
      for (var result : executor.invokeAll(work)) assertThat(result.get()).isEqualTo(200);
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM expense", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_entry", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void installmentsSplitTheTotalAndEachCountsInItsDueMonth() throws Exception {
    Browser a = login("despesas-a@example.test");
    String request = UUID.randomUUID().toString();
    Map<String, Object> body = payable(request, "1000.00", "2026-11-30");
    body.put("installments", 3);
    body.put("supplier", "Fornecedor Sintético");
    body.put("documentNumber", "DUP-123");
    var created = a.post(path(tenantA), JSON.writeValueAsString(body));
    assertThat(created.statusCode()).isEqualTo(200);
    JsonNode first = JSON.readTree(created.body());
    assertThat(first.get("installmentNumber").asInt()).isEqualTo(1);
    assertThat(first.get("installmentCount").asInt()).isEqualTo(3);
    // A retry is the same bill; nothing more is created.
    assertThat(
            JSON.readTree(a.post(path(tenantA), JSON.writeValueAsString(body)).body())
                .get("id")
                .asString())
        .isEqualTo(first.get("id").asString());

    // Equal cents, the last absorbing the rest; one month apart, clamped at the month's end.
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT amount::text AS amount, due_date::text AS due, competence_date::text AS comp,"
                + " supplier FROM expense ORDER BY installment_number");
    assertThat(rows)
        .extracting(row -> row.get("amount"))
        .containsExactly("333.33", "333.33", "333.34");
    assertThat(rows)
        .extracting(row -> row.get("due"))
        .containsExactly("2026-11-30", "2026-12-30", "2027-01-30");
    assertThat(rows)
        .extracting(row -> row.get("comp"))
        .isEqualTo(rows.stream().map(row -> row.get("due")).toList());
    assertThat(rows).extracting(row -> row.get("supplier")).containsOnly("Fornecedor Sintético");
    // One EXPENSE per installment, each in its own due month.
    assertThat(
            jdbc.queryForList(
                "SELECT to_char(competence_at AT TIME ZONE 'America/Sao_Paulo','YYYY-MM')"
                    + " || ' ' || amount::text FROM finance_entry ORDER BY competence_at",
                String.class))
        .containsExactly("2026-11 -333.33", "2026-12 -333.33", "2027-01 -333.34");
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(amount)::text FROM finance_entry WHERE type='EXPENSE'", String.class))
        .isEqualTo("-1000.00");
    assertThat(
            JSON.readTree(a.get(path(tenantA) + "?month=2026-12").body()).get("toPay").asString())
        .isEqualTo("333.33");

    // Installments are neither recurring nor paid on creation; the total must cover every cent.
    Map<String, Object> monthly = payable(UUID.randomUUID().toString(), "90.00", "2026-11-30");
    monthly.put("installments", 3);
    monthly.put("recurrence", "MONTHLY");
    Map<String, Object> tiny = payable(UUID.randomUUID().toString(), "0.02", "2026-11-30");
    tiny.put("installments", 3);
    Map<String, Object> tooMany = payable(UUID.randomUUID().toString(), "90.00", "2026-11-30");
    tooMany.put("installments", 25);
    for (var invalid : List.of(monthly, tiny, tooMany))
      assertThat(a.post(path(tenantA), JSON.writeValueAsString(invalid)).statusCode())
          .isEqualTo(400);
  }

  @Test
  void payingRecordsTheAccountTheSettlementAndTheMonthlySuccessorOnce() throws Exception {
    Browser a = login("despesas-a@example.test");
    UUID cash = defaultAccount(a, tenantA);
    Map<String, Object> body = payable(UUID.randomUUID().toString(), "500.00", "2026-10-10");
    body.put("competenceDate", "2026-10-01");
    body.put("recurrence", "MONTHLY");
    JsonNode bill = JSON.readTree(a.post(path(tenantA), JSON.writeValueAsString(body)).body());
    String id = bill.get("id").asString();
    assertThat(bill.get("situation").asString()).isIn("OPEN", "OVERDUE");

    String payment = pay("2026-10-12", cash, "10.00", "5.00");
    var paid = a.post(path(tenantA) + "/" + id + "/pay", payment);
    assertThat(paid.statusCode()).isEqualTo(200);
    JsonNode view = JSON.readTree(paid.body());
    assertThat(view.get("situation").asString()).isEqualTo("PAID");
    assertThat(view.get("paidTotal").asString()).isEqualTo("505.00");
    assertThat(view.get("paidAccountName").asString()).isEqualTo("Caixa da loja");

    // The same payment again changes nothing; another one is refused.
    assertThat(a.post(path(tenantA) + "/" + id + "/pay", payment).statusCode()).isEqualTo(200);
    var other = a.post(path(tenantA) + "/" + id + "/pay", pay("2026-10-13", cash, null, null));
    assertThat(other.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(other.body()).get("code").asString()).isEqualTo("ALREADY_PAID");

    // Money left the account once; the ledger entry got its settlement; fine and discount are
    // entries of their own on the payment day.
    assertThat(
            jdbc.queryForObject(
                "SELECT string_agg(kind || ' ' || amount::text || ' ' || occurred_on::text, ',')"
                    + " FROM account_movement WHERE account_id=?",
                String.class,
                cash))
        .isEqualTo("EXPENSE_PAYMENT -505.00 2026-10-12");
    assertThat(
            jdbc.queryForList(
                "SELECT entry_key, amount::text AS amount,"
                    + " to_char(settled_at AT TIME ZONE 'America/Sao_Paulo','YYYY-MM-DD') AS settled"
                    + " FROM finance_entry WHERE expense_id=?::uuid ORDER BY entry_key",
                id))
        .extracting(
            row -> row.get("entry_key") + " " + row.get("amount") + " " + row.get("settled"))
        .containsExactlyInAnyOrder(
            "expense-discount:" + id + " 5.00 2026-10-12",
            "expense-surcharge:" + id + " -10.00 2026-10-12",
            "expense:" + id + " -500.00 2026-10-12");

    // The monthly bill created next month's one, unpaid, exactly once.
    List<Map<String, Object>> next =
        jdbc.queryForList(
            "SELECT due_date::text AS due, competence_date::text AS comp, paid_on, recurrence"
                + " FROM expense WHERE recurrence_source_id=?::uuid",
            id);
    assertThat(next).hasSize(1);
    assertThat(next.get(0).get("due")).isEqualTo("2026-11-10");
    assertThat(next.get(0).get("comp")).isEqualTo("2026-11-01");
    assertThat(next.get(0).get("paid_on")).isNull();
    assertThat(next.get(0).get("recurrence")).isEqualTo("MONTHLY");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM expense", Integer.class)).isEqualTo(2);

    // Discount that swallows the bill, future day, foreign or provider account: refused.
    String open =
        JSON.readTree(
                a.post(
                        path(tenantA),
                        JSON.writeValueAsString(
                            payable(UUID.randomUUID().toString(), "50.00", "2026-10-20")))
                    .body())
            .get("id")
            .asString();
    assertThat(
            a.post(path(tenantA) + "/" + open + "/pay", pay("2026-10-20", cash, null, "50.00"))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            a.post(path(tenantA) + "/" + open + "/pay", pay("2999-01-01", cash, null, null))
                .statusCode())
        .isEqualTo(400);
    Browser b = login("despesas-b@example.test");
    UUID foreign = defaultAccount(b, tenantB);
    assertThat(
            a.post(path(tenantA) + "/" + open + "/pay", pay("2026-10-20", foreign, null, null))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            b.post(path(tenantA) + "/" + open + "/pay", pay("2026-10-20", cash, null, null))
                .statusCode())
        .isEqualTo(404);
    UUID provider = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO finance_account(id,tenant_id,type,name,system_key,created_at)"
            + " VALUES (?,?,'PAYMENT_PROVIDER','Mercado Pago','MERCADO_PAGO',now())",
        provider,
        tenantA);
    var refused =
        a.post(path(tenantA) + "/" + open + "/pay", pay("2026-10-20", provider, null, null));
    assertThat(refused.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(refused.body()).get("code").asString())
        .isEqualTo("PROVIDER_READ_ONLY");
    assertThat(
            jdbc.queryForObject(
                "SELECT paid_on FROM expense WHERE id=?::uuid", java.sql.Date.class, open))
        .isNull();
  }

  @Test
  void cancellingAPaidBillReturnsTheMoneyAndReversesEveryEntry() throws Exception {
    Browser a = login("despesas-a@example.test");
    UUID cash = defaultAccount(a, tenantA);
    Map<String, Object> body = payable(UUID.randomUUID().toString(), "200.00", "2026-10-15");
    body.put("paidOn", "2026-10-15");
    body.put("paidAccountId", cash.toString());
    JsonNode bill = JSON.readTree(a.post(path(tenantA), JSON.writeValueAsString(body)).body());
    String id = bill.get("id").asString();
    assertThat(bill.get("situation").asString()).isEqualTo("PAID");
    assertThat(balance(cash)).isEqualTo("-200.00");

    var cancelled =
        a.post(
            path(tenantA) + "/" + id + "/cancel",
            JSON.writeValueAsString(Map.of("reason", "Pagamento lançado na conta errada")));
    assertThat(cancelled.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(cancelled.body()).get("situation").asString()).isEqualTo("CANCELLED");
    assertThat(balance(cash)).isEqualTo("0.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(amount)::text FROM finance_entry WHERE expense_id=?::uuid",
                String.class,
                id))
        .isEqualTo("0.00");
    // A cancelled bill is not paid, and a second cancellation returns nothing more.
    var payCancelled =
        a.post(path(tenantA) + "/" + id + "/pay", pay("2026-10-16", cash, null, null));
    assertThat(payCancelled.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(payCancelled.body()).get("code").asString()).isEqualTo("NOT_PAYABLE");
    a.post(
        path(tenantA) + "/" + id + "/cancel", JSON.writeValueAsString(Map.of("reason", "De novo")));
    assertThat(balance(cash)).isEqualTo("0.00");

    // Paid on creation needs its account, and an account needs a payment date.
    Map<String, Object> noAccount = payable(UUID.randomUUID().toString(), "10.00", "2026-10-15");
    noAccount.put("paidOn", "2026-10-15");
    Map<String, Object> noDay = payable(UUID.randomUUID().toString(), "10.00", "2026-10-15");
    noDay.put("paidAccountId", cash.toString());
    for (var invalid : List.of(noAccount, noDay))
      assertThat(a.post(path(tenantA), JSON.writeValueAsString(invalid)).statusCode())
          .isEqualTo(400);
  }

  @Test
  void cancellingAPaidMonthlyBillAlsoCancelsItsUnpaidSuccessor() throws Exception {
    Browser a = login("despesas-a@example.test");
    UUID cash = defaultAccount(a, tenantA);
    Map<String, Object> body = payable(UUID.randomUUID().toString(), "300.00", "2026-10-05");
    body.put("recurrence", "MONTHLY");
    String id =
        JSON.readTree(a.post(path(tenantA), JSON.writeValueAsString(body)).body())
            .get("id")
            .asString();
    a.post(path(tenantA) + "/" + id + "/pay", pay("2026-10-05", cash, null, null));
    String next =
        jdbc.queryForObject(
            "SELECT id::text FROM expense WHERE recurrence_source_id=?::uuid", String.class, id);

    // Correcting the paid bill (cancel, then record it again and pay) leaves one bill per month.
    a.post(
        path(tenantA) + "/" + id + "/cancel",
        JSON.writeValueAsString(Map.of("reason", "Valor errado")));
    assertThat(
            jdbc.queryForObject(
                "SELECT status || ' ' || cancel_reason FROM expense WHERE id=?::uuid",
                String.class,
                next))
        .isEqualTo("CANCELLED Conta mensal anterior cancelada: Valor errado");
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(amount)::text FROM finance_entry WHERE expense_id=?::uuid",
                String.class,
                next))
        .isEqualTo("0.00");
    // A delayed retry of a create whose bill was paid later is still the same bill.
    Map<String, Object> later = payable(UUID.randomUUID().toString(), "40.00", "2026-10-06");
    String laterId =
        JSON.readTree(a.post(path(tenantA), JSON.writeValueAsString(later)).body())
            .get("id")
            .asString();
    a.post(path(tenantA) + "/" + laterId + "/pay", pay("2026-10-06", cash, null, null));
    var retry = a.post(path(tenantA), JSON.writeValueAsString(later));
    assertThat(retry.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(retry.body()).get("id").asString()).isEqualTo(laterId);
  }

  @Test
  void situationsFilterTheMonthAndOverdueBillsComeFromAnyMonth() throws Exception {
    Browser a = login("despesas-a@example.test");
    UUID cash = defaultAccount(a, tenantA);
    String old =
        JSON.readTree(
                a.post(
                        path(tenantA),
                        JSON.writeValueAsString(
                            payable(UUID.randomUUID().toString(), "70.00", "2026-01-10")))
                    .body())
            .get("id")
            .asString();
    String paid =
        JSON.readTree(
                a.post(
                        path(tenantA),
                        JSON.writeValueAsString(
                            payable(UUID.randomUUID().toString(), "30.00", "2026-01-20")))
                    .body())
            .get("id")
            .asString();
    a.post(path(tenantA) + "/" + paid + "/pay", pay("2026-01-20", cash, null, null));
    a.post(
        path(tenantA),
        JSON.writeValueAsString(payable(UUID.randomUUID().toString(), "99.00", "2027-06-10")));

    JsonNode january = JSON.readTree(a.get(path(tenantA) + "?month=2026-01").body());
    assertThat(january.get("toPay").asString()).isEqualTo("70.00");
    assertThat(january.get("paid").asString()).isEqualTo("30.00");
    assertThat(january.get("overdueCount").asLong()).isEqualTo(1);
    assertThat(january.get("overdueTotal").asString()).isEqualTo("70.00");
    JsonNode paidOnly =
        JSON.readTree(a.get(path(tenantA) + "?month=2026-01&situation=PAID").body());
    assertThat(paidOnly.get("expenses")).hasSize(1);
    assertThat(paidOnly.get("expenses").get(0).get("id").asString()).isEqualTo(paid);
    // Overdue ignores the month: January's bill shows up from any month.
    JsonNode overdue =
        JSON.readTree(a.get(path(tenantA) + "?month=2030-05&situation=OVERDUE").body());
    assertThat(overdue.get("expenses")).hasSize(1);
    assertThat(overdue.get("expenses").get(0).get("id").asString()).isEqualTo(old);
    assertThat(a.get(path(tenantA) + "?situation=LATE").statusCode()).isEqualTo(400);
  }

  private UUID defaultAccount(Browser browser, UUID tenant) throws Exception {
    JsonNode accounts =
        JSON.readTree(browser.get("/api/tenants/" + tenant + "/finance/accounts").body());
    return UUID.fromString(accounts.get(0).get("id").asString());
  }

  private String balance(UUID account) {
    return jdbc.queryForObject(
        "SELECT COALESCE(sum(amount),0)::numeric(20,2)::text FROM account_movement"
            + " WHERE account_id=?",
        String.class,
        account);
  }

  private static Map<String, Object> payable(String request, String amount, String due) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("requestId", request);
    body.put("category", "OTHER");
    body.put("kind", "FIXED");
    body.put("description", "Conta sintética");
    body.put("amount", amount);
    body.put("dueDate", due);
    return body;
  }

  private static String pay(String day, UUID account, String surcharge, String discount)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("paidOn", day);
    body.put("accountId", account.toString());
    body.put("surcharge", surcharge);
    body.put("discount", discount);
    return JSON.writeValueAsString(body);
  }

  private static String expense(
      String request, String category, String kind, String amount, String competence, String paid)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("requestId", request);
    body.put("category", category);
    body.put("kind", kind);
    body.put("description", "Despesa sintética");
    body.put("amount", amount);
    body.put("competenceDate", competence);
    body.put("paidOn", paid);
    return JSON.writeValueAsString(body);
  }

  private static String path(UUID tenant) {
    return "/api/tenants/" + tenant + "/expenses";
  }

  private UUID activeIdentity(String email) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO identity_user (id, email_original, email_key, status, created_at, updated_at, verified_at) VALUES (?, ?, ?, 'ACTIVE', now(), now(), now())",
        id,
        email,
        email);
    jdbc.update(
        "INSERT INTO identity_local_credential (user_id, password_hash, created_at, updated_at) VALUES (?, ?, now(), now())",
        id,
        HASH);
    return id;
  }

  private UUID store(UUID owner, String slug) {
    UUID tenant = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO tenant (id, company_name, created_at) VALUES (?, 'Synthetic company', now())",
        tenant);
    jdbc.update(
        "INSERT INTO tenant_membership (tenant_id, user_id, role, created_at) VALUES (?, ?, 'OWNER', now())",
        tenant,
        owner);
    jdbc.update(
        "INSERT INTO store (id, tenant_id, name, slug, created_at) VALUES (?, ?, 'Synthetic store', ?, now())",
        UUID.randomUUID(),
        tenant,
        slug);
    return tenant;
  }

  private Browser login(String email) throws Exception {
    Browser browser = new Browser();
    browser.csrf();
    assertThat(
            browser
                .send(
                    "POST",
                    "/api/auth/login",
                    JSON.writeValueAsString(Map.of("email", email, "password", PASSWORD)),
                    browser.csrfToken)
                .statusCode())
        .isEqualTo(204);
    browser.csrf();
    return browser;
  }

  private final class Browser {
    final HttpClient client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    String csrfToken;

    void csrf() throws Exception {
      var response = get("/api/auth/csrf");
      assertThat(response.statusCode()).isEqualTo(200);
      csrfToken = JSON.readTree(response.body()).get("token").asString();
    }

    HttpResponse<String> get(String path) throws Exception {
      return send("GET", path, null, null);
    }

    HttpResponse<String> post(String path, String body) throws Exception {
      return send("POST", path, body, csrfToken);
    }

    HttpResponse<String> send(String method, String path, String body, String csrf)
        throws Exception {
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .timeout(Duration.ofSeconds(20));
      if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
      if (body != null) request.header("Content-Type", "application/json");
      request.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(body));
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
  }
}
