export function Arrow({ back = false }: { back?: boolean }) {
  return (
    <svg className={back ? "arrow arrow-back" : "arrow"} width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="M5 12h14m-6-6 6 6-6 6" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
