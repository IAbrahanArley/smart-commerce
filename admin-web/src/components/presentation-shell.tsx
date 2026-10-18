"use client";

import { usePathname } from "next/navigation";
import { useEffect, useRef, type ReactNode } from "react";

export default function PresentationShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const landing = pathname === "/";
  const frame = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!landing || !frame.current) return;
    const root = frame.current;
    const onScroll = () => { root.dataset.scrolled = String(window.scrollY > 50); };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    const observer = typeof IntersectionObserver === "undefined" ? null : new IntersectionObserver(entries => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        entry.target.setAttribute("data-revealed", "true");
        observer?.unobserve(entry.target);
      }
    }, { threshold: 0.12 });
    root.querySelectorAll("[data-reveal]").forEach(element => observer?.observe(element));
    return () => {
      window.removeEventListener("scroll", onScroll);
      observer?.disconnect();
      delete root.dataset.scrolled;
    };
  }, [landing]);

  return <div ref={frame} className={landing ? "presentation landing-page" : "presentation"}>{children}</div>;
}
