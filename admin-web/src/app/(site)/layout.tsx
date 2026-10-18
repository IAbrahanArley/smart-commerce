import type { ReactNode } from "react";

export default function SiteLayout({ children }: { children: ReactNode }) {
  return <main id="conteudo" className="site-main" tabIndex={-1}>{children}</main>;
}
