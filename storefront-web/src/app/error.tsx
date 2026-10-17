"use client";

export default function ErrorPage({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <main id="conteudo" className="notice-page" tabIndex={-1}>
      <h1>Vitrine indisponível</h1>
      <p role="alert">Não foi possível consultar os produtos agora. Tente novamente em instantes.</p>
      <button type="button" onClick={reset}>Tentar novamente</button>
    </main>
  );
}
