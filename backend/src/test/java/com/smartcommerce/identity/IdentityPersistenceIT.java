package com.smartcommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.identity.domain.EmailAddress;
import com.smartcommerce.identity.domain.IdentityStatus;
import com.smartcommerce.identity.domain.PasswordHash;
import com.smartcommerce.identity.repositories.JdbcIdentityRepository;
import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class IdentityPersistenceIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
  private static DriverManagerDataSource dataSource;
  private static JdbcTemplate jdbc;
  private static JdbcIdentityRepository repository;
  private static Flyway flyway;

  @BeforeAll
  static void migrateRealPostgres() {
    dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    repository = new JdbcIdentityRepository(jdbc);
    flyway = Flyway.configure().dataSource(dataSource).cleanDisabled(true).load();
    flyway.migrate();
  }

  @BeforeEach
  void clearOnlyDisposableIdentityFixtures() {
    jdbc.update("DELETE FROM identity_local_credential");
    jdbc.update("DELETE FROM identity_user");
  }

  @Test
  void additiveMigrationPreservesBootstrapAndCanBeReapplied() {
    assertThat(
            jdbc.queryForObject(
                "SELECT metadata_value FROM app_metadata WHERE metadata_key = 'bootstrap'",
                String.class))
        .isEqualTo("SC-001");
    assertThat(
            jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class))
        .startsWith("1", "2");
    flyway.validate();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    assertThat(
            jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class))
        .contains(
            "identity_user",
            "identity_local_credential",
            "identity_email_verification",
            "identity_rate_limit",
            "spring_session",
            "spring_session_attributes",
            "tenant",
            "tenant_membership",
            "store")
        .doesNotContain("roles");
  }

  @Test
  void pendingIdentityRoundTripsWithoutAnyCredential() {
    UUID id = UUID.randomUUID();
    repository.createPending(id, EmailAddress.parse("Owner+qa@BÜCHER.EXAMPLE"), NOW);
    var stored = repository.findUser(id).orElseThrow();
    assertThat(stored.email().original()).isEqualTo("Owner+qa@BÜCHER.EXAMPLE");
    assertThat(stored.email().comparisonKey()).isEqualTo("Owner+qa@xn--bcher-kva.example");
    assertThat(stored.status()).isEqualTo(IdentityStatus.PENDING_EMAIL);
    assertThat(stored.authVersion()).isZero();
    assertThat(stored.verifiedAt()).isNull();
    assertThat(stored.createdAt()).isEqualTo(NOW);
    assertThat(repository.findByEmail(EmailAddress.parse("Owner+qa@xn--bcher-kva.example")))
        .isPresent();
    assertThat(repository.findByEmail(EmailAddress.parse("owner+qa@xn--bcher-kva.example")))
        .isEmpty();
    assertThat(repository.insertCredential(id, hash('1'), NOW)).isFalse();
    assertThat(repository.findCredential(id)).isEmpty();
    assertThat(stored.toString()).doesNotContain("Owner", "BÜCHER");
  }

  @Test
  void uniqueConstraintPreservesCaseDotsAndTags() {
    for (String local : List.of("Owner", "owner", "Own.er", "Owner+tag")) {
      repository.createPending(UUID.randomUUID(), EmailAddress.parse(local + "@example.test"), NOW);
    }
    assertThatThrownBy(
            () ->
                repository.createPending(
                    UUID.randomUUID(), EmailAddress.parse("Owner@EXAMPLE.TEST"), NOW))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageNotContaining("Owner")
        .hasMessageNotContaining("EXAMPLE.TEST")
        .hasNoCause();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_user", Integer.class))
        .isEqualTo(4);
  }

  @Test
  void concurrentEquivalentEmailsOnIndependentConnectionsHaveOneWinner() throws Exception {
    CyclicBarrier barrier = new CyclicBarrier(2);
    Callable<String> insert =
        () -> {
          try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            barrier.await(10, TimeUnit.SECONDS);
            try (PreparedStatement statement =
                connection.prepareStatement(
                    "INSERT INTO identity_user (id,email_original,email_key,status,auth_version,created_at,updated_at) VALUES (?,?,?,'PENDING_EMAIL',0,?,?)")) {
              statement.setObject(1, UUID.randomUUID());
              statement.setString(2, "Concurrent@EXAMPLE.TEST");
              statement.setString(3, EmailAddress.parse("Concurrent@EXAMPLE.TEST").comparisonKey());
              statement.setTimestamp(4, Timestamp.from(NOW));
              statement.setTimestamp(5, Timestamp.from(NOW));
              statement.executeUpdate();
              connection.commit();
              return "committed";
            } catch (SQLException exception) {
              connection.rollback();
              return exception.getSQLState();
            }
          }
        };
    assertThat(race(insert, insert)).containsExactlyInAnyOrder("committed", "23505");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_user", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void databaseRejectsOrphansDuplicatesPlaintextAndInvalidIdentityState() {
    UUID id = activeFixture();
    assertThat(repository.insertCredential(id, hash('1'), NOW)).isTrue();
    assertThat(repository.findCredential(id).orElseThrow().hash().encoded())
        .isEqualTo(hash('1').encoded());
    assertThatThrownBy(() -> repository.insertCredential(id, hash('2'), NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertRawCredential(UUID.randomUUID(), hash('1').encoded()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE identity_local_credential SET password_hash = 'plaintext' WHERE user_id = ?",
                    id))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE identity_user SET auth_version = -1 WHERE id = ?", id))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE identity_user SET verified_at = NULL WHERE id = ?", id))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM identity_user WHERE id = ?", id))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(hash('1').toString()).doesNotContain(hash('1').encoded());
  }

  @Test
  void failedMultiWriteTransactionLeavesNoPartialIdentity() {
    UUID id = UUID.randomUUID();
    var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    assertThatThrownBy(
            () ->
                transaction.execute(
                    status -> {
                      repository.createPending(
                          id, EmailAddress.parse("Rollback@example.test"), NOW);
                      insertRawCredential(id, "plaintext");
                      return null;
                    }))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(repository.findUser(id)).isEmpty();
    assertThat(repository.findCredential(id)).isEmpty();
  }

  @Test
  void databaseRejectsNoncanonicalAndOutOfBoundsHashCostsOnExistingCredential() {
    UUID id = activeFixture();
    repository.insertCredential(id, hash('1'), NOW);
    for (String iterations : List.of("0600000", "599999", "2000001")) {
      String malformed = "{pbkdf2-sha256-nfc-i" + iterations + "-s16-l256-v1}" + "ab".repeat(48);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE identity_local_credential SET password_hash = ? WHERE user_id = ?",
                      malformed,
                      id))
          .isInstanceOf(DataIntegrityViolationException.class)
          .satisfies(
              exception ->
                  assertThat(((SQLException) exception.getCause()).getSQLState())
                      .isEqualTo("23514"));
    }
    assertThat(repository.findCredential(id).orElseThrow().hash().encoded())
        .isEqualTo(hash('1').encoded());
  }

  @Test
  void concurrentRehashUsesCompareAndSwapAndCannotOverwriteWinner() throws Exception {
    UUID id = activeFixture();
    repository.insertCredential(id, hash('1'), NOW);
    CyclicBarrier barrier = new CyclicBarrier(2);
    assertThat(race(rehash(id, hash('2'), barrier), rehash(id, hash('3'), barrier)))
        .containsExactlyInAnyOrder(true, false);
    assertThat(repository.findCredential(id).orElseThrow().hash().encoded())
        .isIn(hash('2').encoded(), hash('3').encoded());
    assertThat(repository.upgradeCredentialHash(id, 0, hash('1'), hash('4'), NOW)).isFalse();
  }

  @Test
  void rehashCannotBypassChangedAuthVersionOrDisabledStatus() {
    UUID id = activeFixture();
    repository.insertCredential(id, hash('1'), NOW);
    jdbc.update("UPDATE identity_user SET auth_version = 1 WHERE id = ?", id);
    assertThat(repository.upgradeCredentialHash(id, 0, hash('1'), hash('2'), NOW)).isFalse();
    jdbc.update("UPDATE identity_user SET status = 'DISABLED' WHERE id = ?", id);
    assertThat(repository.upgradeCredentialHash(id, 1, hash('1'), hash('2'), NOW)).isFalse();
    assertThat(repository.findCredential(id).orElseThrow().hash().encoded())
        .isEqualTo(hash('1').encoded());
  }

  @Test
  void realPasswordHashPersistsAndCanEvolveWithoutDowngrade() {
    UUID id = activeFixture();
    var currentEncoder = new IdentityPasswordEncoder();
    var futureEncoder = new IdentityPasswordEncoder(600_001);
    String password = "Real persisted synthetic password";
    PasswordHash original = new PasswordHash(currentEncoder.encode(password));
    assertThat(repository.insertCredential(id, original, NOW)).isTrue();
    PasswordHash stored = repository.findCredential(id).orElseThrow().hash();
    assertThat(currentEncoder.matches(password, stored.encoded())).isTrue();
    PasswordHash replacement = new PasswordHash(futureEncoder.encode(password));
    assertThat(repository.upgradeCredentialHash(id, 0, stored, replacement, NOW.plusSeconds(1)))
        .isTrue();
    PasswordHash upgraded = repository.findCredential(id).orElseThrow().hash();
    assertThat(futureEncoder.matches(password, upgraded.encoded())).isTrue();
    assertThat(futureEncoder.upgradeEncoding(upgraded.encoded())).isFalse();
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> repository.upgradeCredentialHash(id, 0, upgraded, original, NOW.plusSeconds(2)));
    assertThat(repository.findCredential(id).orElseThrow().updatedAt())
        .isEqualTo(NOW.plusSeconds(1));
  }

  @Test
  void failedFlywayMigrationRollsBackDdlInDisposableSchema(@TempDir Path directory)
      throws Exception {
    Files.writeString(
        directory.resolve("V1__intentional_failure.sql"),
        "CREATE TABLE must_rollback (id INTEGER); SELECT 1/0;");
    var failing =
        Flyway.configure()
            .dataSource(dataSource)
            .schemas("qa_failed_migration")
            .defaultSchema("qa_failed_migration")
            .locations("filesystem:" + directory.toAbsolutePath())
            .cleanDisabled(true)
            .load();
    assertThatThrownBy(failing::migrate).isInstanceOf(FlywayException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'qa_failed_migration' AND table_name = 'must_rollback'",
                Integer.class))
        .isZero();
    flyway.validate();
  }

  private static UUID activeFixture() {
    UUID id = UUID.randomUUID();
    repository.createPending(id, EmailAddress.parse("Fixture" + id + "@example.test"), NOW);
    // Fixture only: no production activation service or proof bypass is implemented.
    jdbc.update(
        "UPDATE identity_user SET status = 'ACTIVE', verified_at = ? WHERE id = ?",
        Timestamp.from(NOW),
        id);
    return id;
  }

  private static PasswordHash hash(char nibble) {
    return new PasswordHash(
        "{" + IdentityPasswordEncoder.CURRENT_ID + "}" + String.valueOf(nibble).repeat(96));
  }

  private static void insertRawCredential(UUID id, String hash) {
    jdbc.update(
        "INSERT INTO identity_local_credential (user_id,password_hash,created_at,updated_at) VALUES (?,?,?,?)",
        id,
        hash,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private static Callable<Boolean> rehash(
      UUID id, PasswordHash replacement, CyclicBarrier barrier) {
    return () ->
        new TransactionTemplate(new DataSourceTransactionManager(dataSource))
            .execute(
                status -> {
                  DataSourceUtils.getConnection(dataSource);
                  try {
                    barrier.await(10, TimeUnit.SECONDS);
                  } catch (Exception exception) {
                    throw new IllegalStateException("Concurrent test barrier failed", exception);
                  }
                  return repository.upgradeCredentialHash(
                      id, 0, hash('1'), replacement, NOW.plusSeconds(1));
                });
  }

  private static <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var firstResult = executor.submit(first);
      var secondResult = executor.submit(second);
      return List.of(firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS));
    }
  }
}
