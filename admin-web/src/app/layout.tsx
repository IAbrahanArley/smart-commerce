import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "Smart Commerce | Administração",
  description: "Ambiente administrativo do Smart Commerce.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>
        <a className="skip-link" href="#conteudo">Pular para o conteúdo</a>
        <header className="site-header">
          <span className="brand">Smart Commerce</span>
          <span>Administração</span>
        </header>
        <main id="conteudo" tabIndex={-1}>{children}</main>
        <footer>Smart Commerce · Ambiente de desenvolvimento</footer>
      </body>
    </html>
  );
}
