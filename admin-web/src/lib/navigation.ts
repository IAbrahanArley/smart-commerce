// Admin navigation shared by the sidebar and the global search. Pure, imported by the smoke test.
// Links never prove authorization: the backend checks every request.

export type NavIcon = "home" | "box" | "tags" | "award" | "palette" | "receipt" | "store" | "truck" | "card" | "chart" | "wallet" | "spark" | "stores" | "user";
export type NavItem = { href: string; label: string; icon: NavIcon; current: boolean; keywords?: string };
export type NavGroup = { label: string; items: NavItem[] };

export function storeBase(tenantId: string): string {
  return `/lojas/${encodeURIComponent(tenantId)}`;
}

export function storeItems(tenantId: string, pathname: string): NavItem[] {
  const base = storeBase(tenantId);
  const at = (path: string) => pathname === `${base}${path}`;
  const under = (path: string) => pathname === `${base}${path}` || pathname.startsWith(`${base}${path}/`);
  return [
    { href: base, label: "Visão geral", icon: "home", current: pathname === base, keywords: "painel dashboard inicio resumo" },
    { href: `${base}/produtos`, label: "Produtos", icon: "box", current: under("/produtos"), keywords: "catalogo estoque sku" },
    { href: `${base}/categorias`, label: "Categorias", icon: "tags", current: at("/categorias"), keywords: "catalogo grupos" },
    { href: `${base}/marcas`, label: "Marcas", icon: "award", current: at("/marcas"), keywords: "catalogo fabricante" },
    { href: `${base}/pedidos`, label: "Pedidos", icon: "receipt", current: under("/pedidos"), keywords: "vendas compras reembolso" },
    { href: `${base}/conteudo`, label: "Aparência e conteúdo", icon: "palette", current: at("/conteudo"), keywords: "vitrine rodape cnpj paginas trocas privacidade aviso banner" },
    { href: `${base}/dados`, label: "Dados da loja", icon: "store", current: at("/dados"), keywords: "logo contatos whatsapp instagram nome" },
    { href: `${base}/entrega`, label: "Entrega", icon: "truck", current: at("/entrega"), keywords: "frete retirada" },
    { href: `${base}/pagamentos`, label: "Pagamentos", icon: "card", current: at("/pagamentos"), keywords: "mercado pago conectar" },
    { href: `${base}/financeiro`, label: "Financeiro", icon: "chart", current: at("/financeiro"), keywords: "receita margem relatorio" },
    { href: `${base}/despesas`, label: "Despesas", icon: "wallet", current: at("/despesas"), keywords: "custos gastos" },
    { href: `${base}/assistente`, label: "Assistente", icon: "spark", current: at("/assistente"), keywords: "ia perguntas explicar" },
  ];
}

export function accountItems(pathname: string): NavItem[] {
  return [
    { href: "/lojas", label: "Minhas lojas", icon: "stores", current: pathname === "/lojas", keywords: "trocar loja empresas" },
    { href: "/conta", label: "Minha conta", icon: "user", current: pathname === "/conta", keywords: "perfil sessao" },
    { href: "/conta/plano", label: "Plano e cobrança", icon: "card", current: pathname === "/conta/plano", keywords: "assinatura pagamento stripe cartao limite teste" },
  ];
}

/** Collapsed sidebar preference, a per-viewer convenience kept in the browser. */
export const SIDEBAR_KEY = "sc.sidebar.collapsed";

export function parseCollapsed(value: string | null): boolean {
  return value === "true";
}

function fold(text: string): string {
  return text.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
}

/** Global search filter: 1 when every typed word appears in the item text, ignoring accents and case. */
export function searchScore(value: string, search: string): number {
  const words = fold(search).split(/\s+/).filter(Boolean);
  if (words.length === 0) return 1;
  const text = fold(value);
  return words.every(word => text.includes(word)) ? 1 : 0;
}
