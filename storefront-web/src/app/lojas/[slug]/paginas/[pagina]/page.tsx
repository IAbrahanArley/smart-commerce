import type { Metadata } from "next";
import Link from "next/link";
import { StoreFrame } from "@/components/store-frame";
import { loadPage } from "@/lib/public-store";
import { storeMetadata } from "@/lib/store-metadata";

// SC-047: no "force-dynamic": it would force no-store on the backend reads, which are cached for
// 60 s per store. The page still renders per request (it reads the cart cookie).

type Props = { params: Promise<{ slug: string; pagina: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug, pagina } = await params;
  const page = await loadPage(slug, pagina);
  return storeMetadata({
    title: `${page.title} | ${page.store.name}`,
    description: `${page.title} da loja ${page.store.name}.`,
    path: `/lojas/${encodeURIComponent(page.store.slug)}/paginas/${encodeURIComponent(pagina)}`,
  });
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
