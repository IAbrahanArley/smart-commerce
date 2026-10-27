// SC-012: Mercado Pago connection seen by the store OWNER. Never a token: the backend only returns
// the provider's numeric account id and dates.

export type PaymentStatus = "UNAVAILABLE" | "NOT_CONNECTED" | "CONNECTED" | "EXPIRED" | "REVOKED";

export type PaymentConnection = {
  status: PaymentStatus;
  accountId: string | null;
  connectedAt: string | null;
  accessExpiresAt: string | null;
  refreshedAt: string | null;
  closedAt: string | null;
};

const STATUSES: readonly PaymentStatus[] = ["UNAVAILABLE", "NOT_CONNECTED", "CONNECTED", "EXPIRED", "REVOKED"];

function optionalText(value: unknown, pattern: RegExp): string | null {
  return typeof value === "string" && pattern.test(value) ? value : null;
}

const ISO = /^\d{4}-\d{2}-\d{2}T[\d:.]+Z$/;

export function parseConnection(raw: unknown): PaymentConnection | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  if (typeof value.status !== "string" || !STATUSES.includes(value.status as PaymentStatus)) return null;
  return {
    status: value.status as PaymentStatus,
    accountId: optionalText(value.accountId, /^\d{1,40}$/),
    connectedAt: optionalText(value.connectedAt, ISO),
    accessExpiresAt: optionalText(value.accessExpiresAt, ISO),
    refreshedAt: optionalText(value.refreshedAt, ISO),
    closedAt: optionalText(value.closedAt, ISO),
  };
}

// The browser only ever leaves for Mercado Pago's own authorization page over HTTPS.
export function authorizationTarget(raw: unknown): string | null {
  if (!raw || typeof raw !== "object") return null;
  const url = (raw as Record<string, unknown>).authorizationUrl;
  if (typeof url !== "string") return null;
  try {
    const parsed = new URL(url);
    return parsed.protocol === "https:" && parsed.hostname === "auth.mercadopago.com" && parsed.pathname === "/authorization"
      && !parsed.username && !parsed.password ? parsed.toString() : null;
  } catch {
    return null;
  }
}

// Result word the backend appends after the provider redirect; anything else is ignored.
export function callbackMessage(result: string | null): { text: string; error: boolean } | null {
  switch (result) {
    case "conectado": return { text: "Conta Mercado Pago conectada.", error: false };
    case "negado": return { text: "A autorização foi recusada no Mercado Pago. Nada foi conectado.", error: true };
    case "expirado": return { text: "A tentativa de conexão expirou ou já foi usada. Clique em “Conectar Mercado Pago” para começar de novo.", error: true };
    case "indisponivel": return { text: "A conexão com o Mercado Pago não está disponível neste ambiente.", error: true };
    case "erro": return { text: "O Mercado Pago não confirmou a conexão. Tente novamente em instantes.", error: true };
    default: return null;
  }
}

export const STATUS_TEXT: Record<PaymentStatus, string> = {
  UNAVAILABLE: "Indisponível neste ambiente",
  NOT_CONNECTED: "Não conectada",
  CONNECTED: "Conectada",
  EXPIRED: "Autorização expirada",
  REVOKED: "Autorização revogada",
};

// SC-042 (ADR-0018 V4): the store's card installment limit. null: Mercado Pago's own default,
// nothing promised in the storefront. undefined: an unexpected answer.
export function parseInstallments(raw: unknown): number | null | undefined {
  if (!raw || typeof raw !== "object") return undefined;
  const value = (raw as Record<string, unknown>).maxInstallments;
  if (value === null) return null;
  return Number.isInteger(value) && (value as number) >= 1 && (value as number) <= 12 ? value as number : undefined;
}

export function installmentsText(count: number | null): string {
  if (count === null) return "padrão do Mercado Pago";
  return count === 1 ? "somente à vista" : `em até ${count}×`;
}

export function installmentsError(status: number): string {
  if (status === 400) return "Escolha de 1 a 12 parcelas.";
  if (status === 404) return "Loja indisponível. Recarregue a página.";
  if (status === 401) return "Sua sessão expirou. Entre novamente para continuar.";
  return "Não foi possível salvar. Tente novamente.";
}
