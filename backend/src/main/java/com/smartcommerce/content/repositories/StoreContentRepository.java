package com.smartcommerce.content.repositories;

import com.smartcommerce.content.domain.CompanyInfo;
import com.smartcommerce.content.domain.StorePageKind;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Company details and institutional pages of a store (SC-043). Every statement is scoped by the
 * tenant it is given; the caller has already checked the OWNER and locked the store.
 */
@Repository
public class StoreContentRepository {
  private final JdbcTemplate jdbc;

  public StoreContentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<CompanyInfo> company(UUID tenant) {
    return jdbc
        .query(
            """
            SELECT announcement,legal_name,cnpj,phone,address,facebook,tiktok,youtube
            FROM store_company WHERE tenant_id=?
            """,
            (row, index) ->
                new CompanyInfo(
                    row.getString("announcement"),
                    row.getString("legal_name"),
                    row.getString("cnpj"),
                    row.getString("phone"),
                    row.getString("address"),
                    row.getString("facebook"),
                    row.getString("tiktok"),
                    row.getString("youtube")),
            tenant)
        .stream()
        .findFirst();
  }

  public void saveCompany(UUID store, UUID tenant, CompanyInfo info, Instant now) {
    jdbc.update(
        """
        INSERT INTO store_company(store_id,tenant_id,announcement,legal_name,cnpj,phone,address,
          facebook,tiktok,youtube,updated_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?)
        ON CONFLICT (store_id) DO UPDATE SET announcement=EXCLUDED.announcement,
          legal_name=EXCLUDED.legal_name,cnpj=EXCLUDED.cnpj,phone=EXCLUDED.phone,
          address=EXCLUDED.address,facebook=EXCLUDED.facebook,tiktok=EXCLUDED.tiktok,
          youtube=EXCLUDED.youtube,updated_at=EXCLUDED.updated_at
        """,
        store,
        tenant,
        info.announcement(),
        info.legalName(),
        info.cnpj(),
        info.phone(),
        info.address(),
        info.facebook(),
        info.tiktok(),
        info.youtube(),
        Timestamp.from(now));
  }

  /** The sanitized body of each page the store wrote. */
  public Map<StorePageKind, String> pages(UUID tenant) {
    Map<StorePageKind, String> pages = new EnumMap<>(StorePageKind.class);
    jdbc.query(
        "SELECT kind,body_html FROM store_page WHERE tenant_id=?",
        row -> {
          pages.put(StorePageKind.valueOf(row.getString("kind")), row.getString("body_html"));
        },
        tenant);
    return pages;
  }

  /** Writes the page; an empty body removes it from the storefront. */
  public void savePage(UUID store, UUID tenant, StorePageKind kind, String html, Instant now) {
    if (html.isEmpty()) {
      jdbc.update("DELETE FROM store_page WHERE tenant_id=? AND kind=?", tenant, kind.name());
      return;
    }
    jdbc.update(
        """
        INSERT INTO store_page(store_id,tenant_id,kind,body_html,updated_at) VALUES (?,?,?,?,?)
        ON CONFLICT (store_id,kind) DO UPDATE SET body_html=EXCLUDED.body_html,updated_at=EXCLUDED.updated_at
        """,
        store,
        tenant,
        kind.name(),
        html,
        Timestamp.from(now));
  }
}
