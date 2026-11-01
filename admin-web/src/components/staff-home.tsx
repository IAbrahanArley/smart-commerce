import { Banknote, ShoppingBag, Users } from "lucide-react";
import PageHeader from "@/components/page-header";
import { Card, CardContent } from "@/components/ui/card";

const COMING = [
  { icon: Banknote, title: "Caixa", text: "Abrir e fechar o caixa, registrar sangria e suprimento." },
  { icon: ShoppingBag, title: "Venda presencial", text: "Registrar as vendas da loja física, baixando o mesmo estoque da vitrine." },
  { icon: Users, title: "Crediário", text: "Receber parcelas dos clientes da loja." },
];

// SC-049 (ADR-0019 C1): an employee's home in the store. The features arrive with SC-052, SC-053
// and SC-055; until then this page only says what is coming. No figures of the store are shown.
export default function StaffHome({ storeName }: { storeName: string }) {
  return <section className="page" aria-labelledby="staff-home-title">
    <PageHeader id="staff-home-title" title={<span translate="no">{storeName}</span>}
      description="Você faz parte da equipe desta loja. Relatórios, custos e contas ficam só com o dono da loja." />
    <h2 className="text-lg font-semibold">Em breve para a equipe</h2>
    <ul className="m-0 grid list-none gap-4 p-0 md:grid-cols-3">
      {COMING.map(({ icon: Icon, title, text }) => <li key={title}><Card className="h-full"><CardContent className="flex items-start gap-3">
        <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary" aria-hidden="true"><Icon className="size-5" /></span>
        <div><h3 className="m-0 text-base font-semibold">{title}</h3><p className="m-0 mt-1 text-sm text-muted-foreground">{text}</p></div>
      </CardContent></Card></li>)}
    </ul>
  </section>;
}
