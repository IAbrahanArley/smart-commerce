"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type MouseEvent, type PointerEvent } from "react";
import { type Banner, bannerHref, bannerImage } from "@/lib/home";

const SWIPE_PX = 40;

// SC-043 (ADR-0018 V8), dots since 2026-11-04: one banner at a time and one dot per banner below the
// image. The current dot fills up while its banner is shown (7 s, CSS) and the carousel moves on when
// the fill ends, so the dots and the rotation never drift apart. Clicking a dot or swiping on a phone
// changes the banner. Rotation only with more than one banner, never under reduced motion, and it
// stops while the pointer or the focus is inside the carousel (the fill pauses where it is).
export function BannerCarousel({ store, banners }: { store: string; banners: Banner[] }) {
  const [current, setCurrent] = useState(0);
  const [held, setHeld] = useState(false);
  const [reduced, setReduced] = useState(true);
  const root = useRef<HTMLElement>(null);
  const swipeFrom = useRef<number | null>(null);
  const swiped = useRef(false);
  const many = banners.length > 1;
  const rotating = many && !held && !reduced;

  useEffect(() => {
    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setReduced(query.matches);
    update();
    query.addEventListener("change", update);
    return () => query.removeEventListener("change", update);
  }, []);

  if (banners.length === 0) return null;
  const go = (step: number) => setCurrent(index => (index + step + banners.length) % banners.length);

  function swipeStart(event: PointerEvent) {
    if (event.pointerType !== "mouse") swipeFrom.current = event.clientX;
  }
  function swipeEnd(event: PointerEvent) {
    const from = swipeFrom.current;
    swipeFrom.current = null;
    if (from === null || !many) return;
    const distance = event.clientX - from;
    if (Math.abs(distance) < SWIPE_PX) return;
    swiped.current = true;
    go(distance < 0 ? 1 : -1);
  }
  // A swipe over a banner with a link changes the banner; it must not also open the link.
  function skipClickAfterSwipe(event: MouseEvent) {
    if (!swiped.current) return;
    swiped.current = false;
    event.preventDefault();
  }

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
      <div className="banner-slides" aria-live={rotating ? "off" : "polite"}
        onPointerDown={event => { swiped.current = false; swipeStart(event); }} onPointerUp={swipeEnd} onClickCapture={skipClickAfterSwipe} onPointerCancel={() => { swipeFrom.current = null; }}>
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
              draggable={false}
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
              {href ? <Link href={href} prefetch={false} className="banner-link" draggable={false}>
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
      {many ? <div className="banner-dots" role="group" aria-label="Escolher banner">
        {banners.map((banner, index) => (
          <button key={banner.id} type="button" className="banner-dot" aria-current={index === current ? "true" : undefined}
            onClick={() => setCurrent(index)}>
            <span className="banner-dot-mark" aria-hidden="true">
              {index === current && !reduced ? <span key={current} className="banner-dot-fill"
                style={{ animationPlayState: rotating ? "running" : "paused" }}
                onAnimationEnd={() => go(1)} /> : null}
            </span>
            <span className="visually-hidden">Banner {index + 1} de {banners.length}</span>
          </button>
        ))}
      </div> : null}
    </section>
  );
}
