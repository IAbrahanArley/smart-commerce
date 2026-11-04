package com.smartcommerce.customers.dto.response;

import com.smartcommerce.customers.domain.StoreCustomer;
import java.util.List;

/**
 * Store customers as the admin sees them (SC-055): the OWNER gets the whole record; an employee
 * gets the name and the phone masked to its last 4 digits, nothing else.
 */
public final class CustomerViews {
  private CustomerViews() {}

  public record Customer(
      String id, String name, String phone, String email, String note, String creditLimit) {
    public static Customer of(StoreCustomer customer, boolean owner) {
      return owner
          ? new Customer(
              customer.id().toString(),
              customer.name(),
              customer.phone(),
              customer.email(),
              customer.note(),
              customer.creditLimit() == null ? null : customer.creditLimit().toPlainString())
          : new Customer(
              customer.id().toString(), customer.name(), customer.maskedPhone(), null, null, null);
    }

    public static List<Customer> of(List<StoreCustomer> customers, boolean owner) {
      return customers.stream().map(customer -> of(customer, owner)).toList();
    }
  }
}
