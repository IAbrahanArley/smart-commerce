package com.smartcommerce.tenant.controllers;

import com.smartcommerce.tenant.dto.request.InvitationAcceptance;
import com.smartcommerce.tenant.dto.request.StaffInvitationRequest;
import com.smartcommerce.tenant.dto.response.AcceptedInvitation;
import com.smartcommerce.tenant.dto.response.StaffOverview;
import com.smartcommerce.tenant.services.StaffService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store employees (SC-049, ADR-0019 C1): the OWNER manages the team of his company; any signed-in,
 * verified account accepts an invitation sent to its own e-mail. The actor always comes from the
 * session.
 */
@SecurityRequirement(name = "session")
@Tag(name = "Staff")
@RestController
public final class StaffController {
  private final StaffService staff;

  public StaffController(StaffService staff) {
    this.staff = staff;
  }

  @GetMapping("/api/tenants/{tenantId}/staff")
  public ResponseEntity<StaffOverview> overview(@PathVariable UUID tenantId, Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(staff.overview(actor(actor), tenantId));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/api/tenants/{tenantId}/staff/invitations")
  public ResponseEntity<StaffOverview.Invitation> invite(
      @PathVariable UUID tenantId,
      @RequestBody StaffInvitationRequest request,
      Authentication actor) {
    staff.admitInvitation(actor(actor), request.email());
    return ResponseEntity.status(201)
        .cacheControl(CacheControl.noStore())
        .body(staff.invite(actor(actor), tenantId, request.email()));
  }

  @SecurityRequirement(name = "csrf")
  @DeleteMapping("/api/tenants/{tenantId}/staff/invitations/{invitationId}")
  public ResponseEntity<Void> cancel(
      @PathVariable UUID tenantId, @PathVariable UUID invitationId, Authentication actor) {
    staff.cancelInvitation(actor(actor), tenantId, invitationId);
    return ResponseEntity.noContent().build();
  }

  @SecurityRequirement(name = "csrf")
  @DeleteMapping("/api/tenants/{tenantId}/staff/{userId}")
  public ResponseEntity<Void> revoke(
      @PathVariable UUID tenantId, @PathVariable UUID userId, Authentication actor) {
    staff.revoke(actor(actor), tenantId, userId);
    return ResponseEntity.noContent().build();
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/api/staff-invitations/acceptance")
  public ResponseEntity<AcceptedInvitation> accept(
      @RequestBody InvitationAcceptance request, Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(staff.accept(actor(actor), request.token()));
  }

  private static UUID actor(Authentication actor) {
    return UUID.fromString(actor.getName());
  }
}
