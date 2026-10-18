import Link from "next/link";

export default function NotFound() {
  return (
    <main id="conteudo" className="site-main" tabIndex={-1}>
      <section aria-labelledby="pagina-ausente">
        <h1 id="pagina-ausente">Página não encontrada</h1>
        <p>Confira o endereço ou volte para o início do painel.</p>
        <Link href="/">Voltar ao início</Link>
      </section>
    </main>
  );
}
