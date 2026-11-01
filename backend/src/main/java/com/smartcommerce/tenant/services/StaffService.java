package com.smartcommerce.tenant.services;

import com.smartcommerce.billing.domain.BillingException;
import com.smartcommerce.billing.services.BillingService;
import com.smartcommerce.identity.domain.EmailAddress;
import com.smartcommerce.identity.exceptions.IdentityException;
import com.smartcommerce.identity.mail.VerificationMail;
import com.smartcommerce.identity.services.IdentityRateLimiter;
import com.smartcommerce.tenant.dto.response.AcceptedInvitation;
import com.smartcommerce.tenant.dto.response.StaffOverview;
import com.smartcommerce.tenant.exceptions.StaffException;
import com.smartcommerce.tenant.repositories.StaffRepository;
import com.smartcommerce.tenant.repositories.TenantAccessRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store employees (SC-049, ADR-0019 C1). Only the company's OWNER lists, invites and revokes; the
 * tenant comes from the route and is honoured only through his membership. An invitation is a
 * single-use token valid for 7 days, accepted only by a signed-in, verified account with the same
 * e-mail. Revoking deletes the membership, so the next request of the employee is already denied.
 * Employees count against the owner's plan once per person, open invitations included.
 */
@Service
public class StaffService {
  static final Duration VALIDITY = Duration.ofDays(7);
  // Invitation e-mails leave from the platform's sender: bounded per owner and per recipient.
  static final int INVITES_PER_OWNER_PER_DAY = 30;
  static final int INVITES_PER_RECIPIENT_PER_DAY = 5;
  private static final String TOKEN_PATTERN = "[A-Za-z0-9_-]{43}";

  private final TenantAccessRepository access;
  private final StaffRepository staff;
  private final BillingService billing;
  private final IdentityRateLimiter limits;
  private final VerificationMail mail;
  private final Clock clock;
  private final String origin;
  private final SecureRandom random = new SecureRandom();

  public StaffService(
      TenantAccessRepository access,
      StaffRepository staff,
      BillingService billing,
      IdentityRateLimiter limits,
      VerificationMail mail,
      Clock clock,
      @Value("${identity.public-origin:http://127.0.0.1:3000}") String origin) {
    this.access = access;
    this.staff = staff;
    this.billing = billing;
    this.limits = limits;
    this.mail = mail;
    this.clock = clock;
    this.origin = origin;
  }

  @Transactional
  public StaffOverview overview(UUID actor, UUID tenant) {
    requireOwner(actor, tenant);
    Instant now = clock.instant();
    var account = billing.account(actor);
    return new StaffOverview(
        staff.members(tenant).stream()
            .map(m -> new StaffOverview.Member(m.userId(), m.email(), m.since()))
            .toList(),
        staff.openInvitations(tenant).stream()
            .map(
                i ->
                    new StaffOverview.Invitation(
                        i.id(),
                        i.email(),
                        i.createdAt(),
                        i.expiresAt(),
                        !i.expiresAt().isAfter(now)))
            .toList(),
        staff.staffAndOpenInvitations(actor, now),
        account.limits().staff(),
        account.enforced());
  }

  /**
   * Admits one more invitation e-mail for this owner and recipient, in its own short transaction
   * (called before {@link #invite}, so the shared admission lock is never held while mailing).
   * Unparseable e-mails are left to {@link #invite}, which refuses them.
   */
  public void admitInvitation(UUID actor, String rawEmail) {
    String recipient;
    try {
      recipient = EmailAddress.parse(rawEmail).comparisonKey();
    } catch (IllegalArgumentException invalid) {
      return;
    }
    if (!limits.acquire(
        java.util.List.of(
            IdentityRateLimiter.quota(
                "staff-invite-owner",
                actor.toString(),
                INVITES_PER_OWNER_PER_DAY,
                Duration.ofDays(1)),
            IdentityRateLimiter.quota(
                "staff-invite-recipient",
                recipient,
                INVITES_PER_RECIPIENT_PER_DAY,
                Duration.ofDays(1)))))
      throw new StaffException(
          StaffException.Reason.THROTTLED,
          "Muitos convites enviados em pouco tempo. Tente de novo amanhã.");
  }

  /**
   * Invites {@code rawEmail}. A previous open invitation to the same e-mail is replaced (and stops
   * counting) before the plan limit is checked; the e-mail goes out in the same transaction, so a
   * failed delivery leaves nothing behind.
   */
  @Transactional
  public StaffOverview.Invitation invite(UUID actor, UUID tenant, String rawEmail) {
    requireOwner(actor, tenant);
    EmailAddress email;
    try {
      email = EmailAddress.parse(rawEmail);
    } catch (IllegalArgumentException invalid) {
      throw new StaffException(StaffException.Reason.INVALID_EMAIL, "Informe um e-mail válido.");
    }
    if (staff.isMember(tenant, email.comparisonKey()))
      throw new StaffException(
          StaffException.Reason.ALREADY_MEMBER, "Esse e-mail já faz parte da equipe desta loja.");
    Instant now = clock.instant();
    staff.cancelOpenFor(tenant, email.comparisonKey(), now);
    // Someone already working for (or invited by) this owner costs no new slot; everyone else does.
    billing.requireStaffSlot(
        actor,
        () ->
            staff.alreadyCounted(actor, email.comparisonKey(), now)
                ? -1
                : staff.staffAndOpenInvitations(actor, now));
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    UUID id = UUID.randomUUID();
    Instant expires = now.plus(VALIDITY);
    staff.insertInvitation(
        id,
        tenant,
        email.comparisonKey(),
        email.original(),
        IdentityRateLimiter.digest(token),
        actor,
        now,
        expires);
    // The token travels in the fragment, so it never reaches server logs or Referer headers.
    mail.sendStaffInvitation(
        email.original(), staff.storeName(tenant), origin + "/convite#token=" + token);
    return new StaffOverview.Invitation(id, email.original(), now, expires, false);
  }

  @Transactional
  public void cancelInvitation(UUID actor, UUID tenant, UUID invitation) {
    requireOwner(actor, tenant);
    if (!staff.cancelInvitation(tenant, invitation, clock.instant()))
      throw new StaffException(StaffException.Reason.MISSING, "Convite não encontrado.");
  }

  @Transactional
  public void revoke(UUID actor, UUID tenant, UUID employee) {
    requireOwner(actor, tenant);
    if (!staff.revokeStaff(tenant, employee, actor, clock.instant()))
      throw new StaffException(StaffException.Reason.MISSING, "Funcionário não encontrado.");
  }

  /** The signed-in account joins the company as an employee, once, before the token expires. */
  @Transactional
  public AcceptedInvitation accept(UUID actor, String token) {
    if (token == null || !token.matches(TOKEN_PATTERN)) throw invalidInvitation();
    String emailKey =
        staff
            .lockVerifiedEmailKey(actor)
            .orElseThrow(
                () ->
                    new IdentityException(
                        IdentityException.Reason.INVALID_CREDENTIALS, "Sessão inválida."));
    var invitation =
        staff
            .lockOpenByDigest(IdentityRateLimiter.digest(token))
            .filter(found -> found.expiresAt().isAfter(clock.instant()))
            .orElseThrow(StaffService::invalidInvitation);
    if (!invitation.emailKey().equals(emailKey))
      throw new StaffException(
          StaffException.Reason.WRONG_ACCOUNT,
          "Este convite foi enviado para outro e-mail. Entre com a conta do e-mail convidado.");
    Instant now = clock.instant();
    // The plan may have shrunk (or the trial ended) since the invitation: check again against the
    // people who already work for the owner (other pending invitations are not a reason to refuse).
    UUID owner = staff.ownerOf(invitation.tenantId());
    try {
      billing.requireStaffSlot(owner, () -> staff.staffMembers(owner, actor));
    } catch (BillingException refused) {
      throw new StaffException(
          StaffException.Reason.OWNER_LIMIT,
          "A loja não pode receber novos funcionários agora. Peça ao dono da loja para revisar o"
              + " plano e enviar um novo convite.");
    }
    if (!staff.addStaff(invitation.tenantId(), actor, now))
      throw new StaffException(
          StaffException.Reason.ALREADY_MEMBER, "Sua conta já faz parte desta loja.");
    staff.markAccepted(invitation.id(), actor, now);
    return new AcceptedInvitation(invitation.tenantId(), invitation.storeName());
  }

  private void requireOwner(UUID actor, UUID tenant) {
    if (access.lockOwnedStore(actor, tenant).isEmpty())
      throw new StaffException(StaffException.Reason.MISSING, "Loja não encontrada.");
  }

  private static StaffException invalidInvitation() {
    return new StaffException(
        StaffException.Reason.INVALID_INVITATION,
        "Convite inválido, expirado ou já usado. Peça um novo convite ao dono da loja.");
  }
}
