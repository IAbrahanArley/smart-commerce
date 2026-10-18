import Link from "next/link";

export default function HomePage() {
  return (
    <section aria-labelledby="titulo">
      <p className="eyebrow">Sprint 1 · Fundação</p>
      <h1 id="titulo">Administração do Smart Commerce</h1>
      <p>Acesse sua conta ou confirme seu e-mail para começar.</p>
      <nav className="auth-links" aria-label="Acesso à conta">
        <Link href="/entrar">Entrar</Link>
        <Link href="/cadastro">Criar conta</Link>
        <Link href="/conta">Minha conta</Link>
      </nav>
      <p className="notice">As funcionalidades de gestão serão disponibilizadas nas próximas etapas.</p>
    </section>
  );
}
