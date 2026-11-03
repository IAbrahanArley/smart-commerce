import Link from "next/link";
import { Banknote, ShoppingBag, Users } from "lucide-react";
import PageHeader from "@/components/page-header";
import { Card, CardContent } from "@/components/ui/card";

const COMING = [
  { icon: Users, title: "Crediário", text: "Receber parcelas dos clientes da loja." },
];

// SC-049 (ADR-0019 C1): an employee's home in the store: cash register (SC-052) and counter sales
// (SC-053); store credit arrives with SC-055. No figures of the store are shown.
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
    <h2 className="text-lg font-semibold">Em breve para a equipe</h2>
    <ul className="m-0 grid list-none gap-4 p-0 md:grid-cols-3">
      {COMING.map(({ icon: Icon, title, text }) => <li key={title}><Card className="h-full"><CardContent className="flex items-start gap-3">
        <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary" aria-hidden="true"><Icon className="size-5" /></span>
        <div><h3 className="m-0 text-base font-semibold">{title}</h3><p className="m-0 mt-1 text-sm text-muted-foreground">{text}</p></div>
      </CardContent></Card></li>)}
    </ul>
  </section>;
}
