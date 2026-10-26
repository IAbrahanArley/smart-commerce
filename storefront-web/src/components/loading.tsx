// Standard loading state of the storefront, matching the admin: only the text "Carregando…", softly
// pulsing (still under reduced motion). "context" names what is loading for screen readers.
export function Loading({ context }: { context?: string }) {
  return <div className="loading-state" role="status">
    <span aria-hidden={context ? true : undefined}>Carregando…</span>
    {context ? <span className="visually-hidden">{`Carregando ${context}…`}</span> : null}
  </div>;
}
