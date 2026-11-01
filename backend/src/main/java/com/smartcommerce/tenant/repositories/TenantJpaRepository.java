package com.smartcommerce.tenant.repositories;

import com.smartcommerce.tenant.entities.Tenant;
import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface TenantJpaRepository extends Repository<Tenant, UUID> {
  String OWNED =
      """
 SELECT t.* FROM tenant t
 JOIN tenant_membership m ON m.tenant_id=t.id AND m.user_id=:actor AND m.role='OWNER'
 JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
 WHERE EXISTS (SELECT 1 FROM store s WHERE s.tenant_id=t.id)
 """;

  @Query(value = OWNED + " ORDER BY t.created_at,t.id", nativeQuery = true)
  List<Tenant> listOwned(@Param("actor") UUID actor);

  @Query(value = OWNED + " AND t.id=:tenant", nativeQuery = true)
  Optional<Tenant> findOwned(@Param("actor") UUID actor, @Param("tenant") UUID tenant);

  // SC-049: companies where the actor works as an employee; only to show them, never to grant
  // the OWNER's access.
  String STAFFED =
      """
 SELECT t.* FROM tenant t
 JOIN tenant_membership m ON m.tenant_id=t.id AND m.user_id=:actor AND m.role='STAFF'
 JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
 WHERE EXISTS (SELECT 1 FROM store s WHERE s.tenant_id=t.id)
 """;

  @Query(value = STAFFED + " ORDER BY t.created_at,t.id", nativeQuery = true)
  List<Tenant> listStaffed(@Param("actor") UUID actor);

  @Query(value = STAFFED + " AND t.id=:tenant", nativeQuery = true)
  Optional<Tenant> findStaffed(@Param("actor") UUID actor, @Param("tenant") UUID tenant);
}
