import Link from "next/link";
import { Banknote, ShoppingBag, Users } from "lucide-react";
import PageHeader from "@/components/page-header";
import { Card, CardContent } from "@/components/ui/card";

// SC-049 (ADR-0019 C1): an employee's home in the store: cash register (SC-052) and counter sales
// (SC-053) and store credit receipts (SC-055). No figures of the store are shown.
export default function StaffHome({ storeName, tenantId }: { storeName: string; tenantId: string }) {
  return <section className="page" aria-labelledby="staff-home-title">
    <PageHeader id="staff-home-title" title={<span translate="no">{storeName}</span>}
      description="Você faz parte da equipe desta loja. Relatórios, custos e contas ficam só com o dono da loja." />
    <Card><CardContent className="flex flex-wrap items-center gap-3">
      <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary" aria-hidden="true"><Banknote className="size-5" /></span>
      <div className="min-w-0 flex-1"><h2 className="m-0 text-base font-semibold">Caixa</h2><p className="m-0 mt-1 text-sm text-muted-foreground">Abrir e fechar o caixa, registrar sangria, suprimento e despesas pagas com o dinheiro da gaveta.</p></div>
      <Link className="button-link" href={`/lojas/${encodeURIComponent(tenantId)}/caixa`}>Ir para o caixa</Link>
    </CardContent></Card>
    <Card><CardContent className="flex flex-wrap items-center gap-3">
      <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary" aria-hidden="true"><ShoppingBag className="size-5" /></span>
      <div className="min-w-0 flex-1"><h2 className="m-0 text-base font-semibold">Vendas na loja</h2><p className="m-0 mt-1 text-sm text-muted-foreground">Registrar as vendas da loja física, baixando o mesmo estoque da vitrine.</p></div>
      <Link className="button-link" href={`/lojas/${encodeURIComponent(tenantId)}/vendas-loja`}>Registrar venda</Link>
    </CardContent></Card>
    <Card><CardContent className="flex flex-wrap items-center gap-3">
      <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary" aria-hidden="true"><Users className="size-5" /></span>
      <div className="min-w-0 flex-1"><h2 className="m-0 text-base font-semibold">Crediário</h2><p className="m-0 mt-1 text-sm text-muted-foreground">Vender no crediário pela tela de vendas e receber as parcelas dos clientes.</p></div>
      <Link className="button-link" href={`/lojas/${encodeURIComponent(tenantId)}/crediario`}>Receber parcela</Link>
    </CardContent></Card>
  </section>;
}
