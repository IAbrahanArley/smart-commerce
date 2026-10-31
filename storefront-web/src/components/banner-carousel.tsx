"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { Arrow } from "@/components/arrow";
import { type Banner, bannerHref, bannerImage } from "@/lib/home";

const INTERVAL_MS = 7000;

// SC-043 (ADR-0018 V8): the home banners. One slide at a time with previous/next and a pause
// control (WCAG 2.2.2). It only rotates by itself with more than one banner, never under reduced
// motion, and stops while the pointer or the focus is inside it.
export function BannerCarousel({ store, banners }: { store: string; banners: Banner[] }) {
  const [current, setCurrent] = useState(0);
  const [paused, setPaused] = useState(false);
  const [held, setHeld] = useState(false);
  const [reduced, setReduced] = useState(true);
  const root = useRef<HTMLElement>(null);
  const many = banners.length > 1;
  const rotating = many && !paused && !held && !reduced;

  useEffect(() => {
    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setReduced(query.matches);
    update();
    query.addEventListener("change", update);
    return () => query.removeEventListener("change", update);
  }, []);

  useEffect(() => {
    if (!rotating) return;
    const timer = window.setInterval(() => setCurrent(index => (index + 1) % banners.length), INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [rotating, banners.length]);

  if (banners.length === 0) return null;
  const go = (step: number) => setCurrent(index => (index + step + banners.length) % banners.length);

  return (
    <section
      ref={root}
      className="banner-carousel"
      aria-roledescription="carrossel"
      aria-label="Destaques da loja"
      onMouseEnter={() => setHeld(true)}
      onMouseLeave={() => setHeld(false)}
      onFocus={() => setHeld(true)}
      onBlur={event => { if (!root.current?.contains(event.relatedTarget as Node | null)) setHeld(false); }}
    >
      {many ? <div className="banner-controls">
        {reduced ? null : <button type="button" className="banner-control" onClick={() => setPaused(value => !value)}>
          {paused ? "Reproduzir" : "Pausar"}
          <span className="visually-hidden"> a troca automática dos banners</span>
        </button>}
        <button type="button" className="banner-control banner-arrow" onClick={() => go(-1)}>
          <Arrow back /><span className="visually-hidden">Banner anterior</span>
        </button>
        <span className="banner-position" aria-hidden="true">{current + 1} / {banners.length}</span>
        <button type="button" className="banner-control banner-arrow" onClick={() => go(1)}>
          <Arrow /><span className="visually-hidden">Próximo banner</span>
        </button>
      </div> : null}
      <div className="banner-slides" aria-live={rotating ? "off" : "polite"}>
        {banners.map((banner, index) => {
          const href = bannerHref(store, banner);
          const picture = <picture>
            {banner.mobileImage ? <source media="(max-width: 47.99rem)" srcSet={bannerImage(store, banner.id, "mobile")}
              width={banner.mobileWidth ?? undefined} height={banner.mobileHeight ?? undefined} /> : null}
            {/* The phone variant needs <picture>, which next/image does not render. */}
            <img
              src={bannerImage(store, banner.id, "desktop")}
              alt={banner.alt}
              width={banner.width ?? undefined}
              height={banner.height ?? undefined}
              loading={index === 0 ? "eager" : "lazy"}
              fetchPriority={index === 0 ? "high" : "auto"}
              decoding="async"
            />
          </picture>;
          return (
            <div
              key={banner.id}
              className="banner-slide"
              role="group"
              aria-roledescription="banner"
              aria-label={`${index + 1} de ${banners.length}`}
              hidden={index !== current}
            >
              {href ? <Link href={href} prefetch={false} className="banner-link">
                {picture}
                {banner.title ? <span className="banner-title">{banner.title}</span> : null}
              </Link> : <>
                {picture}
                {banner.title ? <span className="banner-title">{banner.title}</span> : null}
              </>}
            </div>
          );
        })}
      </div>
    </section>
  );
}
