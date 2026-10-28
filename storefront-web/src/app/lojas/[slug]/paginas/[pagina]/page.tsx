import type { Metadata } from "next";
import Link from "next/link";
import { StoreFrame } from "@/components/store-frame";
import { loadPage } from "@/lib/public-store";

export const dynamic = "force-dynamic";

type Props = { params: Promise<{ slug: string; pagina: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { pagina } = await params;
  const words = pagina.replaceAll("-", " ");
  return { title: words.charAt(0).toUpperCase() + words.slice(1) };
}

// SC-043: an institutional page the store wrote (Sobre, Trocas e devoluções, Entrega, Privacidade,
// Termos). The body is the sanitized product-description format, checked again by loadPage.
export default async function StorePageRoute({ params }: Props) {
  const { slug, pagina } = await params;
  const page = await loadPage(slug, pagina);
  const storeUrl = `/lojas/${encodeURIComponent(page.store.slug)}`;
  return <StoreFrame store={page.store}>
    <main id="conteudo" className="page-width store-page" tabIndex={-1}>
      <nav className="breadcrumbs" aria-label="Você está em">
        <ol>
          <li><Link href={storeUrl} prefetch={false}>Início</Link></li>
          <li><span aria-current="page">{page.title}</span></li>
        </ol>
      </nav>
      <h1>{page.title}</h1>
      <div className="store-page-body" dangerouslySetInnerHTML={{ __html: page.bodyHtml }} />
    </main>
  </StoreFrame>;
}
