import type { ReactNode } from "react";
import Link from "next/link";
import { Check } from "lucide-react";

const BENEFITS = [
  "Catálogo, estoque e vitrine em um só lugar",
  "Pagamentos pelo Mercado Pago, direto na sua conta",
  "Pedidos, financeiro e despesas da loja",
];

// Authentication pages (Dashtail login layout): brand panel from 64rem, the form on the right.
export default function AuthLayout({ children }: { children: ReactNode }) {
  return <div className="grid min-h-screen flex-1 bg-card lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
    <div className="relative hidden overflow-hidden bg-primary p-12 text-primary-foreground lg:flex lg:flex-col lg:justify-between">
      <div className="pointer-events-none absolute -right-32 -top-32 size-[28rem] rounded-full bg-white/5" aria-hidden="true" />
      <div className="pointer-events-none absolute -bottom-40 -left-24 size-[32rem] rounded-full bg-white/5" aria-hidden="true" />
      <Link href="/" className="relative flex w-fit items-center gap-3 rounded-md text-primary-foreground no-underline hover:no-underline focus-visible:ring-2 focus-visible:ring-white">
        <span className="inline-flex size-10 items-center justify-center rounded-lg bg-white text-sm font-bold text-primary" aria-hidden="true">SC</span>
        <span className="text-lg font-bold" translate="no">Smart Commerce</span>
      </Link>
      <div className="relative max-w-lg">
        <p className="m-0 text-4xl font-bold leading-tight xl:text-5xl">Sua loja.<br />Tudo conectado.</p>
        <ul className="m-0 mt-8 grid list-none gap-4 p-0 text-lg">
          {BENEFITS.map(benefit => <li key={benefit} className="flex items-start gap-3">
            <span className="mt-0.5 inline-flex size-6 shrink-0 items-center justify-center rounded-full bg-white/15"><Check className="size-4" aria-hidden="true" /></span>
            {benefit}
          </li>)}
        </ul>
      </div>
      <p className="relative m-0 text-sm text-white/80">Cada empresa vê somente os próprios dados.</p>
    </div>
    <div className="flex flex-col">
      <main id="conteudo" tabIndex={-1} className="flex flex-1 items-center justify-center px-5 py-12 sm:px-10">{children}</main>
      <footer className="flex flex-wrap justify-center gap-x-6 gap-y-1 px-5 pb-6 text-sm">
        <Link href="/termos" className="inline-flex min-h-11 items-center text-muted-foreground">Termos de uso</Link>
        <Link href="/privacidade" className="inline-flex min-h-11 items-center text-muted-foreground">Política de privacidade</Link>
      </footer>
    </div>
  </div>;
}
