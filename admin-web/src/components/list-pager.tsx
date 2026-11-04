"use client";

// Page links under a list (2026-11-04), in the same place and wording style as the order list.
// Lists paged by cursor offer "back to the first page" and "next"; numbered lists add "previous"
// and the "page X of Y" position. Buttons, since the page lives in the screen's state.
export default function ListPager({ label, first, previous, next, position, disabled = false }: {
  label: string;
  first?: { text: string; onClick: () => void } | null;
  previous?: { text: string; onClick: () => void } | null;
  next?: { text: string; onClick: () => void } | null;
  position?: string;
  disabled?: boolean;
}) {
  if (!first && !previous && !next) return null;
  return <nav className="pagination" aria-label={label}>
    {first ? <button type="button" className="button-secondary" disabled={disabled} onClick={first.onClick}>{first.text}</button> : null}
    {previous ? <button type="button" className="button-secondary" disabled={disabled} onClick={previous.onClick}>{previous.text}</button> : null}
    {position ? <span className="field-help" aria-live="polite">{position}</span> : null}
    {next ? <button type="button" className="button-secondary" disabled={disabled} onClick={next.onClick}>{next.text}</button> : null}
  </nav>;
}
