import type { Metadata, Viewport } from "next";
import Link from "next/link";
import "./globals.css";
import { checkoutConfigured, hostedTestEnvironment } from "@/lib/checkout-input";

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
        {/* ADR-0014 H2: permanent notice in the hosted test environment. */}
        {hostedTestEnvironment(process.env.DEPLOYMENT_ENVIRONMENT) ? (
          <p className="test-environment-banner" role="note"><strong>Ambiente de teste.</strong> Dados fictícios e sem pagamentos reais. Não informe dados pessoais verdadeiros.</p>
        ) : null}
        <div className="site-content">{children}</div>
        <footer className="site-footer" id="sobre-a-vitrine">
          <div className="page-width footer-content">
            <div className="footer-about">
              <h2>Sobre esta vitrine</h2>
              <p>{!checkoutConfigured()
                ? "Produtos e preços publicados pela loja para você consultar. A compra por aqui ainda não está disponível."
                : hostedTestEnvironment(process.env.DEPLOYMENT_ENVIRONMENT)
                  ? "Produtos e preços publicados pela loja. Pedidos feitos aqui são de teste."
                  : "Produtos e preços publicados pela loja. O pagamento é feito no Mercado Pago."}</p>
            </div>
            <div className="platform-credit">
              <p>Tecnologia <span className="platform-name" translate="no">Smart Commerce</span></p>
              <p><Link href="/privacidade" prefetch={false}>Privacidade</Link></p>
            </div>
          </div>
        </footer>
      </body>
    </html>
  );
}
