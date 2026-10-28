import type { Metadata } from "next";
import Link from "next/link";
import { StoreFrame } from "@/components/store-frame";
import { ProductCard } from "@/components/product-card";
import { BannerCarousel } from "@/components/banner-carousel";
import { loadSectionCards, loadStoreCatalog } from "@/lib/public-store";
import { categoryPath } from "@/lib/menu";
import { DEFAULT_SECTIONS, sectionHref, sectionQuery } from "@/lib/home";
import { JsonLd } from "@/components/json-ld";
import { organizationLd, storePath } from "@/lib/seo";
import { siteOrigin, storeMetadata } from "@/lib/store-metadata";

// SC-047: no "force-dynamic": it would force no-store on the backend reads, which are cached for
// 60 s per store. The page still renders per request (it reads the cart cookie).

type Props = { params: Promise<{ slug: string }> };

const HOME_QUERY = () => new URLSearchParams({ ordem: "recentes" });

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const { store, catalog } = await loadStoreCatalog(slug, HOME_QUERY());
  const categories = catalog.menu.slice(0, 5).map(item => item.name);
  return storeMetadata({
    title: store.name,
    description: catalog.frame.company?.announcement
      ?? `Produtos da loja ${store.name}${categories.length ? `: ${categories.join(", ")}` : ""}.`,
    path: storePath(store.slug),
    image: store.profile.logo ? `/midia/lojas/${encodeURIComponent(store.slug)}/logo` : null,
  });
}

const SECTION_CARDS = 8;

// SC-045/SC-043: the store home. Banners, category shortcuts and the sections the owner chose in
// "Aparência e conteúdo" (the newest products when none); an empty section is left out.
export default async function StorePage({ params }: Props) {
  const { slug } = await params;
  const { store, catalog } = await loadStoreCatalog(slug, HOME_QUERY());
  const origin = siteOrigin();
  const company = catalog.frame.company;
  const configured = catalog.home.sections.length > 0;
  const sections = configured ? catalog.home.sections : DEFAULT_SECTIONS;
  const cards = await Promise.all(sections.map(section =>
    !configured || section.kind === "NEW" ? Promise.resolve(catalog.products.slice(0, SECTION_CARDS))
      : loadSectionCards(store.slug, sectionQuery(section), SECTION_CARDS)));
  const shown = sections.map((section, index) => ({ section, cards: cards[index], id: `home-section-${index}` }))
    .filter(entry => entry.cards.length > 0);
  return (
    <StoreFrame store={store} frame={catalog.frame}>
      <main id="conteudo" className="page-width store-home" tabIndex={-1}>
        <h1 className="visually-hidden" translate="no">{store.name}</h1>
        {origin ? <JsonLd data={organizationLd(origin, {
          name: store.name, path: storePath(store.slug), legalName: company?.legalName ?? null,
          logo: store.profile.logo ? `/midia/lojas/${encodeURIComponent(store.slug)}/logo` : null,
          phone: company?.phone ?? store.profile.whatsapp, email: store.profile.contactEmail,
          sameAs: [
            store.profile.instagram ? `https://www.instagram.com/${store.profile.instagram}/` : null,
            company?.facebook ? `https://www.facebook.com/${company.facebook}` : null,
            company?.tiktok ? `https://www.tiktok.com/@${company.tiktok}` : null,
            company?.youtube ? `https://www.youtube.com/@${company.youtube}` : null,
          ].filter((link): link is string => link !== null),
        })} /> : null}
        <BannerCarousel store={store.slug} banners={catalog.home.banners} />
        {catalog.menu.length ? <section className="home-section" aria-labelledby="home-categories">
          <h2 id="home-categories">Compre por categoria</h2>
          <ul className="category-shortcuts">
            {catalog.menu.map(item => <li key={item.slug}><Link href={categoryPath(store.slug, item.slug)} prefetch={false}>{item.name}</Link></li>)}
          </ul>
        </section> : null}
        {shown.map(({ section, cards: products, id }) => <section key={id} className="home-section" aria-labelledby={id}>
          <div className="home-section-heading">
            <h2 id={id}>{section.title}</h2>
            <Link href={sectionHref(store.slug, section)} prefetch={false}>Ver todos<span className="visually-hidden"> de {section.title}</span></Link>
          </div>
          <ul className="catalog-grid">
            {products.map(card => <li key={card.id}><ProductCard store={store.slug} card={card} maxInstallments={catalog.maxInstallments} /></li>)}
          </ul>
        </section>)}
        {shown.length === 0 ? <section className="home-section" aria-labelledby="home-empty">
          <div className="empty-state">
            <h2 id="home-empty">Nenhum produto publicado</h2>
            <p>Os produtos da loja aparecerão aqui quando forem publicados.</p>
          </div>
        </section> : null}
      </main>
    </StoreFrame>
  );
}
