import type { ReactNode } from "react";
import Link from "next/link";
import AccountMenu from "@/components/account-menu";

export default function PlatformLayout({ children }: { children: ReactNode }) {
  return <><header className="site-header">
    <p className="site-identity"><Link className="brand" href="/" translate="no">Smart Commerce</Link> <span>Administração</span></p>
    <AccountMenu />
  </header>{children}<footer>Smart Commerce · Ambiente de desenvolvimento</footer></>;
}
