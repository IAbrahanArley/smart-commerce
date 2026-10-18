"use client";

export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <main id="conteudo" className="site-main" tabIndex={-1}>
      <section aria-labelledby="erro-titulo">
        <h1 id="erro-titulo">Não foi possível abrir o painel</h1>
        <p role="alert">Ocorreu um problema ao carregar esta página. Tente novamente.</p>
        <button type="button" onClick={reset}>Tentar novamente</button>
      </section>
    </main>
  );
}
