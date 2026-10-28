package com.smartcommerce.content.dto.request;

import com.smartcommerce.content.domain.CompanyInfo;

/** Company details as typed by the owner; normalized and validated by {@link CompanyInfo}. */
public record CompanyRequest(
    String announcement,
    String legalName,
    String cnpj,
    String phone,
    String address,
    String facebook,
    String tiktok,
    String youtube) {
  public CompanyInfo info() {
    return CompanyInfo.of(announcement, legalName, cnpj, phone, address, facebook, tiktok, youtube);
  }
}
