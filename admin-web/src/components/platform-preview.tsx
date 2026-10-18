"use client";

import Link from "next/link";
import { useState } from "react";
import DemoProducts from "@/components/demo-products";

export default function PlatformPreview() {
  const [view, setView] = useState("admin");
  return <div className="platform-preview">
    <div className="preview-switch" role="group" aria-label="Escolher prévia">
      <button aria-pressed={view === "admin"} onClick={() => setView("admin")}>Admin {view === "admin" ? "✓" : ""}</button>
      <button aria-pressed={view === "loja"} onClick={() => setView("loja")}>Loja {view === "loja" ? "✓" : ""}</button>
    </div>
    <div className="preview-content" aria-live="polite">
      {view === "admin" ? <><div className="preview-description"><h3>Organização da operação.</h3><p>Um lugar para consultar produtos, variações e disponibilidade.</p><Link href="/admin/produtos">Explorar demonstração do admin <span aria-hidden="true">↗</span></Link></div><DemoProducts /></> : <><div className="preview-description"><h3>O outro lado do balcão.</h3><p>Uma vitrine para conhecer o produto e escolher suas opções.</p><Link href="/loja/produto">Explorar demonstração da loja <span aria-hidden="true">↗</span></Link></div><div className="store-preview" data-ui><p>Loja Exemplo · Demonstração</p><h3>Camiseta essencial</h3><p>Algodão, corte reto e opções de tamanho.</p><strong>R$ 79,90</strong><p>Tamanhos P, M e G · Dados fictícios</p></div></>}
    </div>
  </div>;
}
