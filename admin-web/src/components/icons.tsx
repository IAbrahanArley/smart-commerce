// Platform line icons drawn for Smart Commerce (DESIGN.md: Repaginação 2026-10). Always decorative:
// the adjacent text names the action, so the SVG stays out of the accessibility tree.
const PATHS = {
  home: <><path d="M3 11 12 4l9 7" /><path d="M5 10v10h5v-6h4v6h5V10" /></>,
  box: <><path d="m3 7 9-4 9 4v10l-9 4-9-4Z" /><path d="m3 7 9 4 9-4" /><path d="M12 11v10" /></>,
  receipt: <><path d="M6 3h12v18l-3-2-3 2-3-2-3 2Z" /><path d="M9 8h6M9 12h6M9 16h3" /></>,
  truck: <><path d="M2 6h12v10H2Z" /><path d="M14 10h4l3 3v3h-7" /><circle cx="6" cy="18" r="2" /><circle cx="17" cy="18" r="2" /></>,
  card: <><rect x="2" y="5" width="20" height="14" rx="2" /><path d="M2 10h20" /><path d="M6 15h4" /></>,
  store: <><path d="m4 9 1-5h14l1 5" /><path d="M4 9h16" /><path d="M5 9v11h14V9" /><path d="M10 20v-5h4v5" /></>,
  user: <><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4 4-6 8-6s8 2 8 6" /></>,
  menu: <path d="M4 6h16M4 12h16M4 18h16" />,
  eye: <><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /></>,
  external: <><path d="M14 4h6v6" /><path d="m20 4-9 9" /><path d="M18 14v6H4V6h6" /></>,
  chart: <><path d="M4 20V4" /><path d="M4 20h16" /><path d="M8 16v-4M12 16V8M16 16v-6" /></>,
  spark: <><path d="M12 3v4M12 17v4M3 12h4M17 12h4" /><path d="m6.5 6.5 2 2M15.5 15.5l2 2M6.5 17.5l2-2M15.5 8.5l2-2" /></>,
  wallet: <><path d="M4 7h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4Z" /><path d="M4 7V6a2 2 0 0 1 2-2h10v3" /><path d="M16 13h4" /></>,
};

export type IconName = keyof typeof PATHS;

export default function Icon({ name }: { name: IconName }) {
  return <svg className="icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
    strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{PATHS[name]}</svg>;
}
