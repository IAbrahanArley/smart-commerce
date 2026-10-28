package com.smartcommerce.content.repositories;

import com.smartcommerce.catalog.domain.ImageContent;
import com.smartcommerce.content.domain.BannerInput;
import com.smartcommerce.content.domain.BannerSummary;
import com.smartcommerce.content.domain.HomeSection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Banners and sections of a store home (SC-043). Every statement is scoped by the tenant it is
 * given; the caller has already checked the OWNER and locked the store. Image columns are only
 * named for the two fixed variants, never built from input.
 */
@Repository
public class HomeContentRepository {
  private final JdbcTemplate jdbc;

  public HomeContentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<BannerSummary> banners(UUID tenant) {
    return jdbc.query(
        """
        SELECT id,position,alt,title,link_kind,link_target,starts_at,ends_at,
          desktop_content IS NOT NULL AS desktop,mobile_content IS NOT NULL AS mobile
        FROM store_banner WHERE tenant_id=? ORDER BY position,created_at,id
        """,
        (row, index) ->
            new BannerSummary(
                row.getObject("id", UUID.class),
                row.getInt("position"),
                row.getString("alt"),
                row.getString("title"),
                row.getString("link_kind"),
                row.getString("link_target"),
                instant(row.getTimestamp("starts_at")),
                instant(row.getTimestamp("ends_at")),
                row.getBoolean("desktop"),
                row.getBoolean("mobile")),
        tenant);
  }

  /** Locks every banner of the tenant, so the limit and the order hold under concurrent writes. */
  public int lockBanners(UUID tenant) {
    return jdbc.queryForList(
            "SELECT id FROM store_banner WHERE tenant_id=? ORDER BY id FOR UPDATE",
            UUID.class,
            tenant)
        .size();
  }

  public void createBanner(
      UUID id, UUID store, UUID tenant, int position, BannerInput input, Instant now) {
    jdbc.update(
        """
        INSERT INTO store_banner(id,store_id,tenant_id,position,alt,title,link_kind,link_target,
          starts_at,ends_at,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)
        """,
        id,
        store,
        tenant,
        position,
        input.alt(),
        input.title(),
        input.linkKind().name(),
        input.linkTarget(),
        timestamp(input.startsAt()),
        timestamp(input.endsAt()),
        Timestamp.from(now));
  }

  public boolean updateBanner(UUID tenant, UUID banner, BannerInput input) {
    return jdbc.update(
            """
            UPDATE store_banner SET alt=?,title=?,link_kind=?,link_target=?,starts_at=?,ends_at=?
            WHERE tenant_id=? AND id=?
            """,
            input.alt(),
            input.title(),
            input.linkKind().name(),
            input.linkTarget(),
            timestamp(input.startsAt()),
            timestamp(input.endsAt()),
            tenant,
            banner)
        == 1;
  }

  public boolean deleteBanner(UUID tenant, UUID banner) {
    return jdbc.update("DELETE FROM store_banner WHERE tenant_id=? AND id=?", tenant, banner) == 1;
  }

  /** Gives each banner its index in {@code order} as position; false if one is not the tenant's. */
  public boolean reorder(UUID tenant, List<UUID> order) {
    for (int position = 0; position < order.size(); position++)
      if (jdbc.update(
              "UPDATE store_banner SET position=? WHERE tenant_id=? AND id=?",
              position,
              tenant,
              order.get(position))
          != 1) return false;
    return true;
  }

  public boolean saveImage(UUID tenant, UUID banner, boolean mobile, ImageContent image) {
    String sql =
        mobile
            ? "UPDATE store_banner SET mobile_media_type=?,mobile_content=?,mobile_width=?,mobile_height=? WHERE tenant_id=? AND id=?"
            : "UPDATE store_banner SET desktop_media_type=?,desktop_content=?,desktop_width=?,desktop_height=? WHERE tenant_id=? AND id=?";
    return jdbc.update(
            sql, image.mediaType(), image.bytes(), image.width(), image.height(), tenant, banner)
        == 1;
  }

  public Optional<ImageContent> image(UUID tenant, UUID banner, boolean mobile) {
    String sql =
        mobile
            ? "SELECT mobile_content AS content,mobile_media_type AS type,mobile_width AS w,mobile_height AS h FROM store_banner WHERE tenant_id=? AND id=? AND mobile_content IS NOT NULL"
            : "SELECT desktop_content AS content,desktop_media_type AS type,desktop_width AS w,desktop_height AS h FROM store_banner WHERE tenant_id=? AND id=? AND desktop_content IS NOT NULL";
    return jdbc
        .query(
            sql,
            (row, index) ->
                new ImageContent(
                    row.getBytes("content"),
                    row.getString("type"),
                    row.getInt("w"),
                    row.getInt("h")),
            tenant,
            banner)
        .stream()
        .findFirst();
  }

  public List<HomeSection> sections(UUID tenant) {
    return jdbc.query(
        "SELECT kind,title,category_id FROM store_home_section WHERE tenant_id=? ORDER BY position",
        (row, index) ->
            new HomeSection(
                HomeSection.Kind.valueOf(row.getString("kind")),
                row.getString("title"),
                row.getObject("category_id", UUID.class)),
        tenant);
  }

  /**
   * Replaces the sections. The composite foreign key refuses a category of another tenant
   * (DataIntegrityViolationException).
   */
  public void replaceSections(UUID store, UUID tenant, List<HomeSection> sections) {
    jdbc.update("DELETE FROM store_home_section WHERE tenant_id=?", tenant);
    for (int position = 0; position < sections.size(); position++) {
      HomeSection section = sections.get(position);
      jdbc.update(
          """
          INSERT INTO store_home_section(id,store_id,tenant_id,position,kind,title,category_id)
          VALUES (?,?,?,?,?,?,?)
          """,
          UUID.randomUUID(),
          store,
          tenant,
          position,
          section.kind().name(),
          section.title(),
          section.categoryId());
    }
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
