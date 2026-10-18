import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Vitrines | Smart Commerce", template: "%s | Smart Commerce" },
  description: "Consulte os produtos publicados pela loja.",
  robots: { index: false, follow: false },
};

export const viewport: Viewport = { themeColor: "#F6F8FA" };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR">
      <body>
        <a className="skip-link" href="#conteudo">Ir para o conteúdo</a>
        <div className="site-content">{children}</div>
        <footer className="site-footer" id="sobre-a-vitrine">
          <div className="page-width footer-content">
            <div className="footer-about">
              <h2>Sobre esta vitrine</h2>
              <p>Produtos e preços publicados pela loja para você consultar. A compra por aqui ainda não está disponível.</p>
            </div>
            <p className="platform-credit">Tecnologia <span className="platform-name" translate="no">Smart Commerce</span></p>
          </div>
        </footer>
      </body>
    </html>
  );
}
