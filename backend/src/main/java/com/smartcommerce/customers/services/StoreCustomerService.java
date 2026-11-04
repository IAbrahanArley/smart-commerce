package com.smartcommerce.customers.services;

import com.smartcommerce.customers.domain.CustomerException;
import com.smartcommerce.customers.domain.CustomerInput;
import com.smartcommerce.customers.domain.StoreCustomer;
import com.smartcommerce.customers.repositories.StoreCustomerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The store's customers (SC-055, ADR-0019 C9, ADR-0021 R2), for store credit.
 *
 * <ul>
 *   <li>The OWNER and the store's employees search and create customers at the counter; an employee
 *       gives only name and phone and sees the phone masked (decided on 2026-11-04). Only the OWNER
 *       sets e-mail, note and credit limit, and edits a customer. Anyone else gets one 404. Writes
 *       lock the store.
 *   <li>The request id makes a retry the same customer. An e-mail is one customer per store.
 *   <li>Nothing personal is logged or returned in an error.
 * </ul>
 */
@Service
public class StoreCustomerService {
  static final int SEARCH_LIMIT = 20;

  private final StoreCustomerRepository customers;
  private final Clock clock;

  public StoreCustomerService(StoreCustomerRepository customers, Clock clock) {
    this.customers = customers;
    this.clock = clock;
  }

  /** Customers and whether the viewer is the OWNER (an employee sees phones masked). */
  public record Found(List<StoreCustomer> customers, boolean owner) {}

  @Transactional
  public Found search(UUID actor, UUID tenant, String text) {
    boolean owner = owner(member(actor, tenant, false));
    return new Found(
        customers.search(tenant, CustomerInput.search(text), owner, SEARCH_LIMIT), owner);
  }

  public record Saved(StoreCustomer customer, boolean owner) {}

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Saved create(UUID actor, UUID tenant, CustomerInput.Create input) {
    boolean owner = owner(member(actor, tenant, true));
    if (!owner && !input.data().contactOnly()) throw new IllegalArgumentException("Owner fields");
    var previous = customers.byRequest(tenant, input.requestId());
    if (previous.isPresent()) {
      if (!input.data().sameAs(previous.get()))
        throw new CustomerException(CustomerException.Reason.REQUEST_CONFLICT);
      return new Saved(previous.get(), owner);
    }
    if (input.data().emailKey() != null
        && customers.emailTaken(tenant, input.data().emailKey(), null))
      throw new CustomerException(CustomerException.Reason.EMAIL_TAKEN);
    UUID id = UUID.randomUUID();
    customers.insert(tenant, id, input.requestId(), input.data(), actor, now());
    return new Saved(customers.byId(tenant, id).orElseThrow(), owner);
  }

  /** The OWNER edits a customer; lowering the limit below the balance only blocks new sales. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public StoreCustomer update(UUID actor, UUID tenant, UUID id, CustomerInput.Data data) {
    if (!owner(member(actor, tenant, true))) throw missing();
    customers.byId(tenant, id).orElseThrow(StoreCustomerService::missing);
    if (data.emailKey() != null && customers.emailTaken(tenant, data.emailKey(), id))
      throw new CustomerException(CustomerException.Reason.EMAIL_TAKEN);
    customers.update(tenant, id, data, actor, now());
    return customers.byId(tenant, id).orElseThrow();
  }

  /**
   * A customer of the store, for store credit (internal: the caller has already authorized the
   * actor in this store and joins its transaction).
   */
  @Transactional(readOnly = true)
  public Optional<StoreCustomer> find(UUID tenant, UUID id) {
    return customers.byId(tenant, id);
  }

  private String member(UUID actor, UUID tenant, boolean write) {
    return customers.lockMember(actor, tenant, write).orElseThrow(StoreCustomerService::missing);
  }

  private static boolean owner(String role) {
    return "OWNER".equals(role);
  }

  private static CustomerException missing() {
    return new CustomerException(CustomerException.Reason.MISSING);
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
