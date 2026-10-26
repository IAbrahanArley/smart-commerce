import type { ReactNode } from "react";
import Link from "next/link";
import { LEGAL, legalDraft, updatedText } from "@/lib/legal";

// MVP block 1d: shared frame of the platform's legal pages (terms and privacy policy).
export default function LegalPage({ title, children }: { title: string; children: ReactNode }) {
  return <article className="legal-page" aria-labelledby="legal-title">
    <h1 id="legal-title">{title}</h1>
    <p className="field-help">{updatedText()}</p>
    {legalDraft() ? <p className="legal-draft" role="note"><strong>Rascunho em validação.</strong> Este texto ainda será revisado pelo responsável pela plataforma e pode mudar antes do lançamento.</p> : null}
    {children}
    <nav className="auth-links" aria-label="Documentos legais">
      <Link href="/termos">Termos de uso</Link>
      <Link href="/privacidade">Política de privacidade</Link>
      <a href={`mailto:${LEGAL.contactEmail}`}>Fale conosco: {LEGAL.contactEmail}</a>
    </nav>
  </article>;
}
