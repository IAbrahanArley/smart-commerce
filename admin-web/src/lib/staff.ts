// SC-049 (ADR-0019 C1): store employees seen by the OWNER, and the invitation token. Pure, imported
// by the smoke test. The backend decides who may do what; this module only reads its answers.

export type StaffMember = { userId: string; email: string; since: string };
export type StaffInvitation = { id: string; email: string; createdAt: string; expiresAt: string; expired: boolean };
export type StaffOverview = { members: StaffMember[]; invitations: StaffInvitation[]; used: number; limit: number; enforced: boolean };

const ISO = /^\d{4}-\d{2}-\d{2}T[\d:.]+Z$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const text = (value: unknown, max = 320) => typeof value === "string" && value.length > 0 && value.length <= max ? value : null;
const date = (value: unknown) => typeof value === "string" && ISO.test(value) ? value : null;
const id = (value: unknown) => typeof value === "string" && UUID.test(value) ? value : null;
const count = (value: unknown) => typeof value === "number" && Number.isInteger(value) && value >= 0 ? value : null;

function member(raw: unknown): StaffMember | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  const userId = id(value.userId), email = text(value.email), since = date(value.since);
  return userId && email && since ? { userId, email, since } : null;
}

function invitation(raw: unknown): StaffInvitation | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  const invitationId = id(value.id), email = text(value.email), createdAt = date(value.createdAt), expiresAt = date(value.expiresAt);
  if (!invitationId || !email || !createdAt || !expiresAt || typeof value.expired !== "boolean") return null;
  return { id: invitationId, email, createdAt, expiresAt, expired: value.expired };
}

export function parseInvitation(raw: unknown): StaffInvitation | null {
  return invitation(raw);
}

export function parseStaff(raw: unknown): StaffOverview | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  const used = count(value.used), limit = count(value.limit);
  if (!Array.isArray(value.members) || !Array.isArray(value.invitations) || used === null || limit === null || typeof value.enforced !== "boolean") return null;
  const members = value.members.map(member), invitations = value.invitations.map(invitation);
  if (members.some(item => !item) || invitations.some(item => !item)) return null;
  return { members: members as StaffMember[], invitations: invitations as StaffInvitation[], used, limit, enforced: value.enforced };
}

/** "2 de 3 funcionários do seu plano", or null while billing is off (no limit applies). */
export function staffLimitText(overview: StaffOverview): string | null {
  if (!overview.enforced) return null;
  return `${overview.used} de ${overview.limit} ${overview.limit === 1 ? "funcionário" : "funcionários"} do seu plano, contando os convites em aberto e todas as suas lojas.`;
}

/** Client-side check before sending; the backend validates again. */
export function inviteEmailError(email: string): string | null {
  const value = email.trim();
  if (!value) return "Informe o e-mail de quem vai trabalhar na loja.";
  if (value.length > 320 || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value)) return "Informe um e-mail válido.";
  return null;
}

/** The token from the invitation link's fragment (#token=…), or null when it is not one. */
export function invitationToken(hash: string): string | null {
  const match = /^#token=([A-Za-z0-9_-]{43})$/.exec(hash);
  return match ? match[1] : null;
}

/** Message for a refused employee request, from the backend's code; null for unknown answers. */
export function staffRefusal(status: number, body: unknown): string | null {
  const value = body && typeof body === "object" ? body as Record<string, unknown> : {};
  const message = typeof value.message === "string" && value.message.length <= 300 ? value.message : null;
  switch (value.code) {
    case "INVALID_EMAIL": return message ?? "Informe um e-mail válido.";
    case "ALREADY_MEMBER": return message ?? "Esse e-mail já faz parte da equipe desta loja.";
    case "INVALID_INVITATION": return message ?? "Convite inválido, expirado ou já usado. Peça um novo convite ao dono da loja.";
    case "WRONG_ACCOUNT": return message ?? "Este convite foi enviado para outro e-mail. Entre com a conta do e-mail convidado.";
    case "PLAN_LIMIT": return message ?? "Seu plano chegou ao limite de funcionários. Mude de plano em Plano e cobrança.";
    case "PLAN_INACTIVE": return message ?? "Sua assinatura não está ativa. Regularize em Plano e cobrança.";
    case "UNAVAILABLE": return message ?? "Não foi possível enviar o convite agora. Tente novamente.";
    case "THROTTLED": return message ?? "Muitos convites enviados em pouco tempo. Tente de novo amanhã.";
    case "OWNER_LIMIT": return message ?? "A loja não pode receber novos funcionários agora. Fale com o dono da loja.";
    case "CONFLICT": return message ?? "Já existe um convite sendo enviado para esse e-mail. Confira a lista.";
    default: return status === 404 ? "Loja não encontrada." : null;
  }
}
