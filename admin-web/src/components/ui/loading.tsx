import { cn } from "@/lib/utils";

// Standard loading state for every admin screen: only the text "Carregando…", softly pulsing (still
// under reduced motion). "context" names what is loading for screen readers, e.g. "pedidos da loja".
function Loading({ context, className }: { context?: string; className?: string }) {
  return <div role="status" className={cn("flex items-center justify-center py-16 text-sm font-medium text-muted-foreground", className)}>
    <span className="animate-pulse motion-reduce:animate-none" aria-hidden={context ? true : undefined}>Carregando…</span>
    {context ? <span className="sr-only">{`Carregando ${context}…`}</span> : null}
  </div>;
}

export { Loading };
