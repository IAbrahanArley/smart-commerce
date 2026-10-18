import Link from "next/link";

export default function HomePage() {
  return (
    <section aria-labelledby="titulo">
      <h1 id="titulo">Administração do Smart Commerce</h1>
      <p>Entre para gerenciar suas lojas, produtos, vitrine e entrega.</p>
      <nav className="auth-links" aria-label="Acesso à conta">
        <Link href="/entrar">Entrar</Link>
        <Link href="/cadastro">Criar conta</Link>
        <Link href="/lojas">Minhas lojas</Link>
      </nav>
    </section>
  );
}
